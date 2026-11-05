package com.moto.files

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.moto.files.navigation.AppNavHost
import com.moto.files.security.BiometricGate
import com.moto.files.theme.MotoFilesTheme
import com.moto.files.ui.lock.LockGate
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val app = MotoFilesApp.instance

        setContent {
            val settings by app.settings.observe().collectAsState(initial = app.settings.load())

            // Runtime permissions — required for a file manager on Android 10+.
            val needed = buildList {
                if (Build.VERSION.SDK_INT >= 33) {
                    add(Manifest.permission.READ_MEDIA_IMAGES)
                    add(Manifest.permission.READ_MEDIA_VIDEO)
                    add(Manifest.permission.READ_MEDIA_AUDIO)
                    add(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    add(Manifest.permission.READ_EXTERNAL_STORAGE)
                }
                if (Build.VERSION.SDK_INT >= 30) {
                    add(Manifest.permission.MANAGE_EXTERNAL_STORAGE)
                }
            }.filter {
                ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
            }
            val launcher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions()
            ) { }
            LaunchedEffect(Unit) {
                if (needed.isNotEmpty()) launcher.launch(needed.toTypedArray())
            }

            MotoFilesTheme(themeMode = settings.theme) {
                Surface(Modifier.fillMaxSize()) {
                    var unlocked by remember { mutableStateOf(!app.locks.lockEnabled) }
                    if (unlocked) {
                        AppNavHost(app)
                    } else {
                        LockGate(
                            locks = app.locks,
                            onUnlocked = { app.locks.markUnlocked(); unlocked = true },
                            showBiometric = { cb ->
                                BiometricGate.prompt(
                                    this,
                                    onSuccess = { cb(BiometricGate.Outcome.Success) },
                                    onUseAppPin = { cb(BiometricGate.Outcome.UseAppPin) }
                                )
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        lifecycleScope.launch { MotoFilesApp.instance.locks.lock() }
    }
}
