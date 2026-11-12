package pe.pagobox.app.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.flow.first
import pe.pagobox.app.container
import java.util.concurrent.TimeUnit

/** Mantenimiento diario: aplica la política de retención de datos (privacidad). */
class MaintenanceWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val c = applicationContext.container
        val days = c.settings.settings.first().retentionDays
        c.payments.applyRetention(days)
        c.log.info("Mantenimiento", "Retención aplicada: $days días")
        return Result.success()
    }

    companion object {
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<MaintenanceWorker>(1, TimeUnit.DAYS).build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork("pagobox-maintenance", ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
