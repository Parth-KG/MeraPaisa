package com.kg.merapaisa.backup

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.kg.merapaisa.BackupStore
import com.kg.merapaisa.BuildConfig
import com.kg.merapaisa.data.AppDatabase
import com.kg.merapaisa.data.backupFileName
import com.kg.merapaisa.repository.BackupRepository
import java.util.concurrent.TimeUnit

/**
 * The weekly automatic backup.
 *
 * WorkManager is not a new dependency here even though it is newly declared: Glance already pulls
 * `androidx.work` in for the widget, which is why the audit noted WorkManager initialising on the
 * startup critical path. Declaring it explicitly pins a version that already ships rather than
 * relying on a transitive one that could move underneath us.
 *
 * Nothing about this job needs the network, a charger, or an exemption from battery optimisation.
 * It reads a database and writes a file to a folder the user already chose, so it asks for none of
 * those and will run on any phone without special treatment.
 */
class AutoExportWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        val now = System.currentTimeMillis()

        val folder = BackupStore.folderUriNow(context)
        if (folder.isNullOrEmpty()) {
            // No folder chosen: the feature is off rather than broken. Succeed quietly so
            // WorkManager does not spend the next week retrying something the user never enabled.
            return Result.success()
        }

        val treeUri = runCatching { folder.toUri() }.getOrNull()
            ?: return finish(
                context, now,
                "Couldn't open the backup folder, so nothing was saved. Pick it again with Change folder.",
                Result.success()
            )

        if (!BackupWriter.canWriteTo(context, treeUri)) {
            // The folder was deleted, unmounted, or its permission revoked. Retrying cannot fix
            // that, so this reports rather than loops, and Settings shows the sentence.
            return finish(
                context, now,
                "Couldn't write to the backup folder, so nothing was saved. If it was moved or " +
                    "deleted, pick it again with Change folder.",
                Result.success()
            )
        }

        return try {
            val db = AppDatabase.getDatabase(context)
            val repository = BackupRepository(db, db.personDao(), db.groupDao())
            val json = repository.exportJson(now, BuildConfig.VERSION_NAME)

            val uri = BackupWriter.write(context, treeUri, backupFileName(BackupWriter.stamp(now)), json)
                ?: return finish(
                    context, now,
                    "Couldn't write this week's backup file. Mera Paisa will try again shortly.",
                    Result.retry()
                )

            val pruned = BackupWriter.prune(context, treeUri)
            val note = if (pruned > 0) " ${pruned} older ${if (pruned == 1) "backup" else "backups"} removed." else ""
            finish(context, now, "Saved a backup.$note", Result.success())
        } catch (e: Exception) {
            // A transient failure (storage busy, provider not ready) is worth one more go.
            finish(
                context, now,
                "This week's backup didn't finish. Mera Paisa will try again shortly. " +
                    "(${e.message ?: e::class.simpleName})",
                Result.retry()
            )
        }
    }

    private suspend fun finish(context: Context, at: Long, message: String, result: Result): Result {
        BackupStore.recordRun(context, at, message)
        return result
    }

    companion object {
        /** Unique, so rescheduling replaces the job rather than stacking another one beside it. */
        const val WORK_NAME = "mera-paisa-auto-backup"

        /**
         * Starts the weekly job.
         *
         * `KEEP` rather than `REPLACE`: if the job is already scheduled, leave its clock alone.
         * Replacing would restart the week every time this is called, so an app that scheduled on
         * launch would push the next backup out forever and never actually run one.
         *
         * `UPDATE` would express this better but arrived in WorkManager 2.8, and this project is
         * pinned to the 2.7.1 that Glance already brings in. Turning the setting off calls
         * [cancel], so a genuine re-enable enqueues fresh anyway and KEEP costs nothing.
         */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<AutoExportWorker>(7, TimeUnit.DAYS)
                .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
                // A backup is not urgent; letting the system pick a quiet moment within the window
                // is what keeps it off the critical path of anything the user is doing.
                .setInitialDelay(1, TimeUnit.HOURS)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
