/*
 * Copyright 2015 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.sweers.palettehelper.util

import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Build
import android.support.annotation.ColorInt
import android.support.annotation.FloatRange
import android.support.annotation.IntRange
import android.support.annotation.Size
import android.support.v4.graphics.ColorUtils
import android.support.v7.graphics.Palette
import rx.Observable
import rx.observables.MathObservable

/**
 * Converts a given color to a #xxxxxx string.
 */
inline fun Int.hex(): String = "#${Integer.toHexString(this)}"

// Extension functions to Swatch to get hex values
inline fun Palette.Swatch.isLightColor(): Boolean = hsl[2] > 0.5f
inline fun Palette.primarySwatches(): List<Palette.Swatch?> {
    return arrayOf(
            vibrantSwatch,
            mutedSwatch,
            darkVibrantSwatch,
            darkMutedSwatch,
            lightVibrantSwatch,
            lightMutedSwatch
    ).asList()
}
inline fun Palette.uniqueSwatches(): List<Palette.Swatch> {
    return Observable.from(swatches)
            .filter { it != null }
            .distinct { it.toString() }
            .toList().toBlocking().first()
}

enum class Lightness {
    LIGHT, DARK, UNKNOWN
}

val SCRIM_ADJUSTMENT = 0.075f

/**
 * Set the alpha component of `color` to be `alpha`.
 */
fun modifyAlpha(@ColorInt color: Int, @IntRange(from = 0, to = 255) alpha: Int): Int {
    return (color and 16777215) or (alpha shl 24)
}

/**
 * Set the alpha component of `color` to be `alpha`.
 */
fun modifyAlpha(@ColorInt color: Int,
                @FloatRange(from = 0.0, to = 1.0) alpha: Float): Int {
    return modifyAlpha(color, (255f * alpha).toInt())
}

/**
 * Blend `color1` and `color2` using the given ratio.

 * @param ratio of which to blend. 0.0 will return `color1`, 0.5 will give an even blend,
 * *              1.0 will return `color2`.
 */
@ColorInt fun blendColors(@ColorInt color1: Int,
                          @ColorInt color2: Int,
                          @FloatRange(from = 0.0, to = 1.0) ratio: Float): Int {
    val inverseRatio = 1f - ratio
    val a = (Color.alpha(color1) * inverseRatio) + (Color.alpha(color2) * ratio)
    val r = (Color.red(color1) * inverseRatio) + (Color.red(color2) * ratio)
    val g = (Color.green(color1) * inverseRatio) + (Color.green(color2) * ratio)
    val b = (Color.blue(color1) * inverseRatio) + (Color.blue(color2) * ratio)
    return Color.argb(a.toInt(), r.toInt(), g.toInt(), b.toInt())
}

/**
 * Checks if the most populous color in the given palette is dark
 *
 * Annoyingly we have to return this Lightness 'enum' rather than a boolean as palette isn't
 * guaranteed to find the most populous color.
 */
fun isDark(palette: Palette): Lightness {
    val mostPopulous = getMostPopulousSwatch(palette) ?: return Lightness.UNKNOWN
    return if (isDark(mostPopulous.hsl)) Lightness.DARK else Lightness.LIGHT
}

fun getMostPopulousSwatch(palette: Palette?): Palette.Swatch? {
    palette?.primarySwatches()?.let { primarySwatches ->
        return MathObservable.from(Observable.from(primarySwatches)
                .filter { it != null })
                .max { thisSwatch, thatSwatch -> thisSwatch!!.population.compareTo(thatSwatch!!.population) }
                .toBlocking().first()
    }
    return null
}

/**
 * Determines if a given bitmap is dark. This extracts a palette inline so should not be called
 * with a large image!!
 *
 * Note: If palette fails then check the color of the central pixel
 */
@JvmOverloads
fun isDark(bitmap: Bitmap,
            backupPixelX: Int = bitmap.width / 2,
            backupPixelY: Int = bitmap.height / 2): Boolean {
    // first try palette with a small color quant size
    val palette = Palette.from(bitmap).maximumColorCount(3).generate()
    if (palette != null && palette.swatches.size > 0) {
        return isDark(palette) == Lightness.DARK
    } else {
        // if palette failed, then check the color of the specified pixel
        return isDark(bitmap.getPixel(backupPixelX, backupPixelY))
    }
}

/**
 * Check that the lightness value (0–1)
 */
fun isDark(@Size(3) hsl: FloatArray): Boolean {
    return hsl[2] < 0.5f
}

/**
 * Convert to HSL & check that the lightness value
 */
fun isDark(@ColorInt color: Int): Boolean {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(color, hsl)
    return isDark(hsl)
}

/**
 * Calculate a variant of the color to make it more suitable for overlaying information. Light
 * colors will be lightened and dark colors will be darkened

 * @param color the color to adjust
 * @param isDark whether `color` is light or dark
 * @param lightnessMultiplierInput the amount to modify the color e.g. 0.1f will alter it by 10%
 * @return the adjusted color
 */
@JvmOverloads
@ColorInt
fun scrimify(@ColorInt color: Int,
             isDark: Boolean = isDark(color),
             @FloatRange(from = 0.0, to = 1.0) lightnessMultiplierInput: Float): Int {
    var lightnessMultiplier = lightnessMultiplierInput
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(color, hsl)

    if (!isDark) {
        lightnessMultiplier += 1f
    } else {
        lightnessMultiplier = 1f - lightnessMultiplier
    }

    hsl[2] = constrain(0f, 1f, hsl[2] * lightnessMultiplier)
    return ColorUtils.HSLToColor(hsl)
}

fun constrain(min: Float, max: Float, v: Float): Float {
    return Math.max(min, Math.min(max, v))
}

/**
 * Creates a selector drawable that is API-aware. This will create a ripple for Lollipop+ and
 * supports masks. If this is pre-lollipop and no mask is provided, it will fall back to a simple
 * {@link StateListDrawable} with the color as its pressed and focused states.
 *
 * @param color Selector color
 * @param mask Mask drawable for ripples to be bound to
 * @return The drawable if successful, or null if not valid for this case (masked on pre-lollipop)
 */
@JvmOverloads
fun createColorSelector(@ColorInt color: Int, mask: Drawable? = null): Drawable? {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        return RippleDrawable(ColorStateList.valueOf(color), null, mask);
    } else if (mask == null) {
        val colorDrawable: ColorDrawable = ColorDrawable(color);
        val statefulDrawable: StateListDrawable = StateListDrawable();
        statefulDrawable.setEnterFadeDuration(200);
        statefulDrawable.setExitFadeDuration(200);
        statefulDrawable.addState(intArrayOf(android.R.attr.state_pressed), colorDrawable);
        statefulDrawable.addState(intArrayOf(android.R.attr.state_focused), colorDrawable);
        statefulDrawable.addState(intArrayOf(), null);
        return statefulDrawable;
    } else {
        // We don't do it on pre-lollipop because normally selectors can't abide by a mask
        return null;
    }
}
