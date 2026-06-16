package com.lightnet.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lightnet.settings.AppPreferences
import com.lightnet.ui.BackHandlerCompat
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlobalSettingsScreen(onBack: () -> Unit) {
    BackHandlerCompat(onBack = onBack)

    val demo = AppPreferences.demo
    val demoEnabled by demo.demoEnabled.collectAsState()
    val demoPanelCount by demo.demoPanelCount.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Text(
                "Demo Device",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )

            ListItem(
                headlineContent = { Text("Enable demo device") },
                supportingContent = { Text("Show a virtual device in your device list") },
                trailingContent = {
                    Switch(
                        checked  = demoEnabled,
                        onCheckedChange = { demo.setEnabled(it) },
                    )
                },
            )

            if (demoEnabled) {
                HorizontalDivider(Modifier.padding(horizontal = 16.dp))

                // Local draft — commits to settings only on drag release to avoid
                // thrashing the demo device recreate loop in App.kt on every tick.
                var sliderDraft by remember(demoPanelCount) { mutableStateOf(demoPanelCount.toFloat()) }
                val displayCount = sliderDraft.roundToInt()

                ListItem(
                    headlineContent = { Text("Number of panels") },
                    supportingContent = {
                        Column {
                            Text("$displayCount panels")
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "1",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(end = 8.dp),
                                )
                                Slider(
                                    value                = sliderDraft,
                                    onValueChange        = { sliderDraft = it },
                                    onValueChangeFinished = { demo.setPanelCount(displayCount) },
                                    valueRange           = 1f..100f,
                                    steps                = 98,
                                    modifier             = Modifier.weight(1f),
                                )
                                Text(
                                    "100",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 8.dp),
                                )
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
