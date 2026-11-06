package com.moto.files.ui.files

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.moto.files.data.files.TreeEntry
import com.moto.files.data.files.TreeRepository
import com.moto.files.data.files.FileOperations
import com.moto.files.data.trash.TrashRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.moto.files.ui.components.formatBytes
import com.moto.files.ui.viewer.UniversalViewer
import com.moto.files.ui.viewer.FileTypes
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BrowserScreen(title: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val repo = remember(context) { TreeRepository(context) }
    val scope = rememberCoroutineScope()
    val operations = remember(context) { FileOperations(context) }
    val trash = remember(context) { TrashRepository(context) }
    val prefs = remember { context.getSharedPreferences("folders", 0) }
    val rootKey = if (title == "SD card") "sd_root" else "internal_root"
    var root by remember(rootKey) { mutableStateOf(prefs.getString(rootKey, null)?.let(Uri::parse)) }
    val path = remember(rootKey) { mutableStateListOf<Uri>() }
    var entries by remember { mutableStateOf<List<TreeEntry>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var opened by remember { mutableStateOf<TreeEntry?>(null) }
    var loading by remember { mutableStateOf(false) }
    var revision by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var permanent by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<TreeEntry?>(null) }
    var renameText by remember { mutableStateOf("") }
    var overflow by remember { mutableStateOf(false) }
    var details by remember { mutableStateOf<String?>(null) }
    var clipboard by remember { mutableStateOf<List<TreeEntry>>(emptyList()) }
    var cut by remember { mutableStateOf(false) }
    val selected = remember { mutableStateListOf<Uri>() }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                prefs.edit().putString(rootKey, uri.toString()).apply()
                root = uri
                path.clear()
                selected.clear()
            } catch (failure: SecurityException) { message = "Could not retain folder access: ${failure.message}" }
        }
    }
    val folder = path.lastOrNull() ?: root
    LaunchedEffect(folder, revision) {
        entries = emptyList()
        error = null
        if (folder != null) {
            loading = true
            repo.list(folder).fold(onSuccess = { entries = it }, onFailure = { error = it.message ?: "Could not read folder" })
            loading = false
        }
    }
    val chosen = entries.filter { it.uri in selected && !it.isDirectory }
    fun runOperation(action: () -> List<Result<Unit>>) {
        loading = true
        scope.launch {
            val results = withContext(Dispatchers.IO) { runCatching { action() }.getOrElse { listOf(Result.failure(it)) } }
            val failures = results.count { it.isFailure }
            message = if (failures == 0) "Done" else "$failures operation(s) failed: ${results.first { it.isFailure }.exceptionOrNull()?.message}"
            selected.clear()
            loading = false
            revision++
        }
    }
    if (details != null) AlertDialog(onDismissRequest = { details = null },
        title = { Text("File details") }, text = { Text(details!!) },
        confirmButton = { TextButton(onClick = { details = null }) { Text("Close") } })
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false },
        title = { Text(if (permanent) "Delete permanently?" else "Move to Trash?") },
        text = { Text(if (permanent) "These files cannot be restored." else "Files stay in Trash for up to 30 days.") },
        confirmButton = { TextButton(onClick = {
            confirmDelete = false
            val batch = chosen.toList()
            val parent = folder ?: return@TextButton
            runOperation { batch.map { if (permanent) operations.permanentlyDelete(it) else trash.put(it, parent) } }
        }) { Text(if (permanent) "Delete permanently" else "Move to Trash") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } })
    if (renameTarget != null) AlertDialog(onDismissRequest = { renameTarget = null },
        title = { Text("Rename file") },
        text = { OutlinedTextField(value = renameText, onValueChange = { renameText = it }, singleLine = true, label = { Text("Name") }) },
        confirmButton = { TextButton(onClick = {
            val target = renameTarget ?: return@TextButton
            renameTarget = null
            runOperation { listOf(operations.rename(target, renameText).map { Unit }) }
        }) { Text("Rename") } },
        dismissButton = { TextButton(onClick = { renameTarget = null }) { Text("Cancel") } })
    if (opened != null) {
        val file = opened!!
        UniversalViewer(file.uri, file.name, file.mimeType, onClose = { opened = null }, modifier = modifier.fillMaxSize())
        return
    }
    BackHandler(path.isNotEmpty() || selected.isNotEmpty()) {
        if (selected.isNotEmpty()) selected.clear() else path.removeAt(path.lastIndex)
    }
    Column(modifier) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(if (selected.isEmpty()) title else "${selected.size} selected", style = MaterialTheme.typography.headlineSmall)
            TextButton(onClick = { picker.launch(null) }) { Text("Choose folder") }
        }
        if (message != null) Text(message!!, Modifier.padding(horizontal = 24.dp), color = MaterialTheme.colorScheme.primary)
        if (chosen.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(enabled = !loading, onClick = { clipboard = chosen.toList(); cut = false; selected.clear(); message = "Copied to clipboard" }) { Text("Copy") }
                TextButton(enabled = !loading, onClick = { clipboard = chosen.toList(); cut = true; selected.clear(); message = "Ready to move" }) { Text("Cut") }
                if (chosen.size == 1) TextButton(enabled = !loading, onClick = { renameTarget = chosen.first(); renameText = chosen.first().name }) { Text("Rename") }
                TextButton(enabled = !loading, onClick = { permanent = false; confirmDelete = true }) { Text("Delete") }
                Box {
                    IconButton(onClick = { overflow = true }) { Icon(androidx.compose.material.icons.Icons.Outlined.MoreVert, "More actions") }
                    DropdownMenu(expanded = overflow, onDismissRequest = { overflow = false }) {
                        if (chosen.size == 1) DropdownMenuItem(text = { Text("Details & EXIF") }, onClick = {
                            overflow = false
                            val file = chosen.first()
                            scope.launch {
                                details = withContext(Dispatchers.IO) {
                                    buildString {
                                        appendLine("Name: ${file.name}")
                                        appendLine("Type: ${FileTypes.mime(file.name, file.mimeType)}")
                                        appendLine("Size: ${formatBytes(file.length)}")
                                        if (FileTypes.mime(file.name, file.mimeType).startsWith("image/")) {
                                            runCatching {
                                                context.contentResolver.openInputStream(file.uri)?.use { input ->
                                                    val exif = ExifInterface(input)
                                                    exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)?.let { appendLine("Captured: $it") }
                                                    exif.getAttribute(ExifInterface.TAG_MAKE)?.let { appendLine("Camera: $it") }
                                                    exif.getAttribute(ExifInterface.TAG_MODEL)?.let { appendLine("Model: $it") }
                                                    exif.getAttribute(ExifInterface.TAG_ORIENTATION)?.let { appendLine("Orientation: $it") }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        })
                        DropdownMenuItem(text = { Text("Delete permanently") }, onClick = { overflow = false; permanent = true; confirmDelete = true })
                    }
                }
            }
        } else if (clipboard.isNotEmpty() && folder != null) {
            TextButton(modifier = Modifier.padding(horizontal = 24.dp), enabled = !loading, onClick = {
                val destination = folder ?: return@TextButton
                val batch = clipboard.toList()
                val moving = cut
                runOperation { batch.map { operations.copy(it, destination, moving) } }
                clipboard = emptyList()
            }) { Text(if (cut) "Move here" else "Paste here") }
        }
        if (root == null) {
            Text("Choose a folder to browse. Access stays limited to the folder you select.", Modifier.padding(24.dp))
        } else if (loading) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        } else if (error != null) {
            Text(error!!, Modifier.padding(24.dp), color = MaterialTheme.colorScheme.error)
        } else if (entries.isEmpty()) {
            Text("Nothing in this folder", Modifier.padding(24.dp))
        } else {
            val visible = remember(entries, title) { entries.filter { entry -> matchesCategory(entry, title) } }
            LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                items(visible, key = { it.uri.toString() }) { entry ->
                    ListItem(
                        headlineContent = { Text(entry.name, maxLines = 1) },
                        supportingContent = { Text(if (entry.isDirectory) "Folder" else formatBytes(entry.length)) },
                        leadingContent = { Icon(if (entry.isDirectory) Icons.Outlined.Folder else Icons.Outlined.Description, null) },
                        trailingContent = { if (entry.uri in selected) Checkbox(true, onCheckedChange = { if (!it) selected.remove(entry.uri) }) },
                        modifier = Modifier.combinedClickable(
                            onLongClick = { if (!entry.isDirectory && entry.uri !in selected) selected.add(entry.uri) },
                            onClick = {
                                if (selected.isNotEmpty()) {
                                    if (entry.isDirectory) return@combinedClickable
                                    if (entry.uri in selected) selected.remove(entry.uri) else selected.add(entry.uri)
                                } else if (entry.isDirectory) path.add(entry.uri)
                                else opened = entry
                            }
                        )
                    )
                }
            }
        }
    }
}

private fun matchesCategory(entry: TreeEntry, title: String): Boolean {
    if (entry.isDirectory) return true
    val type = entry.mimeType.orEmpty()
    return when (title) {
        "Images" -> type.startsWith("image/")
        "Videos" -> type.startsWith("video/")
        "Audio" -> type.startsWith("audio/")
        "Documents" -> type.startsWith("text/") || type == "application/pdf" || type.contains("document")
        "Apps (APKs)" -> entry.name.endsWith(".apk", true)
        "Large files" -> entry.length >= 100L * 1024 * 1024
        else -> true
    }
}
