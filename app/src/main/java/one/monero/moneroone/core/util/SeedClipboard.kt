package one.monero.moneroone.core.util

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Clipboard writes for seed words (port of iOS SecureClipboard).
 *
 * - The clip is marked sensitive: Android 13+ hides it in the copy preview,
 *   and keyboards keep it out of their clipboard history.
 * - It is cleared after [LIFETIME_SECONDS] by a WorkManager job, not by a
 *   screen: leaving the screen does not cancel it. A timer in this process
 *   would not do either: Android 14+ freezes an app soon after it goes to
 *   the background, and a frozen app runs no timers. A due job wakes the app,
 *   and it also runs after the process is killed.
 * - A background app cannot read the clipboard (Android 10+), so the clear
 *   cannot check what the clipboard holds. It runs only for the latest copy
 *   made here: each copy replaces the pending job, and a job that finds a
 *   newer copy in memory does nothing.
 */
object SeedClipboard {

    /** iOS SecureClipboard.secretLifetime. */
    const val LIFETIME_SECONDS = 45

    /** The name of ClipDescription.EXTRA_IS_SENSITIVE before API 33; keyboards read it there too. */
    private const val EXTRA_IS_SENSITIVE_COMPAT = "android.content.extra.IS_SENSITIVE"

    private const val CLEAR_WORK = "seed_clipboard_clear"
    private const val KEY_COPY = "copy"

    /** Bumped by every copy. 0 in a process that made none (the copying process was killed). */
    private val latestCopy = AtomicInteger(0)

    fun copy(context: Context, text: String) {
        val app = context.applicationContext
        val clipboard = app.getSystemService(ClipboardManager::class.java) ?: return
        val clip = ClipData.newPlainText("Seed Phrase", text)
        clip.description.extras = PersistableBundle().apply {
            val key = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ClipDescription.EXTRA_IS_SENSITIVE
            } else {
                EXTRA_IS_SENSITIVE_COMPAT
            }
            putBoolean(key, true)
        }
        clipboard.setPrimaryClip(clip)

        val clear = OneTimeWorkRequestBuilder<ClearWorker>()
            .setInitialDelay(LIFETIME_SECONDS.toLong(), TimeUnit.SECONDS)
            .setInputData(workDataOf(KEY_COPY to latestCopy.incrementAndGet()))
            .build()
        WorkManager.getInstance(app).enqueueUniqueWork(CLEAR_WORK, ExistingWorkPolicy.REPLACE, clear)
    }

    /** Clears the clipboard when its seed copy is still the latest one made here. */
    class ClearWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
        override fun doWork(): Result {
            val latest = latestCopy.get()
            if (latest != 0 && latest != inputData.getInt(KEY_COPY, 0)) return Result.success()
            val clipboard = applicationContext.getSystemService(ClipboardManager::class.java)
                ?: return Result.success()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                clipboard.clearPrimaryClip()
            } else {
                clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
            }
            return Result.success()
        }
    }
}
