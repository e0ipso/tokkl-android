package com.github.e0ipso.tokkl.ui.screenshot

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.FileNotFoundException
import kotlin.math.abs
import kotlin.math.max
import org.junit.Assert.fail

/**
 * Compares captures with the reference images in the test APK's assets, under [ASSET_DIR].
 *
 * A capture shrinks to a third in each direction before the comparison, which keeps the
 * references small. Each pixel of it averages a 3x3 block, so a thin line still shows, an 8dp
 * change on tokkl-api36 still moves things by 7 pixels and the text stays readable.
 *
 * When a capture differs, or has no reference yet, this writes it to `actual/` and, when there
 * is a reference of the same size, a diff to `diff/`, both under `screenshots/` in the
 * additional test output directory that the Android Gradle plugin passes to the tests. The
 * plugin copies that directory to the build machine after the run. In the diff, differing
 * pixels are magenta over a pale copy of the reference.
 */
internal object ReferenceImages {

    /** Fails unless [capture], shrunk, matches the reference image `<name>.png` within the tolerance. */
    fun assertMatches(name: String, capture: ImageBitmap) {
        val actual = shrink(capture.asAndroidBitmap())
        val reference = load(name)
        if (reference == null) {
            val written = write(actual, "actual", name)
            fail("no reference image $ASSET_DIR/$name.png in the test assets. This capture is in $written.")
            return
        }
        if (reference.width != actual.width || reference.height != actual.height) {
            val written = write(actual, "actual", name)
            fail(
                "$name is ${actual.width}x${actual.height}, but its reference is ${reference.width}x${reference.height}. " +
                    "Record the references on tokkl-api36. This capture is in $written.",
            )
            return
        }
        val comparison = compare(reference, actual)
        if (comparison.differing > MAX_DIFFERING_PIXELS) {
            val writtenActual = write(actual, "actual", name)
            val writtenDiff = write(comparison.diff, "diff", name)
            fail(
                "$name differs from its reference in ${comparison.differing} pixels, more than $MAX_DIFFERING_PIXELS, " +
                    "within ${comparison.bounds}. The capture is in $writtenActual and the diff in $writtenDiff.",
            )
        }
    }

    /**
     * Averages each [SCALE] by [SCALE] block of [bitmap] into one pixel. Rows and columns past the
     * last whole block drop out.
     */
    private fun shrink(bitmap: Bitmap): Bitmap {
        val source = if (bitmap.config == Bitmap.Config.ARGB_8888) bitmap else bitmap.copy(Bitmap.Config.ARGB_8888, false)
        val stride = source.width
        val pixels = IntArray(stride * source.height).also { source.getPixels(it, 0, stride, 0, 0, stride, source.height) }
        val width = source.width / SCALE
        val height = source.height / SCALE
        val area = SCALE * SCALE
        val shrunk = IntArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val sums = IntArray(4)
                for (dy in 0 until SCALE) {
                    for (dx in 0 until SCALE) {
                        val pixel = pixels[(y * SCALE + dy) * stride + x * SCALE + dx]
                        for (channel in 0 until 4) sums[channel] += pixel ushr (channel * 8) and 0xFF
                    }
                }
                shrunk[y * width + x] = (0 until 4).fold(0) { argb, channel ->
                    argb or ((sums[channel] + area / 2) / area shl (channel * 8))
                }
            }
        }
        return Bitmap.createBitmap(shrunk, width, height, Bitmap.Config.ARGB_8888)
    }

    private fun load(name: String): Bitmap? {
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        return try {
            assets.open("$ASSET_DIR/$name.png").use { BitmapFactory.decodeStream(it) }
        } catch (_: FileNotFoundException) {
            null
        }
    }

    private class Comparison(val differing: Int, val bounds: String, val diff: Bitmap)

    /**
     * Counts the pixels where any channel differs by more than [CHANNEL_TOLERANCE], and draws
     * them over a pale copy of [reference].
     */
    private fun compare(reference: Bitmap, actual: Bitmap): Comparison {
        val width = reference.width
        val height = reference.height
        val expected = IntArray(width * height).also { reference.getPixels(it, 0, width, 0, 0, width, height) }
        val got = IntArray(width * height).also { actual.getPixels(it, 0, width, 0, 0, width, height) }
        val diff = IntArray(width * height)
        var differing = 0
        var left = width
        var top = height
        var right = -1
        var bottom = -1
        for (i in expected.indices) {
            if (channelDelta(expected[i], got[i]) > CHANNEL_TOLERANCE) {
                differing++
                diff[i] = DIFF_COLOUR
                val x = i % width
                val y = i / width
                left = minOf(left, x)
                top = minOf(top, y)
                right = max(right, x)
                bottom = max(bottom, y)
            } else {
                diff[i] = pale(expected[i])
            }
        }
        val bounds = if (differing == 0) "no area" else "x $left..$right, y $top..$bottom of ${width}x$height"
        return Comparison(differing, bounds, Bitmap.createBitmap(diff, width, height, Bitmap.Config.ARGB_8888))
    }

    private fun channelDelta(a: Int, b: Int): Int =
        (0 until 32 step 8).maxOf { shift -> abs((a ushr shift and 0xFF) - (b ushr shift and 0xFF)) }

    /** The pixel's luminance as a light grey, so the magenta stands out. */
    private fun pale(argb: Int): Int {
        val luminance = ((argb ushr 16 and 0xFF) * 299 + (argb ushr 8 and 0xFF) * 587 + (argb and 0xFF) * 114) / 1000
        val grey = 160 + luminance * 95 / 255
        return (0xFF shl 24) or (grey shl 16) or (grey shl 8) or grey
    }

    /** Writes [bitmap] as `screenshots/<kind>/<name>.png` in the test output directory and returns its path. */
    private fun write(bitmap: Bitmap, kind: String, name: String): File {
        val dir = File(outputDir(), "screenshots/$kind").apply { mkdirs() }
        val file = File(dir, "$name.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return file
    }

    /**
     * The directory the Android Gradle plugin copies to the build machine, or the app's cache
     * directory, which `adb shell run-as` reaches, when the tests run without the plugin.
     */
    private fun outputDir(): File =
        InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")?.let(::File)
            ?: InstrumentationRegistry.getInstrumentation().targetContext.cacheDir

    private const val ASSET_DIR = "screenshots"
    private const val SCALE = 3

    // Two runs on tokkl-api36, each on a freshly booted emulator, matched pixel for pixel. The
    // tolerance leaves room for small rendering differences elsewhere, such as another
    // SwiftShader build, and is far below what an 8dp shift changes.

    /** Rendering differences up to this, in any channel from 0 to 255, do not count. */
    private const val CHANNEL_TOLERANCE = 8

    /** How many pixels of a shrunk capture may differ by more than [CHANNEL_TOLERANCE]. */
    private const val MAX_DIFFERING_PIXELS = 20

    private const val DIFF_COLOUR = 0xFFFF00FF.toInt()
}
