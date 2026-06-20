package com.hermes.wrapper.domain

import android.content.Intent
import android.net.Uri
import android.os.Build
import com.hermes.wrapper.data.SharePayload

class ShareIntentParser {
    fun parse(intent: Intent?): SharePayload? {
        if (intent == null) return null
        return when (intent.action) {
            Intent.ACTION_SEND -> parseSingle(intent)
            Intent.ACTION_SEND_MULTIPLE -> parseMultiple(intent)
            else -> null
        }
    }

    private fun parseSingle(intent: Intent): SharePayload? {
        val text = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim()
        val uri: Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_STREAM)
        }
        if (text.isNullOrEmpty() && uri == null) return null
        return SharePayload(sharedText = text, fileUris = listOfNotNull(uri))
    }

    private fun parseMultiple(intent: Intent): SharePayload? {
        val uris: List<Uri> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
                ?: emptyList()
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableArrayListExtra<android.os.Parcelable>(Intent.EXTRA_STREAM)
                ?.filterIsInstance<Uri>()
                .orEmpty()
        }
        if (uris.isEmpty()) return null
        val text = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim()
        return SharePayload(sharedText = text, fileUris = uris)
    }
}
