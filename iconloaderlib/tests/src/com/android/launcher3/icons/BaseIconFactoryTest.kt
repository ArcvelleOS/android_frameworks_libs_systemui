/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.launcher3.icons

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import androidx.test.core.app.ApplicationProvider
import com.android.launcher3.icons.BaseIconFactory.IconOptions
import com.android.launcher3.icons.mono.ColorList
import com.android.launcher3.icons.mono.MonoIconThemeController
import com.android.launcher3.icons.mono.MonoThemedBitmap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BaseIconFactoryTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun fullBleed_has_no_alpha() {
        val info =
            factory(drawFullBleedIcons = true)
                .createBadgedIconBitmap(AdaptiveIconDrawable(ColorDrawable(Color.RED), null))

        assertFalse(info.icon.hasAlpha())
        assertEquals(BitmapInfo.FLAG_FULL_BLEED, info.flags and BitmapInfo.FLAG_FULL_BLEED)
    }

    @Test
    fun non_fullBleed_has_alpha() {
        val info =
            factory(drawFullBleedIcons = false)
                .createBadgedIconBitmap(AdaptiveIconDrawable(ColorDrawable(Color.RED), null))
        assertTrue(info.icon.hasAlpha())
        assertEquals(0, info.flags and BitmapInfo.FLAG_FULL_BLEED)
    }

    @Test
    fun icon_options_overrides_fullBleed() {
        val info =
            factory(drawFullBleedIcons = false)
                .createBadgedIconBitmap(
                    AdaptiveIconDrawable(ColorDrawable(Color.RED), null),
                    IconOptions().setDrawFullBleed(true),
                )
        assertFalse(info.icon.hasAlpha())
        assertEquals(BitmapInfo.FLAG_FULL_BLEED, info.flags and BitmapInfo.FLAG_FULL_BLEED)

        val info2 =
            factory(drawFullBleedIcons = true)
                .createBadgedIconBitmap(
                    AdaptiveIconDrawable(ColorDrawable(Color.RED), null),
                    IconOptions().setDrawFullBleed(false),
                )
        assertTrue(info2.icon.hasAlpha())
        assertEquals(0, info2.flags and BitmapInfo.FLAG_FULL_BLEED)
    }

    @Test
    fun pack_shape_survives_archived_app_full_bleed_options() {
        val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).drawRect(20f, 8f, 44f, 56f, Paint().apply { color = Color.RED })
        val drawable = BitmapDrawable(context.resources, bitmap).apply {
            changingConfigurations = BaseIconFactory.CONFIG_HINT_NO_WRAP
            setBounds(3, 5, 27, 37)
        }
        val oldBounds = Rect(drawable.bounds)

        val info = factory(drawFullBleedIcons = true).createBadgedIconBitmap(
            drawable,
            IconOptions().assumeFullBleedIcon(true).setDrawFullBleed(true)
                .setIconScale(1f).setAddShadows(false),
        )

        assertEquals(Color.TRANSPARENT, info.icon.getPixel(4, 32))
        assertEquals(Color.RED, info.icon.getPixel(32, 32))
        assertEquals(0, info.flags and BitmapInfo.FLAG_FULL_BLEED)
        assertEquals(oldBounds, drawable.bounds)
    }

    @Test
    fun legacy_app_without_pack_hint_still_uses_full_bleed() {
        val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        val info = factory(drawFullBleedIcons = true).createBadgedIconBitmap(
            BitmapDrawable(context.resources, bitmap)
        )
        assertFalse(info.icon.hasAlpha())
        assertEquals(BitmapInfo.FLAG_FULL_BLEED, info.flags and BitmapInfo.FLAG_FULL_BLEED)
    }

    @Test
    fun adaptive_pack_artwork_is_not_converted_to_a_full_bleed_launcher_shape() {
        val drawable = AdaptiveIconDrawable(ColorDrawable(Color.TRANSPARENT),
            ColorDrawable(Color.RED)).apply {
            changingConfigurations = BaseIconFactory.CONFIG_HINT_NO_WRAP
        }
        val info = factory(drawFullBleedIcons = true).createBadgedIconBitmap(
            drawable, IconOptions().setAddShadows(false)
        )
        assertTrue(info.icon.hasAlpha())
        assertEquals(Color.TRANSPARENT, info.icon.getPixel(0, 0))
        assertEquals(0, info.flags and BitmapInfo.FLAG_FULL_BLEED)
    }

    @Test
    fun smaller_themed_glyph_keeps_the_background_at_full_size() {
        val mono = Bitmap.createBitmap(64, 64, Bitmap.Config.ALPHA_8).apply {
            eraseColor(Color.WHITE)
        }
        val colors: (Context) -> ColorList = {
            ColorList(Color.BLACK, Color.WHITE, Color.BLACK, Color.BLACK, Color.WHITE)
        }
        fun render(scale: Float): Bitmap {
            val info = BitmapInfo(
                icon = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888),
                color = Color.BLACK,
                defaultIconShape = factory().defaultIconShape,
                themedBitmap = MonoThemedBitmap(mono, colors, foregroundScale = scale),
            )
            val output = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
            info.newIcon(context, BitmapInfo.FLAG_THEMED or BitmapInfo.FLAG_NO_BADGE).apply {
                setBounds(0, 0, 64, 64)
                draw(Canvas(output))
            }
            return output
        }
        val styled = render(0.5f)
        assertEquals(Color.WHITE, styled.getPixel(32, 32))
        assertEquals(Color.BLACK, styled.getPixel(10, 32))
        assertEquals(Color.WHITE, render(1f).getPixel(10, 32))
    }

    @Test
    fun explicit_pack_artwork_is_not_rethemed_by_the_optional_mono_style() {
        val drawable = AdaptiveIconDrawable(ColorDrawable(Color.TRANSPARENT),
            ColorDrawable(Color.RED), ColorDrawable(Color.WHITE)).apply {
            changingConfigurations = BaseIconFactory.CONFIG_HINT_NO_WRAP
        }
        val info = factory(
            drawFullBleedIcons = true,
            themeController = MonoIconThemeController(shouldForceThemeIcon = true,
                foregroundScale = 0.5f),
        ).createBadgedIconBitmap(drawable, IconOptions().setAddShadows(false))
        assertTrue(info.themedBitmap == null || info.themedBitmap === ThemedBitmap.NOT_SUPPORTED)
        assertEquals(Color.RED, info.icon.getPixel(32, 32))
        assertEquals(Color.TRANSPARENT, info.icon.getPixel(0, 0))
    }

    private fun factory(
        fullResIconDpi: Int = context.resources.displayMetrics.densityDpi,
        iconBitmapSize: Int = 64,
        drawFullBleedIcons: Boolean = false,
        themeController: IconThemeController? = null,
    ) =
        BaseIconFactory(
            context = context,
            fullResIconDpi = fullResIconDpi,
            iconBitmapSize = iconBitmapSize,
            drawFullBleedIcons = drawFullBleedIcons,
            themeController = themeController,
        )
}
