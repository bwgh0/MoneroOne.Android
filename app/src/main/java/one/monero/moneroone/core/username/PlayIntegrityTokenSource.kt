package one.monero.moneroone.core.username

import android.content.Context
import com.google.android.gms.tasks.Task
import com.google.android.play.core.integrity.IntegrityManagerFactory
import com.google.android.play.core.integrity.StandardIntegrityException
import com.google.android.play.core.integrity.StandardIntegrityManager.PrepareIntegrityTokenRequest
import com.google.android.play.core.integrity.StandardIntegrityManager.StandardIntegrityTokenRequest
import com.google.android.play.core.integrity.model.StandardIntegrityErrorCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import one.monero.moneroone.BuildConfig
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** The two Play calls of a standard request. Unit tests replace it. */
internal interface StandardIntegrityPlay {
    /** Prepares a new token provider: Google's warmup, which reads the phone's device recall again. */
    suspend fun prepare(): StandardIntegrityProvider
}

/** One prepared provider. Throws [PlayIntegrityError]. */
internal fun interface StandardIntegrityProvider {
    /** The token whose `requestHash` is [requestHash]. */
    suspend fun request(requestHash: String): String
}

/** A failed Play call, with its [StandardIntegrityErrorCode]. */
internal class PlayIntegrityError(val errorCode: Int, cause: Throwable? = null) : Exception(cause)

/**
 * Play Integrity standard requests for username claims. Each token gets a new provider. Google refreshes
 * device recall only in the warmup (prepare), so a provider kept across claims would send the recall bits
 * from before the first claim, and the registry would read the phone's claim slot as unused again (audit F3).
 * A retry of a claim asks for a new token, so it also prepares a new provider.
 */
internal class PlayIntegrityTokenSource internal constructor(private val play: StandardIntegrityPlay) : IntegrityTokenSource {
    override suspend fun token(requestHash: String): String = try {
        try { play.prepare().request(requestHash) } catch (e: PlayIntegrityError) {
            // Play Store data was cleared after the warmup: prepare a new provider once.
            if (e.errorCode != StandardIntegrityErrorCode.INTEGRITY_TOKEN_PROVIDER_INVALID) throw e
            play.prepare().request(requestHash)
        }
    } catch (e: CancellationException) { throw e } catch (e: PlayIntegrityError) {
        throw IntegrityTokenException(UsernameDeviceProof.failure(e.errorCode), e)
    } catch (e: Exception) {
        throw IntegrityTokenException(IntegrityFailure.TRANSIENT, e)
    }

    companion object {
        /** Null when the build has no cloud project number (BuildConfig.PLAY_INTEGRITY_CLOUD_PROJECT_NUMBER). */
        fun forBuild(context: Context): PlayIntegrityTokenSource? {
            val number = BuildConfig.PLAY_INTEGRITY_CLOUD_PROJECT_NUMBER.toLongOrNull()?.takeIf { it > 0 } ?: return null
            return PlayIntegrityTokenSource(GooglePlayIntegrity(context.applicationContext, number))
        }
    }
}

/** The Play Integrity library. Nothing is kept between two prepares. */
private class GooglePlayIntegrity(private val context: Context, private val cloudProjectNumber: Long) : StandardIntegrityPlay {
    override suspend fun prepare(): StandardIntegrityProvider {
        val provider = play {
            IntegrityManagerFactory.createStandard(context)
                .prepareIntegrityToken(PrepareIntegrityTokenRequest.builder().setCloudProjectNumber(cloudProjectNumber).build()).await()
        }
        return StandardIntegrityProvider { requestHash ->
            play { provider.request(StandardIntegrityTokenRequest.builder().setRequestHash(requestHash).build()).await().token() }
        }
    }

    private inline fun <T> play(block: () -> T): T =
        try { block() } catch (e: StandardIntegrityException) { throw PlayIntegrityError(e.errorCode, e) }
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