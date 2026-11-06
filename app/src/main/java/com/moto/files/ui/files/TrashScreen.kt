package com.moto.files.ui.files

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.moto.files.data.trash.TrashRepository
import com.moto.files.data.trash.TrashedFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

@Composable
fun TrashScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val repo = remember(context) { TrashRepository(context) }
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf<List<TrashedFile>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var confirmEmpty by remember { mutableStateOf(false) }
    fun refresh() { scope.launch { items = withContext(Dispatchers.IO) { repo.list() } } }
    LaunchedEffect(Unit) { items = withContext(Dispatchers.IO) { repo.list() } }
    if (confirmEmpty) AlertDialog(onDismissRequest = { confirmEmpty = false }, title = { Text("Empty trash now?") },
        text = { Text("This permanently removes every item in Trash. It cannot be undone.") },
        confirmButton = { TextButton(onClick = {
            confirmEmpty = false; busy = true
            scope.launch {
                val failures = withContext(Dispatchers.IO) { items.count { repo.remove(it).isFailure } }
                busy = false; message = if (failures > 0) "$failures items could not be removed" else "Trash emptied"
                refresh()
            }
        }) { Text("Delete permanently") } },
        dismissButton = { TextButton(onClick = { confirmEmpty = false }) { Text("Cancel") } })
    Column(modifier) {
        Row(Modifier.fillMaxWidth().padding(24.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text("Trash", style = MaterialTheme.typography.headlineLarge)
                Text("Items expire after 30 days", style = MaterialTheme.typography.bodySmall)
            }
            if (items.isNotEmpty()) TextButton(enabled = !busy, onClick = { confirmEmpty = true }) { Text("Empty now") }
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (message != null) Text(message!!, Modifier.padding(horizontal = 24.dp), color = MaterialTheme.colorScheme.primary)
        if (items.isEmpty() && !busy) {
            Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) { Text("Nothing in Trash") }
        } else LazyColumn {
            items(items, key = { it.id }) { item ->
                ListItem(headlineContent = { Text(item.name) },
                    supportingContent = { Text("Deleted ${DateFormat.getDateInstance().format(Date(item.deletedAt))}") },
                    leadingContent = { Icon(Icons.Outlined.DeleteOutline, null) },
                    trailingContent = {
                        TextButton(enabled = !busy, onClick = {
                            busy = true
                            scope.launch {
                                val result = withContext(Dispatchers.IO) { repo.restore(item) }
                                message = result.exceptionOrNull()?.message ?: "Restored ${item.name}"
                                busy = false; refresh()
                            }
                        }) { Text("Restore") }
                    })
            }
        }
    }
}
