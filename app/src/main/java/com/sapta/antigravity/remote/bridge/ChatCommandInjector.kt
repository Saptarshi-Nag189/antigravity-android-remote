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

                        // 1. Scan for Google Account profile anchors and buttons
                        var accountElements = document.querySelectorAll([
                            '[aria-label*="Google Account" i]',
                            'a[href*="SignOutOptions"]',
                            'a[href*="accounts.google.com"]',
                            '[data-identifier*="@" i]',
                            '[aria-label*="@" i]',
                            'button[aria-label*="Account" i]',
                            'a[aria-label*="Account" i]',
                            '[role="button"][aria-label*="Account" i]',
                            'header [role="button"]'
                        ].join(', '));

                        for (var i = 0; i < accountElements.length; i++) {
                            var el = accountElements[i];
                            var label = el.getAttribute('aria-label') || el.getAttribute('title') || '';

                            if (!email && label) {
                                var emailMatch = label.match(/([a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,})/i);
                                if (emailMatch) {
                                    email = emailMatch[1].trim();
                                }
                            }

                            if (!email) {
                                var dataId = el.getAttribute('data-identifier') || el.getAttribute('data-email') || '';
                                if (dataId && dataId.indexOf('@') !== -1) {
                                    email = dataId.trim();
                                }
                            }

                            if (!name && label) {
                                var raw = label.replace(/^Google Account:?\s*/i, '').trim();
                                var candidate = raw.split('\n')[0].split('(')[0].split(',')[0].trim();
                                if (candidate.length >= 2 &&
                                    candidate.toLowerCase() !== 'google account' &&
                                    candidate.toLowerCase() !== 'profile' &&
                                    !candidate.includes('@')) {
                                    name = candidate;
                                }
                            }

                            if (!avatarUrl) {
                                var imgs = el.querySelectorAll('img');
                                for (var j = 0; j < imgs.length; j++) {
                                    var src = imgs[j].src || imgs[j].getAttribute('src') || '';
                                    if (src.indexOf('googleusercontent.com') !== -1 || src.indexOf('ggpht.com') !== -1) {
                                        avatarUrl = src;
                                        break;
                                    }
                                }
                            }
                        }

                        // 2. Fallback scan for user email in document
                        if (!email) {
                            var emailMeta = document.querySelector('meta[name="user-email"], [data-user-email], [data-email]');
                            if (emailMeta) {
                                email = emailMeta.getAttribute('content') || emailMeta.getAttribute('data-user-email') || emailMeta.getAttribute('data-email') || '';
                            }
                        }
                        if (!email) {
                            var mailAnchors = document.querySelectorAll('a[href^="mailto:"]');
                            if (mailAnchors.length > 0) {
                                var m = mailAnchors[0].href.replace(/^mailto:/i, '').split('?')[0].trim();
                                if (m.indexOf('@') !== -1) email = m;
                            }
                        }
                        if (!email) {
                            var allElems = document.querySelectorAll('[aria-label], [title]');
                            for (var e = 0; e < allElems.length; e++) {
                                var text = (allElems[e].getAttribute('aria-label') || '') + ' ' + (allElems[e].getAttribute('title') || '');
                                var match = text.match(/([a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,})/i);
                                if (match) {
                                    email = match[1].trim();
                                    break;
                                }
                            }
                        }

                        // 3. Fallback scan for user name in document
                        if (!name) {
                            var headings = document.querySelectorAll('h1, h2, [role="heading"], .user-name, [data-user-name]');
                            for (var h = 0; h < headings.length; h++) {
                                var hText = (headings[h].innerText || headings[h].textContent || '').trim();
                                var welcomeMatch = hText.match(/Welcome,\s*([A-Za-z\s]+)/i);
                                if (welcomeMatch && welcomeMatch[1].trim().length > 1) {
                                    name = welcomeMatch[1].trim();
                                    break;
                                }
                            }
                        }

                        // 4. Fallback scan for avatar URL
                        if (!avatarUrl) {
                            var allImgs = document.querySelectorAll('img[src*="googleusercontent.com"], img[src*="ggpht.com"]');
                            for (var k = 0; k < allImgs.length; k++) {
                                var s = allImgs[k].src || allImgs[k].getAttribute('src') || '';
                                if (s.indexOf('/a/') !== -1 || s.indexOf('photo') !== -1 || s.indexOf('/ogw/') !== -1 ||
                                    allImgs[k].classList.contains('gb_X') || allImgs[k].classList.contains('gbii')) {
                                    avatarUrl = s;
                                    break;
                                }
                            }
                            if (!avatarUrl && allImgs.length > 0) {
                                avatarUrl = allImgs[0].src;
                            }
                        }

                        // 5. Upgrade low-res avatar URL to high resolution (=s256-c-mo)
                        if (avatarUrl && avatarUrl.indexOf('googleusercontent.com') !== -1) {
                            avatarUrl = avatarUrl.replace(/=s\d+(-[a-z0-9]+)*/i, '=s256-c-mo');
                        }

                        // 6. Pro / Subscription Detection
                        var bodyText = document.body ? (document.body.innerText || '') : '';
                        var proElements = document.querySelectorAll('.pro-badge, [aria-label*="Pro" i], [class*="badge" i], [class*="pro" i], [data-plan*="pro" i]');
                        for (var p = 0; p < proElements.length; p++) {
                            var pTxt = (proElements[p].innerText || proElements[p].textContent || '').trim().toLowerCase();
                            if (pTxt === 'pro' || pTxt.includes('pro tier') || pTxt.includes('antigravity pro') || pTxt.includes('gemini advanced') || pTxt.includes('google one')) {
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
                setTimeout(extractData, 500);
                setTimeout(extractData, 1500);
                setTimeout(extractData, 3500);
                setTimeout(extractData, 6000);

                if (window.MutationObserver && document.body) {
                    var observer = new MutationObserver(function() {
                        extractData();
                    });
                    observer.observe(document.body, { childList: true, subtree: true, attributes: true, attributeFilter: ['aria-label', 'src', 'title'] });
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
