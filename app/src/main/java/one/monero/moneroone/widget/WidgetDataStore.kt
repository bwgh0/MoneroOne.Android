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

    fun savePrice(context: Context, price: Double, change24h: Double?, currencyCode: String, currencySymbol: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putFloat(KEY_PRICE, price.toFloat())
            .putFloat(KEY_CHANGE_24H, (change24h ?: 0.0).toFloat())
            .putString(KEY_CURRENCY_CODE, currencyCode)
            .putString(KEY_CURRENCY_SYMBOL, currencySymbol)
            .apply()
    }

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
    fun saveBalance(context: Context, balance: Long, unlockedBalance: Long) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putLong(KEY_BALANCE, balance)
            .putLong(KEY_UNLOCKED_BALANCE, unlockedBalance)
            .apply()
    }

    fun getBalance(context: Context): Long =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getLong(KEY_BALANCE, 0L)

    fun getUnlockedBalance(context: Context): Long =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getLong(KEY_UNLOCKED_BALANCE, 0L)

    // Transaction data (stored as simple pipe-delimited string)
    // Format: "direction|amount|timestamp;direction|amount|timestamp;..."
    fun saveTransactions(context: Context, transactions: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_TRANSACTIONS, transactions)
            .apply()
    }

    fun getTransactions(context: Context): String =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_TRANSACTIONS, "") ?: ""

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
