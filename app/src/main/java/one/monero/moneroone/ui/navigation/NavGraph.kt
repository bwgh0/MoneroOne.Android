package one.monero.moneroone.ui.navigation

import one.monero.moneroone.core.locale.tr
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.composable
import one.monero.moneroone.ui.screens.send.OpenSendLink
import one.monero.moneroone.ui.screens.send.PaymentRequestResult
import one.monero.moneroone.ui.screens.send.SendPrefillSource
import one.monero.moneroone.ui.screens.send.readMoneroUri
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
import one.monero.moneroone.ui.screens.wallet.AddWalletScreen
import one.monero.moneroone.ui.screens.keystone.KeystonePairScreen
import one.monero.moneroone.ui.screens.keystone.KeystoneSignScreen
import one.monero.moneroone.ui.screens.keystone.KeystoneKeyImageSyncScreen
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
import one.monero.moneroone.ui.screens.settings.WidgetSettingsScreen
import one.monero.moneroone.core.wallet.WalletViewModel
import one.monero.moneroone.ui.screens.chart.ChartViewModel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

sealed class Screen(val route: String) {
    data object Welcome : Screen("welcome")
    data object CreateWallet : Screen("create_wallet?adding={adding}") {
        fun createRoute(adding: Boolean = false) = "create_wallet?adding=$adding"
    }
    data object RestoreWallet : Screen("restore_wallet?adding={adding}") {
        fun createRoute(adding: Boolean = false) = "restore_wallet?adding=$adding"
    }
    data object AddWallet : Screen("add_wallet")
    data object KeystonePair : Screen("keystone_pair?adding={adding}") {
        fun createRoute(adding: Boolean = false) = "keystone_pair?adding=" + adding
    }
    data object KeystoneSign : Screen("keystone_sign?address={address}&amount={amount}&sweep={sweep}") {
        fun createRoute(address: String, amount: Long, sweep: Boolean): String =
            "keystone_sign?address=" + android.net.Uri.encode(address) + "&amount=" + amount + "&sweep=" + sweep
    }
    data object KeystoneKeyImages : Screen("keystone_key_images")
    data object SetPin : Screen("set_pin")
    data object SetupBiometrics : Screen("setup_biometrics")
    data object Unlock : Screen("unlock")
    data object Main : Screen("main")
    data object Send : Screen("send?address={address}&amount={amount}&source={source}") {
        /** [source]: where a pre-filled amount came from, a [SendPrefillSource] name. */
        fun createRoute(address: String? = null, amount: String? = null, source: SendPrefillSource? = null): String {
            val params = mutableListOf<String>()
            if (!address.isNullOrBlank()) params.add("address=${android.net.Uri.encode(address)}")
            if (!amount.isNullOrBlank()) params.add("amount=${android.net.Uri.encode(amount)}")
            if (source != null) params.add("source=${source.name}")
            return if (params.isEmpty()) "send" else "send?${params.joinToString("&")}"
        }
    }
    data object QRScanner : Screen("qr_scanner")
    data object Receive : Screen("receive")
    data object TransactionDetail : Screen("transaction/{txId}") {
        fun createRoute(txId: String) = "transaction/$txId"
    }
    data object TransactionList : Screen("transaction_list?asOf={asOf}") {
        /** [asOf] (epoch ms) lists only what had happened by then; null lists everything. */
        fun createRoute(asOf: Long? = null) = if (asOf != null) "transaction_list?asOf=$asOf" else "transaction_list"
    }
    data object AddressPicker : Screen("address_picker")
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
    data object WidgetSettings : Screen("widget_settings")
    data object AddPriceAlert : Screen("add_price_alert")
}

// crossFade token: SwiftUI's default .easeInOut (0.35 s, cubic 0.42/0/0.58/1), which iOS runs on unlock.
private val UnlockCrossFade = tween<Float>(350, easing = CubicBezierEasing(0.42f, 0f, 0.58f, 1f))

@Composable
fun MoneroOneNavHost(
    modifier: Modifier = Modifier,
    paymentLink: String? = null,
    onPaymentLinkConsumed: () -> Unit = {},
    navController: NavHostController = rememberNavController(),
    walletViewModel: WalletViewModel = viewModel(),
    chartViewModel: ChartViewModel = viewModel()
) {
    val walletState by walletViewModel.walletState.collectAsState()
    val isLocked by walletViewModel.isLocked.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    val entry by navController.currentBackStackEntryAsState()
    // Keep the request until a wallet is open and authentication is complete.
    // Main opens Send with it. An open Send takes it, or refuses it once the
    // user has confirmed (iOS SendFlowView); while the scanner is up it waits
    // for Send. Other screens, such as wallet setup, keep it until Main.
    // A refused link says why in an alert, as on iOS (openPaymentLink).
    var paymentLinkError by remember { mutableStateOf<String?>(null) }
    var openSendLink by remember { mutableStateOf<OpenSendLink?>(null) }
    LaunchedEffect(paymentLink, isLocked, walletState.hasWallet, entry) {
        val route = entry?.destination?.route
        if (paymentLink == null || isLocked || !walletState.hasWallet ||
            (route != Screen.Main.route && route != Screen.Send.route)) return@LaunchedEffect
        val result = readMoneroUri(paymentLink)
        onPaymentLinkConsumed()
        paymentLinkError = when {
            result is PaymentRequestResult.Invalid -> result.error.message
            // No spend key: say so instead of opening a Send it cannot finish.
            walletViewModel.activeWallet.value?.isViewOnly == true && walletViewModel.activeWallet.value?.isKeystone != true ->
                tr("This wallet is view-only and cannot send. Switch to a wallet that can send, then open the link again.")
            else -> {
                val request = (result as PaymentRequestResult.Valid).data
                if (route == Screen.Send.route) openSendLink = OpenSendLink(request.address, request.amount)
                else navController.navigate(Screen.Send.createRoute(request.address, request.amount, SendPrefillSource.LINK))
                null
            }
        }
    }
    paymentLinkError?.let { message ->
        AlertDialog(
            onDismissRequest = { paymentLinkError = null },
            title = { Text(tr("Can't Open Payment Link")) },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { paymentLinkError = null }) { Text(tr("OK")) }
            }
        )
    }

    // Price/chart fetches run only while a wallet exists, so no IP goes to
    // monero.one before key generation, and none after the last wallet is gone.
    LaunchedEffect(walletState.hasWallet) {
        if (walletState.hasWallet) {
            chartViewModel.start()
        } else {
            chartViewModel.stop()
        }
    }

    // One live price, as on iOS: the wallet screens take each price the Price
    // tab fetches, so the balance card gets one on resume even when the
    // wallet's own fetch at launch failed.
    LaunchedEffect(chartViewModel, walletViewModel) {
        chartViewModel.uiState
            .map { it.currency to it.currentPrice }
            .distinctUntilChanged()
            .collect { (currency, price) -> walletViewModel.takePriceTabPrice(currency, price) }
    }

    // Check for auto-lock when app resumes
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                walletViewModel.checkAndApplyAutoLock()
                // Restart wallet sync if it dropped while backgrounded
                walletViewModel.startWallet()
                // Refresh the price and chart if they went stale while away
                chartViewModel.onForeground()
            } else if (event == Lifecycle.Event.ON_STOP) {
                chartViewModel.onBackground()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Locking drops any in-flight send flow so unlock lands on Main,
    // never on a stale pre-filled send screen
    LaunchedEffect(isLocked) {
        if (isLocked) {
            val route = navController.currentDestination?.route
            if (route == Screen.Send.route || route == Screen.QRScanner.route) {
                navController.popBackStack(Screen.Main.route, inclusive = false)
            }
        }
    }

    // Locked: only the lock screen is composed, nothing of the wallet behind it. Unlocking cross-fades
    // into the wallet as iOS does (ContentView's .easeInOut); locking is instant, so no wallet content
    // fades on screen when the app comes back to its lock.
    AnimatedContent(
        targetState = isLocked && walletState.hasWallet,
        modifier = modifier,
        transitionSpec = {
            if (targetState) {
                EnterTransition.None togetherWith ExitTransition.None
            } else {
                fadeIn(UnlockCrossFade) togetherWith fadeOut(UnlockCrossFade)
            }
        },
        label = "lock"
    ) { locked ->
        if (locked) {
            UnlockScreen(
                walletViewModel = walletViewModel,
                onUnlocked = {
                    // verifyPin already sets isLocked = false, which cross-fades to the wallet
                },
                onResetWallet = {
                    walletViewModel.removeWallet()
                }
            )
            return@AnimatedContent
        }

        val startDestination = remember(walletState.hasWallet) {
            if (!walletState.hasWallet) Screen.Welcome.route else Screen.Main.route
        }

        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.fillMaxSize(),
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
                    onCreateWallet = { navController.navigate(Screen.CreateWallet.createRoute(adding = false)) },
                    onRestoreWallet = { navController.navigate(Screen.RestoreWallet.createRoute(adding = false)) },
                    onPairKeystone = { navController.navigate(Screen.KeystonePair.createRoute(adding = false)) }
                )
            }

            composable(Screen.AddWallet.route) {
                AddWalletScreen(
                    walletViewModel = walletViewModel,
                    onCreateWallet = { navController.navigate(Screen.CreateWallet.createRoute(adding = true)) },
                    onRestoreWallet = { navController.navigate(Screen.RestoreWallet.createRoute(adding = true)) },
                    onPairKeystone = { navController.navigate(Screen.KeystonePair.createRoute(adding = true)) },
                    onBack = { navController.popBackStack() }
                )
            }

            composable(
                route = Screen.CreateWallet.route,
                arguments = listOf(
                    navArgument("adding") {
                        type = NavType.BoolType
                        defaultValue = false
                    }
                )
            ) { backStackEntry ->
                val adding = backStackEntry.arguments?.getBoolean("adding") ?: false
                CreateWalletScreen(
                    walletViewModel = walletViewModel,
                    // The entry id outlives Activity recreation, the lock screen and process death.
                    flowId = backStackEntry.id,
                    isAddingWallet = adding,
                    onWalletCreated = {
                        if (adding) {
                            // 2nd+ wallet: PIN step is skipped, return to Main.
                            navController.popBackStack(Screen.Main.route, inclusive = false)
                        } else {
                            navController.navigate(Screen.SetPin.route) {
                                popUpTo(Screen.Welcome.route) { inclusive = true }
                            }
                        }
                    },
                    onBack = { navController.popBackStack() }
                )
            }

            composable(
                route = Screen.RestoreWallet.route,
                arguments = listOf(
                    navArgument("adding") {
                        type = NavType.BoolType
                        defaultValue = false
                    }
                )
            ) { backStackEntry ->
                val adding = backStackEntry.arguments?.getBoolean("adding") ?: false
                RestoreWalletScreen(
                    walletViewModel = walletViewModel,
                    // The entry id outlives Activity recreation, the lock screen and process death.
                    flowId = backStackEntry.id,
                    isAddingWallet = adding,
                    onWalletRestored = {
                        if (adding) {
                            navController.popBackStack(Screen.Main.route, inclusive = false)
                        } else {
                            navController.navigate(Screen.SetPin.route) {
                                popUpTo(Screen.Welcome.route) { inclusive = true }
                            }
                        }
                    },
                    onBack = { navController.popBackStack() }
                )
            }


            composable(
                route = Screen.KeystonePair.route,
                arguments = listOf(
                    navArgument("adding") {
                        type = NavType.BoolType
                        defaultValue = false
                    }
                )
            ) { backStackEntry ->
                val adding = backStackEntry.arguments?.getBoolean("adding") ?: false
                KeystonePairScreen(
                    walletViewModel = walletViewModel,
                    flowId = backStackEntry.id,
                    isAddingWallet = adding,
                    onPaired = {
                        if (adding) {
                            navController.popBackStack(Screen.Main.route, inclusive = false)
                        } else {
                            navController.navigate(Screen.SetPin.route) {
                                popUpTo(Screen.Welcome.route) { inclusive = true }
                            }
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
                    },
                    navArgument("source") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    }
                )
            ) { backStackEntry ->
                val address = backStackEntry.arguments?.getString("address")?.takeIf { it.isNotBlank() }
                val amount = backStackEntry.arguments?.getString("amount")?.takeIf { it.isNotBlank() }
                val source = backStackEntry.arguments?.getString("source")
                    ?.let { name -> SendPrefillSource.entries.firstOrNull { it.name == name } }
                SendScreen(
                    walletViewModel = walletViewModel,
                    initialAddress = address,
                    initialAmount = amount,
                    prefillSource = source,
                    incomingLink = openSendLink,
                    onIncomingLink = { link, accepted ->
                        if (openSendLink === link) openSendLink = null
                        if (!accepted) paymentLinkError = tr("Send is still showing another payment. Close it, then open the link again.")
                    },
                    onBack = { navController.popBackStack() },
                    onScanQr = { navController.navigate(Screen.QRScanner.route) },
                    onSent = {
                        navController.navigate(Screen.Main.route) {
                            popUpTo(Screen.Main.route) { inclusive = true }
                        }
                    },
                    onKeystoneSign = { dest, atomic, sweep ->
                        navController.navigate(Screen.KeystoneSign.createRoute(dest, atomic, sweep))
                    }
                )
            }


            composable(
                route = Screen.KeystoneSign.route,
                arguments = listOf(
                    navArgument("address") { type = NavType.StringType },
                    navArgument("amount") { type = NavType.LongType; defaultValue = 0L },
                    navArgument("sweep") { type = NavType.BoolType; defaultValue = false }
                )
            ) { backStackEntry ->
                KeystoneSignScreen(
                    walletViewModel = walletViewModel,
                    address = backStackEntry.arguments?.getString("address").orEmpty(),
                    amountAtomic = backStackEntry.arguments?.getLong("amount") ?: 0L,
                    sweepAll = backStackEntry.arguments?.getBoolean("sweep") ?: false,
                    onBack = { navController.popBackStack() },
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
                        navController.navigate(Screen.Send.createRoute(uriData.address, uriData.amount, SendPrefillSource.QR_CODE))
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

            composable(
                route = Screen.TransactionList.route,
                arguments = listOf(
                    navArgument("asOf") {
                        type = NavType.LongType
                        defaultValue = -1L
                    }
                )
            ) { backStackEntry ->
                TransactionListScreen(
                    walletViewModel = walletViewModel,
                    asOf = backStackEntry.arguments?.getLong("asOf")?.takeIf { it >= 0 },
                    onBack = { navController.popBackStack() },
                    onTransactionClick = { txId ->
                        navController.navigate(Screen.TransactionDetail.createRoute(txId))
                    }
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

            composable("language") {
                one.monero.moneroone.ui.screens.settings.LanguageScreen(onBack = { navController.popBackStack() })
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
                    onNodeSettingsClick = { navController.navigate(Screen.NodeSettings.route) },
                    onKeystoneKeyImageSync = { navController.navigate(Screen.KeystoneKeyImages.route) }
                )
            }


            composable(Screen.KeystoneKeyImages.route) {
                KeystoneKeyImageSyncScreen(
                    walletViewModel = walletViewModel,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(Screen.NodeSettings.route) {
                NodeSettingsScreen(
                    onBack = { navController.popBackStack() },
                    onNodeChanged = { walletViewModel.changeNode() }
                )
            }

            composable(Screen.WidgetSettings.route) {
                WidgetSettingsScreen(onBack = { navController.popBackStack() })
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
}
