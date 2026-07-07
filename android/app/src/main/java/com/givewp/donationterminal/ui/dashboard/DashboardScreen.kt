package com.givewp.donationterminal.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AddCard
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.givewp.donationterminal.ui.common.ReaderStatusChip

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onNewDonation: () -> Unit,
    onTransactionHistory: () -> Unit,
    onReaderStatus: () -> Unit,
    onSettings: () -> Unit,
    onLoggedOut: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val readerStatus by viewModel.readerStatus.collectAsState()
    val loggedOut by viewModel.loggedOut.collectAsState()
    val userName by viewModel.userName.collectAsState()

    LaunchedEffect(loggedOut) {
        if (loggedOut) onLoggedOut()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Donation Terminal") },
                actions = {
                    IconButton(onClick = viewModel::logout) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Log out")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            userName?.let {
                Text("Welcome, $it", style = MaterialTheme.typography.titleLarge)
            }

            Row(modifier = Modifier.fillMaxWidth()) {
                ReaderStatusChip(status = readerStatus)
            }

            Button(onClick = onNewDonation, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.AddCard, contentDescription = null)
                Text("  New Donation")
            }
            Button(onClick = onTransactionHistory, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.History, contentDescription = null)
                Text("  Transaction History")
            }
            Button(onClick = onReaderStatus, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.PointOfSale, contentDescription = null)
                Text("  Reader Status")
            }
            Button(onClick = onSettings, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Settings, contentDescription = null)
                Text("  Settings")
            }
        }
    }
}
