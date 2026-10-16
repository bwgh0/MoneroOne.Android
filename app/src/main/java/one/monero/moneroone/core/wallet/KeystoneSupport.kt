package one.monero.moneroone.core.wallet

import org.json.JSONObject

data class KeystonePairing(
    val primaryAddress: String,
    val privateViewKey: String,
    val restoreHeight: Long,
    val walletName: String,
    val source: String?,
    val encrypted: Boolean
)

object KeystoneUrTypes {
    const val OUTPUT = "xmr-output"
    const val KEY_IMAGE = "xmr-keyimage"
    const val TX_UNSIGNED = "xmr-txunsigned"
    const val TX_SIGNED = "xmr-txsigned"

    fun isExpected(type: String, expected: String): Boolean =
        type.equals(expected, ignoreCase = true)
}

fun parseKeystonePairing(raw: ByteArray): KeystonePairing =
    parseKeystonePairingText(raw.toString(Charsets.UTF_8))

fun parseKeystonePairingText(raw: String): KeystonePairing {
    val json = JSONObject(raw.trim().removePrefix("\uFEFF"))
    val encrypted = json.optBoolean("encrypted", false)
    require(!encrypted) {
        "Keystone Private QR mode is encrypted and is not supported yet. On Keystone, open the normal Monero/Feather connection QR and scan again."
    }

    val source = json.optString("source").trim().takeIf { it.isNotEmpty() }
    if (source != null) {
        require(source.equals("Keystone", ignoreCase = true)) {
            "This is not a Keystone Monero connection QR."
        }
    }

    val address = json.optString("primaryAddress").trim()
    val viewKey = json.optString("privateViewKey").trim().lowercase()
    require(address.isNotEmpty() && address.length <= 200) { "Pairing QR is missing a valid primaryAddress" }
    require(viewKey.matches(Regex("^[0-9a-f]{64}$"))) { "Pairing QR is missing a valid privateViewKey" }
    val height = json.optLong("restoreHeight", 0L).coerceAtLeast(0L)
    val name = json.optString("walletName").trim().take(80).ifEmpty { "Keystone" }
    return KeystonePairing(address, viewKey, height, name, source, encrypted)
}
