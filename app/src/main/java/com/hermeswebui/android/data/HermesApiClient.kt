package com.hermeswebui.android.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URI
import org.json.JSONArray
import org.json.JSONObject

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

    private val sseFeatureKeys = setOf(
        "session_sse",
        "session_sse_enabled",
        "session_sse_summary",
        "session_sse_summary_enabled",
        "sse",
        "sse_enabled",
        "sse_summary"
    )

    /**
     * Describes the SSE capability level detected on the server.
     */
    enum class SseCapability {
        /** HERMES_WEBUI_SESSION_SSE_ENABLED=1 and /api/status reports session SSE on. */
        SESSION_SSE_ENABLED,
        /** /api/status flag not set, but /api/sessions/gateway/stream is reachable. */
        GATEWAY_STREAM_AVAILABLE,
        /**
         * /api/sessions/gateway/stream returned HTTP 404 — the feature route does not exist on
         * this server build, almost certainly because HERMES_WEBUI_SESSION_SSE_ENABLED is not set
         * in the container environment.  This is the common "not yet opted in" case and should be
         * presented to the user with a clear enable-the-flag message rather than a generic error.
         */
        FEATURE_DISABLED,
        /** No SSE capability detected (network error or unexpected server response). */
        NONE
    }

    /** The prompt text a user can paste into Hermes chat to ask it to enable session SSE. */
    const val SSE_ENABLE_HERMES_PROMPT =
        "Please enable the session SSE feature on this Hermes server. " +
        "Add the environment variable HERMES_WEBUI_SESSION_SSE_ENABLED=1 to the server " +
        "environment (for example, in your docker-compose.yml under the hermes service " +
        "environment section) and restart the container:\n\n" +
        "    HERMES_WEBUI_SESSION_SSE_ENABLED=1\n\n" +
        "Then run: docker compose restart"

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

    /**
     * Probes the live gateway stream endpoint at [baseUrl]/api/sessions/gateway/stream.
     * Returns the HTTP status code on success, or null on network/connection error.
     *
     * Callers should interpret:
     *  - 200, 401, 403, etc. → route is registered (SSE endpoint exists)
     *  - 404                 → route is absent → HERMES_WEBUI_SESSION_SSE_ENABLED not set
     *  - null               → could not reach server at all
     */
    suspend fun probeGatewayStreamEndpoint(baseUrl: String): Int? = withContext(Dispatchers.IO) {
        try {
            val url = URI(baseUrl.trimEnd('/')).resolve("/api/sessions/gateway/stream").toURL()
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "HEAD"
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout = TIMEOUT_MS
            conn.instanceFollowRedirects = false
            conn.setRequestProperty("Accept", "text/event-stream")
            val code = conn.responseCode
            conn.disconnect()
            code
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Returns the detected [SseCapability] level for the given server.
     *
     * Priority:
     * 1. If /api/status reports session_sse_enabled → SESSION_SSE_ENABLED
     * 2. If /api/sessions/gateway/stream responds (non-404) → GATEWAY_STREAM_AVAILABLE
     * 3. If /api/sessions/gateway/stream returns 404 → FEATURE_DISABLED (flag not set)
     * 4. Otherwise → NONE (network error / unreachable)
     */
    suspend fun detectSseCapability(baseUrl: String): SseCapability = withContext(Dispatchers.IO) {
        try {
            val statusUrl = URI(baseUrl.trimEnd('/')).resolve("/api/status").toURL()
            val conn = statusUrl.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout = TIMEOUT_MS
            conn.instanceFollowRedirects = false
            val code = conn.responseCode
            if (code in 200..299) {
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                conn.disconnect()
                if (parseSseFeatureFlag(body)) return@withContext SseCapability.SESSION_SSE_ENABLED
            } else {
                conn.disconnect()
            }
        } catch (_: Exception) { /* fall through to gateway probe */ }

        when (val gatewayStatus = probeGatewayStreamEndpoint(baseUrl)) {
            null -> SseCapability.NONE
            404, 405 -> SseCapability.FEATURE_DISABLED
            else -> if (gatewayStatus in 200..499) SseCapability.GATEWAY_STREAM_AVAILABLE
                    else SseCapability.NONE
        }
    }

    /** Convenience wrapper — returns true only if SSE is actually usable (not just "disabled"). */
    suspend fun isSessionSseSupported(baseUrl: String): Boolean {
        val cap = detectSseCapability(baseUrl)
        return cap == SseCapability.SESSION_SSE_ENABLED || cap == SseCapability.GATEWAY_STREAM_AVAILABLE
    }

    private fun parseSseFeatureFlag(rawJson: String): Boolean {
        val root = runCatching { JSONObject(rawJson) }.getOrNull() ?: return false

        fun normalize(key: String): String {
            return key.trim().lowercase().replace("-", "_").replace(" ", "_")
        }

        fun parseBooleanish(value: Any?): Boolean? {
            return when (value) {
                is Boolean -> value
                is Number -> value.toInt() != 0
                is String -> when (value.trim().lowercase()) {
                    "true", "enabled", "on", "1" -> true
                    "false", "disabled", "off", "0" -> false
                    else -> null
                }
                else -> null
            }
        }

        fun inspect(value: Any?): Boolean? {
            when (value) {
                is JSONObject -> {
                    var foundFalse = false
                    val keys = value.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        val child = value.opt(key)
                        val normalized = normalize(key)
                        if (normalized in sseFeatureKeys) {
                            val parsed = parseBooleanish(child)
                            if (parsed == true) return true
                            if (parsed == false) foundFalse = true
                        }

                        val nested = inspect(child)
                        if (nested == true) return true
                        if (nested == false) foundFalse = true
                    }
                    return if (foundFalse) false else null
                }
                is JSONArray -> {
                    var foundFalse = false
                    for (index in 0 until value.length()) {
                        val child = value.opt(index)
                        if (child is String && normalize(child) in sseFeatureKeys) {
                            return true
                        }
                        val nested = inspect(child)
                        if (nested == true) return true
                        if (nested == false) foundFalse = true
                    }
                    return if (foundFalse) false else null
                }
                else -> return parseBooleanish(value)
            }
        }

        return inspect(root) == true
    }
}

