package com.moto.files.ui.tools

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.moto.files.tools.WifiShare

@Composable
fun WifiScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    var folder by remember { mutableStateOf<Uri?>(null) }
    var server by remember { mutableStateOf<WifiShare?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            server?.close(); server = null
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                folder = uri
            } catch (failure: Exception) { error = failure.message }
        }
    }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) { server?.close(); server = null }
        }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { lifecycle.lifecycle.removeObserver(observer); server?.close() }
    }
    Column(modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Wi-Fi sharing", style = MaterialTheme.typography.headlineLarge)
        Text("Temporary, read-only sharing on your current Wi-Fi. Anyone with the link on this network can download these files. Traffic is not encrypted: do not use public Wi-Fi.")
        TextButton(onClick = { picker.launch(null) }) { Text("Choose shared folder") }
        if (folder != null) Button(onClick = {
            if (server != null) { server?.close(); server = null }
            else runCatching { WifiShare(context, folder!!) }.onSuccess { server = it; error = null }.onFailure { error = it.message }
        }) { Text(if (server == null) "Start sharing" else "Stop sharing") }
        if (server != null) {
            Text("Open this address on a computer connected to the same Wi-Fi:")
            Text(server!!.address, style = MaterialTheme.typography.bodyMedium)
        }
        if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error)
    }
}
