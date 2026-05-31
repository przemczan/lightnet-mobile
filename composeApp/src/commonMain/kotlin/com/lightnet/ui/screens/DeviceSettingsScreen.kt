package com.lightnet.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import com.lightnet.api.http.LightnetHttpClient
import com.lightnet.api.http.model.AppearanceRequest
import com.lightnet.ui.colorToHex
import com.lightnet.device.LightnetDevice
import com.lightnet.discovery.SavedDevice
import com.lightnet.discovery.effectiveHost
import com.lightnet.ui.BackHandlerCompat
import com.lightnet.ui.parseHexColor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceSettingsScreen(
    savedDevice: SavedDevice,
    device: LightnetDevice?,
    httpClient: LightnetHttpClient?,
    onBack: () -> Unit,
) {
    BackHandlerCompat(onBack = onBack)

    val scope = rememberCoroutineScope()
    val snapshot by remember(device) {
        device?.snapshot ?: MutableStateFlow(null)
    }.collectAsState()

    var brightness by remember { mutableStateOf(128f) }
    var palette by remember { mutableStateOf<String?>(null) }
    var baseColors by remember { mutableStateOf<List<String>>(emptyList()) }
    var colorPickerIndex by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(httpClient) {
        val appearance = httpClient?.runCatching { getAppearance() }?.getOrNull() ?: return@LaunchedEffect
        brightness = appearance.brightness.toFloat()
        palette = appearance.palette
        baseColors = appearance.baseColors
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Device settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 16.dp,
                start = 16.dp,
                end = 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item {
                SettingsSectionTitle("APPEARANCE")
                Spacer(Modifier.height(8.dp))
                Card(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        // Brightness
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                                Text("Global brightness", style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    "${(brightness * 100f / 255f).roundToInt()} / 100",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Slider(
                                value = brightness / 255f,
                                onValueChange = { brightness = it * 255f },
                                onValueChangeFinished = {
                                    val b = brightness.toInt()
                                    scope.launch { httpClient?.runCatching { setAppearance(AppearanceRequest(brightness = b)) } }
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }

                        HorizontalDivider()

                        // Base colours
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Base colours", style = MaterialTheme.typography.bodyMedium)
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                            ) {
                                val labels = listOf("Primary", "Secondary", "Tertiary")
                                val fallbacks = listOf(Color(0xFFCF5B3C), Color(0xFF2F6DB0), Color(0xFF3C9A5F))
                                labels.forEachIndexed { i, label ->
                                    val color = baseColors.getOrNull(i)
                                        ?.let { parseHexColor(it) }
                                        ?: fallbacks[i]
                                    ColorSwatch(
                                        label   = label,
                                        color   = color,
                                        onClick = { colorPickerIndex = i },
                                    )
                                }
                            }
                        }

                        HorizontalDivider()

                        // Palette
                        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                            Text("Active palette", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                palette ?: "—",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            item {
                SettingsSectionTitle("FIRMWARE")
                Spacer(Modifier.height(8.dp))
                Card(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        Arrangement.SpaceBetween,
                        Alignment.CenterVertically,
                    ) {
                        Text("Update panel firmware", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Coming soon",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            item {
                SettingsSectionTitle("ABOUT")
                Spacer(Modifier.height(8.dp))
                Card(Modifier.fillMaxWidth()) {
                    Column {
                        val hostname = savedDevice.hostName
                            ?: savedDevice.effectiveHost.ifEmpty { "—" }
                        val panelCount = snapshot?.panels?.size?.toString() ?: "—"
                        AboutRow("Hostname", hostname)
                        HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                        AboutRow("Firmware", "—")
                        HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                        AboutRow("Panels", panelCount)
                    }
                }
            }
        }
    }

    colorPickerIndex?.let { idx ->
        val fallbacks = listOf(Color(0xFFCF5B3C), Color(0xFF2F6DB0), Color(0xFF3C9A5F))
        val initial = baseColors.getOrNull(idx)?.let { parseHexColor(it) } ?: fallbacks.getOrElse(idx) { Color.White }
        ColorPickerSheet(
            initial    = initial,
            baseColors = baseColors,
            onPick     = { color ->
                val updated = baseColors.toMutableList()
                    .also { list ->
                        while (list.size <= idx) list.add("#FFFFFF")
                        list[idx] = colorToHex(color)
                    }
                    .toList()
                baseColors = updated
            },
            onDismiss    = {
                colorPickerIndex = null
                scope.launch {
                    val updated = baseColors
                    httpClient?.runCatching {
                        setAppearance(AppearanceRequest(baseColors = updated))
                    }
                }
            },
        )
    }
}

@Composable
private fun SettingsSectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        letterSpacing = MaterialTheme.typography.labelSmall.letterSpacing,
    )
}

@Composable
private fun AboutRow(label: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun ColorSwatch(label: String, color: Color, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.clickable { onClick() },
    ) {
        Box(
            Modifier
                .size(40.dp)
                .background(color, MaterialTheme.shapes.small)
                .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small),
        )
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

