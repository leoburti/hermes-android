package com.hermeswebui.android.background

internal object ReconnectBackgroundPolicy {
    internal fun shouldKeepAlive(
        backgroundReconnectEnabled: Boolean,
        activityVisible: Boolean,
        isReconnecting: Boolean
    ): Boolean {
        return backgroundReconnectEnabled && !activityVisible && isReconnecting
    }

    internal fun shouldCancelAutoRetryOnStop(
        backgroundReconnectEnabled: Boolean,
        activityVisible: Boolean,
        isReconnecting: Boolean
    ): Boolean {
        return !shouldKeepAlive(
            backgroundReconnectEnabled = backgroundReconnectEnabled,
            activityVisible = activityVisible,
            isReconnecting = isReconnecting
        )
    }
}
