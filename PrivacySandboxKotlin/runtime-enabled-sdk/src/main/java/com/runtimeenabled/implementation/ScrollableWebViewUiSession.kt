package com.runtimeenabled.implementation

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.webkit.CookieManager
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.privacysandbox.sdkruntime.core.controller.SdkSandboxControllerCompat
import androidx.privacysandbox.ui.core.SandboxedUiAdapter
import androidx.privacysandbox.ui.provider.AbstractSandboxedUiAdapter
import com.runtimeenabled.api.SdkBannerRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import java.util.concurrent.Executor

/**
 * Implementation of [SandboxedUiAdapter.Session], used for banner ad requests.
 * This class extends [AbstractSandboxedUiAdapter.AbstractSession] to provide the functionality in
 * cohesion with [AbstractSandboxedUiAdapter]
 *
 * @param clientExecutor The executor to use for client callbacks.
 * @param sdkContext The context of the SDK.
 * @param request The banner ad request.
 * @param mediateeAdapter The UI adapter for a mediatee SDK, if applicable.
 */
class ScrollableWebViewUiSession(
    private val clientExecutor: Executor,
    private val sdkContext: Context,
    private val request: SdkBannerRequest,
    private val client: SandboxedUiAdapter.SessionClient,
) : AbstractSandboxedUiAdapter.AbstractSession() {

    /** A scope for launching coroutines in the client executor. */
    private val scope = CoroutineScope(clientExecutor.asCoroutineDispatcher() + Job())

    override val view: View = createWebViewAd()

    lateinit var webView: WebView

    private var shouldScrollWebView = false

    fun onScrollableCondition() {
        shouldScrollWebView = true
    }

    @SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
    private fun createWebViewAd(): WebView {
        webView = WebView(sdkContext)
        val webViewSetting = webView.settings
        webViewSetting.loadsImagesAutomatically = true
        webViewSetting.loadWithOverviewMode = true
        webViewSetting.javaScriptEnabled = true
        webViewSetting.domStorageEnabled = true
        webViewSetting.useWideViewPort = true

        WebView.setWebContentsDebuggingEnabled(true)
        webViewSetting.cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK
        CookieManager.getInstance().setAcceptCookie(true)
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                // Get the content height of the web page
                view?.evaluateJavascript("document.body.scrollHeight",
                    ValueCallback { value ->
                        val displayMetrics = sdkContext.resources.displayMetrics
                        val contentHeight = value.toIntOrNull() ?: displayMetrics.heightPixels
//                        clientExecutor.execute {
//                            client.onResizeRequested(displayMetrics.widthPixels, contentHeight)
//                        }
                        Log.d("RE_SDK", "Height of the scrollable web content: $contentHeight")
                    })
            }
        }
        webView.webChromeClient = WebChromeClient()

        webView.loadUrl("https://privacysandbox.google.com/blog")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            webView.setOnScrollChangeListener { v, scrollX, scrollY, oldScrollX, oldScrollY ->
                val deltaY = scrollY - oldScrollY
                // If we are at the top and were scrolling up (or no scroll), disable further webview scrolling.
                if (webView.scrollY == 0 && deltaY <= 0 && shouldScrollWebView) {
                    shouldScrollWebView = false
                }
           }
        }
        webView.setOnTouchListener { v, event ->
            scrollSwitchIfScrollable(event)
            false
        }
        return webView
    }

    private fun scrollSwitchIfScrollable(event: MotionEvent) {
        when (event.action) {
            MotionEvent.ACTION_DOWN ->
                webView.requestDisallowInterceptTouchEvent(shouldScrollWebView)
        }
    }

    override fun close() {
        // Notifies that the client has closed the session. It's a good opportunity to dispose
        // any resources that were acquired to maintain the session.
        scope.cancel()
    }

    override fun notifyConfigurationChanged(configuration: Configuration) {
        // Notifies that the device configuration has changed and affected the app.
    }

    override fun notifyResized(width: Int, height: Int) {
        // Notifies that the size of the presentation area in the app has changed.
    }

    override fun notifyUiChanged(uiContainerInfo: Bundle) {
        // Notify the session when the presentation state of its UI container has changed.
    }

    override fun notifyZOrderChanged(isZOrderOnTop: Boolean) {
        // Notifies that the Z order has changed for the UI associated by this session.
    }
}