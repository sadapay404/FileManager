package com.moto.files.ui.screens

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.moto.files.MotoFilesApp
import com.moto.files.model.Category
import com.moto.files.model.FsEntry
import com.moto.files.tools.Archives
import com.moto.files.tools.Hashing
import com.moto.files.ui.components.StorageBar
import com.moto.files.ui.components.human
import com.moto.files.vm.FilesVm
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilesScreen(vm: FilesVm, app: MotoFilesApp) {
    val dir by vm.dir.collectAsState()
    val entries by vm.entries.collectAsState()
    val picked by vm.picked.collectAsState()
    val storage by vm.storage.collectAsState()
    val clip by vm.clip.collectAsState()
    val scope = rememberCoroutineScope()
    var toast by remember { mutableStateOf<String?>(null) }
    var details by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(dir) { vm.open(dir) }

    Scaffold(
        topBar = {
            if (picked.isNotEmpty()) {
                TopAppBar(
                    title = { Text("${picked.size} selected") },
                    navigationIcon = {
                        IconButton(onClick = { vm.clearPicked() }) {
                            Icon(Icons.Default.Close, "Clear")
                        }
                    },
                    actions = {
                        IconButton(onClick = { vm.cut() }) { Icon(Icons.Default.ContentCut, "Cut") }
                        IconButton(onClick = { vm.copy() }) { Icon(Icons.Default.ContentCopy, "Copy") }
                        IconButton(onClick = { vm.trashSelected() }) { Icon(Icons.Default.Delete, "Delete") }
                        var overflow by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { overflow = true }) {
                                Icon(Icons.Default.MoreVert, "More")
                            }
                            DropdownMenu(overflow, { overflow = false }) {
                                // Permanent delete is ONLY here — never in the primary bar.
                                DropdownMenuItem(
                                    text = { Text("Delete permanently") },
                                    leadingIcon = { Icon(Icons.Default.DeleteForever, null) },
                                    onClick = { overflow = false; vm.deleteForeverSelected() }
                                )
                                DropdownMenuItem(
                                    text = { Text("Archive (ZIP)") },
                                    leadingIcon = { Icon(Icons.Default.Archive, null) },
                                    onClick = {
                                        overflow = false
                                        val files = vm.pickedFiles()
                                        scope.launch {
                                            val dest = File(dir, "archive_${System.currentTimeMillis()}.zip")
                                            toast = if (Archives.create(files, dest, Archives.Kind.ZIP))
                                                "Archived ${files.size} item(s)" else "Archive failed"
                                            vm.clearPicked(); vm.open()
                                        }
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Details / hashes") },
                                    leadingIcon = { Icon(Icons.Default.Info, null) },
                                    onClick = {
                                        overflow = false
                                        val f = vm.pickedFiles().firstOrNull()
                                        if (f != null) scope.launch {
                                            details = "${f.name}\n${human(f.length())}\n" +
                                                "SHA-256 ${Hashing.of(f, Hashing.Algo.SHA256)}\n" +
                                                "MD5 ${Hashing.of(f, Hashing.Algo.MD5)}"
                                        }
                                    }
                                )
                            }
                        }
                    }
                )
            } else {
                TopAppBar(
                    title = { Text(dir.name.ifEmpty { "Internal Storage" }) },
                    navigationIcon = {
                        if (dir != app.files.internal) {
                            IconButton(onClick = { vm.up() }) { Icon(Icons.Default.ArrowBack, "Up") }
                        }
                    },
                    actions = {
                        if (clip != null) {
                            IconButton(onClick = { vm.paste() }) {
                                Icon(Icons.Default.ContentPaste, "Paste")
                            }
                        }
                    }
                )
            }
        }
    ) { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text("Quick access", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(Category.entries) { c ->
                        AssistChip(
                            onClick = { vm.openCategory(c) },
                            label = { Text(c.name.lowercase().replaceFirstChar(Char::uppercase)) },
                            shape = RoundedCornerShape(999.dp)
                        )
                    }
                }
            }
            item {
                val s = storage
                if (s != null) {
                    Card(shape = RoundedCornerShape(24.dp)) {
                        Column(Modifier.padding(20.dp)) {
                            Text("Storage", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(12.dp))
                            StorageBar(s.first, s.second)
                        }
                    }
                }
            }
            items(entries, key = { it.path }) { e ->
                EntryRow(
                    e,
                    e.path in picked,
                    onClick = {
                        if (picked.isNotEmpty()) vm.toggle(e.path)
                        else if (e.isDir) vm.open(File(e.path))
                    },
                    onLongClick = { vm.toggle(e.path) }
                )
            }
        }
    }

    toast?.let {
        AlertDialog(onDismissRequest = { toast = null }, confirmButton = {
            TextButton(onClick = { toast = null }) { Text("OK") }
        }, text = { Text(it) })
    }
    details?.let {
        AlertDialog(onDismissRequest = { details = null }, title = { Text("Details") },
            confirmButton = {
                TextButton(onClick = { details = null }) { Text("Close") }
            }, text = { Text(it, style = MaterialTheme.typography.bodyMedium) })
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun EntryRow(e: FsEntry, selected: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    val icon = when {
        e.isDir -> Icons.Default.Folder
        e.mime?.startsWith("image/") == true -> Icons.Default.Image
        e.mime?.startsWith("video/") == true -> Icons.Default.VideoFile
        e.mime?.startsWith("audio/") == true -> Icons.Default.AudioFile
        e.ext == "APK" -> Icons.Default.Android
        else -> Icons.Default.InsertDriveFile
    }
    ListItem(
        headlineContent = { Text(e.name) },
        supportingContent = { Text(if (e.isDir) "Folder" else human(e.size)) },
        leadingContent = {
            Icon(icon, null, tint = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant)
        },
        colors = ListItemDefaults.colors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surface
        ),
        modifier = Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
    )
}
