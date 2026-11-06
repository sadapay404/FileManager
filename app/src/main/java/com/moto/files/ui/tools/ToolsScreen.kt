package com.moto.files.ui.tools

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.moto.files.data.files.TreeEntry
import com.moto.files.data.files.TreeRepository
import com.moto.files.data.trash.TrashRepository
import com.moto.files.tools.FolderTools
import com.moto.files.tools.PdfTools
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ToolsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val repo = remember { TreeRepository(context) }
    val tools = remember { FolderTools(context) }
    val trash = remember { TrashRepository(context) }
    val pdf = remember { PdfTools(context) }
    val tags = remember { context.getSharedPreferences("tags", 0) }
    val scope = rememberCoroutineScope()
    var folder by remember { mutableStateOf<Uri?>(null) }
    var entries by remember { mutableStateOf<List<TreeEntry>>(emptyList()) }
    var selected by remember { mutableStateOf<TreeEntry?>(null) }
    var query by remember { mutableStateOf("") }
    var tag by remember { mutableStateOf("") }
    var minMb by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var duplicates by remember { mutableStateOf<List<List<TreeEntry>>>(emptyList()) }
    var confirmClean by remember { mutableStateOf(false) }
    val pdfSelection = remember { mutableStateListOf<Uri>() }
    fun refresh() { folder?.let { uri -> scope.launch { entries = repo.list(uri).getOrElse { message = it.message; emptyList() } } } }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                folder = uri; selected = null; duplicates = emptyList(); refresh()
            } catch (failure: SecurityException) { message = failure.message }
        }
    }
    fun perform(action: () -> String) {
        busy = true
        scope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { action() } }
            message = result.getOrElse { "Failed: ${it.message}" }
            busy = false
            refresh()
        }
    }
    if (confirmClean) AlertDialog(onDismissRequest = { confirmClean = false },
        title = { Text("Move extra copies to Trash?") },
        text = { Text("Keep one file from each matching group. Review the list first; files moved to Trash can be restored for 30 days.") },
        confirmButton = { TextButton(onClick = {
            confirmClean = false
            val parent = folder ?: return@TextButton
            val extras = duplicates.flatMap { it.drop(1) }
            perform {
                val failures = extras.count { trash.put(it, parent).isFailure }
                duplicates = emptyList()
                "${extras.size - failures} moved to Trash; $failures failed"
            }
        }) { Text("Move extras") } },
        dismissButton = { TextButton(onClick = { confirmClean = false }) { Text("Cancel") } })
    Column(modifier) {
        Row(Modifier.fillMaxWidth().padding(24.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Tools", style = MaterialTheme.typography.headlineLarge)
            TextButton(onClick = { picker.launch(null) }) { Text("Choose folder") }
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (message != null) Text(message!!, Modifier.padding(horizontal = 24.dp), style = MaterialTheme.typography.bodySmall)
        if (folder == null) {
            Text("Choose a folder to search and analyze. Tools only access that folder and its selected files.", Modifier.padding(24.dp))
            return@Column
        }
        LazyColumn(contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { Text("Find & organize", style = MaterialTheme.typography.titleLarge) }
            item { OutlinedTextField(query, { query = it }, label = { Text("Name, extension or tag") }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
            item { OutlinedTextField(minMb, { minMb = it.filter(Char::isDigit) }, label = { Text("Minimum size in MB (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
            item { Text("Storage", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 12.dp)) }
            item { TextButton(enabled = !busy, onClick = { perform {
                val groups = tools.duplicates(entries)
                duplicates = groups
                "${groups.size} duplicate groups found"
            } }) { Text("Find exact duplicates") } }
            if (duplicates.isNotEmpty()) {
                item { Text("Matching SHA-256 and size; one copy per group is kept.") }
                duplicates.forEachIndexed { index, group ->
                    item { Text("Group ${index + 1}: ${group.joinToString { it.name }}", style = MaterialTheme.typography.bodyMedium) }
                }
                item { Button(enabled = !busy, onClick = { confirmClean = true }) { Text("Move extra copies to Trash") } }
            }
            item { Text("Selected file", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 12.dp)) }
            if (selected != null) {
                val file = selected!!
                item { Text(file.name, style = MaterialTheme.typography.titleMedium) }
                item { OutlinedTextField(tag, { tag = it }, label = { Text("Personal tag") }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
                item { TextButton(onClick = { tags.edit().putString(file.uri.toString(), tag.trim()).apply(); message = "Tag saved" }) { Text("Save tag") } }
                item {
                    Row {
                        TextButton(enabled = !busy, onClick = { perform { "SHA-256: ${tools.hash(file.uri, "SHA-256")}" } }) { Text("SHA-256") }
                        TextButton(enabled = !busy, onClick = { perform { "MD5: ${tools.hash(file.uri, "MD5")}" } }) { Text("MD5") }
                    }
                }
                if (file.mimeType?.startsWith("image/") == true) item {
                    TextButton(enabled = !busy, onClick = { perform { tools.compressImage(file, folder!!); "Compressed JPEG saved alongside original" } }) { Text("Export smaller JPEG") }
                }
                if (file.mimeType == "application/pdf" || file.name.endsWith(".pdf", true)) item {
                    TextButton(enabled = !busy, onClick = { perform { "Created ${pdf.split(file.uri, folder!!)} page PDFs" } }) { Text("Split PDF into pages") }
                }
                if (file.name.endsWith(".zip", true)) item {
                    TextButton(enabled = !busy, onClick = { perform { "Extracted ${tools.unzip(file, folder!!)} files" } }) { Text("Extract ZIP here") }
                }
                item { TextButton(enabled = !busy, onClick = { perform { tools.zip(listOf(file), folder!!); "ZIP created" } }) { Text("Create ZIP") } }
            } else item { Text("Tap a file below to see available tools.") }
            if (pdfSelection.size >= 2) item {
                Button(enabled = !busy, onClick = {
                    val sources = pdfSelection.toList()
                    perform { pdf.merge(sources, folder!!); "PDFs merged in selection order" }
                    pdfSelection.clear()
                }) { Text("Merge ${pdfSelection.size} PDFs") }
            }
            val size = minMb.toLongOrNull()?.times(1024L * 1024L) ?: 0L
            val visible = entries.filter { !it.isDirectory && it.length >= size &&
                (query.isBlank() || it.name.contains(query, true) || tags.getString(it.uri.toString(), "")!!.contains(query, true)) }
            items(visible, key = { it.uri.toString() }) { file ->
                ListItem(headlineContent = { Text(file.name, maxLines = 1) },
                    supportingContent = { val saved = tags.getString(file.uri.toString(), "").orEmpty(); if (saved.isNotEmpty()) Text("#$saved") },
                    trailingContent = { if (file.name.endsWith(".pdf", true)) Checkbox(file.uri in pdfSelection, onCheckedChange = { checked ->
                        if (checked) pdfSelection.add(file.uri) else pdfSelection.remove(file.uri)
                    }) },
                    modifier = Modifier.clickable { selected = file; tag = tags.getString(file.uri.toString(), "").orEmpty() })
            }
        }
    }
}
