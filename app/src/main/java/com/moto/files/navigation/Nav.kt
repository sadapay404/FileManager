package com.moto.files.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.moto.files.MotoFilesApp
import com.moto.files.ui.lock.LockSetupScreen
import com.moto.files.ui.screens.*
import com.moto.files.vm.*

object Dest {
    const val SHELL = "shell"
    const val BIN = "bin"
    const val VAULT = "vault"
    const val SETTINGS = "settings"
    const val LOCK_SETUP = "lock_setup"
}

@Composable
fun AppNavHost(app: MotoFilesApp) {
    val nav = rememberNavController()
    NavHost(nav, startDestination = Dest.SHELL) {
        composable(Dest.SHELL) {
            ShellScreen(
                filesVm = viewModel { FilesVm(app) },
                galleryVm = viewModel { GalleryVm(app) },
                app = app,
                openBin = { nav.navigate(Dest.BIN) },
                openVault = { nav.navigate(Dest.VAULT) },
                openSettings = { nav.navigate(Dest.SETTINGS) }
            )
        }
        composable(Dest.BIN) {
            BinScreen(viewModel { BinVm(app) }, onBack = { nav.popBackStack() })
        }
        composable(Dest.VAULT) {
            VaultScreen(app = app, onBack = { nav.popBackStack() })
        }
        composable(Dest.SETTINGS) {
            SettingsScreen(
                settingsVm = viewModel { SettingsVm(app) },
                app = app,
                onBack = { nav.popBackStack() },
                openLockSetup = { nav.navigate(Dest.LOCK_SETUP) }
            )
        }
        composable(Dest.LOCK_SETUP) {
            LockSetupScreen(
                locks = app.locks,
                onDone = { nav.popBackStack() },
                onBack = { nav.popBackStack() }
            )
        }
    }
}
