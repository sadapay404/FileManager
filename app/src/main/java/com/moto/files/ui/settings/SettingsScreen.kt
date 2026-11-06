package com.moto.files.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.moto.files.data.security.Credentials
import com.moto.files.data.security.VaultRepository
import com.moto.files.ui.security.CredentialField
import com.moto.files.ui.theme.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(theme: ThemeMode, onTheme: (ThemeMode) -> Unit, onAppLockConfigured: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val credentials = remember { Credentials(context) }
    val vault = remember { VaultRepository(context) }
    val scope = rememberCoroutineScope()
    var hasLock by remember { mutableStateOf(credentials.configured()) }
    var separate by remember { mutableStateOf(credentials.separateVault()) }
    var biometric by remember { mutableStateOf(credentials.biometricEnabled()) }
    var setup by remember { mutableStateOf<String?>(null) }
    var kind by remember { mutableStateOf("PIN") }
    var secret by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    if (setup != null) AlertDialog(onDismissRequest = { setup = null; secret = ""; confirm = "" },
        title = { Text(if (setup == "vault") "Separate Vault credential" else "Set app lock") },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.horizontalScroll(rememberScrollState())) { listOf("PIN", "Password", "Pattern").forEach { option ->
                    FilterChip(selected = kind == option, onClick = { kind = option; secret = ""; confirm = "" },
                        label = { Text(option) }, modifier = Modifier.padding(end = 4.dp))
                } }
                CredentialField(kind, secret, { secret = it }, "New $kind")
                CredentialField(kind, confirm, { confirm = it }, "Confirm $kind")
                if (message != null) Text(message!!, color = MaterialTheme.colorScheme.error)
            }
        }, confirmButton = { TextButton(onClick = {
            if (secret != confirm) { message = "Credentials do not match"; return@TextButton }
            val target = setup
            scope.launch {
                val result = withContext(Dispatchers.Default) { runCatching { credentials.set(secret, kind, target == "vault") } }
                message = result.exceptionOrNull()?.message
                if (result.isSuccess) {
                    if (target == "vault") { credentials.useSeparateVault(true); separate = true } else { hasLock = true; onAppLockConfigured() }
                    setup = null; secret = ""; confirm = ""
                }
            }
        }) { Text("Save") } }, dismissButton = { TextButton(onClick = { setup = null; secret = ""; confirm = "" }) { Text("Cancel") } })
    LazyColumn(modifier, contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp)) {
        item { Text("Settings", style = MaterialTheme.typography.headlineLarge) }
        item { SettingsHeader("General") }
        item { Text("Your files stay on your device. Folder access is chosen by you.", style = MaterialTheme.typography.bodyMedium) }
        item { SettingsHeader("Appearance") }
        item { Text("Color theme", style = MaterialTheme.typography.titleMedium) }
        item {
            Column { ThemeMode.entries.forEach { option ->
                RadioButtonRow(option.name.lowercase().replaceFirstChar { it.uppercase() }, theme == option) { onTheme(option) }
            } }
        }
        item { SettingsHeader("Security") }
        item { ListItem(headlineContent = { Text("App lock") }, supportingContent = { Text(if (hasLock) "Enabled" else "Set up a credential") },
            trailingContent = { TextButton(onClick = { if (!hasLock) { kind = "PIN"; setup = "app" } }) { Text(if (hasLock) "On" else "Set up") } }) }
        if (hasLock) {
            item { ListItem(headlineContent = { Text("Biometric unlock") }, supportingContent = { Text("Always falls back to the app credential") },
                trailingContent = { Switch(checked = biometric, onCheckedChange = { biometric = it; credentials.setBiometric(it) }) }) }
            item { ListItem(headlineContent = { Text("Separate Vault credential") }, supportingContent = { Text("Vault always asks for a credential") },
                trailingContent = { TextButton(onClick = {
                    if (!separate) {
                        if (vault.items().isEmpty()) { kind = "PIN"; setup = "vault" }
                        else message = "Empty Vault before changing its credential mode"
                    }
                    else if (vault.items().isEmpty()) { credentials.useSeparateVault(false); separate = false }
                    else message = "Empty Vault before changing its credential mode"
                }) { Text(if (separate) "Separate" else "Set up") } }) }
        }
        item { SettingsHeader("Storage & Trash") }
        item { Text("Trash retains files for up to 30 days. Open Trash from the navigation menu to restore or empty it.") }
        item { SettingsHeader("Advanced tools") }
        item { Text("Open Tools from the navigation menu to search, tag, check hashes, find duplicates, create ZIPs and export compressed images.") }
        if (message != null && setup == null) item { Text(message!!, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable private fun SettingsHeader(name: String) {
    Text(name, Modifier.padding(top = 28.dp, bottom = 12.dp), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
}
@Composable private fun RadioButtonRow(name: String, selected: Boolean, onClick: () -> Unit) {
    ListItem(headlineContent = { Text(name) }, leadingContent = { RadioButton(selected, onClick) }, modifier = Modifier)
}
