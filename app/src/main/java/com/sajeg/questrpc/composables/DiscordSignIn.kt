package com.sajeg.questrpc.composables

import android.annotation.SuppressLint
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.ui.viewinterop.AndroidView

// Reads the token through a freshly created, same-origin iframe instead of the main
// window's localStorage. Discord's client patches window.localStorage.getItem on the
// main document specifically to break this well-known token-grabbing technique; a
// fresh iframe's contentWindow gets an unpatched Storage object that still reads the
// same per-origin data. Matches the approach used by dead8309/Rpc (built on the same
// KizzyRPC library this app uses), see DiscordLoginWebView.kt there.
private const val TOKEN_JS = """
    (function() {
        var i = document.createElement('iframe');
        document.body.appendChild(i);
        var t = i.contentWindow.localStorage.token;
        document.body.removeChild(i);
        return t ? t.slice(1, -1) : null;
    })();
"""

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun SignInDiscord(onDataRetrieved: (token: String?) -> Unit) {
    val url = "https://discord.com/login"
    AndroidView(factory = {
        WebView(it).apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                // Discord's web client fails to parse the Quest's WebView user agent
                // (it doesn't recognize the "Quest" build string), which can crash or
                // block the page before the login form ever renders. A standard mobile
                // Chrome UA makes Discord treat it as a normal supported browser.
                // See https://github.com/dead8309/Rpc/issues/345 for the same class of bug.
                userAgentString =
                    "Mozilla/5.0 (Linux; Android 14; SM-S921U; Build/UP1A.231005.007) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Mobile Safari/537.36"
            }
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )

            // The token only shows up in localStorage once the user finishes logging in,
            // which - since Discord's client is a single-page app - usually happens
            // without ever triggering another full page load/onPageFinished. So instead
            // of waiting for the URL to change to something recognizable, poll from the
            // first page load onward and keep polling (up to 5 minutes, enough time to
            // type credentials) until a token shows up. Before it exists,
            // evaluateJavascript reports it back as the *literal 4-character string*
            // "null" (not a Kotlin null) - poll instead of giving up (or crashing, as the
            // old substring-based parsing used to) on that.
            var started = false
            fun tryFetchToken(view: WebView, attemptsLeft: Int) {
                view.evaluateJavascript(TOKEN_JS) { value ->
                    // Never log `value` or the derived token: it's the user's live
                    // Discord auth token, and Logcat is readable by any app holding
                    // READ_LOGS or by anyone with adb access to the device.
                    if (value != null && value != "null" && value.length > 2) {
                        // Strip the one JSON-encoding quote layer evaluateJavascript adds
                        // around the string TOKEN_JS returned.
                        val token = value.substring(1, value.length - 1)
                        onDataRetrieved(token)
                        view.visibility = View.GONE
                        Log.d("FetchedToken", "Token fetched successfully")
                    } else if (attemptsLeft > 0) {
                        view.postDelayed({ tryFetchToken(view, attemptsLeft - 1) }, 1000)
                    } else {
                        Log.d("FetchedToken", "Failed to fetch token")
                    }
                }
            }

            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    if (view != null && !started) {
                        started = true
                        tryFetchToken(view, attemptsLeft = 300)
                    }
                }
            }

            loadUrl(url)
        }
    })
}
