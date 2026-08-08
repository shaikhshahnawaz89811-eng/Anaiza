package com.aidesktop.os.data.ai

import org.json.JSONObject

/**
 * Builds the JS body run inside WhatsApp Web (web.whatsapp.com) by
 * BrowserController.runAutomation. Everything here operates only on the DOM
 * of that one page, inside this app's own WebView — same as a user-script
 * extension would. It never touches anything outside that page.
 *
 * WhatsApp Web's internal class names/attributes change over time, so every
 * selector below has fallbacks and the script always calls `finish(...)`
 * with an honest status instead of silently doing nothing:
 *   - "not_logged_in"   -> QR code still showing, needs the user to scan it
 *   - "not_found"       -> no chat matched the name at all
 *   - "ambiguous"       -> more than one chat could match; lists the exact
 *                          titles found so the caller can ask the user which one
 *   - "sent"            -> message was typed and the send action was triggered
 *   - "send_failed"     -> chat opened but compose box/send control couldn't be found
 *   - "error" / "timeout" -> unexpected failure, reported rather than swallowed
 */
object WhatsAppAutomation {

    fun buildSendMessageScript(contactName: String, message: String): String {
        val contactLiteral = JSONObject.quote(contactName)
        val messageLiteral = JSONObject.quote(message)
        return """
            var CONTACT = $contactLiteral;
            var MESSAGE = $messageLiteral;
            var deadline = Date.now() + 18000;

            function isLoggedIn() {
                // Main chat list pane only exists once logged in.
                return !!(document.querySelector('[data-testid="chat-list"]') ||
                          document.querySelector('#pane-side'));
            }
            function isShowingQr() {
                return !!(document.querySelector('[data-testid="qrcode"]') ||
                          document.querySelector('canvas[aria-label*="scan" i]'));
            }
            function findSearchBox() {
                return document.querySelector('[data-testid="chat-list-search"]') ||
                       document.querySelector('div[contenteditable="true"][data-tab="3"]') ||
                       document.querySelector('#side [contenteditable="true"]');
            }
            function setEditableText(el, text) {
                el.focus();
                document.execCommand('selectAll', false, null);
                document.execCommand('delete', false, null);
                document.execCommand('insertText', false, text);
                el.dispatchEvent(new Event('input', {bubbles: true}));
            }
            function chatRowTitle(row) {
                var el = row.querySelector('[title]');
                return el ? el.getAttribute('title') : (row.innerText || '').split('\\n')[0];
            }
            function findChatRows() {
                var list = document.querySelector('[data-testid="chat-list"]') || document.querySelector('#pane-side');
                if (!list) return [];
                return Array.prototype.slice.call(list.querySelectorAll('[role="listitem"], [role="row"]'));
            }
            function findComposeBox() {
                return document.querySelector('[data-testid="conversation-compose-box-input"]') ||
                       document.querySelector('footer div[contenteditable="true"]');
            }
            function findSendButton() {
                return document.querySelector('[data-testid="send"]') ||
                       document.querySelector('button[aria-label="Send" i]') ||
                       document.querySelector('span[data-icon="send"]');
            }

            function step1_waitLoginAndSearch() {
                if (Date.now() > deadline) { finish({status: "error", reason: "timeout_waiting_for_load"}); return; }
                if (isShowingQr()) { finish({status: "not_logged_in"}); return; }
                if (!isLoggedIn()) { setTimeout(step1_waitLoginAndSearch, 400); return; }
                var box = findSearchBox();
                if (!box) { setTimeout(step1_waitLoginAndSearch, 400); return; }
                setEditableText(box, CONTACT);
                setTimeout(step2_readResults, 700);
            }

            function step2_readResults() {
                var rows = findChatRows();
                var titles = rows.map(chatRowTitle).filter(function(t) { return !!t; });
                var lowerContact = CONTACT.toLowerCase();
                var exact = [];
                var partial = [];
                for (var i = 0; i < rows.length; i++) {
                    var t = titles[i];
                    if (!t) continue;
                    var lt = t.toLowerCase();
                    if (lt === lowerContact) exact.push({row: rows[i], title: t});
                    else if (lt.indexOf(lowerContact) !== -1) partial.push({row: rows[i], title: t});
                }
                var candidates = exact.length > 0 ? exact : partial;
                if (candidates.length === 0) {
                    finish({status: "not_found", searched: CONTACT});
                    return;
                }
                if (candidates.length > 1 && exact.length !== 1) {
                    finish({
                        status: "ambiguous",
                        searched: CONTACT,
                        matches: candidates.map(function(c) { return c.title; }).slice(0, 6)
                    });
                    return;
                }
                var chosen = candidates[0];
                chosen.row.click();
                setTimeout(function() { step3_sendMessage(chosen.title); }, 700);
            }

            function step3_sendMessage(matchedTitle, attemptsLeft) {
                if (attemptsLeft === undefined) attemptsLeft = 15;
                var box = findComposeBox();
                if (!box) {
                    if (attemptsLeft <= 0) { finish({status: "send_failed", reason: "no_compose_box", matchedContact: matchedTitle}); return; }
                    setTimeout(function() { step3_sendMessage(matchedTitle, attemptsLeft - 1); }, 300);
                    return;
                }
                setEditableText(box, MESSAGE);
                setTimeout(function() { step4_confirmSend(matchedTitle); }, 300);
            }

            function step4_confirmSend(matchedTitle) {
                var sendBtn = findSendButton();
                if (sendBtn) {
                    sendBtn.click();
                } else {
                    var box = findComposeBox();
                    if (box) {
                        box.dispatchEvent(new KeyboardEvent('keydown', {key: 'Enter', code: 'Enter', keyCode: 13, which: 13, bubbles: true}));
                    } else {
                        finish({status: "send_failed", reason: "no_send_control", matchedContact: matchedTitle});
                        return;
                    }
                }
                finish({status: "sent", matchedContact: matchedTitle});
            }

            step1_waitLoginAndSearch();
        """.trimIndent()
    }
}
