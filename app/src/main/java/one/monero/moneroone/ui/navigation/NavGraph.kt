package one.monero.moneroone.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import one.monero.moneroone.ui.screens.MainScreen
import one.monero.moneroone.ui.screens.onboarding.CreateWalletScreen
import one.monero.moneroone.ui.screens.onboarding.RestoreWalletScreen
import one.monero.moneroone.ui.screens.onboarding.SetPinScreen
import one.monero.moneroone.ui.screens.onboarding.SetupBiometricsScreen
import one.monero.moneroone.ui.screens.onboarding.WelcomeScreen
import one.monero.moneroone.ui.screens.receive.AddressPickerScreen
import one.monero.moneroone.ui.screens.receive.ReceiveScreen
import one.monero.moneroone.ui.screens.send.QRScannerScreen
import one.monero.moneroone.ui.screens.send.SendScreen
import one.monero.moneroone.ui.screens.transactions.TransactionDetailScreen
import one.monero.moneroone.ui.screens.transactions.TransactionListScreen
import one.monero.moneroone.ui.screens.unlock.UnlockScreen
import one.monero.moneroone.ui.screens.wallet.PortfolioChartScreen
import one.monero.moneroone.ui.screens.settings.BackupSeedScreen
import one.monero.moneroone.ui.screens.settings.ChangePinScreen
import one.monero.moneroone.ui.screens.settings.CurrencyScreen
import one.monero.moneroone.ui.screens.settings.NodeSettingsScreen
import one.monero.moneroone.ui.screens.settings.AddPriceAlertScreen
import one.monero.moneroone.ui.screens.settings.DonationScreen
import one.monero.moneroone.ui.screens.settings.PriceAlertsScreen
import one.monero.moneroone.ui.screens.settings.SecurityScreen
import one.monero.moneroone.ui.screens.settings.SyncSettingsScreen
import one.monero.moneroone.ui.screens.settings.ThemeScreen
import one.monero.moneroone.core.wallet.WalletViewModel
import one.monero.moneroone.ui.screens.chart.ChartViewModel

sealed class Screen(val route: String) {
    data object Welcome : Screen("welcome")
    data object CreateWallet : Screen("create_wallet")
    data object RestoreWallet : Screen("restore_wallet")
    data object SetPin : Screen("set_pin")
    data object SetupBiometrics : Screen("setup_biometrics")
    data object Unlock : Screen("unlock")
    data object Main : Screen("main")
    data object Send : Screen("send?address={address}&amount={amount}") {
        fun createRoute(address: String? = null, amount: String? = null): String {
            val params = mutableListOf<String>()
            if (!address.isNullOrBlank()) params.add("address=$address")
            if (!amount.isNullOrBlank()) params.add("amount=$amount")
            return if (params.isEmpty()) "send" else "send?${params.joinToString("&")}"
        }
    }
    data object QRScanner : Screen("qr_scanner")
    data object Receive : Screen("receive")
    data object TransactionDetail : Screen("transaction/{txId}") {
        fun createRoute(txId: String) = "transaction/$txId"
    }
    data object TransactionList : Screen("transaction_list")
    data object AddressPicker : Screen("address_picker")
    data object PortfolioChart : Screen("portfolio_chart")
    data object Settings : Screen("settings")
    data object BackupSeed : Screen("backup_seed")
    data object Security : Screen("security")
    data object ChangePin : Screen("change_pin")
    data object Theme : Screen("theme")
    data object Currency : Screen("currency")
    data object SyncSettings : Screen("sync_settings")
    data object NodeSettings : Screen("node_settings")
    data object Donation : Screen("donation")
    data object PriceAlerts : Screen("price_alerts")
    data object AddPriceAlert : Screen("add_price_alert")
}

@Composable
fun MoneroOneNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    walletViewModel: WalletViewModel = viewModel(),
    chartViewModel: ChartViewModel = viewModel()
) {
    val walletState by walletViewModel.walletState.collectAsState()
    val isLocked by walletViewModel.isLocked.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current

    // Check for auto-lock when app resumes
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                walletViewModel.checkAndApplyAutoLock()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val startDestination = remember {
        when {
            !walletState.hasWallet -> Screen.Welcome.route
            isLocked -> Screen.Unlock.route
            else -> Screen.Main.route
        }
    }

    androidx.compose.runtime.LaunchedEffect(isLocked, walletState.hasWallet) {
        val currentRoute = navController.currentDestination?.route
        if (walletState.hasWallet && !isLocked && currentRoute != Screen.Main.route) {
            if (currentRoute == Screen.Unlock.route) {
                navController.navigate(Screen.Main.route) {
                    popUpTo(0) { inclusive = true }
                }
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
        enterTransition = {
            fadeIn(
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
            ) + slideIntoContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Start,
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
            )
        },
        exitTransition = {
            fadeOut(
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
            ) + slideOutOfContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Start,
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
            )
        },
        popEnterTransition = {
            fadeIn(
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
            ) + slideIntoContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.End,
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
            )
        },
        popExitTransition = {
            fadeOut(
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
            ) + slideOutOfContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.End,
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
            )
        }
    ) {
        composable(Screen.Welcome.route) {
            WelcomeScreen(
                onCreateWallet = { navController.navigate(Screen.CreateWallet.route) },
                onRestoreWallet = { navController.navigate(Screen.RestoreWallet.route) }
            )
        }

        composable(Screen.CreateWallet.route) {
            CreateWalletScreen(
                walletViewModel = walletViewModel,
                onWalletCreated = {
                    navController.navigate(Screen.SetPin.route) {
                        popUpTo(Screen.Welcome.route) { inclusive = true }
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.RestoreWallet.route) {
            RestoreWalletScreen(
                walletViewModel = walletViewModel,
                onWalletRestored = {
                    navController.navigate(Screen.SetPin.route) {
                        popUpTo(Screen.Welcome.route) { inclusive = true }
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.SetPin.route) {
            SetPinScreen(
                walletViewModel = walletViewModel,
                onPinSet = {
                    navController.navigate(Screen.SetupBiometrics.route) {
                        popUpTo(Screen.SetPin.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.SetupBiometrics.route) {
            SetupBiometricsScreen(
                walletViewModel = walletViewModel,
                onContinue = {
                    navController.navigate(Screen.Main.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Unlock.route) {
            UnlockScreen(
                walletViewModel = walletViewModel,
                onUnlocked = {
                    navController.navigate(Screen.Main.route) {
                        popUpTo(Screen.Unlock.route) { inclusive = true }
                    }
                },
                onResetWallet = {
                    walletViewModel.removeWallet()
                    navController.navigate(Screen.Welcome.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Main.route) {
            MainScreen(
                walletViewModel = walletViewModel,
                navController = navController,
                chartViewModel = chartViewModel,
                onNavigateToSend = { navController.navigate(Screen.Send.createRoute()) },
                onNavigateToReceive = { navController.navigate(Screen.Receive.route) }
            )
        }

        composable(
            route = Screen.Send.route,
            arguments = listOf(
                navArgument("address") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("amount") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val address = backStackEntry.arguments?.getString("address")?.takeIf { it.isNotBlank() }
            val amount = backStackEntry.arguments?.getString("amount")?.takeIf { it.isNotBlank() }
            SendScreen(
                walletViewModel = walletViewModel,
                initialAddress = address,
                initialAmount = amount,
                onBack = { navController.popBackStack() },
                onScanQr = { navController.navigate(Screen.QRScanner.route) },
                onSent = {
                    navController.navigate(Screen.Main.route) {
                        popUpTo(Screen.Main.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.QRScanner.route) {
            QRScannerScreen(
                onBack = { navController.popBackStack() },
                onScanned = { uriData ->
                    navController.popBackStack()
                    navController.navigate(Screen.Send.createRoute(uriData.address, uriData.amount))
                }
            )
        }

        composable(Screen.Receive.route) {
            ReceiveScreen(
                walletViewModel = walletViewModel,
                onBack = { navController.popBackStack() },
                onSelectAddress = { navController.navigate(Screen.AddressPicker.route) }
            )
        }

        composable(Screen.AddressPicker.route) {
            AddressPickerScreen(
                walletViewModel = walletViewModel,
                onBack = { navController.popBackStack() },
                onAddressSelected = { _, _ ->
                    navController.popBackStack()
                }
            )
        }

        // Transaction screens
        composable(
            route = Screen.TransactionDetail.route,
            arguments = listOf(
                navArgument("txId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val txId = backStackEntry.arguments?.getString("txId") ?: ""
            TransactionDetailScreen(
                walletViewModel = walletViewModel,
                txId = txId,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.TransactionList.route) {
            TransactionListScreen(
                walletViewModel = walletViewModel,
                onBack = { navController.popBackStack() },
                onTransactionClick = { txId ->
                    navController.navigate(Screen.TransactionDetail.createRoute(txId))
                }
            )
        }

        composable(Screen.PortfolioChart.route) {
            PortfolioChartScreen(
                walletViewModel = walletViewModel,
                onBack = { navController.popBackStack() },
                chartViewModel = chartViewModel
            )
        }

        // Settings screens
        composable(Screen.BackupSeed.route) {
            BackupSeedScreen(
                walletViewModel = walletViewModel,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Security.route) {
            SecurityScreen(
                walletViewModel = walletViewModel,
                onBack = { navController.popBackStack() },
                onNavigateToChangePin = { navController.navigate(Screen.ChangePin.route) }
            )
        }

        composable(Screen.ChangePin.route) {
            ChangePinScreen(
                walletViewModel = walletViewModel,
                onBack = { navController.popBackStack() },
                onSuccess = { navController.popBackStack() }
            )
        }

        composable(Screen.Theme.route) {
            ThemeScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Currency.route) {
            val currentPrice by walletViewModel.currentPrice.collectAsState()
            val selectedCurrency by walletViewModel.selectedCurrency.collectAsState()
            val isLoading = currentPrice == null

            CurrencyScreen(
                currentPrice = currentPrice,
                selectedCurrency = selectedCurrency,
                isLoading = isLoading,
                onBack = { navController.popBackStack() },
                onCurrencySelected = { currency ->
                    chartViewModel.selectCurrency(currency)
                    walletViewModel.refreshPrice(currency)
                }
            )
        }

        composable(Screen.SyncSettings.route) {
            SyncSettingsScreen(
                walletViewModel = walletViewModel,
                onBack = { navController.popBackStack() },
                onNodeSettingsClick = { navController.navigate(Screen.NodeSettings.route) }
            )
        }

        composable(Screen.NodeSettings.route) {
            NodeSettingsScreen(
                onBack = { navController.popBackStack() },
                onNodeChanged = { walletViewModel.changeNode() }
            )
        }

        composable(Screen.PriceAlerts.route) {
            PriceAlertsScreen(
                onBack = { navController.popBackStack() },
                onAddAlert = { navController.navigate(Screen.AddPriceAlert.route) }
            )
        }

        composable(Screen.AddPriceAlert.route) {
            AddPriceAlertScreen(
                walletViewModel = walletViewModel,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Donation.route) {
            DonationScreen(
                onBack = { navController.popBackStack() },
                onSendXmr = { address, amount ->
                    navController.navigate(Screen.Send.createRoute(address, amount)) {
                        popUpTo(Screen.Donation.route) { inclusive = true }
                    }
                }
            )
        }
    }
}
