package com.moto.files.ui.vault

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.moto.files.data.security.Credentials
import com.moto.files.data.security.VaultItem
import com.moto.files.data.security.VaultRepository
import com.moto.files.ui.security.CredentialField
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun VaultScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val credentials = remember { Credentials(context) }
    val repo = remember { VaultRepository(context) }
    val scope = rememberCoroutineScope()
    val separate = credentials.separateVault()
    var key by remember { mutableStateOf<ByteArray?>(null) }
    var secret by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var list by remember { mutableStateOf<List<VaultItem>>(emptyList()) }
    var selected by remember { mutableStateOf<VaultItem?>(null) }
    var preview by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var confirmRemove by remember { mutableStateOf<VaultItem?>(null) }
    var exportItem by remember { mutableStateOf<VaultItem?>(null) }
    DisposableEffect(key) { onDispose { key?.fill(0) } }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val item = exportItem
        val copy = key?.copyOf()
        exportItem = null
        if (uri != null && item != null && copy != null) scope.launch {
            val result = withContext(Dispatchers.IO) { repo.exportFile(item, copy, uri) }
            copy.fill(0)
            message = result.exceptionOrNull()?.message ?: "Exported ${item.name}"
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val current = key?.copyOf()
        if (uri != null && current != null) {
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    val name = context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                        if (it.moveToFirst()) it.getString(0) else null
                    } ?: "Private file"
                    repo.importFile(uri, name, context.contentResolver.getType(uri) ?: "application/octet-stream", current)
                }
                current.fill(0)
                message = result.exceptionOrNull()?.message ?: "Encrypted copy saved. Original remains in its folder."
                list = withContext(Dispatchers.IO) { repo.items() }
            }
        }
    }
    if (!credentials.configured()) {
        Column(modifier.padding(24.dp)) {
            Text("Vault needs app lock", style = MaterialTheme.typography.headlineSmall)
            Text("Set up an app credential in Settings before adding private files.")
        }
        return
    }
    if (key == null) {
        Column(modifier.padding(32.dp), verticalArrangement = Arrangement.Center) {
            Text("Unlock Vault", style = MaterialTheme.typography.headlineLarge)
            Text(if (separate) "Enter your separate Vault credential" else "Enter your app credential")
            Spacer(Modifier.height(24.dp))
            CredentialField(credentials.type(separate), secret, { secret = it }, "Credential")
            if (message != null) Text(message!!, color = MaterialTheme.colorScheme.error)
            Button(onClick = {
                scope.launch {
                    val result = withContext(Dispatchers.Default) { runCatching { credentials.vaultKey(secret, separate) } }
                    secret = ""
                    if (result.isSuccess) { key = result.getOrThrow(); list = withContext(Dispatchers.IO) { repo.items() }; message = null }
                    else message = "Incorrect credential"
                }
            }, enabled = secret.isNotEmpty()) { Text("Unlock") }
        }
        return
    }
    if (confirmRemove != null) AlertDialog(onDismissRequest = { confirmRemove = null }, title = { Text("Remove from Vault?") },
        text = { Text("This encrypted copy will be permanently deleted. The original, if still present, is unchanged.") },
        confirmButton = { TextButton(onClick = {
            val item = confirmRemove ?: return@TextButton
            confirmRemove = null
            scope.launch {
                val result = withContext(Dispatchers.IO) { repo.remove(item) }
                message = result.exceptionOrNull()?.message ?: "Removed"
                list = withContext(Dispatchers.IO) { repo.items() }
            }
        }) { Text("Remove") } }, dismissButton = { TextButton(onClick = { confirmRemove = null }) { Text("Cancel") } })
    Column(modifier) {
        Row(Modifier.fillMaxWidth().padding(24.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Vault", style = MaterialTheme.typography.headlineLarge)
            TextButton(onClick = { key?.fill(0); key = null; selected = null; preview = null }) { Text("Lock") }
        }
        Text("Files are encrypted here. Import keeps the original.", Modifier.padding(horizontal = 24.dp))
        Button(onClick = { picker.launch(arrayOf("*/*")) }, modifier = Modifier.padding(24.dp)) { Text("Import file") }
        if (message != null) Text(message!!, Modifier.padding(horizontal = 24.dp))
        if (selected != null && preview != null) {
            Image(preview!!.asImageBitmap(), contentDescription = selected!!.name,
                modifier = Modifier.fillMaxWidth().weight(1f), contentScale = ContentScale.Fit)
            TextButton(onClick = { selected = null; preview = null }) { Text("Close preview") }
        } else if (list.isEmpty()) {
            Text("No encrypted files yet", Modifier.padding(24.dp))
        } else LazyColumn {
            items(list, key = { it.id }) { item ->
                ListItem(headlineContent = { Text(item.name) }, supportingContent = { Text(item.mime) },
                    trailingContent = {
                        Row {
                            if (item.mime.startsWith("image/")) TextButton(onClick = {
                                val current = key?.copyOf() ?: return@TextButton
                                scope.launch {
                                    val result = withContext(Dispatchers.IO) { runCatching {
                                        val bytes = repo.imageBytes(item, current)
                                        try { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: error("Cannot display image") }
                                        finally { bytes.fill(0) }
                                    } }
                                    current.fill(0)
                                    if (result.isSuccess) { preview = result.getOrThrow(); selected = item }
                                    else message = result.exceptionOrNull()?.message
                                }
                            }) { Text("View") }
                            TextButton(onClick = { exportItem = item; exporter.launch(item.name) }) { Text("Export") }
                            TextButton(onClick = { confirmRemove = item }) { Text("Remove") }
                        }
                    })
            }
        }
    }
}
