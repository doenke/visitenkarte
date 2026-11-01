package de.soenkejacobs.visitenkarte.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import de.soenkejacobs.visitenkarte.VisitenkarteApp
import java.util.concurrent.TimeUnit

/** Gleicht die Inhalte regelmäßig im Hintergrund mit dem Server ab. */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val repo = (applicationContext as VisitenkarteApp).repository
        if (!repo.settings.load().isConfigured) return Result.success()
        return if (repo.syncNow().isSuccess) Result.success() else Result.retry()
    }

    companion object {
        private const val NAME = "periodic-sync"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<SyncWorker>(12, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
