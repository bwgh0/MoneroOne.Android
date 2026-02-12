package one.monero.moneroone.data.model

import kotlinx.serialization.Serializable

@Serializable
data class PriceAlert(
    val id: String,
    val condition: AlertCondition,
    val targetPrice: Double,
    val currencyCode: String,
    val isEnabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val lastTriggeredAt: Long? = null
)

@Serializable
enum class AlertCondition { ABOVE, BELOW }
