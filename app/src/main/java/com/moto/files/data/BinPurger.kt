package com.moto.files.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.moto.files.repo.BinRepository

/** Daily worker: purges Recycle Bin entries older than the configured retention. */
class BinPurger(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = try {
        val days = SettingsStore.get(applicationContext).load().binDays
        BinRepository.get(applicationContext).purgeOlderThan(days)
        Result.success()
    } catch (e: Exception) {
        Result.retry()
    }

    companion object { const val NAME = "bin_purge" }
}
