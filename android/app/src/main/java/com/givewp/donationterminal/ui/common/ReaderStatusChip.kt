package com.givewp.donationterminal.ui.common

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.givewp.donationterminal.domain.repository.ReaderConnectionStatus

@Composable
fun ReaderStatusChip(status: ReaderConnectionStatus, modifier: Modifier = Modifier) {
    val (label, color) = when (status) {
        ReaderConnectionStatus.CONNECTED -> "Reader connected" to Color(0xFF1E8E3E)
        ReaderConnectionStatus.CONNECTING -> "Connecting..." to Color(0xFFB8860B)
        ReaderConnectionStatus.NOT_CONNECTED -> "Reader not connected" to Color(0xFF757575)
    }
    AssistChip(
        onClick = {},
        label = { Text(label) },
        colors = AssistChipDefaults.assistChipColors(labelColor = color),
        modifier = modifier.padding(4.dp)
    )
}
