package com.wearify.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.wearify.app.R
import com.wearify.app.data.HomeSensorSite

private data class SensorHotspotSpec(
    val id: String,
    val yarnAnchorX: Float,
    val yarnAnchorY: Float,
    val buttonOffsetX: Dp,
    val buttonOffsetY: Dp,
    val color: Color,
    val label: String,
    val icon: ImageVector,
)

private val SensorHotspotTouchSize = 48.dp
private val SensorHotspotVisualSize = 41.dp
private val SensorHotspotIconSize = 20.dp

private val SensorHotspots = listOf(
    SensorHotspotSpec(
        "neck", .62600f, .16000f, 0.dp, 0.dp,
        GarmentMotionGreen, "Motion",
        Icons.AutoMirrored.Filled.DirectionsWalk,
    ),
    SensorHotspotSpec(
        "shoulder", .21200f, .18000f, 0.dp, 0.dp,
        GarmentLightOrange, "Light + UV",
        Icons.Default.WbSunny,
    ),
    SensorHotspotSpec(
        "chest", .56300f, .57000f, 0.dp, 0.dp,
        GarmentHeartSoundCyan, "Heart sound",
        Icons.Default.GraphicEq,
    ),
    SensorHotspotSpec(
        "upper_arm", .80600f, .57000f, 0.dp, 0.dp,
        GarmentPulseRed, "Pulse",
        Icons.Default.Favorite,
    ),
    SensorHotspotSpec(
        "armpit", .69800f, .70000f, 0.dp, 0.dp,
        GarmentTemperaturePurple, "Temperature",
        Icons.Default.Thermostat,
    ),
)

@Composable
fun InteractiveSensorMap(
    sites: List<HomeSensorSite>,
    modifier: Modifier = Modifier,
) {
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedSite = sites.firstOrNull { it.id == selectedId }
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.White)
            .semantics {
                contentDescription =
                    "Interactive garment map with five modality buttons"
            },
    ) {
        Image(
                    painter = painterResource(R.drawable.home_garment_photo_v6),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit,
        )
        SensorHotspots.forEach { spec ->
            val site = sites.firstOrNull { it.id == spec.id } ?: return@forEach
            SensorIconButton(
                site = site,
                spec = spec,
                selected = selectedId == spec.id,
                modifier = Modifier.offset(
                    x = maxWidth * spec.yarnAnchorX + spec.buttonOffsetX - SensorHotspotTouchSize / 2,
                    y = maxHeight * spec.yarnAnchorY + spec.buttonOffsetY - SensorHotspotTouchSize / 2,
                ),
                onActivate = {
                    selectedId = if (selectedId == spec.id) null else spec.id
                },
            )
        }
        AnimatedVisibility(
            visible = selectedSite != null,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(14.dp)
                .zIndex(2f),
            enter = fadeIn() + scaleIn(initialScale = .96f),
            exit = fadeOut() + scaleOut(targetScale = .96f),
        ) {
            selectedSite?.let { site ->
                SensorSummaryPopover(
                    site = site,
                    spec = SensorHotspots.first { it.id == site.id },
                    onDismiss = { selectedId = null },
                )
            }
        }
    }
}

@Composable
private fun SensorIconButton(
    site: HomeSensorSite,
    spec: SensorHotspotSpec,
    selected: Boolean,
    modifier: Modifier,
    onActivate: () -> Unit,
) {
    Box(
        modifier = modifier
            .size(SensorHotspotTouchSize)
            .clickable(onClick = onActivate)
            .clearAndSetSemantics {
                role = Role.Button
                contentDescription =
                    "${spec.label}, ${site.status}. Tap for the current reading."
                onClick(label = "Show ${spec.label} summary") {
                    onActivate()
                    true
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        val supported = site.status in setOf("accepted", "observed")
        Surface(
            modifier = Modifier
                .size(SensorHotspotVisualSize)
                .graphicsLayer(alpha = if (selected) 1f else .85f),
            color = Color.White.copy(alpha = .75f),
            shape = CircleShape,
            border = BorderStroke(
                width = if (selected) 2.5.dp else 2.dp,
                color = spec.color.copy(alpha = if (supported || selected) 1f else .58f),
            ),
            shadowElevation = if (selected) 9.dp else 5.dp,
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = spec.icon,
                    contentDescription = null,
                    tint = spec.color.copy(alpha = if (supported || selected) 1f else .66f),
                    modifier = Modifier.size(SensorHotspotIconSize),
                )
            }
        }
    }
}

@Composable
private fun SensorSummaryPopover(
    site: HomeSensorSite,
    spec: SensorHotspotSpec,
    onDismiss: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onDismiss),
        color = WearifyGlass,
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, WearifyLine),
        shadowElevation = 10.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = spec.icon,
                contentDescription = null,
                tint = spec.color,
                modifier = Modifier.size(22.dp),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    site.sensorLabel,
                    style = MaterialTheme.typography.titleMedium,
                    color = WearifyInk,
                )
                Text(
                    site.summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = WearifyMuted,
                )
            }
            Text(
                site.status.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = spec.color,
                fontFamily = WearifyDataFont,
            )
        }
    }
}
