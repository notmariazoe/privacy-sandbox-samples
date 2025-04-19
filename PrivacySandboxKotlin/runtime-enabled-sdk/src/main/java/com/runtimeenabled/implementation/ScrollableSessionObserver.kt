package com.runtimeenabled.implementation

import android.os.Bundle
import android.util.Log
import androidx.privacysandbox.ui.core.SandboxedSdkViewUiInfo
import androidx.privacysandbox.ui.core.SessionObserver
import androidx.privacysandbox.ui.core.SessionObserverContext

class ScrollableSessionObserver(
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
        Log.i("ScrollableSessionObs", "UI info: " +
                "On-screen geometry: $onScreen, width: $width, height: $height," +
                " opacity: $opacity")
        if (onScreen.bottom == height) {
            onScrollableConditionMet?.invoke()
        }
    }

    override fun onSessionClosed() {
        Log.i("ScrollableSessionObs", "onSessionClosed")
    }
}