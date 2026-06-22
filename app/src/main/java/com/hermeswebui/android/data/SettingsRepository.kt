package com.hermeswebui.android.data

import android.content.Context
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.hermeswebui.android.core.security.UrlOrigins

@Suppress("DEPRECATION")
class SettingsRepository(context: Context) : SettingsStore {
    private val sharedPreferences = EncryptedSharedPreferences.create(
        context,
        FILE_NAME,
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    init {
        // Migrate away from storing dashboard URLs in Android preferences.
        // Versions before 0.1.5 stored a dashboard URL in SharedPreferences and injected it
        // into WebUI via /api/dashboard/config. Now WebUI owns dashboard config completely.
        // Clear any stored dashboard URLs to prevent old behavior on app upgrade.
        runMigration()
    }

    private fun runMigration() {
        val lastMigrationVersion = sharedPreferences.getInt(KEY_LAST_MIGRATION_VERSION, 0)
        val currentMigrationVersion = 1 // Increment this when adding new migrations

        if (lastMigrationVersion < currentMigrationVersion) {
            // Migration 1: Clear dashboard URLs from pre-0.1.5 versions
            sharedPreferences.edit {
                remove(KEY_DASHBOARD_URL)
                putInt(KEY_LAST_MIGRATION_VERSION, 1)
            }
        }
    }

    override fun getSettings(defaultUrl: String, defaultDashboardUrl: String): AppSettings {
        val serverUrl = sharedPreferences.getString(KEY_SERVER_URL, defaultUrl)?.trim().orEmpty()
        val rawDashboardUrl = sharedPreferences
            .getString(KEY_DASHBOARD_URL, defaultDashboardUrl)
            ?.trim()
            .orEmpty()
        val dashboardUrl = UrlOrigins.normalizeOriginUrl(rawDashboardUrl)
        val parsedHosts = setOf(serverUrl, dashboardUrl)
            .mapNotNull(UrlOrigins::hostFrom)
            .toSet()
        val hostCsv = sharedPreferences.getString(KEY_ALLOWED_HOSTS, parsedHosts.joinToString(",")).orEmpty()
        val isConfigured = sharedPreferences.getBoolean(KEY_IS_CONFIGURED, false)
        val allowlist = hostCsv
            .split(',')
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }
            .toSet()
        return AppSettings(
            serverUrl = serverUrl,
            dashboardUrl = dashboardUrl,
            allowedHosts = allowlist,
            isConfigured = isConfigured
        )
    }

    override fun saveAppUrls(serverUrl: String, dashboardUrl: String) {
        val normalizedDashboardUrl = UrlOrigins.normalizeOriginUrl(dashboardUrl)
        val hosts = setOf(serverUrl, normalizedDashboardUrl)
            .mapNotNull(UrlOrigins::hostFrom)
            .toSet()
        sharedPreferences.edit {
            putString(KEY_SERVER_URL, serverUrl)
            putString(KEY_DASHBOARD_URL, normalizedDashboardUrl)
            putString(KEY_ALLOWED_HOSTS, hosts.joinToString(","))
            putBoolean(KEY_IS_CONFIGURED, true)
        }
    }

    override fun clearWebSession() {
        sharedPreferences.edit { remove(KEY_LAST_URL) }
    }

    fun hasRequestedNotificationPermission(): Boolean {
        return sharedPreferences.getBoolean(KEY_NOTIFICATION_PERMISSION_REQUESTED, false)
    }

    fun markNotificationPermissionRequested() {
        sharedPreferences.edit { putBoolean(KEY_NOTIFICATION_PERMISSION_REQUESTED, true) }
    }

    override fun saveLastLoadedUrl(url: String) {
        sharedPreferences.edit { putString(KEY_LAST_URL, url) }
    }

    fun getLastLoadedUrl(): String? = sharedPreferences.getString(KEY_LAST_URL, null)

    companion object {
        private const val FILE_NAME = "hermes_secure_prefs"
        private const val KEY_SERVER_URL = "server_url"
        // Preserve the original encrypted preference key so existing installs keep their saved URL.
        private const val KEY_DASHBOARD_URL = "dashboard_terminal_url"
        private const val KEY_ALLOWED_HOSTS = "allowed_hosts"
        private const val KEY_LAST_URL = "last_url"
        private const val KEY_IS_CONFIGURED = "is_configured"
        private const val KEY_NOTIFICATION_PERMISSION_REQUESTED = "notification_permission_requested"
        private const val KEY_LAST_MIGRATION_VERSION = "last_migration_version"
    }
}
