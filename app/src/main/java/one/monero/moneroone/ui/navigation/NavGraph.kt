package one.monero.moneroone.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import one.monero.moneroone.ui.screens.MainScreen
import one.monero.moneroone.ui.screens.onboarding.CreateWalletScreen
import one.monero.moneroone.ui.screens.onboarding.RestoreWalletScreen
import one.monero.moneroone.ui.screens.onboarding.SetPinScreen
import one.monero.moneroone.ui.screens.onboarding.WelcomeScreen
import one.monero.moneroone.ui.screens.unlock.UnlockScreen
import one.monero.moneroone.core.wallet.WalletViewModel

sealed class Screen(val route: String) {
    data object Welcome : Screen("welcome")
    data object CreateWallet : Screen("create_wallet")
    data object RestoreWallet : Screen("restore_wallet")
    data object SetPin : Screen("set_pin")
    data object Unlock : Screen("unlock")
    data object Main : Screen("main")
    data object Send : Screen("send")
    data object Receive : Screen("receive")
    data object TransactionDetail : Screen("transaction/{txId}") {
        fun createRoute(txId: String) = "transaction/$txId"
    }
    data object Settings : Screen("settings")
    data object ViewSeed : Screen("view_seed")
    data object NodeSettings : Screen("node_settings")
}

@Composable
fun MoneroOneNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    walletViewModel: WalletViewModel = viewModel()
) {
    val walletState by walletViewModel.walletState.collectAsState()
    val isLocked by walletViewModel.isLocked.collectAsState()

    // Determine start destination only once based on initial state
    val startDestination = when {
        !walletState.hasWallet -> Screen.Welcome.route
        isLocked -> Screen.Unlock.route
        else -> Screen.Main.route
    }

    // Handle navigation when lock state changes
    androidx.compose.runtime.LaunchedEffect(isLocked, walletState.hasWallet) {
        val currentRoute = navController.currentDestination?.route
        if (walletState.hasWallet && !isLocked && currentRoute != Screen.Main.route) {
            // Wallet exists and is unlocked - go to main if not already there
            if (currentRoute == Screen.Unlock.route || currentRoute == Screen.SetPin.route) {
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
            fadeIn(animationSpec = tween(300)) +
                slideIntoContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Start,
                    animationSpec = tween(300)
                )
        },
        exitTransition = {
            fadeOut(animationSpec = tween(300)) +
                slideOutOfContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Start,
                    animationSpec = tween(300)
                )
        },
        popEnterTransition = {
            fadeIn(animationSpec = tween(300)) +
                slideIntoContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.End,
                    animationSpec = tween(300)
                )
        },
        popExitTransition = {
            fadeOut(animationSpec = tween(300)) +
                slideOutOfContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.End,
                    animationSpec = tween(300)
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
                }
            )
        }

        composable(Screen.Main.route) {
            MainScreen(
                walletViewModel = walletViewModel,
                navController = navController
            )
        }
    }
}
