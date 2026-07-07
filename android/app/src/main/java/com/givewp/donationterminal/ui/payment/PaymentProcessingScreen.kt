package com.givewp.donationterminal.ui.payment

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentProcessingScreen(
    onSuccess: () -> Unit,
    onFailed: () -> Unit,
    viewModel: PaymentProcessingViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) { viewModel.start() }

    LaunchedEffect(uiState) {
        when (uiState) {
            is PaymentProcessingUiState.Success -> onSuccess()
            is PaymentProcessingUiState.Failed -> onFailed()
            else -> Unit
        }
    }

    val (stepIndex, label) = when (uiState) {
        PaymentProcessingUiState.CreatingIntent -> 0 to "Creating payment..."
        PaymentProcessingUiState.WaitingForCard -> 1 to "Waiting for card -- tap, insert, or swipe"
        PaymentProcessingUiState.ProcessingPayment -> 2 to "Processing payment..."
        PaymentProcessingUiState.ConfirmingWithBackend -> 3 to "Confirming donation..."
        else -> 0 to "Please wait..."
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Processing Payment") }) }) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator(modifier = Modifier.padding(bottom = 24.dp))
            Text(label, style = MaterialTheme.typography.titleLarge)
            LinearProgressIndicator(
                progress = { (stepIndex + 1) / 4f },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp)
            )

            OutlinedButton(
                onClick = viewModel::cancel,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 32.dp)
            ) {
                Text("Cancel")
            }
        }
    }
}
