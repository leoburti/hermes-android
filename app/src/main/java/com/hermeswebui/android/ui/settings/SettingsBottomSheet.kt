package com.hermeswebui.android.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hermeswebui.android.data.ServerProfile

@Composable
fun SettingsBottomSheet(
    initialServerUrl: String,
    isConfigured: Boolean,
    onSave: (String) -> Unit,
    onResetSession: () -> Unit,
    onDismiss: () -> Unit,
    serverProfiles: List<ServerProfile> = emptyList(),
    onAddProfile: (String, String) -> Unit = { _, _ -> },
    onDeleteProfile: (String) -> Unit = {}
) {
    var serverUrl by remember(initialServerUrl, isConfigured) {
        mutableStateOf(if (isConfigured) initialServerUrl else "")
    }
    var isServerUrlFocused by remember { mutableStateOf(false) }
    var showAddProfileDialog by remember { mutableStateOf(false) }
    var profileToDelete by remember { mutableStateOf<ServerProfile?>(null) }

    if (showAddProfileDialog) {
        AddServerProfileDialog(
            onConfirm = { name, url ->
                onAddProfile(name, url)
                showAddProfileDialog = false
            },
            onDismiss = { showAddProfileDialog = false }
        )
    }

    if (profileToDelete != null) {
        AlertDialog(
            onDismissRequest = { profileToDelete = null },
            title = { Text("Delete server profile?") },
            text = { Text("Delete \"${profileToDelete!!.name}\"?") },
            dismissButton = {
                TextButton(onClick = { profileToDelete = null }) {
                    Text("Cancel")
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteProfile(profileToDelete!!.id)
                        profileToDelete = null
                    }
                ) {
                    Text("Delete")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(text = "Application Settings", style = MaterialTheme.typography.headlineSmall)

        // Current Server URL Section
        Text(text = "Current Server", style = MaterialTheme.typography.titleSmall)
        OutlinedTextField(
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { isServerUrlFocused = it.isFocused },
            value = serverUrl,
            onValueChange = { serverUrl = it },
            singleLine = true,
            label = { Text("Hermes server URL") },
            placeholder = {
                if (!isConfigured && !isServerUrlFocused && serverUrl.isBlank()) {
                    Text(initialServerUrl)
                }
            },
            supportingText = { Text("HTTP or HTTPS. Host is automatically allowlisted.") }
        )

        // Server Profiles Section
        if (serverProfiles.isNotEmpty()) {
            Text(text = "Server Profiles", style = MaterialTheme.typography.titleSmall)
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(serverProfiles) { profile ->
                    ListItem(
                        headlineContent = {
                            Text(
                                profile.name,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        supportingContent = {
                            Text(
                                profile.url,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall
                            )
                        },
                        trailingContent = {
                            IconButton(
                                onClick = { profileToDelete = profile }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete profile"
                                )
                            }
                        }
                    )
                }
            }

            // Add New Server Button
            OutlinedButton(
                onClick = { showAddProfileDialog = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Add new server")
            }
        }

        // Action Buttons
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = onResetSession) {
                Text("Reset web session")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
                Button(onClick = {
                    onSave(serverUrl.trim())
                }) {
                    Text("Save")
                }
            }
        }
    }
}

@Composable
private fun AddServerProfileDialog(
    onConfirm: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var profileName by remember { mutableStateOf("") }
    var profileUrl by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add server profile") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = profileName,
                    onValueChange = { profileName = it },
                    label = { Text("Server name (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = profileUrl,
                    onValueChange = { profileUrl = it },
                    label = { Text("Server URL") },
                    placeholder = { Text("https://hermes.example.com") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    supportingText = { Text("HTTP or HTTPS") }
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (profileUrl.isNotBlank()) {
                        val name = profileName.ifBlank { profileUrl }
                        onConfirm(name, profileUrl)
                        onDismiss()
                    }
                },
                enabled = profileUrl.isNotBlank()
            ) {
                Text("Add")
            }
        }
    )
}

