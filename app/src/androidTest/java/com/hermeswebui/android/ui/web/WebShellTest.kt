package com.hermeswebui.android.ui.web

import android.webkit.WebView
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented UI tests for [WebShell].
 *
 * These tests verify that the error recovery buttons render correctly and fire
 * the right callbacks — in particular the "Edit server URL" button added for
 * issue #8 (wrong server URL recovery).
 *
 * Run on a connected device or emulator:
 *   ./gradlew connectedDebugAndroidTest
 */
@RunWith(AndroidJUnit4::class)
class WebShellTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // ------------------------------------------------------------------
    // Error screen presence
    // ------------------------------------------------------------------

    @Test
    fun errorScreen_showsEditServerUrlButton() {
        composeTestRule.setContent {
            val context = LocalContext.current
            val fakeWebView = remember { WebView(context) }
            WebShell(
                webView = fakeWebView,
                isLoading = false,
                hasLoadedContent = false,
                isOffline = false,
                errorMessage = "Connection refused",
                onRefresh = {},
                onRetry = {},
                onOpenExternal = {},
                onOpenSettings = {}
            )
        }

        composeTestRule.onNodeWithText("Edit server URL").assertIsDisplayed()
    }

    @Test
    fun errorScreen_showsRetryAndOpenInBrowserButtons() {
        composeTestRule.setContent {
            val context = LocalContext.current
            val fakeWebView = remember { WebView(context) }
            WebShell(
                webView = fakeWebView,
                isLoading = false,
                hasLoadedContent = false,
                isOffline = false,
                errorMessage = "Connection refused",
                onRefresh = {},
                onRetry = {},
                onOpenExternal = {},
                onOpenSettings = {}
            )
        }

        composeTestRule.onNodeWithText("Retry").assertIsDisplayed()
        composeTestRule.onNodeWithText("Open in browser").assertIsDisplayed()
    }

    // ------------------------------------------------------------------
    // Callback wiring
    // ------------------------------------------------------------------

    @Test
    fun errorScreen_clickEditServerUrl_invokesOnOpenSettings() {
        var settingsOpened = false

        composeTestRule.setContent {
            val context = LocalContext.current
            val fakeWebView = remember { WebView(context) }
            WebShell(
                webView = fakeWebView,
                isLoading = false,
                hasLoadedContent = false,
                isOffline = false,
                errorMessage = "Connection refused",
                onRefresh = {},
                onRetry = {},
                onOpenExternal = {},
                onOpenSettings = { settingsOpened = true }
            )
        }

        composeTestRule.onNodeWithText("Edit server URL").performClick()

        assertThat(settingsOpened).isTrue()
    }

    @Test
    fun errorScreen_clickRetry_invokesOnRetry() {
        var retryCalled = false

        composeTestRule.setContent {
            val context = LocalContext.current
            val fakeWebView = remember { WebView(context) }
            WebShell(
                webView = fakeWebView,
                isLoading = false,
                hasLoadedContent = false,
                isOffline = false,
                errorMessage = "Connection refused",
                onRefresh = {},
                onRetry = { retryCalled = true },
                onOpenExternal = {},
                onOpenSettings = {}
            )
        }

        composeTestRule.onNodeWithText("Retry").performClick()

        assertThat(retryCalled).isTrue()
    }

    // ------------------------------------------------------------------
    // No error — buttons must not appear
    // ------------------------------------------------------------------

    @Test
    fun noError_editServerUrlButton_isNotDisplayed() {
        composeTestRule.setContent {
            val context = LocalContext.current
            val fakeWebView = remember { WebView(context) }
            WebShell(
                webView = fakeWebView,
                isLoading = false,
                hasLoadedContent = true,
                isOffline = false,
                errorMessage = null,
                onRefresh = {},
                onRetry = {},
                onOpenExternal = {},
                onOpenSettings = {}
            )
        }

        composeTestRule.onNodeWithText("Edit server URL").assertDoesNotExist()
        composeTestRule.onNodeWithText("Retry").assertDoesNotExist()
        composeTestRule.onNodeWithText("Open in browser").assertDoesNotExist()
    }
}

