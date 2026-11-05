package com.moto.files.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.moto.files.model.BinItem
import com.moto.files.ui.components.human
import com.moto.files.vm.BinVm

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BinScreen(vm: BinVm, onBack: () -> Unit) {
    val items by vm.items.collectAsState()
    var confirm by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.refresh() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Recycle Bin") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
                },
                actions = {
                    IconButton(onClick = { confirm = true }) {
                        Icon(Icons.Default.DeleteSweep, "Empty bin")
                    }
                }
            )
        }
    ) { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Text("Auto-purged after ${BinItem.BIN_DAYS} days.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            items(items, key = { it.id }) { item ->
                ListItem(
                    headlineContent = { Text(item.name) },
                    supportingContent = { Text("${human(item.size)} · ${item.daysLeft} days left") },
                    trailingContent = {
                        IconButton(onClick = { vm.restore(item) }) {
                            Icon(Icons.Default.RestoreFromTrash, "Restore")
                        }
                    }
                )
            }
        }
    }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Empty bin now?") },
            text = { Text("All ${items.size} items are permanently deleted.") },
            confirmButton = {
                TextButton(onClick = { confirm = false; vm.empty() }) { Text("Empty") }
            },
            dismissButton = {
                TextButton(onClick = { confirm = false }) { Text("Cancel") }
            }
        )
    }
}
