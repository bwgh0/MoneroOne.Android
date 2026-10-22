package one.monero.moneroone

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.horizontalsystems.monerokit.SyncState
import one.monero.moneroone.core.username.UsernameBinding
import one.monero.moneroone.ui.screens.wallet.BalanceCard
import one.monero.moneroone.ui.screens.wallet.BalanceHistoryState
import one.monero.moneroone.ui.screens.wallet.UsernameCard
import one.monero.moneroone.ui.screens.wallet.rememberUsernameCardShare
import one.monero.moneroone.ui.screens.wallet.WalletRowCard
import one.monero.moneroone.ui.screens.wallet.WalletRowCheck
import one.monero.moneroone.ui.theme.MoneroOneTheme

/** Renders the real native components without keys, a wallet engine or registry calls. */
class UsernamePreviewActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val dark = intent.getBooleanExtra("dark", false)
        delegate.localNightMode = if (dark) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val handle = intent.getStringExtra("handle")?.take(15) ?: "Joe"
        // --ez share true writes the card's share image to files/username-card-share.png (app external files).
        val writeShare = intent.getBooleanExtra("share", false)
        val binding = UsernameBinding("preview", "mainnet", handle.lowercase(java.util.Locale.ROOT), handle, "", "preview", "", 0)
        setContent {
            MoneroOneTheme(darkTheme = dark) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Column(Modifier.statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(24.dp)) {
                        Text("Username Preview", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                        Text("Synthetic wallet · native components", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        val history = remember { BalanceHistoryState() }
                        BalanceCard(username = binding, balance = 12_482_600_000_000, unlockedBalance = 12_482_600_000_000,
                            formatXmr = { "12.4826" }, liveFiat = { "$3,400.80" }, formatFiat = { "$3,400.80" },
                            fiatMode = false, syncState = SyncState.Synced, isOnline = true, history = history, historicalPoint = null
                        ) { }
                        Text("Your Username", style = MaterialTheme.typography.titleLarge)
                        val (capture, share) = rememberUsernameCardShare()
                        UsernameCard(binding.displayHandle, binding.externalAddress, capture, still = true)
                        if (writeShare) LaunchedEffect(Unit) {
                            withFrameNanos { }; withFrameNanos { }
                            val bitmap = share()
                            java.io.File(getExternalFilesDir(null), "username-card-share.png").outputStream().use {
                                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                            }
                        }
                        Text("Switcher Row", style = MaterialTheme.typography.titleLarge)
                        WalletRowCard(emoji = "\uD83D\uDCB0", name = "Everyday", address = null, username = binding.shortName,
                            balanceText = "12.4826 XMR", isActive = true) { WalletRowCheck(true) }
                    }
                }
            }
        }
    }
}
