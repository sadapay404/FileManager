package com.moto.files

import android.app.Application
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.moto.files.data.BinPurger
import com.moto.files.data.SettingsStore
import com.moto.files.repo.BinRepository
import com.moto.files.repo.FileRepository
import com.moto.files.repo.MediaRepository
import com.moto.files.security.LockManager
import java.util.concurrent.TimeUnit

class MotoFilesApp : Application() {

    val settings by lazy { SettingsStore.get(this) }
    val locks by lazy { LockManager.get(this) }
    val files by lazy { FileRepository.get(this) }
    val media by lazy { MediaRepository.get(this) }
    val bin by lazy { BinRepository.get(this) }

    override fun onCreate() {
        super.onCreate()
        instance = this
        // Daily worker that purges bin items older than the configured retention.
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            BinPurger.NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<BinPurger>(24, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().build())
                .build()
        )
    }

    companion object {
        @Volatile
        lateinit var instance: MotoFilesApp
            private set
    }
}
