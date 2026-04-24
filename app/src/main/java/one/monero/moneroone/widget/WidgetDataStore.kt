package one.monero.moneroone.widget

import android.content.Context

object WidgetDataStore {

    private const val PREFS_NAME = "monero_widget_data"
    private const val KEY_PRICE = "price"
    private const val KEY_CHANGE_24H = "change_24h"
    private const val KEY_CURRENCY_CODE = "currency_code"
    private const val KEY_CURRENCY_SYMBOL = "currency_symbol"
    private const val KEY_BALANCE = "balance"
    private const val KEY_UNLOCKED_BALANCE = "unlocked_balance"
    private const val KEY_TRANSACTIONS = "transactions"
    private const val KEY_WALLET_WIDGET_ENABLED = "wallet_widget_enabled"
    private const val KEY_PRICE_UPDATED_AT = "price_updated_at"

    fun savePrice(context: Context, price: Double, change24h: Double?, currencyCode: String, currencySymbol: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putFloat(KEY_PRICE, price.toFloat())
            .putFloat(KEY_CHANGE_24H, (change24h ?: 0.0).toFloat())
            .putString(KEY_CURRENCY_CODE, currencyCode)
            .putString(KEY_CURRENCY_SYMBOL, currencySymbol)
            .putLong(KEY_PRICE_UPDATED_AT, System.currentTimeMillis())
            .apply()
    }

    fun getPriceUpdatedAt(context: Context): Long =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getLong(KEY_PRICE_UPDATED_AT, 0L)

    fun getPrice(context: Context): Float =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getFloat(KEY_PRICE, 0f)

    fun getChange24h(context: Context): Float =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getFloat(KEY_CHANGE_24H, 0f)

    fun getCurrencySymbol(context: Context): String =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_CURRENCY_SYMBOL, "$") ?: "$"

    // Balance data
    private const val KEY_BALANCE_UPDATED_AT = "balance_updated_at"
    private const val KEY_SYNC_STATUS = "sync_status"

    fun saveBalance(context: Context, balance: Long, unlockedBalance: Long) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putLong(KEY_BALANCE, balance)
            .putLong(KEY_UNLOCKED_BALANCE, unlockedBalance)
            .putLong(KEY_BALANCE_UPDATED_AT, System.currentTimeMillis())
            .apply()
    }

    fun getBalance(context: Context): Long =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getLong(KEY_BALANCE, 0L)

    fun getUnlockedBalance(context: Context): Long =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getLong(KEY_UNLOCKED_BALANCE, 0L)

    fun getBalanceUpdatedAt(context: Context): Long =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getLong(KEY_BALANCE_UPDATED_AT, 0L)

    fun saveSyncStatus(context: Context, status: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_SYNC_STATUS, status)
            .apply()
    }

    fun getSyncStatus(context: Context): String =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_SYNC_STATUS, "synced") ?: "synced"

    // Transaction data (stored as simple pipe-delimited string)
    // Format: "direction|amount|timestamp;direction|amount|timestamp;..."
    fun saveTransactions(context: Context, transactions: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_TRANSACTIONS, transactions)
            .putLong(KEY_BALANCE_UPDATED_AT, System.currentTimeMillis())
            .apply()
    }

    fun getTransactions(context: Context): String =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_TRANSACTIONS, "") ?: ""

    // Chart data for price widget (comma-separated doubles)
    private const val KEY_CHART_POINTS = "chart_points"

    fun saveChartPoints(context: Context, points: List<Double>) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_CHART_POINTS, points.joinToString(","))
            .apply()
    }

    fun getChartPoints(context: Context): List<Double> {
        val str = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_CHART_POINTS, null) ?: return emptyList()
        return str.split(",").mapNotNull { it.toDoubleOrNull() }
    }

    // Wallet widget enable/disable
    fun setWalletWidgetEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_WALLET_WIDGET_ENABLED, enabled)
            .apply()
    }

    fun isWalletWidgetEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_WALLET_WIDGET_ENABLED, false)
}
