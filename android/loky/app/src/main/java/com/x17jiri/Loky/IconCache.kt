package com.x17jiri.Loky

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.ui.graphics.Color
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.alpha
import androidx.core.graphics.blue
import androidx.core.graphics.green
import androidx.core.graphics.red

// Maps Color -> ImageBitmap
class IconCache(
	private val context: Context
) {
	private val cache = mutableMapOf<Int, Bitmap>()

	operator fun get(tintColor: Int): Bitmap {
		return cache.getOrPut(tintColor) {
			val originalBitmap: Bitmap = BitmapFactory.decodeResource(context.resources, R.drawable.marker)
			val tintedBitmap = Bitmap.createBitmap(originalBitmap.width, originalBitmap.height, Bitmap.Config.ARGB_8888)
			val canvas = Canvas(tintedBitmap)
			val paint = Paint()

			val hsl = FloatArray(3)
			ColorUtils.colorToHSL(tintColor, hsl)
			val L = hsl[2];

			for (x in 0 until originalBitmap.width) {
				for (y in 0 until originalBitmap.height) {
					val pixel = originalBitmap.getPixel(x, y)
					val l = pixel.red
					val a = pixel.alpha
					hsl[2] = (if (l > 245) { l / 255f } else { l / 0.5f * L / 255f }).coerceIn(0f, 1f)
					var newPixel = ColorUtils.HSLToColor(hsl)
					newPixel = android.graphics.Color.argb(a, newPixel.red, newPixel.green, newPixel.blue)
					tintedBitmap.setPixel(x, y, newPixel)
				}
			}

			tintedBitmap
		}
	}
}
