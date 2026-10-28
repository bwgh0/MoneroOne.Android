package one.monero.moneroone.paymentlinks

import android.content.Context
import io.horizontalsystems.monerokit.MoneroKit
import io.horizontalsystems.monerokit.model.WalletManager as NativeWalletManager
import one.monero.moneroone.core.node.NodeCredentialStore
import one.monero.moneroone.core.wallet.DefaultNodes
import one.monero.moneroone.core.wallet.SecurePrefs
import one.monero.moneroone.core.wallet.SeedType
import one.monero.moneroone.core.wallet.SeedValidation
import one.monero.moneroone.core.wallet.WalletFilePasswords
import timber.log.Timber
import java.io.File

/**
 * Envelope wallet files: `no_backup/envelopes/<id>/`, outside the kit's
 * `files/wallets` root that the launch sweep cleans, and outside backups.
 */
object EnvelopeFiles {

    fun root(context: Context): File = File(context.noBackupFilesDir, "envelopes")

    fun dir(context: Context, id: String): File {
        require(PaymentLink.isValidId(id)) { "Bad envelope id" }
        return File(root(context), id)
    }

    /**
     * The kit opens wallets by a name under `files/wallets`. An envelope's name is
     * the relative path from there into its own directory, so the kit works on it
     * unchanged and its files stay out of the wallets root.
     */
    fun kitWalletId(context: Context, id: String): String {
        val walletRoot = File(context.filesDir, "wallets").absoluteFile
        val dir = dir(context, id).absoluteFile.apply { mkdirs() }
        return File(dir, KIT_WALLET_NAME).relativeTo(walletRoot).path
    }

    private const val KIT_WALLET_NAME = "envelope"

    /** Holds the restore height the envelope's cache scans from, once a scan from there has begun. */
    private const val SCANNED_FILE = "scanned-from"

    /** The envelope has a kit wallet on disk (its keys file at least). */
    fun hasWallet(context: Context, id: String): Boolean =
        dir(context, id).listFiles()?.any { it.name.startsWith(KIT_WALLET_NAME) } == true

    /** The restore height this envelope's cache scans from; null when no scan from a known height has begun. */
    fun scannedFrom(context: Context, id: String): Long? =
        runCatching { File(dir(context, id), SCANNED_FILE).takeIf { it.isFile }?.readText()?.trim()?.toLongOrNull() }.getOrNull()

    /** Records that a scan from [restoreHeight] has begun (the kit reported a refresh). The lowest start stays. */
    fun markScanned(context: Context, id: String, restoreHeight: Long) {
        val from = scannedFrom(context, id)?.let { minOf(it, restoreHeight) } ?: restoreHeight
        runCatching { File(dir(context, id), SCANNED_FILE).writeText(from.toString()) }
            .onFailure { Timber.w(it, "PaymentLinks: could not record an envelope scan") }
    }

    /**
     * Deletes the envelope's kit wallet (keys and cache; its secret stays in the record), so the next
     * open restores it and scans from a new height. Waits for a close still writing the cache. Off Main.
     */
    fun deleteWallet(context: Context, id: String) {
        if (!MoneroKit.deleteWallet(context.applicationContext, kitWalletId(context, id))) {
            Timber.w("PaymentLinks: could not delete an envelope cache")
        }
        // wallet2 also leaves an `.unportable` marker next to the cache.
        dir(context, id).listFiles()?.filter { it.name.startsWith(KIT_WALLET_NAME) || it.name == SCANNED_FILE }?.forEach { it.delete() }
    }

    fun delete(context: Context, id: String) {
        if (dir(context, id).deleteRecursively()) {
            WalletFilePasswords.get(context).delete(WalletFilePasswords.envelopeName(id))
        } else {
            Timber.w("PaymentLinks: could not delete envelope files")
        }
    }

    /** Deletes the files of every envelope not in [keep] (settled or removed links). */
    fun sweep(context: Context, keep: Set<String>) {
        root(context).listFiles()?.forEach { file ->
            if (file.name !in keep && file.deleteRecursively()) {
                WalletFilePasswords.get(context).delete(WalletFilePasswords.envelopeName(file.name))
            }
        }
    }
}

/**
 * An envelope's scan must start at or below its restore height. wallet2 opens a wallet saved before its
 * first scan as a new one and moves its start to the chain tip (WalletImpl::doInit), so the blocks below,
 * the funding block among them, are never scanned (iOS Link 10: a check that timed out, an app kill or a
 * node drop during the first scan). The kit already skips that store and puts the start back
 * (WalletService.initWallet); an envelope with no record of a scan begun from its height is restored anyway.
 */
object EnvelopeScanRules {
    /** The envelope's wallet files go before an open from [restoreHeight]: no scan from there or lower has begun. */
    fun rebuildBeforeOpen(hasWallet: Boolean, scannedFrom: Long?, restoreHeight: Long): Boolean =
        hasWallet && (scannedFrom == null || scannedFrom > restoreHeight)
}

/** The node and proxy the main wallet uses, for an envelope kit. */
internal object EnvelopeNode {
    fun kitNodeString(context: Context): String {
        val prefs = context.getSharedPreferences("monero_wallet", Context.MODE_PRIVATE)
        val uri = prefs.getString("selected_node", null) ?: DefaultNodes.initial(context)
        return NodeCredentialStore(SecurePrefs.open(context)).kitNodeString(uri)
    }
}

class EnvelopeMismatchException : IllegalStateException("The envelope did not restore to its address")

/**
 * A new envelope before funding: the link, the address the funding goes to,
 * and what the sender's record keeps (a v2 link carries neither the amount nor
 * the expiry).
 */
class EnvelopeDraft(
    val link: PaymentLink,
    val address: String,
    /** The amount the recipient gets, atomic units. */
    val amount: Long,
    /** Unix seconds; kept in the sender's record only. */
    val expiry: Long
) {
    override fun toString(): String = "EnvelopeDraft(n=${link.id}, a=$amount)"
}

object EnvelopeFactory {

    /** The spec's default expiry: 30 days. */
    const val EXPIRY_SECONDS = 30L * 24 * 60 * 60

    /** The restore height sits this many blocks under the sender's daemon height. */
    const val HEIGHT_MARGIN = 10L

    fun restoreHeight(daemonHeight: Long): Long = (daemonHeight - HEIGHT_MARGIN).coerceAtLeast(0)

    /**
     * Makes a random envelope for a v2 link (new links are always v2): a random
     * 16-byte secret, and the legacy 25 words of its spend key ([EnvelopeV2]).
     * The words are restored through wallet2's legacy restore, and the address
     * it gives must equal the address the kit derives from the words, or
     * nothing may be funded. Native code: call off Main.
     */
    fun create(context: Context, amount: Long, daemonHeight: Long, nowSeconds: Long): EnvelopeDraft {
        val height = restoreHeight(daemonHeight)
        val link = PaymentLink.v2(EnvelopeV2.newSecret(), height) ?: throw EnvelopeMismatchException()
        val validated = SeedValidation.validate(link.words, SeedType.ELECTRUM_25)
        val restored = restoredAddress(context, link.id, link.words, height)
        if (restored != validated.primaryAddress) {
            EnvelopeFiles.delete(context, link.id)
            throw EnvelopeMismatchException()
        }
        return EnvelopeDraft(link, validated.primaryAddress, amount, nowSeconds + EXPIRY_SECONDS)
    }

    /** The address wallet2's legacy restore gives for [words]. Leaves no files behind. */
    fun restoredAddress(context: Context, id: String, words: List<String>, height: Long): String? {
        val dir = EnvelopeFiles.dir(context, id).apply { mkdirs() }
        val file = File(dir, "restore-check")
        return try {
            val wallet = NativeWalletManager.getInstance()
                .recoveryWallet(file, WalletFilePasswords.throwaway(), words.joinToString(" "), "", height)
            try {
                if (wallet.status.isOk) wallet.address else null
            } finally {
                wallet.close()
            }
        } finally {
            dir.listFiles()?.filter { it.name.startsWith("restore-check") }?.forEach { it.delete() }
        }
    }

    /** The address the words derive to (for an own-link check). Native code: call off Main. */
    fun address(words: List<String>): String? = runCatching {
        SeedValidation.validate(words, SeedType.ELECTRUM_25).primaryAddress
    }.getOrNull()
}
