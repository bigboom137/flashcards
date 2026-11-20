package com.flashcards.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.flashcards.AppViewModel
import com.flashcards.sync.SyncProtocol

/**
 * Pairs the phone with the desktop app over Wi-Fi: pick a computer that answered the
 * search (or type its address), enter the six-digit code it shows, and swap libraries.
 */
@Composable
fun SyncDialog(vm: AppViewModel, onDismiss: () -> Unit) {
    val state = vm.syncState
    var code by remember { mutableStateOf("") }
    var manualAddress by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<SyncProtocol.HostInfo?>(null) }

    LaunchedEffect(Unit) { vm.discoverDesktops() }

    val hosts = (state as? AppViewModel.SyncState.Found)?.hosts.orEmpty()
    val chosen = selected ?: hosts.firstOrNull()
    val address = manualAddress.trim().ifEmpty { chosen?.address.orEmpty() }
    val port = if (manualAddress.isBlank()) chosen?.port ?: SyncProtocol.DEFAULT_HTTP_PORT
    else SyncProtocol.DEFAULT_HTTP_PORT
    val canSync = address.isNotEmpty() && code.length == 6 &&
        state !is AppViewModel.SyncState.Working

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sync with computer") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when (state) {
                    is AppViewModel.SyncState.Done -> {
                        Text("Sync complete", style = MaterialTheme.typography.titleMedium, color = EasyGreen)
                        Text(state.summary, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    is AppViewModel.SyncState.Failed -> {
                        Text("Sync failed", style = MaterialTheme.typography.titleMedium, color = HardPurple)
                        Text(state.message, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    is AppViewModel.SyncState.Working -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(12.dp))
                            Text(state.step)
                        }
                    }

                    else -> {
                        Text(
                            "On the computer, open Flashcards and turn on “Share over Wi-Fi”. Both devices must be on the same network.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        if (state is AppViewModel.SyncState.Searching) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(12.dp))
                                Text("Looking for computers…")
                            }
                        } else {
                            if (hosts.isEmpty()) {
                                Text(
                                    "No computers found. Type the address shown on the computer instead.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            hosts.forEach { host ->
                                val isSelected = host.address == chosen?.address && manualAddress.isBlank()
                                Card(
                                    modifier = Modifier.fillMaxWidth().clickable {
                                        selected = host
                                        manualAddress = ""
                                    },
                                ) {
                                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Default.Computer,
                                            contentDescription = null,
                                            tint = if (isSelected) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        Spacer(Modifier.width(12.dp))
                                        Column {
                                            Text(host.name, style = MaterialTheme.typography.bodyLarge)
                                            Text(
                                                "${host.address}:${host.port}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                }
                            }
                            TextButton(onClick = { vm.discoverDesktops() }) { Text("Search again") }
                        }

                        OutlinedTextField(
                            value = manualAddress,
                            onValueChange = { manualAddress = it },
                            label = { Text("Or type the computer's address") },
                            placeholder = { Text("192.168.1.20") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = code,
                            onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) code = it },
                            label = { Text("Code shown on the computer") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(0.dp))
                        Text(
                            "Both devices end up with every deck and card from the other. Nothing is deleted.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        confirmButton = {
            when (state) {
                is AppViewModel.SyncState.Done -> TextButton(onClick = onDismiss) { Text("Done") }
                is AppViewModel.SyncState.Failed -> TextButton(onClick = { vm.resetSync(); vm.discoverDesktops() }) {
                    Text("Try again")
                }
                else -> TextButton(
                    onClick = { vm.syncWith(address, port, code) },
                    enabled = canSync,
                ) { Text("Sync") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
