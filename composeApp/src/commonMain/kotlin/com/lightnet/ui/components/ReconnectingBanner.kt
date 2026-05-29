package com.lightnet.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun ReconnectingBanner(
    text: String = "Reconnecting…",
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .background(Color(0xFF4A3A0F))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StatusDot(DeviceStatus.Connecting)
        Text(text, color = Color(0xFFFFD580), fontWeight = FontWeight.Bold)
    }
}
