package com.moto.files.ui.screens

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.moto.files.MotoFilesApp
import com.moto.files.model.HomeMode
import com.moto.files.ui.components.ModeSwitch
import com.moto.files.vm.FilesVm
import com.moto.files.vm.GalleryVm
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShellScreen(
    filesVm: FilesVm,
    galleryVm: GalleryVm,
    app: MotoFilesApp,
    openBin: () -> Unit,
    openVault: () -> Unit,
    openSettings: () -> Unit
) {
    var mode by remember { mutableStateOf(HomeMode.FILES) }
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    LaunchedEffect(mode) { if (mode == HomeMode.GALLERY) galleryVm.load() }

    ModalNavigationDrawer(
        drawerState = drawer,
        drawerContent = {
            ModalDrawerSheet {
                Text("Moto Files", style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(24.dp))
                DrawerRow(Icons.Default.Folder, "Internal Storage") {
                    mode = HomeMode.FILES
                    filesVm.open(app.files.internal)
                    scope.launch { drawer.close() }
                }
                DrawerRow(Icons.Default.SdStorage, "SD Card") {
                    app.files.sdCard?.let { filesVm.open(it) }
                    mode = HomeMode.FILES
                    scope.launch { drawer.close() }
                }
                DrawerRow(Icons.Default.Delete, "Recycle Bin") {
                    scope.launch { drawer.close() }
                    openBin()
                }
                DrawerRow(Icons.Default.Lock, "Vault") {
                    scope.launch { drawer.close() }
                    openVault()
                }
                DrawerRow(Icons.Default.Settings, "Settings") {
                    scope.launch { drawer.close() }
                    openSettings()
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(if (mode == HomeMode.FILES) "Files" else "Gallery") },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawer.open() } }) {
                            Icon(Icons.Default.Menu, "Menu")
                        }
                    },
                    actions = {
                        ModeSwitch(
                            gallery = mode == HomeMode.GALLERY,
                            onFiles = { mode = HomeMode.FILES },
                            onGallery = { mode = HomeMode.GALLERY }
                        )
                    }
                )
            }
        ) { pad ->
            Crossfade(
                targetState = mode,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow
                ),
                modifier = Modifier.padding(pad),
                label = "mode"
            ) { m ->
                when (m) {
                    HomeMode.FILES -> FilesScreen(filesVm, app)
                    HomeMode.GALLERY -> GalleryScreen(galleryVm)
                }
            }
        }
    }
}

@Composable
private fun DrawerRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    NavigationDrawerItem(
        icon = { Icon(icon, null) },
        label = { Text(label) },
        selected = false,
        onClick = onClick,
        modifier = Modifier.padding(horizontal = 12.dp)
    )
}
