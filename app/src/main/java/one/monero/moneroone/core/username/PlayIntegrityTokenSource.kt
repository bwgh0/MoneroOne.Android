package one.monero.moneroone.core.username

import android.content.Context
import com.google.android.gms.tasks.Task
import com.google.android.play.core.integrity.IntegrityManagerFactory
import com.google.android.play.core.integrity.StandardIntegrityException
import com.google.android.play.core.integrity.StandardIntegrityManager.PrepareIntegrityTokenRequest
import com.google.android.play.core.integrity.StandardIntegrityManager.StandardIntegrityTokenProvider
import com.google.android.play.core.integrity.StandardIntegrityManager.StandardIntegrityTokenRequest
import com.google.android.play.core.integrity.model.StandardIntegrityErrorCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import one.monero.moneroone.BuildConfig
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Play Integrity standard requests for username claims. The token provider is
 * prepared at the first claim and kept for the process, as Google advises.
 */
internal class PlayIntegrityTokenSource private constructor(
    private val context: Context,
    private val cloudProjectNumber: Long,
) : IntegrityTokenSource {
    private val lock = Mutex()
    private var provider: StandardIntegrityTokenProvider? = null

    override suspend fun token(requestHash: String): String = try {
        try { request(requestHash, renew = false) } catch (e: StandardIntegrityException) {
            // The provider expired, or Play Store data was cleared: prepare a new one once.
            if (e.errorCode != StandardIntegrityErrorCode.INTEGRITY_TOKEN_PROVIDER_INVALID) throw e
            request(requestHash, renew = true)
        }
    } catch (e: CancellationException) { throw e } catch (e: StandardIntegrityException) {
        throw IntegrityTokenException(UsernameDeviceProof.failure(e.errorCode), e)
    } catch (e: Exception) {
        throw IntegrityTokenException(IntegrityFailure.TRANSIENT, e)
    }

    private suspend fun request(requestHash: String, renew: Boolean): String {
        val ready = lock.withLock {
            if (renew) provider = null
            provider ?: IntegrityManagerFactory.createStandard(context)
                .prepareIntegrityToken(PrepareIntegrityTokenRequest.builder().setCloudProjectNumber(cloudProjectNumber).build())
                .await().also { provider = it }
        }
        return ready.request(StandardIntegrityTokenRequest.builder().setRequestHash(requestHash).build()).await().token()
    }

    companion object {
        @Volatile private var instance: PlayIntegrityTokenSource? = null

        /** Null when the build has no cloud project number (BuildConfig.PLAY_INTEGRITY_CLOUD_PROJECT_NUMBER). */
        fun forBuild(context: Context): PlayIntegrityTokenSource? {
            val number = BuildConfig.PLAY_INTEGRITY_CLOUD_PROJECT_NUMBER.toLongOrNull()?.takeIf { it > 0 } ?: return null
            return instance ?: synchronized(this) {
                instance ?: PlayIntegrityTokenSource(context.applicationContext, number).also { instance = it }
            }
        }
    }
}

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addOnCompleteListener { task ->
        val error = task.exception
        when {
            error != null -> continuation.resumeWithException(error)
            // A task that Play cancelled is a transient failure, not a cancel of the claim.
            task.isCanceled -> continuation.resumeWithException(IllegalStateException("Play Integrity task cancelled"))
            else -> continuation.resume(task.result)
        }
    }
}
