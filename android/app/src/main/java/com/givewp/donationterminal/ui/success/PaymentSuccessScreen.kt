package com.givewp.donationterminal.ui.success

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun PaymentSuccessScreen(
    onNewDonation: () -> Unit,
    onBackToDashboard: () -> Unit,
    viewModel: PaymentSuccessViewModel = hiltViewModel()
) {
    val uiState = remember { viewModel.uiState }
    val transaction = uiState.transaction
    val donorName = transaction?.donor?.fullName ?: uiState.draft?.donor?.fullName

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Filled.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Text("Payment Successful", style = MaterialTheme.typography.headlineMedium)

        Card(modifier = Modifier
            .fillMaxWidth()
            .padding(top = 24.dp)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ReceiptRow("Amount", uiState.formattedAmount)
                if (transaction?.anonymous == false && donorName != null) {
                    ReceiptRow("Donor", donorName)
                }
                uiState.collectionResult?.let { collection ->
                    val brand = collection.cardBrand?.replaceFirstChar { it.uppercase() } ?: "Card"
                    val masked = collection.cardLast4?.let { "•••• $it" } ?: ""
                    ReceiptRow("Payment Method", "$brand $masked".trim())
                }
                transaction?.let {
                    HorizontalDivider()
                    ReceiptRow("Transaction ID", it.transactionId)
                    it.givewpDonationId?.let { id -> ReceiptRow("GiveWP Donation ID", id.toString()) }
                }
            }
        }

        Button(
            onClick = {
                viewModel.onDoneWithFlow()
                onNewDonation()
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 32.dp)
        ) {
            Text("New Donation")
        }
        OutlinedButton(
            onClick = {
                viewModel.onDoneWithFlow()
                onBackToDashboard()
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
        ) {
            Text("Back to Dashboard")
        }
    }
}

@Composable
private fun ReceiptRow(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}
