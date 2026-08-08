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
     * Scrapes the top real, non-ad video results for a query WITHOUT clicking
     * anything — real titles/channels/URLs as they actually appear on the
     * results page, used so the AI can show the user real options to confirm
     * (e.g. for a named movie/show/anime) before anything plays.
     */
    fun buildPreviewSearchScript(query: String): String {
        val queryLiteral = JSONObject.quote(query)
        return """
            var QUERY = $queryLiteral;
            var deadline = Date.now() + 12000;

            function isSearchResultsPage() {
                return location.href.indexOf('/results') !== -1;
            }

            function findRealVideoLinks() {
                var candidates = Array.prototype.slice.call(
                    document.querySelectorAll('ytd-video-renderer a#video-title')
                );
                var out = [];
                for (var i = 0; i < candidates.length; i++) {
                    var el = candidates[i];
                    var isAd = el.closest('ytd-promoted-video-renderer, ytd-display-ad-renderer, ytd-ad-slot-renderer, ytd-in-feed-ad-layout-renderer');
                    if (isAd || !el.href) continue;
                    var renderer = el.closest('ytd-video-renderer');
                    var channelEl = renderer ? renderer.querySelector('ytd-channel-name #text, ytd-channel-name a') : null;
                    out.push({
                        title: (el.getAttribute('title') || el.textContent || '').trim().slice(0, 140),
                        channel: channelEl ? (channelEl.textContent || '').trim() : '',
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
            var deadline = Date.now() + 15000;

            function isWatchPage() {
                return location.href.indexOf('/watch') !== -1;
            }

            function startAdWatcher() {
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
                startAdWatcher();
                finish({status: "playing", title: (titleEl ? titleEl.textContent : '').trim().slice(0, 140)});
            }

            step_confirmPlaying();
        """.trimIndent()
    }

    fun buildPlayScript(query: String): String {
        val queryLiteral = JSONObject.quote(query)
        return """
            var QUERY = $queryLiteral;
            var deadline = Date.now() + 15000;

            function isSearchResultsPage() {
                return location.href.indexOf('/results') !== -1;
            }
            function isWatchPage() {
                return location.href.indexOf('/watch') !== -1;
            }

            function findFirstRealVideo() {
                // Real organic video results only — explicitly skip ad slots and
                // the "People also watched" ad shelf, which use different tags.
                var candidates = Array.prototype.slice.call(
                    document.querySelectorAll('ytd-video-renderer a#video-title, ytd-compact-video-renderer a#video-title')
                );
                for (var i = 0; i < candidates.length; i++) {
                    var el = candidates[i];
                    var isAd = el.closest('ytd-promoted-video-renderer, ytd-display-ad-renderer, ytd-ad-slot-renderer, ytd-in-feed-ad-layout-renderer');
                    if (!isAd && el.href) return el;
                }
                return null;
            }

            function startAdWatcher() {
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
                    // Some ad units require a checkbox/consent tick before the skip
                    // control becomes clickable — tick it if present so skip can follow
                    // on the next interval tick, but never click anything else blind.
                    var box = document.querySelector('.ytp-ad-checkbox, input[type="checkbox"].ytp-ad-checkbox');
                    if (box && box.offsetParent !== null && !box.checked) { box.click(); }
                }, 700);
            }

            function step1_waitResultsAndClick() {
                if (Date.now() > deadline) { finish({status: "error", reason: "timeout_waiting_for_results"}); return; }
                if (!isSearchResultsPage()) { setTimeout(step1_waitResultsAndClick, 300); return; }
                var video = findFirstRealVideo();
                if (!video) { setTimeout(step1_waitResultsAndClick, 400); return; }
                var title = video.getAttribute('title') || video.textContent || 'video';
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
                startAdWatcher();
                finish({status: "playing", title: title.trim().slice(0, 120)});
            }

            step1_waitResultsAndClick();
        """.trimIndent()
    }
}
