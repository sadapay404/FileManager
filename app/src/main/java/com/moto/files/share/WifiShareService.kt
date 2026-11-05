package com.moto.files.share

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Environment
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.moto.files.MotoFilesApp
import java.io.File

/** Foreground service hosting the Wi-Fi share server; shows the access URL + token. */
class WifiShareService : Service() {

    private var server: WifiShareServer? = null

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Wi-Fi sharing", NotificationManager.IMPORTANCE_LOW)
        )
        // Server starts in onStartCommand so the caller's root/port extras are honored.
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (server == null) {
            val root = intent?.getStringExtra(EXTRA_ROOT)?.let(::File)
                ?: Environment.getExternalStorageDirectory()
            val port = intent?.getIntExtra(EXTRA_PORT, 8080) ?: 8080
            val s = WifiShareServer(applicationContext, root, port)
            val url = s.start() ?: run { stopSelf(); return START_NOT_STICKY }
            server = s
            startForeground(
                NOTIF_ID,
                NotificationCompat.Builder(this, CHANNEL)
                    .setContentTitle("Wi-Fi file sharing on")
                    .setContentText(url)
                    .setSmallIcon(android.R.drawable.stat_sys_upload)
                    .setOngoing(true)
                    .build()
            )
        }
        return START_STICKY
    }

    override fun onDestroy() {
        server?.stop()
        server = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val CHANNEL = "wifi_share"
        const val NOTIF_ID = 4242
        const val EXTRA_ROOT = "root"
        const val EXTRA_PORT = "port"

        fun start(context: Context, root: File, port: Int) {
            context.startForegroundService(
                Intent(MotoFilesApp.instance, WifiShareService::class.java)
                    .putExtra(EXTRA_ROOT, root.absolutePath)
                    .putExtra(EXTRA_PORT, port)
            )
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, WifiShareService::class.java))
        }
    }
}
