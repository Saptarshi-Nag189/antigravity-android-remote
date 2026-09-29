package com.sapta.antigravity.remote.bridge

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.webkit.WebView

/**
 * ChatCommandInjector
 *
 * Dispatches slash commands and system prompts into the active web session's
 * chat textarea via Shadow DOM traversal and synthetic input events, with clipboard fallback.
 */
object ChatCommandInjector {

    fun injectAccountExtractor(webView: WebView) {
        val accountExtractorJs = """
            (function() {
                function extractData() {
                    try {
                        var name = '';
                        var email = '';
                        var avatarUrl = '';
                        var isPro = false;

                        var accountButtons = document.querySelectorAll(
                            'button[aria-label*="Google Account" i], button[aria-label*="@" i], [aria-label*="Account Information" i], [data-identifier*="@" i], [aria-label*="Profile" i]'
                        );

                        for (var i = 0; i < accountButtons.length; i++) {
                            var btn = accountButtons[i];
                            var label = btn.getAttribute('aria-label') || '';
                            var emailMatch = label.match(/([a-zA-Z0-9._-]+@[a-zA-Z0-9._-]+\.[a-zA-Z0-9._-]+)/i);
                            if (emailMatch && !email) {
                                email = emailMatch[1];
                            }
                            var cleanLabel = label.replace(/Google Account:?/i, '').replace(email, '').trim();
                            if (cleanLabel.length > 2 && !name) {
                                name = cleanLabel.split('\n')[0].replace(/\(.*?\)/g, '').trim();
                            }

                            var img = btn.querySelector('img');
                            if (img && img.src && !avatarUrl) {
                                avatarUrl = img.src;
                            }
                        }

                        if (!email) {
                            var allElements = document.querySelectorAll('*');
                            for (var e = 0; e < allElements.length; e++) {
                                var elemText = allElements[e].innerText || '';
                                if (elemText.length < 120) {
                                    var match = elemText.match(/([a-zA-Z0-9._-]+@[a-zA-Z0-9._-]+\.[a-zA-Z0-9._-]+)/i);
                                    if (match) {
                                        email = match[1];
                                        break;
                                    }
                                }
                            }
                        }

                        if (!avatarUrl) {
                            var imgs = document.querySelectorAll('img[src*="googleusercontent.com"], img[src*="ggpht.com"]');
                            for (var j = 0; j < imgs.length; j++) {
                                if (imgs[j].src) {
                                    avatarUrl = imgs[j].src;
                                    break;
                                }
                            }
                        }

                        var bodyText = document.body ? document.body.innerText : '';
                        var proElements = document.querySelectorAll('.pro-badge, [aria-label*="Pro" i], [class*="badge" i], [class*="pro" i], [data-plan*="pro" i]');
                        for (var k = 0; k < proElements.length; k++) {
                            var txt = (proElements[k].innerText || proElements[k].textContent || '').trim().toLowerCase();
                            if (txt === 'pro' || txt.includes('pro tier') || txt.includes('antigravity pro') || txt.includes('gemini advanced')) {
                                isPro = true;
                                break;
                            }
                        }
                        if (!isPro && (window.location.href.indexOf('tier=pro') !== -1 || /antigravity\s+pro/i.test(bodyText))) {
                            isPro = true;
                        }

                        if (window.AntigravityNativeBridge && (name || email || avatarUrl)) {
                            window.AntigravityNativeBridge.onAccountInfoExtracted(name, email, avatarUrl, isPro);
                        }
                    } catch(e) {}
                }

                extractData();
                setTimeout(extractData, 1500);
                setTimeout(extractData, 4000);

                if (window.MutationObserver && document.body) {
                    var observer = new MutationObserver(function() {
                        extractData();
                    });
                    observer.observe(document.body, { childList: true, subtree: true, attributes: true, attributeFilter: ['aria-label', 'src'] });
                }
            })();
        """.trimIndent()
        webView.evaluateJavascript(accountExtractorJs, null)
    }

    fun insertCommandIntoChat(context: Context, webView: WebView, cmd: String) {
        // 1. Copy to clipboard for immediate user fallback
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("antigravity_command", cmd)
            clipboard.setPrimaryClip(clip)
        } catch (_: Exception) {}

        // 2. DOM text placement
        val escaped = cmd.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n").replace("\r", "")
        val js = """
            (function() {
                var text = '$escaped';

                function collectAllElements(root) {
                    var list = [];
                    function walk(node) {
                        if (!node) return;
                        if (node.nodeType === 1) {
                            list.push(node);
                            if (node.shadowRoot) {
                                walk(node.shadowRoot);
                            }
                        }
                        var ch = node.children || node.childNodes;
                        for (var i = 0; ch && i < ch.length; i++) {
                            walk(ch[i]);
                        }
                    }
                    walk(root);
                    return list;
                }

                function findPromptTarget() {
                    var candidates = [];
                    var allNodes = collectAllElements(document);

                    var iframes = document.querySelectorAll('iframe');
                    for (var f = 0; f < iframes.length; f++) {
                        try {
                            var fDoc = iframes[f].contentDocument || iframes[f].contentWindow.document;
                            if (fDoc) {
                                allNodes = allNodes.concat(collectAllElements(fDoc));
                            }
                        } catch(e) {}
                    }

                    for (var i = 0; i < allNodes.length; i++) {
                        var el = allNodes[i];
                        var tag = el.tagName.toLowerCase();
                        if (tag === 'body' || tag === 'html') continue;

                        var isCe = el.isContentEditable || el.getAttribute('contenteditable') === 'true';
                        var role = (el.getAttribute('role') || '').toLowerCase();
                        var aria = (el.getAttribute('aria-label') || '').toLowerCase();
                        var ph = (el.getAttribute('placeholder') || el.getAttribute('data-placeholder') || '').toLowerCase();
                        var cls = (typeof el.className === 'string') ? el.className.toLowerCase() : '';

                        var isCandidate = (
                            isCe ||
                            tag === 'textarea' ||
                            role === 'textbox' ||
                            role === 'combobox' ||
                            cls.includes('prosemirror') ||
                            cls.includes('ql-editor') ||
                            cls.includes('rich-textarea') ||
                            cls.includes('input-area') ||
                            aria.includes('prompt') || aria.includes('message') || aria.includes('ask') || aria.includes('chat') ||
                            ph.includes('prompt') || ph.includes('message') || ph.includes('ask') || ph.includes('chat') || ph.includes('type')
                        );

                        if (isCandidate) {
                            var rect = el.getBoundingClientRect();
                            if (rect.width > 20 && rect.height > 15) {
                                candidates.push({ el: el, rect: rect, top: rect.top, area: rect.width * rect.height });
                            }
                        }
                    }

                    if (candidates.length === 0) return null;
                    candidates.sort(function(a, b) { return b.top - a.top; });
                    return candidates[0].el;
                }

                var target = findPromptTarget();
                if (!target) return false;

                try {
                    target.focus();
                    target.click();

                    if (target.tagName.toLowerCase() === 'textarea' || target.tagName.toLowerCase() === 'input') {
                        target.value = text;
                        target.dispatchEvent(new Event('input', { bubbles: true, cancelable: true }));
                        target.dispatchEvent(new Event('change', { bubbles: true, cancelable: true }));
                    } else if (target.isContentEditable || target.getAttribute('contenteditable') === 'true') {
                        document.execCommand('selectAll', false, null);
                        var inserted = document.execCommand('insertText', false, text);
                        if (!inserted) {
                            target.innerText = text;
                            target.dispatchEvent(new InputEvent('input', { bubbles: true, cancelable: true, data: text }));
                        }
                    }
                    return true;
                } catch(err) {
                    return false;
                }
            })();
        """.trimIndent()

        webView.evaluateJavascript(js, null)
    }
}
