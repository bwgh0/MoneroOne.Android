package one.monero.moneroone.core.alert

import android.content.Context
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import one.monero.moneroone.data.model.AlertCondition
import one.monero.moneroone.data.model.Currency
import one.monero.moneroone.data.model.PriceAlert
import timber.log.Timber

class PriceAlertManager(context: Context) {

    private val prefs = context.getSharedPreferences("monero_wallet", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        private const val KEY_ALERTS = "price_alerts"
        private const val COOLDOWN_MS = 3600_000L // 1 hour
    }

    fun getAlerts(): List<PriceAlert> {
        val raw = prefs.getString(KEY_ALERTS, null) ?: return emptyList()
        return try {
            json.decodeFromString<List<PriceAlert>>(raw)
        } catch (e: Exception) {
            Timber.e(e, "Failed to parse price alerts")
            emptyList()
        }
    }

    fun addAlert(alert: PriceAlert) {
        val alerts = getAlerts().toMutableList()
        alerts.add(alert)
        saveAlerts(alerts)
    }

    fun deleteAlert(id: String) {
        val alerts = getAlerts().filter { it.id != id }
        saveAlerts(alerts)
    }

    fun toggleAlert(id: String) {
        val alerts = getAlerts().map {
            if (it.id == id) it.copy(isEnabled = !it.isEnabled) else it
        }
        saveAlerts(alerts)
    }

    fun checkAlerts(prices: Map<Currency, Double>): List<PriceAlert> {
        val alerts = getAlerts()
        val now = System.currentTimeMillis()
        val triggered = mutableListOf<PriceAlert>()

        val updated = alerts.map { alert ->
            if (!alert.isEnabled) return@map alert

            // Cooldown check
            val lastTriggered = alert.lastTriggeredAt ?: 0L
            if (now - lastTriggered < COOLDOWN_MS) return@map alert

            val currency = Currency.entries.find { it.code == alert.currencyCode } ?: return@map alert
            val currentPrice = prices[currency] ?: return@map alert

            val isTriggered = when (alert.condition) {
                AlertCondition.ABOVE -> currentPrice >= alert.targetPrice
                AlertCondition.BELOW -> currentPrice <= alert.targetPrice
            }

            if (isTriggered) {
                triggered.add(alert)
                alert.copy(lastTriggeredAt = now)
            } else {
                alert
            }
        }

        if (triggered.isNotEmpty()) {
            saveAlerts(updated)
        }

        return triggered
    }

    fun hasEnabledAlerts(): Boolean = getAlerts().any { it.isEnabled }

    private fun saveAlerts(alerts: List<PriceAlert>) {
        prefs.edit().putString(KEY_ALERTS, json.encodeToString(alerts)).apply()
    }
}
