package com.hermeswebui.android

import com.google.common.truth.Truth.assertThat
import com.hermeswebui.android.background.ReconnectBackgroundPolicy
import org.junit.Test

class ReconnectBackgroundPolicyTest {
    @Test
    fun `keepAlive true only when toggle on and app backgrounded and reconnecting`() {
        assertThat(
            ReconnectBackgroundPolicy.shouldKeepAlive(
                backgroundReconnectEnabled = true,
                activityVisible = false,
                isReconnecting = true
            )
        ).isTrue()

        assertThat(
            ReconnectBackgroundPolicy.shouldKeepAlive(
                backgroundReconnectEnabled = false,
                activityVisible = false,
                isReconnecting = true
            )
        ).isFalse()

        assertThat(
            ReconnectBackgroundPolicy.shouldKeepAlive(
                backgroundReconnectEnabled = true,
                activityVisible = true,
                isReconnecting = true
            )
        ).isFalse()

        assertThat(
            ReconnectBackgroundPolicy.shouldKeepAlive(
                backgroundReconnectEnabled = true,
                activityVisible = false,
                isReconnecting = false
            )
        ).isFalse()
    }

    @Test
    fun `shouldCancelAutoRetryOnStop mirrors inverse keepAlive policy`() {
        val cases = listOf(
            Triple(true, false, true),
            Triple(false, false, true),
            Triple(true, true, true),
            Triple(true, false, false),
            Triple(false, true, false)
        )

        cases.forEach { (enabled, visible, reconnecting) ->
            val keepAlive = ReconnectBackgroundPolicy.shouldKeepAlive(
                backgroundReconnectEnabled = enabled,
                activityVisible = visible,
                isReconnecting = reconnecting
            )
            val cancelAutoRetry = ReconnectBackgroundPolicy.shouldCancelAutoRetryOnStop(
                backgroundReconnectEnabled = enabled,
                activityVisible = visible,
                isReconnecting = reconnecting
            )
            assertThat(cancelAutoRetry).isEqualTo(!keepAlive)
        }
    }
}
