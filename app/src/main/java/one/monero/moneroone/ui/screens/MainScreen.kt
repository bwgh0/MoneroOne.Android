package one.monero.moneroone.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavHostController
import one.monero.moneroone.core.wallet.WalletViewModel
import one.monero.moneroone.ui.screens.chart.ChartScreen
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
    navController: NavHostController
) {
    var selectedTab by remember { mutableIntStateOf(0) }

    val navItems = listOf(
        BottomNavItem("Wallet", Icons.Filled.Wallet, Icons.Outlined.Wallet),
        BottomNavItem("Chart", Icons.Filled.ShowChart, Icons.Outlined.ShowChart),
        BottomNavItem("Settings", Icons.Filled.Settings, Icons.Outlined.Settings)
    )

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
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
                        label = { Text(item.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MoneroOrange,
                            selectedTextColor = MoneroOrange,
                            indicatorColor = MoneroOrange.copy(alpha = 0.1f)
                        )
                    )
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (selectedTab) {
                0 -> WalletScreen(
                    walletViewModel = walletViewModel,
                    onSendClick = { /* Navigate to send */ },
                    onReceiveClick = { /* Navigate to receive */ },
                    onTransactionClick = { /* Navigate to tx detail */ }
                )
                1 -> ChartScreen()
                2 -> SettingsScreen(
                    walletViewModel = walletViewModel,
                    onBackupClick = { /* Navigate to backup */ },
                    onNodeSettingsClick = { /* Navigate to node settings */ }
                )
            }
        }
    }
}
