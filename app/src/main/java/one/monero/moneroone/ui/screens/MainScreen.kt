package one.monero.moneroone.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Wallet
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.ShowChart
import androidx.compose.material.icons.outlined.Wallet
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import android.widget.Toast
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import one.monero.moneroone.core.wallet.WalletViewModel
import androidx.compose.runtime.collectAsState
import one.monero.moneroone.ui.screens.chart.ChartScreen
import one.monero.moneroone.ui.screens.chart.ChartViewModel
import one.monero.moneroone.ui.screens.settings.SettingsScreen
import one.monero.moneroone.ui.screens.wallet.WalletScreen
import one.monero.moneroone.ui.theme.MoneroOrange

data class BottomNavItem(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

@Composable
fun MainScreen(
    walletViewModel: WalletViewModel,
    navController: NavHostController,
    chartViewModel: ChartViewModel,
    onNavigateToSend: () -> Unit = {},
    onNavigateToReceive: () -> Unit = {}
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val context = LocalContext.current
    val chartUiState by chartViewModel.uiState.collectAsState()

    val navItems = listOf(
        BottomNavItem("Wallet", Icons.Filled.Wallet, Icons.Outlined.Wallet),
        BottomNavItem("Chart", Icons.Filled.ShowChart, Icons.Outlined.ShowChart),
        BottomNavItem("Settings", Icons.Filled.Settings, Icons.Outlined.Settings)
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0.dp),
        bottomBar = {
            Box(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .windowInsetsPadding(WindowInsets.navigationBars)
            ) {
                NavigationBar(
                    modifier = Modifier.clip(RoundedCornerShape(28.dp)),
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    tonalElevation = 3.dp
                ) {
                navItems.forEachIndexed { index, item ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        icon = {
                            Icon(
                                imageVector = if (selectedTab == index) item.selectedIcon else item.unselectedIcon,
                                contentDescription = item.label
                            )
                        },
                        label = {
                            Text(
                                text = item.label,
                                style = MaterialTheme.typography.labelMedium
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MoneroOrange,
                            selectedTextColor = MoneroOrange,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            indicatorColor = MoneroOrange.copy(alpha = 0.12f)
                        )
                    )
                }
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = {
                    (fadeIn(
                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                    ) + scaleIn(
                        initialScale = 0.96f,
                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                    )).togetherWith(
                        fadeOut(
                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                        ) + scaleOut(
                            targetScale = 0.96f,
                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                        )
                    )
                },
                label = "tab_content"
            ) { tab ->
                when (tab) {
                    0 -> WalletScreen(
                        walletViewModel = walletViewModel,
                        onSendClick = onNavigateToSend,
                        onReceiveClick = onNavigateToReceive,
                        onTransactionClick = { tx ->
                            navController.navigate("transaction/${tx.hash}")
                        },
                        onSeeAllTransactionsClick = {
                            navController.navigate("transaction_list")
                        },
                        onBalanceClick = {
                            navController.navigate("portfolio_chart")
                        },
                        priceChange24h = chartUiState.priceChange24h
                    )
                    1 -> ChartScreen(
                        viewModel = chartViewModel,
                        onPriceAlertsClick = { navController.navigate("price_alerts") }
                    )
                    2 -> SettingsScreen(
                        walletViewModel = walletViewModel,
                        onBackupClick = { navController.navigate("backup_seed") },
                        onSecurityClick = { navController.navigate("security") },
                        onThemeClick = { navController.navigate("theme") },
                        onCurrencyClick = { navController.navigate("currency") },
                        onPriceAlertsClick = { navController.navigate("price_alerts") },
                        onSyncSettingsClick = { navController.navigate("sync_settings") },
                        onResetSyncClick = {
                            walletViewModel.resetSync()
                            Toast.makeText(context, "Sync reset initiated", Toast.LENGTH_SHORT).show()
                        },
                        onRemoveWalletClick = {
                            walletViewModel.removeWallet()
                            navController.navigate("welcome") {
                                popUpTo(0) { inclusive = true }
                            }
                        },
                        onDonateClick = { navController.navigate("donation") }
                    )
                }
            }
        }
    }
}
