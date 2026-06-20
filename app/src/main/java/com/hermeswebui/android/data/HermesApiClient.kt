package com.hermeswebui.android.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URI

/**
 * Minimal client for Hermes WebUI dashboard API calls made natively by the app.
 *
 * The dashboard exposes a public /api/status liveness endpoint that requires no
 * authentication (see hermes_cli/dashboard_auth/public_paths.py). The app uses
 * it to distinguish "server is down" from "content/render error" when the
 * WebView reports a load failure.
 */
object HermesApiClient {
    private const val TIMEOUT_MS = 4_000

    /**
     * Returns true if the Hermes WebUI server responds to its public
     * liveness endpoint at [baseUrl]/api/status.
     */
    suspend fun isServerReachable(baseUrl: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = URI(baseUrl.trimEnd('/')).resolve("/api/status").toURL()
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout = TIMEOUT_MS
            conn.instanceFollowRedirects = false
            val code = conn.responseCode
            conn.disconnect()
            code in 200..299
        } catch (_: Exception) {
            false
        }
    }
}

