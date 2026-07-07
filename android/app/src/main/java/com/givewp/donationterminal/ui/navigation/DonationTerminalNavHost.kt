package com.givewp.donationterminal.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.givewp.donationterminal.ui.dashboard.DashboardScreen
import com.givewp.donationterminal.ui.failed.PaymentFailedScreen
import com.givewp.donationterminal.ui.history.TransactionDetailScreen
import com.givewp.donationterminal.ui.history.TransactionHistoryScreen
import com.givewp.donationterminal.ui.login.LoginScreen
import com.givewp.donationterminal.ui.newdonation.NewDonationScreen
import com.givewp.donationterminal.ui.payment.PaymentProcessingScreen
import com.givewp.donationterminal.ui.reader.ReaderConnectionScreen
import com.givewp.donationterminal.ui.settings.SettingsScreen
import com.givewp.donationterminal.ui.splash.SplashScreen
import com.givewp.donationterminal.ui.success.PaymentSuccessScreen

@Composable
fun DonationTerminalNavHost(navController: NavHostController) {
    NavHost(navController = navController, startDestination = Screen.Splash.route) {

        composable(Screen.Splash.route) {
            SplashScreen(
                onNavigateToLogin = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                },
                onNavigateToDashboard = {
                    navController.navigate(Screen.Dashboard.route) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Login.route) {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(Screen.Dashboard.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Dashboard.route) {
            DashboardScreen(
                onNewDonation = { navController.navigate(Screen.NewDonation.route) },
                onTransactionHistory = { navController.navigate(Screen.TransactionHistory.route) },
                onReaderStatus = { navController.navigate(Screen.ReaderConnection.route) },
                onSettings = { navController.navigate(Screen.Settings.route) },
                onLoggedOut = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.Dashboard.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.NewDonation.route) {
            NewDonationScreen(
                onBack = { navController.popBackStack() },
                onContinueToPayment = { navController.navigate(Screen.ReaderConnection.route) }
            )
        }

        composable(Screen.ReaderConnection.route) {
            ReaderConnectionScreen(
                onBack = { navController.popBackStack() },
                onCollectPayment = { navController.navigate(Screen.PaymentProcessing.route) }
            )
        }

        composable(Screen.PaymentProcessing.route) {
            PaymentProcessingScreen(
                onSuccess = {
                    navController.navigate(Screen.PaymentSuccess.route) {
                        popUpTo(Screen.NewDonation.route) { inclusive = true }
                    }
                },
                onFailed = {
                    navController.navigate(Screen.PaymentFailed.route) {
                        popUpTo(Screen.PaymentProcessing.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.PaymentSuccess.route) {
            PaymentSuccessScreen(
                onNewDonation = {
                    navController.navigate(Screen.NewDonation.route) {
                        popUpTo(Screen.Dashboard.route) { inclusive = false }
                    }
                },
                onBackToDashboard = {
                    navController.navigate(Screen.Dashboard.route) {
                        popUpTo(Screen.Dashboard.route) { inclusive = false }
                    }
                }
            )
        }

        composable(Screen.PaymentFailed.route) {
            PaymentFailedScreen(
                onRetryReaderConnection = {
                    navController.navigate(Screen.ReaderConnection.route) {
                        popUpTo(Screen.NewDonation.route) { inclusive = false }
                    }
                },
                onRetryPaymentProcessing = {
                    navController.navigate(Screen.PaymentProcessing.route) {
                        popUpTo(Screen.PaymentFailed.route) { inclusive = true }
                    }
                },
                onCancelToDashboard = {
                    navController.navigate(Screen.Dashboard.route) {
                        popUpTo(Screen.Dashboard.route) { inclusive = false }
                    }
                }
            )
        }

        composable(Screen.TransactionHistory.route) {
            TransactionHistoryScreen(
                onTransactionClick = { transactionId ->
                    navController.navigate(Screen.TransactionDetail.createRoute(transactionId))
                }
            )
        }

        composable(
            route = Screen.TransactionDetail.route,
            arguments = listOf(navArgument(Screen.TransactionDetail.ARG_TRANSACTION_ID) { type = NavType.StringType })
        ) {
            TransactionDetailScreen(onBack = { navController.popBackStack() })
        }

        composable(Screen.Settings.route) {
            SettingsScreen()
        }
    }
}
