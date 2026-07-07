package com.givewp.donationterminal.ui.navigation

sealed class Screen(val route: String) {
    data object Splash : Screen("splash")
    data object Login : Screen("login")
    data object Dashboard : Screen("dashboard")
    data object NewDonation : Screen("new_donation")
    data object ReaderConnection : Screen("reader_connection")
    data object PaymentProcessing : Screen("payment_processing")
    data object PaymentSuccess : Screen("payment_success")
    data object PaymentFailed : Screen("payment_failed")
    data object TransactionHistory : Screen("transaction_history")
    data object TransactionDetail : Screen("transaction_detail/{transactionId}") {
        fun createRoute(transactionId: String) = "transaction_detail/$transactionId"
        const val ARG_TRANSACTION_ID = "transactionId"
    }
    data object Settings : Screen("settings")
}
