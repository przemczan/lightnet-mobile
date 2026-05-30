package com.lightnet.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector

enum class RootTab(val label: String, val icon: ImageVector) {
    Control("Control", Icons.Default.Tune),
    Library("Library", Icons.Default.Palette),
    Devices("Devices", Icons.Default.Wifi),
    Debug("Debug", Icons.Default.BugReport),
}

@Composable
fun LightnetBottomNav(
    selected: RootTab,
    onSelect: (RootTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    NavigationBar(modifier = modifier) {
        RootTab.entries.forEach { tab ->
            NavigationBarItem(
                selected = tab == selected,
                onClick  = { onSelect(tab) },
                icon     = { Icon(tab.icon, contentDescription = tab.label) },
                label    = { Text(tab.label) },
            )
        }
    }
}
