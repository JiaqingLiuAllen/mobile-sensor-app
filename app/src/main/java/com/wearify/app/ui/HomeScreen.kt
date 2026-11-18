package com.wearify.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.wearify.app.R
import com.wearify.app.data.BleConnectionState
import com.wearify.app.data.HomeSensorSite
import com.wearify.app.data.WearifyUiState

@Composable
fun HomeScreen(
    uiState: WearifyUiState,
    onOpenSettings: () -> Unit,
    onOpenChat: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val connected = uiState.connectionState is BleConnectionState.Connected
    val sites = liveSensorSites(uiState, connected)
    LazyColumn(
        modifier = modifier.fillMaxSize().background(WearifyBackground),
        contentPadding = PaddingValues(start = 20.dp, top = 24.dp, end = 20.dp, bottom = 116.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { HomeHeader(uiState.connectionState, onOpenSettings) }
        item { BodyContextLoom(sites) }
        item { BodyResponseCard(sites, connected) }
        item { AskClothingAiCard(onOpenChat) }
    }
}

private fun liveSensorSites(state: WearifyUiState, connected: Boolean): List<HomeSensorSite> {
    val sample = state.lastSample.takeIf { connected }
    fun site(id: String, placement: String, label: String, reading: String?) =
        HomeSensorSite(id, placement, label, if (reading == null) "waiting" else "observed",
            reading ?: "Waiting for a signal.")

    val light = listOfNotNull(sample?.als?.let { "Ambient light $it" }, sample?.uv?.let { "UV $it" })
    return listOf(
        site("neck", "Neck", "Motion", sample?.ax?.let { "Accelerometer signal available." }),
        site("shoulder", "Shoulder", "Light + UV", light.takeIf { it.isNotEmpty() }?.joinToString(" · ")),
        site("chest", "Chest", "Heart sounds", state.contactMicAnalysis.takeIf { connected && it.filteredPoints.isNotEmpty() }?.verdict?.label),
        site("upper_arm", "Upper arm", "Optical pulse", state.ppgAnalysis.takeIf { connected && it.processedPoints.isNotEmpty() }?.verdict?.label),
        site("armpit", "Armpit", "Temperature", sample?.tempC?.let { "%.1f °C".format(it) }),
    )
}

@Composable
private fun HomeHeader(
    connectionState: BleConnectionState,
    onOpenSettings: () -> Unit,
) {
    val connected = connectionState is BleConnectionState.Connected
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Clothing AI", style = MaterialTheme.typography.displaySmall, color = WearifyInk)
            Text("Your body context", style = MaterialTheme.typography.bodyMedium, color = WearifyMuted)
        }
        Surface(
            modifier = Modifier.clickable(onClick = onOpenSettings),
            color = if (connected) WearifySoftGreen else WearifySurface,
            shape = CircleShape,
            border = BorderStroke(1.dp, WearifyLine),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 11.dp, vertical = 9.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(7.dp).background(if (connected) WearifyPine else WearifySage, CircleShape))
                Text(if (connected) "Connected" else "Off", style = MaterialTheme.typography.labelLarge, color = WearifyInk)
                Icon(Icons.Default.Settings, contentDescription = "Open settings", tint = WearifyMuted, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun BodyContextLoom(
    sites: List<HomeSensorSite>,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = WearifyBlue,
        contentColor = Color.White,
        shape = RoundedCornerShape(30.dp),
    ) {
        Column(
            modifier = Modifier
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF124A9C),
                            Color(0xFF2468C8),
                            Color(0xFF3E7AD4),
                        ),
                    ),
                )
                .padding(start = 18.dp, top = 18.dp, end = 18.dp, bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Sensor map",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White.copy(alpha = 0.72f),
                    fontFamily = WearifyDataFont,
                )
                Text(
                    "TAP AN ICON",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = .72f),
                    fontFamily = WearifyDataFont,
                )
            }
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1024f / 1380f),
                color = Color.White,
                shape = RoundedCornerShape(24.dp),
            ) {
                InteractiveSensorMap(
                    sites = sites,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun BodyResponseCard(
    sites: List<HomeSensorSite>,
    connected: Boolean,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = WearifySurface,
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, WearifyLine),
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Body response", style = MaterialTheme.typography.titleLarge, color = WearifyInk)
                Text(
                    "Current session",
                    style = MaterialTheme.typography.bodySmall,
                    color = WearifyMuted,
                    fontFamily = WearifyDataFont,
                )
            }
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = WearifySoftBlue,
                shape = RoundedCornerShape(18.dp),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 15.dp, vertical = 13.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Text(
                        "OVERALL",
                        style = MaterialTheme.typography.labelSmall,
                        color = WearifyBlue,
                        fontFamily = WearifyDataFont,
                    )
                    Text(
                        if (connected) "Your current sensor readings." else "Connect a wearable or start a demo in Settings.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = WearifyInk,
                    )
                }
            }
            sites.forEach { site ->
                HomeModalitySummaryRow(site)
            }
        }
    }
}

private data class HomeModalityVisual(
    val icon: ImageVector,
    val color: Color,
)

private fun homeModalityVisual(siteId: String): HomeModalityVisual = when (siteId) {
    "neck" -> HomeModalityVisual(Icons.AutoMirrored.Filled.DirectionsWalk, GarmentMotionGreen)
    "shoulder" -> HomeModalityVisual(Icons.Default.WbSunny, GarmentLightOrange)
    "chest" -> HomeModalityVisual(Icons.Default.GraphicEq, GarmentHeartSoundCyan)
    "upper_arm" -> HomeModalityVisual(Icons.Default.Favorite, GarmentPulseRed)
    "armpit" -> HomeModalityVisual(Icons.Default.Thermostat, GarmentTemperaturePurple)
    else -> HomeModalityVisual(Icons.Default.GraphicEq, WearifyBlue)
}

@Composable
private fun HomeModalitySummaryRow(site: HomeSensorSite) {
    val visual = homeModalityVisual(site.id)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Surface(
            modifier = Modifier.size(38.dp),
            color = visual.color.copy(alpha = .12f),
            shape = CircleShape,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = visual.icon,
                    contentDescription = null,
                    tint = visual.color,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                "${site.sensorLabel} · ${site.placement}",
                style = MaterialTheme.typography.labelLarge,
                color = WearifyInk,
            )
            Text(
                site.summary,
                style = MaterialTheme.typography.bodySmall,
                color = WearifyMuted,
            )
        }
    }
}

@Composable
private fun AskClothingAiCard(
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        color = WearifySoftGreen,
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, WearifyLine),
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(R.drawable.home_guide_man_detailed_v3),
                modifier = Modifier.size(48.dp),
                contentDescription = "Clothing AI guide",
                contentScale = ContentScale.Fit,
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("Ask Clothing AI", style = MaterialTheme.typography.titleMedium, color = WearifyInk)
                Text("Open the chat interface", style = MaterialTheme.typography.bodySmall, color = WearifyMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Open Ask Clothing AI", tint = WearifyPine)
        }
    }
}


@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun HomePreview() {
    WearifyTheme {
        HomeScreen(
            uiState = WearifyUiState(),
            onOpenSettings = {},
            onOpenChat = {},
        )
    }
}
