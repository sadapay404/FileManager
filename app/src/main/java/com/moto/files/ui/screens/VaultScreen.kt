package com.moto.files.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.moto.files.MotoFilesApp
import com.moto.files.security.Crypto
import com.moto.files.ui.lock.PinPad
import java.io.File

/**
 * Hidden vault: AES-256-GCM encrypted files under filesDir/vault.
 * Unlocked with the app-lock credential or a separate vault credential.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultScreen(app: MotoFilesApp, onBack: () -> Unit) {
    var secret by remember { mutableStateOf<String?>(null) }
    var needsSetup by remember { mutableStateOf(!app.locks.hasVaultCredential) }
    var error by remember { mutableStateOf(false) }
    var reset by remember { mutableIntStateOf(0) }
    val vaultDir = File(app.filesDir, "vault")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Vault") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
                }
            )
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            if (secret == null) {
                Column(
                    Modifier.fillMaxSize().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Default.Lock, null, Modifier.size(64.dp))
                    Spacer(Modifier.height(16.dp))
                    Text(if (needsSetup) "Set vault credential" else "Enter vault credential",
                        style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(24.dp))
                    PinPad(pinLength = 0, reset = reset) { v ->
                        when {
                            needsSetup -> {
                                app.locks.setVaultCredential(true, v)
                                secret = v
                            }
                            app.locks.checkVault(v) -> secret = v
                            else -> { error = true; reset++ }
                        }
                    }
                    if (error) Text("Incorrect", color = MaterialTheme.colorScheme.error)
                }
            } else {
                val files = remember { vaultDir.listFiles()?.toList() ?: emptyList() }
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(files, key = { it.absolutePath }) { f ->
                        ListItem(
                            headlineContent = { Text(f.name) },
                            supportingContent = { Text("Encrypted · ${f.length()} bytes") }
                        )
                    }
                }
            }
        }
    }
}

/** Encrypt a file into the vault (keyed by the vault credential) and remove the original. */
suspend fun moveToVault(source: File, credential: String, vaultDir: File) {
    vaultDir.mkdirs()
    Crypto.encryptFile(source, File(vaultDir, source.name + ".enc"), credential)
    source.delete()
}
