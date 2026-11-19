package com.steeben.dial.tube

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Keeps ONE WebView alive for the whole app session, so switching tabs does not reload
 * YouTube or interrupt a playing video.
 */
object TubeWebHolder {

    private const val HOME = "https://m.youtube.com"
    private val internalHosts = listOf(
        "youtube.com", "youtu.be", "google.com", "gstatic.com", "googlevideo.com", "ytimg.com",
    )

    private var webView: WebView? = null
    private var wrapper: android.content.MutableContextWrapper? = null
    private var customView: View? = null
    private var customCallback: WebChromeClient.CustomViewCallback? = null

    var progress by mutableIntStateOf(100)
        private set
    var canGoBack by mutableStateOf(false)
        private set
    var isFullscreen by mutableStateOf(false)
        private set

    @SuppressLint("SetJavaScriptEnabled")
    fun attach(activityContext: Context): WebView {
        val existing = webView
        if (existing != null) {
            wrapper?.baseContext = activityContext
            (existing.parent as? ViewGroup)?.removeView(existing)
            return existing
        }

        val w = android.content.MutableContextWrapper(activityContext)
        wrapper = w
        val wv = WebView(w)
        webView = wv

        wv.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            loadWithOverviewMode = true
            useWideViewPort = true
            // Drop the "; wv" marker so YouTube serves its normal mobile site.
            userAgentString = userAgentString.replace("; wv", "")
        }
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(wv, true)
        }

        wv.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val host = request.url.host.orEmpty()
                if (internalHosts.any { host == it || host.endsWith(".$it") }) return false
                runCatching {
                    view.context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(request.url.toString()))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
                return true
            }

            override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
                view.evaluateJavascript(TubeScripts.build(), null)
            }

            override fun onPageFinished(view: WebView, url: String?) {
                view.evaluateJavascript(TubeScripts.build(), null)
                canGoBack = view.canGoBack()
            }

            override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
                canGoBack = view.canGoBack()
            }
        }

        wv.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                progress = newProgress
            }

            override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                val activity = wrapper?.baseContext.findActivity() ?: return
                customView = view
                customCallback = callback
                val decor = activity.window.decorView as FrameLayout
                decor.addView(
                    view,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                )
                WindowInsetsControllerCompat(activity.window, decor).apply {
                    systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    hide(WindowInsetsCompat.Type.systemBars())
                }
                isFullscreen = true
            }

            override fun onHideCustomView() = exitFullscreen()
        }

        wv.loadUrl(HOME)
        return wv
    }

    fun exitFullscreen() {
        val activity = wrapper?.baseContext.findActivity()
        val view = customView
        if (activity != null && view != null) {
            (activity.window.decorView as FrameLayout).removeView(view)
            WindowCompat.getInsetsController(activity.window, activity.window.decorView)
                .show(WindowInsetsCompat.Type.systemBars())
        }
        customCallback?.onCustomViewHidden()
        customView = null
        customCallback = null
        isFullscreen = false
    }

    fun goBack() { webView?.goBack() }

    /** Called when the tab leaves the screen. The page keeps running (audio continues). */
    fun detach(appContext: Context) {
        wrapper?.baseContext = appContext.applicationContext
        (webView?.parent as? ViewGroup)?.removeView(webView)
    }

    private fun Context?.findActivity(): Activity? {
        var c = this
        while (c is ContextWrapper) {
            if (c is Activity) return c
            c = c.baseContext
        }
        return null
    }
}
