package com.runtimeenabled.implementation

import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.privacysandbox.ui.core.SandboxedSdkViewUiInfo
import androidx.privacysandbox.ui.core.SandboxedUiAdapter
import androidx.privacysandbox.ui.core.SessionConstants
import androidx.privacysandbox.ui.core.SessionObserver
import androidx.privacysandbox.ui.core.SessionObserverContext
import androidx.privacysandbox.ui.core.SessionObserverFactory
import androidx.privacysandbox.ui.provider.AbstractSandboxedUiAdapter
import com.runtimeenabled.api.SdkBannerRequest
import com.runtimeenabled.api.SdkSandboxedUiAdapter
import java.util.concurrent.Executor

/**
 * Implementation of [com.runtimeenabled.api.SdkSandboxedUiAdapter] that handles banner ad requests.
 *
 * This class extends [androidx.privacysandbox.ui.provider.AbstractSandboxedUiAdapter] and provides the functionality to open
 * UI sessions. The usage of [androidx.privacysandbox.ui.provider.AbstractSandboxedUiAdapter] simplifies the implementation.
 *
 * @param sdkContext The context of the SDK.
 * @param request The banner ad request.
 * @param mediateeAdapter The UI adapter for a mediatee SDK, if applicable.
 */
class ScrollableWebViewUiAdapter(
    private val sdkContext: Context,
    private val request: SdkBannerRequest
) : AbstractSandboxedUiAdapter(), SdkSandboxedUiAdapter {
    /**
     * Opens a new session to display remote UI.
     * The session will handle notifications from and to the client.
     * We consider the client the owner of the SandboxedSdkView.
     *
     @param context The client's context.
     * @param sessionConstants Constants related to the session, such as the presentation id.
     * @param initialWidth The initial width of the adapter's view.
     * @param initialHeight The initial height of the adapter's view.
     * @param isZOrderOnTop Whether the session's view should be drawn on top of other views.
     * @param clientExecutor The executor to use for client callbacks.
     * @param client A UI adapter representing the client of this single session.
     */
    override fun openSession(
        context: Context,
        sessionConstants: SessionConstants,
        initialWidth: Int,
        initialHeight: Int,
        isZOrderOnTop: Boolean,
        clientExecutor: Executor,
        client: SandboxedUiAdapter.SessionClient
    ) {
        val session = ScrollableWebViewUiSession(clientExecutor, sdkContext, request, client)
        addObserverFactory(ScrollableSessionObserverFactory(session::onScrollableCondition))
        clientExecutor.execute {
            Log.d("RE_SDK", "Session opened")
            Log.d("RE_SDK", "Initial height: $initialHeight")
            Log.d("RE_SDK", "Z-Order on top: $isZOrderOnTop")
            client.onSessionOpened(session)
        }
    }
}

/**
 * A factory for creating [SessionObserver] instances.
 *
 * This class provides a way to create observers that can monitor the lifecycle of UI sessions
 * and receive updates about UI container changes.
 */
private class ScrollableSessionObserverFactory(
    private val onScrollableConditionMet: (() -> Unit)? = null
) : SessionObserverFactory {
    override fun create(): SessionObserver {
        return ScrollableSessionObserver(onScrollableConditionMet)
    }

    inner class ScrollableSessionObserver(
        private val onScrollableConditionMet: (() -> Unit)? = null
    ) : SessionObserver {
        override fun onSessionOpened(sessionObserverContext: SessionObserverContext) {
            Log.i("ScrollableSessionObs", "onSessionOpened $sessionObserverContext")
        }

        /**
         * Called when the UI container associated with a session changes.
         *
         * @param uiContainerInfo A Bundle containing information about the UI container,
         * including on-screen geometry, width, height, and opacity.
         */
        override fun onUiContainerChanged(uiContainerInfo: Bundle) {
            val sandboxedSdkViewUiInfo = SandboxedSdkViewUiInfo.fromBundle(uiContainerInfo)
            val onScreen = sandboxedSdkViewUiInfo.onScreenGeometry
            val width = sandboxedSdkViewUiInfo.uiContainerWidth
            val height = sandboxedSdkViewUiInfo.uiContainerHeight
            val opacity = sandboxedSdkViewUiInfo.uiContainerOpacityHint
            Log.i(
                "ScrollableSessionObs", "UI info: " +
                        "On-screen geometry: $onScreen, width: $width, height: $height," +
                        " opacity: $opacity"
            )
            if (onScreen.bottom == height) {
                onScrollableConditionMet?.invoke()
            }
        }

        override fun onSessionClosed() {
            Log.i("ScrollableSessionObs", "onSessionClosed")
        }
    }
}