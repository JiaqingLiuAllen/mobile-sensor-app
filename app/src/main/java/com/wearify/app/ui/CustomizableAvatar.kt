package com.wearify.app.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import com.wearify.app.R
import com.wearify.app.data.AvatarAppearance
import com.wearify.app.data.AvatarGenderPresentation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

internal val AvatarSkinToneColors = listOf(
    Color(0xFFEAB18F),
    Color(0xFFE4A27C),
    Color(0xFFC9855C),
    Color(0xFF925A40),
    Color(0xFF59352A),
)

/**
 * Three fixed character assets preserve a coherent illustrated silhouette:
 * short-haired man, long-haired woman, and medium-haired non-binary guide.
 * Runtime recoloring changes skin and hair pigment only; facial features and
 * garment pixels remain owned by the original artwork.
 */
@Composable
fun CustomizableGarmentAvatar(
    appearance: AvatarAppearance,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    val context = LocalContext.current
    val baseResource = when (appearance.genderPresentation) {
        AvatarGenderPresentation.Woman -> R.drawable.sensor_avatar_feminine_v1
        AvatarGenderPresentation.NonBinary -> R.drawable.sensor_avatar_nonbinary_v1
        AvatarGenderPresentation.Man -> R.drawable.home_guide_man_detailed_v3
    }
    val rendered by produceState<ImageBitmap?>(
        initialValue = null,
        key1 = baseResource,
        key2 = appearance.skinToneIndex,
        key3 = appearance.hairColor,
    ) {
        value = withContext(Dispatchers.Default) {
            recolorAvatar(
                source = BitmapFactory.decodeResource(context.resources, baseResource),
                skin = AvatarSkinToneColors[appearance.skinToneIndex.coerceIn(0, 4)],
                hairArgb = appearance.hairColor.argb.toInt(),
            ).asImageBitmap()
        }
    }

    Box(modifier = modifier) {
        rendered?.let { bitmap ->
            Image(
                bitmap = bitmap,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        }
    }
}

private fun recolorAvatar(
    source: Bitmap,
    skin: Color,
    hairArgb: Int,
): Bitmap {
    val result = source.copy(Bitmap.Config.ARGB_8888, true)
    val width = result.width
    val height = result.height
    val pixels = IntArray(width * height)
    result.getPixels(pixels, 0, width, 0, 0, width, height)
    val backgroundMask = connectedLightBackgroundMask(pixels, width, height)
    val hairMask = connectedHairMask(pixels, width, height)
    val skinArgb = skin.toArgb()
    val skinR = skinArgb shr 16 and 0xff
    val skinG = skinArgb shr 8 and 0xff
    val skinB = skinArgb and 0xff
    val hairR = hairArgb shr 16 and 0xff
    val hairG = hairArgb shr 8 and 0xff
    val hairB = hairArgb and 0xff

    for (index in pixels.indices) {
        if (backgroundMask[index]) {
            pixels[index] = 0
            continue
        }
        val pixel = pixels[index]
        val alpha = pixel ushr 24
        if (alpha < 16) continue
        val red = pixel shr 16 and 0xff
        val green = pixel shr 8 and 0xff
        val blue = pixel and 0xff
        val y = index / width
        val x = index - y * width
        val normalizedX = x.toFloat() / width
        val normalizedY = y.toFloat() / height
        val luminance = (red * 299 + green * 587 + blue * 114) / 1000
        val isSkinPigment = red > 92 &&
            red > green * 1.04 &&
            green > blue * 1.06 &&
            red - blue > 24
        val inFace = normalizedY in 0.06f..0.37f &&
            normalizedX in 0.29f..0.71f
        val inNeck = normalizedY in 0.32f..0.47f &&
            normalizedX in 0.38f..0.62f
        val inArm = normalizedY > 0.63f &&
            (normalizedX < 0.34f || normalizedX > 0.66f)
        val isSkin = isSkinPigment && (inFace || inNeck || inArm)
        val recolored = when {
            hairMask[index] -> shadedColor(alpha, hairR, hairG, hairB, luminance, 62)
            isSkin -> skinColorWithOriginalDetail(
                alpha = alpha,
                targetR = skinR,
                targetG = skinG,
                targetB = skinB,
                sourceR = red,
                sourceG = green,
                sourceB = blue,
            )
            else -> pixel
        }
        pixels[index] = recolored
    }
    result.setPixels(pixels, 0, width, 0, 0, width, height)
    return result
}

/**
 * Remove only the pale neutral background connected to the image border.
 * Internal light regions such as eye whites are enclosed by the illustration,
 * so they remain untouched. This preserves the supplied artwork exactly while
 * allowing the same sticker to sit naturally on colored app surfaces.
 */
private fun connectedLightBackgroundMask(
    pixels: IntArray,
    width: Int,
    height: Int,
): BooleanArray {
    val candidates = BooleanArray(pixels.size)
    val selected = BooleanArray(pixels.size)
    val queue = IntArray(pixels.size)

    for (index in pixels.indices) {
        val pixel = pixels[index]
        if (pixel ushr 24 < 16) continue
        val red = pixel shr 16 and 0xff
        val green = pixel shr 8 and 0xff
        val blue = pixel and 0xff
        val lightest = max(red, max(green, blue))
        val darkest = min(red, min(green, blue))
        candidates[index] = darkest > 188 && lightest - darkest < 46
    }

    var head = 0
    var tail = 0
    fun seed(index: Int) {
        if (candidates[index] && !selected[index]) {
            selected[index] = true
            queue[tail++] = index
        }
    }
    for (x in 0 until width) {
        seed(x)
        seed((height - 1) * width + x)
    }
    for (y in 0 until height) {
        seed(y * width)
        seed(y * width + width - 1)
    }

    while (head < tail) {
        val index = queue[head++]
        val y = index / width
        val x = index - y * width
        for (dy in -1..1) {
            val nextY = y + dy
            if (nextY !in 0 until height) continue
            for (dx in -1..1) {
                if (dx == 0 && dy == 0) continue
                val nextX = x + dx
                if (nextX !in 0 until width) continue
                seed(nextY * width + nextX)
            }
        }
    }
    return selected
}

/**
 * Select only dark, hair-like pixels connected to the hair at the top of the
 * supplied artwork. Facial features can have similar colors, but they are not
 * connected to those seeds and therefore never inherit the chosen hair color.
 */
private fun connectedHairMask(
    pixels: IntArray,
    width: Int,
    height: Int,
): BooleanArray {
    val candidates = BooleanArray(pixels.size)
    val selected = BooleanArray(pixels.size)
    val queue = IntArray(pixels.size)
    val maximumHairY = (height * 0.57f).toInt()

    for (index in pixels.indices) {
        val pixel = pixels[index]
        if (pixel ushr 24 < 16) continue
        val y = index / width
        val x = index - y * width
        if (y >= maximumHairY || x < width * 0.16f || x > width * 0.84f) continue
        val red = pixel shr 16 and 0xff
        val green = pixel shr 8 and 0xff
        val blue = pixel and 0xff
        val luminance = (red * 299 + green * 587 + blue * 114) / 1000
        val channelSpan = max(red, max(green, blue)) - min(red, min(green, blue))
        candidates[index] = luminance < 165 &&
            channelSpan < 90 &&
            blue - red < 22 &&
            blue - green < 22
    }

    var head = 0
    var tail = 0
    val seedBottom = (height * 0.17f).toInt()
    val seedLeft = (width * 0.20f).toInt()
    val seedRight = (width * 0.80f).toInt()
    for (y in 0 until seedBottom) {
        for (x in seedLeft until seedRight) {
            val index = y * width + x
            if (candidates[index] && !selected[index]) {
                selected[index] = true
                queue[tail++] = index
            }
        }
    }

    while (head < tail) {
        val index = queue[head++]
        val y = index / width
        val x = index - y * width
        for (dy in -1..1) {
            val nextY = y + dy
            if (nextY !in 0 until height) continue
            for (dx in -1..1) {
                if (dx == 0 && dy == 0) continue
                val nextX = x + dx
                if (nextX !in 0 until width) continue
                val next = nextY * width + nextX
                if (candidates[next] && !selected[next]) {
                    selected[next] = true
                    queue[tail++] = next
                }
            }
        }
    }
    return selected
}

/**
 * Retain the artwork's natural local color differences while shifting its
 * overall skin tone. Unlike the old facial ellipses, this has no hard mouth or
 * eyelid boundary, so lips and eyelids keep their painted detail without
 * leaving a differently colored stripe when a darker or lighter tone is used.
 */
private fun skinColorWithOriginalDetail(
    alpha: Int,
    targetR: Int,
    targetG: Int,
    targetB: Int,
    sourceR: Int,
    sourceG: Int,
    sourceB: Int,
): Int {
    val sourceReferenceR = 246f
    val sourceReferenceG = 174f
    val sourceReferenceB = 128f
    fun shifted(target: Int, source: Int, reference: Float): Int =
        (target * source / reference).toInt().coerceIn(0, 255)
    return alpha shl 24 or
        (shifted(targetR, sourceR, sourceReferenceR) shl 16) or
        (shifted(targetG, sourceG, sourceReferenceG) shl 8) or
        shifted(targetB, sourceB, sourceReferenceB)
}

private fun shadedColor(
    alpha: Int,
    targetR: Int,
    targetG: Int,
    targetB: Int,
    sourceLuminance: Int,
    referenceLuminance: Int,
): Int {
    val shade = (sourceLuminance.toFloat() / referenceLuminance).coerceIn(.48f, 1.38f)
    fun channel(value: Int): Int = min(255, max(0, (value * shade).toInt()))
    return alpha shl 24 or
        (channel(targetR) shl 16) or
        (channel(targetG) shl 8) or
        channel(targetB)
}
