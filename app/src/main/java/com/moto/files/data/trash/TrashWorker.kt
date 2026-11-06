package com.moto.files.data.trash

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class TrashWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        TrashRepository(applicationContext).purgeExpired()
        Result.success()
    } catch (_: Exception) { Result.retry() }
}

fun scheduleTrashPurge(context: Context) {
    WorkManager.getInstance(context).enqueue(OneTimeWorkRequestBuilder<TrashWorker>().build())
    WorkManager.getInstance(context).enqueueUniquePeriodicWork("trash-retention",
        ExistingPeriodicWorkPolicy.KEEP, PeriodicWorkRequestBuilder<TrashWorker>(1, TimeUnit.DAYS).build())
}
