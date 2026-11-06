package com.moto.files

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalLifecycleOwner
import com.moto.files.data.security.Credentials
import com.moto.files.ui.security.LockScreen
import com.moto.files.ui.theme.ThemeMode
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.moto.files.data.trash.scheduleTrashPurge
import com.moto.files.navigation.MotoFilesApp
import com.moto.files.ui.theme.MotoTheme

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE, android.view.WindowManager.LayoutParams.FLAG_SECURE)
        scheduleTrashPurge(this)
        val credentials = Credentials(this)
        val prefs = getSharedPreferences("appearance", MODE_PRIVATE)
        setContent {
            var theme by remember { mutableStateOf(runCatching { ThemeMode.valueOf(prefs.getString("mode", "SYSTEM")!!) }.getOrDefault(ThemeMode.SYSTEM)) }
            var unlocked by remember { mutableStateOf(!credentials.configured()) }
            val lifecycle = LocalLifecycleOwner.current
            DisposableEffect(lifecycle) {
                var stopped = false
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_STOP) stopped = true
                    if (event == Lifecycle.Event.ON_START && stopped) {
                        unlocked = !credentials.configured()
                        stopped = false
                    }
                }
                lifecycle.lifecycle.addObserver(observer)
                onDispose { lifecycle.lifecycle.removeObserver(observer) }
            }
            MotoTheme(theme) {
                Surface(Modifier.fillMaxSize()) {
                    if (unlocked) MotoFilesApp(theme, onTheme = { newTheme ->
                        theme = newTheme
                        prefs.edit().putString("mode", newTheme.name).apply()
                    }, onAppLockConfigured = { unlocked = false })
                    else LockScreen(credentials, onUnlocked = { unlocked = true })
                }
            }
        }
    }
}
