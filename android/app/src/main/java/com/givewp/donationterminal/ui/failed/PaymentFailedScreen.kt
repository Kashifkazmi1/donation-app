package com.givewp.donationterminal.ui.failed

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun PaymentFailedScreen(
    onRetryReaderConnection: () -> Unit,
    onRetryPaymentProcessing: () -> Unit,
    onCancelToDashboard: () -> Unit,
    viewModel: PaymentFailedViewModel = hiltViewModel()
) {
    val error = viewModel.error

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Filled.ErrorOutline,
            contentDescription = null,
            tint = Color(0xFFB3261E),
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Text("Payment Failed", style = MaterialTheme.typography.headlineMedium)
        Text(
            text = error?.message ?: "An unknown error occurred.",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(top = 8.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Text(
            text = "Code: ${error?.code ?: "UNKNOWN"}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )

        Button(
            onClick = {
                when (viewModel.retryDestination()) {
                    RetryDestination.READER_CONNECTION -> onRetryReaderConnection()
                    RetryDestination.PAYMENT_PROCESSING -> onRetryPaymentProcessing()
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 32.dp)
        ) {
            Text("Retry")
        }
        OutlinedButton(
            onClick = {
                viewModel.onCancel()
                onCancelToDashboard()
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
        ) {
            Text("Cancel")
        }
    }
}
