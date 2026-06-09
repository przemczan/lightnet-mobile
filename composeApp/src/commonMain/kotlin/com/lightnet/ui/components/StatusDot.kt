package com.lightnet.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lightnet.device.ConnectionState

enum class DeviceStatus { Connected, Connecting, Disconnected, Unknown }

fun deviceStatus(connectionState: ConnectionState, isOnline: Boolean?): DeviceStatus = when (connectionState) {
    ConnectionState.IDLE         -> DeviceStatus.Unknown
    ConnectionState.CONNECTING   -> DeviceStatus.Connecting
    ConnectionState.DISCONNECTED -> DeviceStatus.Disconnected
    ConnectionState.CONNECTED    -> when (isOnline) {
        true  -> DeviceStatus.Connected
        false -> DeviceStatus.Disconnected
        null  -> DeviceStatus.Connecting
    }
}

@Composable
fun DeviceStatus.color(): Color = when (this) {
    DeviceStatus.Connected    -> Color(0xFF4CAF50)
    DeviceStatus.Connecting   -> Color(0xFFFFC107)
    DeviceStatus.Disconnected -> Color(0xFFF44336)
    DeviceStatus.Unknown      -> Color(0xFF9E9E9E)
}

@Composable
fun StatusDot(
    status: DeviceStatus,
    modifier: Modifier = Modifier,
    size: Dp = 10.dp,
) {
    Box(modifier.size(size).background(status.color(), CircleShape))
}
