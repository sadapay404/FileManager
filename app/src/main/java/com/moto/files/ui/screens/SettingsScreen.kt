package com.moto.files.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.moto.files.MotoFilesApp
import com.moto.files.model.ThemeMode
import com.moto.files.share.WifiShareService
import com.moto.files.ui.components.ExpressiveSlider
import com.moto.files.vm.SettingsVm
import android.os.Environment

/**
 * All intermediate/advanced configuration lives here, grouped into
 * General / Appearance / Security / Storage & Trash / Advanced Tools.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settingsVm: SettingsVm,
    app: MotoFilesApp,
    onBack: () -> Unit,
    openLockSetup: () -> Unit
) {
    val settings by settingsVm.settings.collectAsState()
    val locks = app.locks.settings

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
                }
            )
        }
    ) { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            item { Section("General") {
                var hidden by remember { mutableStateOf(settings.showHidden) }
                SwitchRow("Show hidden files", hidden) {
                    hidden = it; settingsVm.update { s -> s.copy(showHidden = it) }
                }
                var cols by remember { mutableStateOf(settings.gridColumns) }
                Text("Gallery columns: $cols")
                ExpressiveSlider(
                    value = cols.toFloat(),
                    onChange = {
                        cols = it.toInt().coerceIn(2..6)
                        settingsVm.update { s -> s.copy(gridColumns = cols) }
                    },
                    range = 2f..6f
                )
            } }
            item { Section("Appearance") {
                ThemeMode.entries.forEach { t ->
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        RadioButton(
                            selected = settings.theme == t,
                            onClick = { settingsVm.update { s -> s.copy(theme = t) } }
                        )
                        Text(t.name.lowercase().replaceFirstChar(Char::uppercase))
                    }
                }
            } }
            item { Section("Security") {
                var bio by remember { mutableStateOf(locks.biometric) }
                var vsep by remember { mutableStateOf(locks.vaultSeparate) }
                OutlinedButton(onClick = openLockSetup, Modifier.fillMaxWidth()) {
                    Text("Set / change app lock (${locks.kind})")
                }
                SwitchRow("Biometric unlock (falls back to app PIN)", bio) {
                    bio = it; app.locks.setBiometric(it)
                }
                SwitchRow("Vault uses its own credential", vsep) {
                    vsep = it; app.locks.setVaultCredential(it)
                }
            } }
            item { Section("Storage & Trash") {
                Text("Recycle bin auto-purges after ${settings.binDays} days (daily worker).")
            } }
            item { Section("Advanced Tools") {
                var sharing by remember { mutableStateOf(false) }
                val ctx = LocalContext.current
                SwitchRow("Wi-Fi sharing (http://<phone-ip>:${settings.sharePort}/)", sharing) { on ->
                    sharing = on
                    if (on) WifiShareService.start(
                        ctx, Environment.getExternalStorageDirectory(), settings.sharePort)
                    else WifiShareService.stop(ctx)
                }
                val tools = listOf(
                    "Duplicate finder & one-tap cleaner",
                    "Wi-Fi file transfer (token-protected HTTP)",
                    "Photo/video compression",
                    "MD5 / SHA-256 checksums (overflow menu)",
                    "PDF merge & split",
                    "ZIP / 7z / TAR (overflow menu)",
                    "Tags & smart search"
                )
                tools.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
            } }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Text(title, style = MaterialTheme.typography.titleLarge)
    Spacer(Modifier.height(8.dp))
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label)
        Switch(checked, onChange)
    }
}
