package com.aidesktop.os.data.ai

import org.json.JSONObject

/**
 * Builds the JS body run inside youtube.com (in this app's own WebView) to
 * actually search, pick, and play a video — not just open the search page —
 * and to keep watching for ads for as long as that tab stays open, clicking
 * whatever skip/close control shows up whenever it shows up.
 *
 * The ad-watcher is a plain `setInterval` left running inside the page after
 * `finish(...)` is called once for the initial "started playing" result —
 * it does NOT need a round-trip to Kotlin per ad, which is what lets it react
 * immediately regardless of whether the skip button appears at second 1 or
 * second 5, or the ad is an unskippable-until-a-checkbox type, etc. Every
 * selector has fallbacks because YouTube's ad markup varies by ad type and
 * changes over time; when nothing matches, it does nothing rather than
 * clicking something wrong.
 */
object YouTubeAutomation {

    /**
     * Finds real, non-ad video result anchors on a /results search page,
     * covering BOTH markups YouTube currently serves: the older
     * `ytd-video-renderer`/`ytd-compact-video-renderer` (with a real
     * `#video-title` anchor) and the newer `yt-lockup-view-model` card
     * redesign YouTube has been rolling out through 2025-2026, which has NO
     * `#video-title` anchor at all — a script that only looks for the old
     * markup finds zero results on that redesign and silently does nothing,
     * which is exactly the "search happens but nothing plays" symptom. The
     * fallback is kept generic (any real `/watch` link inside a
     * `yt-lockup-view-model` card) rather than pinned to a specific class
     * name, since YouTube keeps renaming those classes across redesigns.
     * Returns the same flat list of anchors either way so every caller
     * (preview search, click-to-play) works unchanged regardless of which
     * markup the current page actually has.
     */
    private const val findVideoAnchorsJs = """
        function isAdRenderer(el) {
            return !!el.closest('ytd-promoted-video-renderer, ytd-display-ad-renderer, ytd-ad-slot-renderer, ytd-in-feed-ad-layout-renderer');
        }
        function findVideoAnchors() {
            var primary = Array.prototype.slice.call(
                document.querySelectorAll('ytd-video-renderer a#video-title, ytd-compact-video-renderer a#video-title')
            ).filter(function(el) { return !isAdRenderer(el) && !!el.href; });
            if (primary.length > 0) return primary;

            // Newer redesign fallback: no #video-title anchor exists, so pick
            // the real watch-page link out of each yt-lockup-view-model card.
            var lockups = Array.prototype.slice.call(document.querySelectorAll('yt-lockup-view-model'));
            var fallback = [];
            for (var i = 0; i < lockups.length; i++) {
                if (isAdRenderer(lockups[i])) continue;
                var a = lockups[i].querySelector('a[href^="/watch"]');
                if (a) fallback.push(a);
            }
            return fallback;
        }
        function titleForAnchor(el) {
            var explicit = el.getAttribute('title');
            if (explicit) return explicit;
            // Old markup: the anchor itself IS the title text. New markup:
            // the matched anchor may just wrap the thumbnail image with no
            // text, so look for the real title element inside the same card.
            if ((el.textContent || '').trim().length > 0) return el.textContent;
            var card = el.closest('yt-lockup-view-model');
            var titleEl = card ? card.querySelector(
                '.yt-lockup-metadata-view-model-wiz__title, .ytLockupMetadataViewModelTitle, h3, [role="heading"]'
            ) : null;
            return titleEl ? titleEl.textContent : '';
        }
        function channelForAnchor(el) {
            var renderer = el.closest('ytd-video-renderer, ytd-compact-video-renderer, yt-lockup-view-model');
            var channelEl = renderer ? renderer.querySelector('ytd-channel-name #text, ytd-channel-name a, .yt-content-metadata-view-model-wiz__metadata-text') : null;
            return channelEl ? (channelEl.textContent || '').trim() : '';
        }
    """

    /**
     * The real, shared ad-skip loop. Idempotent via a window-level flag, so
     * it is always safe to inject repeatedly (once per YouTube page load, or
     * again right after a click-to-play) without ever stacking up duplicate
     * `setInterval`s. Installing it is exactly one call — `installAdWatcher()`
     * — used both by the AI's own play scripts below AND by an automatic
     * per-page-load injection (see BrowserWindowContent's onPageFinished),
     * so ad-skip works the same way whether a video was started by the AI
     * or by the user tapping around YouTube themselves.
     */
    const val adWatcherInstallJs = """
        function installAdWatcher() {
            if (window.__aiDesktopAdWatcherInstalled) return;
            window.__aiDesktopAdWatcherInstalled = true;
            setInterval(function() {
                var skipSelectors = [
                    '.ytp-ad-skip-button-modern',
                    '.ytp-ad-skip-button',
                    '.ytp-skip-ad-button',
                    'button.ytp-ad-overlay-close-button',
                    '.ytp-ad-overlay-close-button'
                ];
                for (var i = 0; i < skipSelectors.length; i++) {
                    var btn = document.querySelector(skipSelectors[i]);
                    if (btn && btn.offsetParent !== null) { btn.click(); return; }
                }
                var box = document.querySelector('.ytp-ad-checkbox, input[type="checkbox"].ytp-ad-checkbox');
                if (box && box.offsetParent !== null && !box.checked) { box.click(); }
            }, 700);
        }
    """

    /**
     * Scrapes the top real, non-ad video results for a query WITHOUT clicking
     * anything — real titles/channels/URLs as they actually appear on the
     * results page, used so the AI can show the user real options to confirm
     * (e.g. for a named movie/show/anime) before anything plays.
     */
    fun buildPreviewSearchScript(query: String): String {
        val queryLiteral = JSONObject.quote(query)
        return """
            $findVideoAnchorsJs
            var QUERY = $queryLiteral;
            var deadline = Date.now() + 12000;

            function isSearchResultsPage() {
                return location.href.indexOf('/results') !== -1;
            }

            function findRealVideoLinks() {
                var candidates = findVideoAnchors();
                var out = [];
                for (var i = 0; i < candidates.length; i++) {
                    var el = candidates[i];
                    out.push({
                        title: titleForAnchor(el).trim().slice(0, 140),
                        channel: channelForAnchor(el),
                        url: el.href
                    });
                    if (out.length >= 5) break;
                }
                return out;
            }

            function step_waitAndExtract(attemptsLeft) {
                if (attemptsLeft === undefined) attemptsLeft = 25;
                if (Date.now() > deadline) { finish({status: "error", reason: "timeout_waiting_for_results"}); return; }
                if (!isSearchResultsPage()) { setTimeout(function() { step_waitAndExtract(attemptsLeft - 1); }, 300); return; }
                var results = findRealVideoLinks();
                if (results.length === 0) {
                    if (attemptsLeft <= 0) { finish({status: "error", reason: "no_results_found"}); return; }
                    setTimeout(function() { step_waitAndExtract(attemptsLeft - 1); }, 400);
                    return;
                }
                finish({status: "ok", query: QUERY, results: results});
            }

            step_waitAndExtract();
        """.trimIndent()
    }

    /**
     * Plays a specific, already-known video URL directly (no search step) —
     * used after the user has confirmed which real search result they meant.
     * Reuses the same honest ad-watcher as buildPlayScript.
     */
    fun buildPlayUrlScript(url: String): String {
        return """
            $adWatcherInstallJs
            var deadline = Date.now() + 15000;

            function isWatchPage() {
                return location.href.indexOf('/watch') !== -1;
            }

            function step_confirmPlaying(attemptsLeft) {
                if (attemptsLeft === undefined) attemptsLeft = 25;
                if (Date.now() > deadline) { finish({status: "error", reason: "timeout_waiting_for_watch_page"}); return; }
                if (!isWatchPage()) {
                    if (attemptsLeft <= 0) { finish({status: "error", reason: "did_not_reach_watch_page"}); return; }
                    setTimeout(function() { step_confirmPlaying(attemptsLeft - 1); }, 300);
                    return;
                }
                var player = document.querySelector('video.html5-main-video, video');
                if (!player) {
                    if (attemptsLeft <= 0) { finish({status: "error", reason: "no_video_element"}); return; }
                    setTimeout(function() { step_confirmPlaying(attemptsLeft - 1); }, 300);
                    return;
                }
                var titleEl = document.querySelector('h1.ytd-watch-metadata yt-formatted-string, h1.title yt-formatted-string');
                if (player.paused) { player.play().catch(function() {}); }
                installAdWatcher();
                finish({status: "playing", title: (titleEl ? titleEl.textContent : '').trim().slice(0, 140)});
            }

            step_confirmPlaying();
        """.trimIndent()
    }

    fun buildPlayScript(query: String): String {
        val queryLiteral = JSONObject.quote(query)
        return """
            $findVideoAnchorsJs
            $adWatcherInstallJs
            var QUERY = $queryLiteral;
            var deadline = Date.now() + 15000;

            function isSearchResultsPage() {
                return location.href.indexOf('/results') !== -1;
            }
            function isWatchPage() {
                return location.href.indexOf('/watch') !== -1;
            }

            function findFirstRealVideo() {
                var candidates = findVideoAnchors();
                return candidates.length > 0 ? candidates[0] : null;
            }

            function step1_waitResultsAndClick() {
                if (Date.now() > deadline) { finish({status: "error", reason: "timeout_waiting_for_results"}); return; }
                if (!isSearchResultsPage()) { setTimeout(step1_waitResultsAndClick, 300); return; }
                var video = findFirstRealVideo();
                if (!video) { setTimeout(step1_waitResultsAndClick, 400); return; }
                var title = titleForAnchor(video) || 'video';
                video.click();
                setTimeout(function() { step2_confirmPlaying(title); }, 900);
            }

            function step2_confirmPlaying(title, attemptsLeft) {
                if (attemptsLeft === undefined) attemptsLeft = 20;
                if (!isWatchPage()) {
                    if (attemptsLeft <= 0) { finish({status: "error", reason: "did_not_reach_watch_page"}); return; }
                    setTimeout(function() { step2_confirmPlaying(title, attemptsLeft - 1); }, 300);
                    return;
                }
                var player = document.querySelector('video.html5-main-video, video');
                if (!player) {
                    if (attemptsLeft <= 0) { finish({status: "error", reason: "no_video_element"}); return; }
                    setTimeout(function() { step2_confirmPlaying(title, attemptsLeft - 1); }, 300);
                    return;
                }
                if (player.paused) { player.play().catch(function() {}); }
                installAdWatcher();
                finish({status: "playing", title: title.trim().slice(0, 120)});
            }

            step1_waitResultsAndClick();
        """.trimIndent()
    }
}
