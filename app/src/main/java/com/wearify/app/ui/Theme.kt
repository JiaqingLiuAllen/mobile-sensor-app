package com.wearify.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wearify.app.R

val WearifyCanvas = Color(0xFFF7F9FE)
val WearifyInk = Color(0xFF17223B)
val WearifyBlue = Color(0xFF1A5FCC)
val WearifySky = Color(0xFF6EA8FE)
val WearifyCyan = Color(0xFF4CA7D9)
val WearifyViolet = Color(0xFF777EE8)
val WearifyPine = WearifyBlue
val WearifySage = Color(0xFF8DA3C3)
val WearifyCoral = Color(0xFFE1636A)
val WearifyLine = Color(0xFFDCE5F3)
val GarmentMotionGreen = Color(0xFF55B72D)
val GarmentLightOrange = Color(0xFFD96A15)
val GarmentHeartSoundCyan = Color(0xFF168FA5)
val GarmentPulseRed = Color(0xFFD9434B)
val GarmentTemperaturePurple = Color(0xFF7246D8)
val AppleBlue = WearifyBlue
val AppleGreen = WearifyCyan
val AppleYellow = Color(0xFFF4B74A)
val AppleRed = WearifyCoral
val ApplePink = WearifyCoral
val ApplePurple = WearifyViolet
val AppleTeal = WearifyCyan
val AppleOrange = Color(0xFFF29A60)
val GoogleBlue = AppleBlue
val GoogleGreen = AppleGreen
val GoogleYellow = AppleYellow
val GoogleRed = AppleRed
val HealthPink = Color(0xFFFF2D55)
val HealthPurple = Color(0xFFAF52DE)
val HealthTeal = WearifyCyan
val HealthOrange = AppleOrange
val WearifyMuted = Color(0xFF52627C)
val WearifySecondaryText = Color(0xFF71809A)
val WearifySurface = Color(0xFFFFFFFF)
val WearifyGlass = Color(0xF8FFFFFF)
val WearifyBackground = WearifyCanvas
val WearifySoftBlue = Color(0xFFE8F0FE)
val WearifySoftPink = Color(0xFFFCECEF)
val WearifySoftGreen = Color(0xFFE8F0FE)
val WearifySoftOrange = Color(0xFFFFF0E7)
val WearifySoftTeal = Color(0xFFE9F5FC)
val WearifyActionGreen = Color(0xFF2F7D68)
val WearifyActionGreenSoft = Color(0xFFE7F4EE)
val WearifyGrid = WearifyLine
val WearifySeparator = WearifyLine

val WearifyDataFont = FontFamily(Font(R.font.ibm_plex_mono_regular, FontWeight.Normal))
private val WearifyTextFont = FontFamily(
    Font(R.font.instrument_sans, FontWeight.Normal),
    Font(R.font.instrument_sans, FontWeight.Medium),
    Font(R.font.instrument_sans, FontWeight.SemiBold),
    Font(R.font.instrument_sans, FontWeight.Bold),
)

private val WearifyColorScheme = lightColorScheme(
    primary = AppleBlue,
    onPrimary = Color.White,
    primaryContainer = WearifySoftBlue,
    onPrimaryContainer = WearifyInk,
    secondary = AppleGreen,
    onSecondary = Color.White,
    secondaryContainer = WearifySoftGreen,
    onSecondaryContainer = WearifyInk,
    tertiary = AppleOrange,
    onTertiary = Color.White,
    tertiaryContainer = WearifySoftOrange,
    onTertiaryContainer = WearifyInk,
    error = AppleRed,
    errorContainer = WearifySoftPink,
    onErrorContainer = WearifyInk,
    background = WearifyBackground,
    onBackground = WearifyInk,
    surface = WearifySurface,
    onSurface = WearifyInk,
    surfaceVariant = Color(0xFFF0F2F6),
    onSurfaceVariant = WearifyMuted,
    outline = WearifySeparator,
    outlineVariant = Color(0xFFE5E5EA),
)

private val WearifyTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = WearifyTextFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = 30.sp,
        lineHeight = 36.sp,
        letterSpacing = 0.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = WearifyTextFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = 26.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = WearifyTextFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = 21.sp,
        lineHeight = 27.sp,
        letterSpacing = 0.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = WearifyTextFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        letterSpacing = 0.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = WearifyTextFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 21.sp,
        letterSpacing = 0.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = WearifyTextFont,
        fontWeight = FontWeight.Normal,
        fontSize = 17.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = WearifyTextFont,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 21.sp,
        letterSpacing = 0.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = WearifyTextFont,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = WearifyTextFont,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = WearifyTextFont,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.sp,
    ),
)

private val WearifyShapes = Shapes(
    small = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
)

@Composable
fun WearifyTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = WearifyColorScheme,
        typography = WearifyTypography,
        shapes = WearifyShapes,
        content = content,
    )
}
