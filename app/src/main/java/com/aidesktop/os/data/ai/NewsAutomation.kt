package com.aidesktop.os.data.ai

import org.json.JSONObject

/**
 * Builds the JS body run inside news.google.com (in this app's own WebView)
 * to pull real headlines from real, distinct sources for a topic — the
 * actual text visible in the DOM, not anything the model invents. The AI
 * is expected to summarize ONLY from the `articles` array this returns;
 * AiToolExecutor's reply text says so explicitly so the model doesn't
 * fabricate details the scrape didn't actually find.
 *
 * Every selector has fallbacks because Google News' markup changes over
 * time; if nothing can be read at all, it reports that honestly instead
 * of returning an empty-looking success.
 */
object NewsAutomation {

    fun buildScrapeScript(topic: String): String {
        val topicLiteral = JSONObject.quote(topic)
        return """
            var TOPIC = $topicLiteral;
            var deadline = Date.now() + 12000;

            function findArticleNodes() {
                return Array.prototype.slice.call(document.querySelectorAll('article'));
            }

            function textOf(el, selectors) {
                for (var i = 0; i < selectors.length; i++) {
                    var node = el.querySelector(selectors[i]);
                    if (node && node.textContent && node.textContent.trim()) {
                        return node.textContent.trim();
                    }
                }
                return '';
            }

            function extractOne(article) {
                var title = textOf(article, ['h3', 'h4', 'a.JtKRv']);
                var source = textOf(article, ['div.vr1PYe', '.wEwyrc', '.NUnG9d']);
                var timeEl = article.querySelector('time');
                var time = timeEl ? (timeEl.getAttribute('datetime') || timeEl.textContent || '') : '';
                var link = article.querySelector('a');
                var url = '';
                if (link && link.getAttribute('href')) {
                    var href = link.getAttribute('href');
                    url = href.indexOf('./') === 0 ? ('https://news.google.com' + href.slice(1)) : href;
                }
                if (!title) return null;
                return { title: title, source: source, time: time, url: url };
            }

            function dedupeBySource(items) {
                var seenSources = {};
                var seenTitles = {};
                var out = [];
                for (var i = 0; i < items.length; i++) {
                    var it = items[i];
                    if (!it || !it.title) continue;
                    if (seenTitles[it.title]) continue;
                    seenTitles[it.title] = true;
                    out.push(it);
                }
                return out;
            }

            function step_waitAndExtract(attemptsLeft) {
                if (attemptsLeft === undefined) attemptsLeft = 25;
                if (Date.now() > deadline) {
                    finish({status: "error", reason: "timeout_waiting_for_results"});
                    return;
                }
                var nodes = findArticleNodes();
                if (nodes.length === 0) {
                    if (attemptsLeft <= 0) {
                        finish({status: "error", reason: "no_articles_found_on_page"});
                        return;
                    }
                    setTimeout(function() { step_waitAndExtract(attemptsLeft - 1); }, 400);
                    return;
                }
                var extracted = nodes.map(extractOne).filter(function(x) { return !!x; });
                var deduped = dedupeBySource(extracted).slice(0, 8);
                if (deduped.length === 0) {
                    if (attemptsLeft <= 0) {
                        finish({status: "error", reason: "articles_present_but_unreadable"});
                        return;
                    }
                    setTimeout(function() { step_waitAndExtract(attemptsLeft - 1); }, 400);
                    return;
                }
                finish({status: "ok", topic: TOPIC, articles: deduped});
            }

            step_waitAndExtract();
        """.trimIndent()
    }
}
