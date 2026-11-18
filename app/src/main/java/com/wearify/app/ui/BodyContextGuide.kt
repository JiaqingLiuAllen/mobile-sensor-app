package com.wearify.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.wearify.app.data.AvatarAppearance
import com.wearify.app.data.HomeBodyContext

/**
 * A friendly, affect-neutral body-context guide. The generated character is
 * visual only; Compose-owned evidence dots carry all state and accessibility.
 */
@Composable
fun BodyContextGuide(
    context: HomeBodyContext,
    appearance: AvatarAppearance = AvatarAppearance(),
    modifier: Modifier = Modifier,
    size: Dp = 170.dp,
    onDark: Boolean = false,
) {
    val quiet = if (onDark) Color.White.copy(alpha = 0.28f) else WearifyLine
    val accessibilitySummary = listOf(
        "IMU ${context.imu}",
        "contact microphone ${context.contactMic}",
        "PPG ${context.ppg}",
    ).joinToString(", ")
    Box(
        modifier = modifier
            .size(size)
            .semantics {
                contentDescription =
                    "Body-context evidence guide: $accessibilitySummary"
            },
    ) {
        CustomizableGarmentAvatar(
            appearance = appearance,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
        )
        GuideEvidenceDot(
            accepted = context.imu == "accepted",
            activeColor = WearifyViolet,
            quietColor = quiet,
            size = size,
            xFraction = .47f,
            yFraction = .39f,
        )
        GuideEvidenceDot(
            accepted = context.contactMic == "accepted",
            activeColor = WearifyCoral,
            quietColor = quiet,
            size = size,
            xFraction = .48f,
            yFraction = .58f,
        )
        GuideEvidenceDot(
            accepted = context.ppg == "accepted",
            activeColor = WearifySky,
            quietColor = quiet,
            size = size,
            xFraction = .77f,
            yFraction = .63f,
        )
    }
}

@Composable
private fun GuideEvidenceDot(
    accepted: Boolean,
    activeColor: Color,
    quietColor: Color,
    size: Dp,
    xFraction: Float,
    yFraction: Float,
) {
    val dotSize = (size * .085f).coerceIn(5.dp, 15.dp)
    Box(
        Modifier
            .offset(
                x = size * xFraction - dotSize / 2,
                y = size * yFraction - dotSize / 2,
            )
            .size(dotSize)
            .clip(CircleShape)
            .background(if (accepted) activeColor else quietColor)
            .border(
                width = (size * .008f).coerceIn(.6.dp, 1.5.dp),
                color = Color.White.copy(alpha = if (accepted) .92f else .5f),
                shape = CircleShape,
            ),
    )
}
