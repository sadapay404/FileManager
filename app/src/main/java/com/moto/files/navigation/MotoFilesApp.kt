package com.moto.files.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.moto.files.ui.files.BrowserScreen
import com.moto.files.ui.files.FilesScreen
import com.moto.files.ui.files.TrashScreen
import com.moto.files.ui.gallery.GalleryScreen
import com.moto.files.ui.settings.SettingsScreen
import com.moto.files.ui.vault.VaultScreen
import com.moto.files.ui.tools.ToolsScreen
import com.moto.files.ui.tools.WifiScreen
import com.moto.files.ui.theme.ThemeMode
import kotlinx.coroutines.launch

private enum class Mode { FILES, GALLERY }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MotoFilesApp(theme: ThemeMode, onTheme: (ThemeMode) -> Unit, onAppLockConfigured: () -> Unit) {
    var mode by rememberSaveable { mutableStateOf(Mode.FILES) }
    var tab by rememberSaveable { mutableStateOf("Photos") }
    var destination by rememberSaveable { mutableStateOf<String?>(null) }
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    BackHandler(destination != null) { destination = null }
    ModalNavigationDrawer(drawerState = drawer, drawerContent = {
        ModalDrawerSheet {
            Spacer(Modifier.height(24.dp))
            Text("Moto Files", Modifier.padding(horizontal = 24.dp), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(16.dp))
            listOf("Internal storage", "SD card", "Trash", "Vault", "Tools", "Wi-Fi sharing", "Settings").forEach { name ->
                NavigationDrawerItem(label = { Text(name) }, selected = destination == name, onClick = {
                    destination = name
                    scope.launch { drawer.close() }
                })
            }
        }
    }) {
        Scaffold(
            topBar = {
                TopAppBar(title = { Text(destination ?: "Moto Files") },
                    navigationIcon = { IconButton(onClick = { scope.launch { drawer.open() } }) { Icon(Icons.Outlined.Menu, "Open navigation") } })
            },
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(selected = destination == null && mode == Mode.FILES,
                        onClick = { mode = Mode.FILES; destination = null }, icon = { Icon(Icons.Outlined.Folder, null) }, label = { Text("Files") })
                    listOf("Photos", "Albums", "Memories").forEach { name ->
                        NavigationBarItem(selected = destination == null && mode == Mode.GALLERY && tab == name,
                            onClick = { mode = Mode.GALLERY; tab = name; destination = null },
                            icon = { Icon(when (name) { "Albums" -> Icons.Outlined.PhotoAlbum; "Memories" -> Icons.Outlined.AutoAwesome; else -> Icons.Outlined.Photo }, null) },
                            label = { Text(name) })
                    }
                }
            }
        ) { padding ->
            AnimatedContent(targetState = destination ?: mode.name, modifier = Modifier.padding(padding).fillMaxSize(),
                transitionSpec = { fadeIn(spring()) togetherWith fadeOut(spring()) }, label = "Mode switch") { page ->
                when (page) {
                    Mode.FILES.name -> FilesScreen(onCategory = { destination = it })
                    Mode.GALLERY.name -> GalleryScreen(tab)
                    "Trash" -> TrashScreen()
                    "Vault" -> VaultScreen()
                    "Tools" -> ToolsScreen()
                    "Wi-Fi sharing" -> WifiScreen()
                    "Settings" -> SettingsScreen(theme, onTheme, onAppLockConfigured)
                    "Internal storage", "SD card", "Downloads", "Images", "Videos", "Audio", "Documents", "Apps (APKs)", "Large files" -> BrowserScreen(page)
                    else -> FilesScreen(onCategory = { destination = it })
                }
            }
        }
    }
}
