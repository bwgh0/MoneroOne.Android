package one.monero.moneroone.ui.navigation

import one.monero.moneroone.core.locale.tr
import androidx.biometric.BiometricManager
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.platform.LocalContext
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
import one.monero.moneroone.ui.screens.send.TransferScreen
import one.monero.moneroone.ui.screens.transactions.TransactionDetailScreen
import one.monero.moneroone.ui.screens.transactions.TransactionFilter
import one.monero.moneroone.ui.screens.transactions.TransactionListScreen
import one.monero.moneroone.ui.screens.unlock.UnlockScreen
import one.monero.moneroone.ui.screens.wallet.AddWalletScreen
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
import one.monero.moneroone.paymentlinks.ClaimSheetHost
import one.monero.moneroone.paymentlinks.ActivityTransactionDestination
import one.monero.moneroone.paymentlinks.PaymentLinkCodec
import one.monero.moneroone.paymentlinks.PaymentLinkDetailScreen
import one.monero.moneroone.paymentlinks.PaymentLinkRoutes
import one.monero.moneroone.paymentlinks.PaymentLinksViewModel
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
    data object SetPin : Screen("set_pin")
    data object SetupBiometrics : Screen("setup_biometrics")
    data object Unlock : Screen("unlock")
    data object Main : Screen("main")
    data object Send : Screen("send?address={address}&amount={amount}&source={source}&name={name}&to={to}") {
        /**
         * [source]: where a pre-filled amount came from, a [SendPrefillSource] name.
         * [transferTo]: a Transfer to the wallet with this id (its saved primary address).
         */
        fun createRoute(
            address: String? = null,
            amount: String? = null,
            source: SendPrefillSource? = null,
            name: String? = null,
            transferTo: String? = null
        ): String {
            val params = mutableListOf<String>()
            if (!address.isNullOrBlank()) params.add("address=${android.net.Uri.encode(address)}")
            if (!amount.isNullOrBlank()) params.add("amount=${android.net.Uri.encode(amount)}")
            if (source != null) params.add("source=${source.name}")
            if (!name.isNullOrBlank()) params.add("name=${android.net.Uri.encode(name)}")
            if (!transferTo.isNullOrBlank()) params.add("to=${android.net.Uri.encode(transferTo)}")
            return if (params.isEmpty()) "send" else "send?${params.joinToString("&")}"
        }
    }
    /** Transfer between your wallets, from the wallet switcher. */
    data object Transfer : Screen("transfer")
    data object QRScanner : Screen("qr_scanner")
    data object Receive : Screen("receive")
    data object TransactionDetail : Screen("transaction/{txId}") {
        fun createRoute(txId: String) = "transaction/$txId"
    }
    data object TransactionList : Screen("transaction_list?asOf={asOf}&filter={filter}") {
        /** [asOf] (epoch ms) lists only what had happened by then; null lists everything. [links] opens the Links filter. */
        fun createRoute(asOf: Long? = null, links: Boolean = false): String {
            val args = listOfNotNull(asOf?.let { "asOf=$it" }, "filter=links".takeIf { links })
            return if (args.isEmpty()) "transaction_list" else "transaction_list?" + args.joinToString("&")
        }
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
    chartViewModel: ChartViewModel = viewModel(),
    paymentLinks: PaymentLinksViewModel = viewModel()
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
            walletViewModel.activeWallet.value?.isViewOnly == true ->
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
            if (route == Screen.Send.route || route == Screen.QRScanner.route || route == Screen.Transfer.route) {
                navController.popBackStack(Screen.Main.route, inclusive = false)
            }
        }
    }

    // Onboarding runs from Welcome to Main. Its PIN save makes hasWallet true on the
    // way to Biometrics, so the start screen changes to Main only when this ends
    // (OnboardingRoutes.start). Kept out here so that a lock during onboarding
    // comes back to the same step.
    var onboarding by rememberSaveable { mutableStateOf(!walletState.hasWallet) }
    LaunchedEffect(walletState.hasWallet) {
        // With no wallet left, the next wallet goes through onboarding again.
        if (!walletState.hasWallet) onboarding = true
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

        val startDestination = OnboardingRoutes.start(walletState.hasWallet, onboarding)

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
                    onRestoreWallet = { navController.navigate(Screen.RestoreWallet.createRoute(adding = false)) }
                )
            }

            composable(Screen.AddWallet.route) {
                AddWalletScreen(
                    walletViewModel = walletViewModel,
                    onCreateWallet = { navController.navigate(Screen.CreateWallet.createRoute(adding = true)) },
                    onRestoreWallet = { navController.navigate(Screen.RestoreWallet.createRoute(adding = true)) },
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

            composable(Screen.SetPin.route) {
                val context = LocalContext.current
                SetPinScreen(
                    walletViewModel = walletViewModel,
                    onPinSet = {
                        // As on iOS, a device that cannot use biometrics skips that step.
                        val biometricsAvailable = BiometricManager.from(context)
                            .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
                            BiometricManager.BIOMETRIC_SUCCESS
                        val next = OnboardingRoutes.afterPin(biometricsAvailable)
                        if (next != null) {
                            navController.navigate(next) {
                                popUpTo(Screen.SetPin.route) { inclusive = true }
                            }
                        } else {
                            // The start screen changes to Main, which replaces this screen.
                            onboarding = false
                        }
                    }
                )
            }

            composable(Screen.SetupBiometrics.route) {
                // The wallet was removed from the lock screen during this step:
                // nothing is left to set up, so start over at Welcome.
                LaunchedEffect(walletState.hasWallet) {
                    if (!walletState.hasWallet) {
                        navController.navigate(Screen.Welcome.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                }
                SetupBiometricsScreen(
                    walletViewModel = walletViewModel,
                    // The start screen changes to Main, which replaces this screen.
                    onContinue = { onboarding = false }
                )
            }

            composable(Screen.Main.route) {
                MainScreen(
                    walletViewModel = walletViewModel,
                    navController = navController,
                    chartViewModel = chartViewModel,
                    onNavigateToSend = { navController.navigate(Screen.Send.createRoute()) },
                    onNavigateToReceive = { navController.navigate(Screen.Receive.route) },
                    paymentLinks = paymentLinks,
                    linkAlertShowing = paymentLinkError != null
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
                    },
                    navArgument("name") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                    navArgument("to") {
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
                val name = backStackEntry.arguments?.getString("name")?.takeIf { it.isNotBlank() }
                val transferToId = backStackEntry.arguments?.getString("to")?.takeIf { it.isNotBlank() }
                SendScreen(
                    transferToId = transferToId,
                    walletViewModel = walletViewModel,
                    initialAddress = address,
                    initialAmount = amount,
                    prefillSource = source,
                    initialName = name,
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
                    paymentLinks = paymentLinks,
                    onClaimLink = { paymentLinks.store.accept(it) }
                )
            }

            composable(Screen.Transfer.route) {
                TransferScreen(
                    walletViewModel = walletViewModel,
                    onBack = { navController.popBackStack() },
                    onPick = { wallet -> navController.navigate(Screen.Send.createRoute(transferTo = wallet.id)) }
                )
            }

            composable(Screen.QRScanner.route) {
                QRScannerScreen(
                    onBack = { navController.popBackStack() },
                    onScanned = { uriData ->
                        navController.popBackStack()
                        navController.navigate(Screen.Send.createRoute(uriData.address, uriData.amount, SendPrefillSource.QR_CODE, uriData.name))
                    },
                    onClaimLink = { raw ->
                        PaymentLinkCodec.isClaimLink(raw).also { claim ->
                            if (claim) {
                                paymentLinks.store.accept(raw)
                                navController.popBackStack()
                            }
                        }
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
                // A payment link transaction opens the link's detail.
                ActivityTransactionDestination(
                    txId = txId,
                    linkDetail = { kind, id ->
                        PaymentLinkDetailScreen(
                            paymentLinks = paymentLinks,
                            walletViewModel = walletViewModel,
                            kind = kind,
                            id = id,
                            onBack = { navController.popBackStack() },
                            onOpenTransaction = { navController.navigate(PaymentLinkRoutes.transaction(it)) }
                        )
                    }
                ) {
                    TransactionDetailScreen(
                        walletViewModel = walletViewModel,
                        txId = txId,
                        onBack = { navController.popBackStack() }
                    )
                }
            }

            composable(
                route = Screen.TransactionList.route,
                arguments = listOf(
                    navArgument("asOf") {
                        type = NavType.LongType
                        defaultValue = -1L
                    },
                    navArgument("filter") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    }
                )
            ) { backStackEntry ->
                TransactionListScreen(
                    walletViewModel = walletViewModel,
                    paymentLinks = paymentLinks,
                    asOf = backStackEntry.arguments?.getLong("asOf")?.takeIf { it >= 0 },
                    initialFilter = if (backStackEntry.arguments?.getString("filter") == "links") TransactionFilter.LINKS else TransactionFilter.ALL,
                    onBack = { navController.popBackStack() },
                    onTransactionClick = { txId ->
                        navController.navigate(Screen.TransactionDetail.createRoute(txId))
                    },
                    onOpenSentLink = { id -> navController.navigate(PaymentLinkRoutes.sentDetail(id)) }
                )
            }

            // A sent link with no funding row in the open wallet's history (its funding wallet is gone).
            composable(
                route = PaymentLinkRoutes.SENT_DETAIL,
                arguments = listOf(navArgument("id") { type = NavType.StringType })
            ) { backStackEntry ->
                PaymentLinkDetailScreen(
                    paymentLinks = paymentLinks,
                    walletViewModel = walletViewModel,
                    kind = PaymentLinkRoutes.SENT,
                    id = backStackEntry.arguments?.getString("id").orEmpty(),
                    onBack = { navController.popBackStack() },
                    onOpenTransaction = { navController.navigate(PaymentLinkRoutes.transaction(it)) }
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
                    onNodeSettingsClick = { navController.navigate(Screen.NodeSettings.route) }
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

            composable(
                route = PaymentLinkRoutes.TRANSACTION,
                arguments = listOf(navArgument("txId") { type = NavType.StringType })
            ) { backStackEntry ->
                TransactionDetailScreen(
                    walletViewModel = walletViewModel,
                    txId = backStackEntry.arguments?.getString("txId").orEmpty(),
                    onBack = { navController.popBackStack() }
                )
            }
        }

        // Payment link claims: after unlock, with a wallet, and never over wallet setup.
        val setupRoutes = remember {
            setOf(
                Screen.Welcome.route, Screen.CreateWallet.route, Screen.RestoreWallet.route, Screen.AddWallet.route,
                Screen.SetPin.route, Screen.SetupBiometrics.route, Screen.Unlock.route, Screen.QRScanner.route
            )
        }
        ClaimSheetHost(
            paymentLinks = paymentLinks,
            walletViewModel = walletViewModel,
            canShow = walletState.hasWallet && entry?.destination?.route.let { it != null && it !in setupRoutes }
        )
    }
}
