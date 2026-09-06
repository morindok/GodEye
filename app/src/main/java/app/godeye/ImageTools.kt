package app.godeye

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.roundToInt

object ImageTools {
    /** Decode at bounded resolution, correct orientation, re-encode without EXIF/GPS. */
    fun jpeg(file: File): ByteArray {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "تصویر معتبر نیست." }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 2560) sample *= 2
        val decoded = requireNotNull(BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample }))
        try {
            val exif = ExifInterface(file)
            val matrix = Matrix().apply {
                if (exif.isFlipped) postScale(-1f, 1f)
                postRotate(exif.rotationDegrees.toFloat())
            }
            val rotated = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
            try {
                val scale = minOf(1f, 1280f / maxOf(rotated.width, rotated.height))
                val resized = Bitmap.createScaledBitmap(rotated, (rotated.width * scale).roundToInt().coerceAtLeast(1),
                    (rotated.height * scale).roundToInt().coerceAtLeast(1), true)
                try {
                    return ByteArrayOutputStream().use { output ->
                        check(resized.compress(Bitmap.CompressFormat.JPEG, 82, output))
                        output.toByteArray()
                    }
                } finally { if (resized !== rotated && resized !== decoded) resized.recycle() }
            } finally { if (rotated !== decoded) rotated.recycle() }
        } finally { decoded.recycle() }
    }
}
