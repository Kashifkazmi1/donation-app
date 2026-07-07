package com.givewp.donationterminal.ui.reader

import android.bluetooth.BluetoothAdapter
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.givewp.donationterminal.domain.model.DiscoveredReader
import com.givewp.donationterminal.domain.model.ReaderConnectionPhase
import com.givewp.donationterminal.ui.common.ErrorBanner

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderConnectionScreen(
    onBack: () -> Unit,
    onCollectPayment: () -> Unit,
    viewModel: ReaderConnectionViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val navigateToPayment by viewModel.navigateToPayment.collectAsState()
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        viewModel.onPermissionsResult(results.values.all { it })
    }

    val enableBluetoothLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        viewModel.onBluetoothEnableResult(result.resultCode == android.app.Activity.RESULT_OK)
    }

    LaunchedEffect(Unit) {
        viewModel.checkRequirementsAndStart()
    }

    LaunchedEffect(navigateToPayment) {
        if (navigateToPayment) {
            viewModel.consumeNavigationEvent()
            onCollectPayment()
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Reader Connection") }) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            uiState.errorMessage?.let { ErrorBanner(message = it) }

            when (uiState.phase) {
                ReaderConnectionPhase.CHECKING_REQUIREMENTS -> LoadingRow("Checking requirements...")

                ReaderConnectionPhase.MISSING_PERMISSIONS -> RequirementCard(
                    title = "Bluetooth permission required",
                    description = "This app needs Bluetooth (and, on older Android versions, Location) " +
                        "permission to discover and connect to your Stripe M2 card reader.",
                    actionLabel = "Grant Permission",
                    onAction = { permissionLauncher.launch(viewModel.requiredPermissions()) }
                )

                ReaderConnectionPhase.BLUETOOTH_DISABLED -> RequirementCard(
                    title = "Bluetooth is turned off",
                    description = "Turn on Bluetooth to discover nearby card readers.",
                    actionLabel = "Enable Bluetooth",
                    onAction = {
                        enableBluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                    }
                )

                ReaderConnectionPhase.DISCOVERING -> LoadingRow("Searching for nearby readers...")

                ReaderConnectionPhase.NO_READERS_FOUND -> RequirementCard(
                    title = "No readers found",
                    description = "Make sure your Stripe M2 reader is powered on and nearby, then try again.",
                    actionLabel = "Scan Again",
                    onAction = viewModel::retry
                )

                ReaderConnectionPhase.AWAITING_SELECTION -> ReaderList(
                    readers = uiState.discoveredReaders,
                    onSelect = viewModel::onReaderSelected
                )

                ReaderConnectionPhase.CONNECTING -> LoadingRow("Connecting to reader...")

                ReaderConnectionPhase.CONNECTED -> ConnectedReaderCard(
                    reader = uiState.connectedReader,
                    batteryLevel = uiState.batteryLevel
                )

                ReaderConnectionPhase.ERROR -> RequirementCard(
                    title = "Something went wrong",
                    description = uiState.errorMessage ?: "Unable to connect to the reader.",
                    actionLabel = "Try Again",
                    onAction = viewModel::retry
                )
            }

            Button(
                onClick = viewModel::onCollectPaymentClicked,
                enabled = uiState.phase == ReaderConnectionPhase.CONNECTED,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Collect Payment")
            }

            OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                Text("Back")
            }
        }
    }
}

@Composable
private fun LoadingRow(label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        CircularProgressIndicator()
        Text(label, modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun RequirementCard(
    title: String,
    description: String,
    actionLabel: String,
    onAction: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(description, style = MaterialTheme.typography.bodyMedium)
            Button(onClick = onAction) { Text(actionLabel) }
        }
    }
}

@Composable
private fun ReaderList(readers: List<DiscoveredReader>, onSelect: (DiscoveredReader) -> Unit) {
    Column {
        Text("Multiple readers found -- select one:", style = MaterialTheme.typography.titleLarge)
        LazyColumn(modifier = Modifier.padding(top = 8.dp)) {
            items(readers) { reader ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(reader.label ?: reader.serialNumber, style = MaterialTheme.typography.bodyLarge)
                        Text("Serial: ${reader.serialNumber}", style = MaterialTheme.typography.bodyMedium)
                        Button(onClick = { onSelect(reader) }, modifier = Modifier.padding(top = 8.dp)) {
                            Text("Connect")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConnectedReaderCard(reader: DiscoveredReader?, batteryLevel: Float?) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Connected", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
            Text(reader?.label ?: reader?.serialNumber ?: "Reader")
            batteryLevel?.let { level ->
                Text("Battery: ${(level * 100).toInt()}%")
                LinearProgressIndicator(
                    progress = { level.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
