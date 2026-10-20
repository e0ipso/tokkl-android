package com.github.e0ipso.tokkl

import android.content.ComponentName
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.util.TypedValue
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import kotlin.math.abs
import kotlin.math.hypot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

/**
 * The launch splash (docs/PRD.md, Visual direction, Wordmark): Android 12 and later draw it from
 * the activity's theme before the app runs, so a test cannot capture it. These check what Android
 * draws it from instead. MainActivity's theme, in light and in dark, sets the splash icon to the
 * wordmark on the window background, with no branding image and no icon background; the icon
 * fits the wordmark in the 192dp circle Android shows of it, and in dark traces it with the halo.
 * The first screen still shows as soon as the app has read its session, with nothing held.
 */
class LaunchSplashTest {
    private val composeRule = createEmptyComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(SessionRule()).around(composeRule)

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun mainActivityStartsInTheThemeWithTheSplash() {
        val info = context.packageManager.getActivityInfo(ComponentName(context, MainActivity::class.java), 0)
        assertEquals("MainActivity's theme", R.style.Theme_Tokkl, info.themeResource)
    }

    @Test
    fun inLightTheSplashIsTheWordmarkOnTheWindowBackground() = assertSplashTheme(dark = false)

    @Test
    fun inDarkTheSplashIsTheWordmarkOnTheWindowBackground() = assertSplashTheme(dark = true)

    /** The wordmark alone, about 180dp of the 288dp square, its shape kept, inside the 192dp circle. */
    @Test
    fun theSplashIconFitsTheWordmarkInTheCircle() {
        for (dark in listOf(false, true)) {
            val icon = render(dark)
            val shown = Bounds.of(icon) { alpha(it) > 0 }
            val mode = if (dark) "dark" else "light"
            val wordmark = if (dark) Bounds.of(render(dark = false)) { alpha(it) > 0 } else shown
            assertEquals("$mode: the wordmark's share of the square", WORDMARK_SHARE, wordmark.width / SIZE.toFloat(), SHARE_SLACK)
            assertEquals("$mode: the wordmark keeps its shape", WORDMARK_ASPECT_RATIO, wordmark.width / wordmark.height, 0.03f)
            val radius = CIRCLE_SHARE * SIZE / 2
            val outside = (shown.top..shown.bottom).flatMap { y -> (shown.left..shown.right).map { x -> x to y } }
                .filter { (x, y) -> alpha(icon.getPixel(x, y)) > 0 && hypot(x + 0.5f - SIZE / 2f, y + 0.5f - SIZE / 2f) > radius }
            assertTrue("$mode: the icon draws outside the circle at ${outside.take(5)}", outside.isEmpty())
        }
    }

    /**
     * In dark the icon is the light one with a ring in the wordmark-halo colour around it, as
     * ui/Brand.kt draws the halo. In light nothing traces the wordmark.
     */
    @Test
    fun inDarkTheHaloTracesTheWordmark() {
        val light = render(dark = false)
        val dark = render(dark = true)
        val halo = inMode(dark = true).getColor(R.color.wordmark_halo)
        var ring = 0
        var stray = 0
        for (y in 0 until SIZE) {
            for (x in 0 until SIZE) {
                if (alpha(light.getPixel(x, y)) > 0) continue
                val pixel = dark.getPixel(x, y)
                if (alpha(pixel) == 0) continue
                if (near(pixel, halo)) ring++ else if (alpha(pixel) == 255) stray++
            }
        }
        assertTrue("only $ring pixels of halo around the wordmark", ring > MIN_HALO_PIXELS)
        assertEquals("opaque pixels around the wordmark that are not the halo", 0, stray)
    }

    @Test
    fun theFirstScreenShowsWithoutWaitingOnTheSplash() {
        ActivityScenario.launch(MainActivity::class.java).use {
            composeRule.awaitTimerPage()
        }
    }

    private fun assertSplashTheme(dark: Boolean) {
        val theme = inMode(dark).apply { setTheme(R.style.Theme_Tokkl) }.theme
        val value = TypedValue()

        assertTrue("the theme sets a splash icon", theme.resolveAttribute(android.R.attr.windowSplashScreenAnimatedIcon, value, true))
        assertEquals("the splash icon", R.drawable.splash_wordmark, value.resourceId)

        assertTrue("the theme sets a splash background", theme.resolveAttribute(android.R.attr.windowSplashScreenBackground, value, true))
        assertEquals("the splash background", hex(inMode(dark).getColor(R.color.window_background)), hex(value.data))

        val branded = theme.resolveAttribute(android.R.attr.windowSplashScreenBrandingImage, value, true) &&
            value.type != TypedValue.TYPE_NULL && value.resourceId != 0
        assertTrue("the splash has a branding image", !branded)
        val iconBackground = theme.resolveAttribute(android.R.attr.windowSplashScreenIconBackgroundColor, value, true) &&
            value.type in TypedValue.TYPE_FIRST_COLOR_INT..TypedValue.TYPE_LAST_COLOR_INT && Color.alpha(value.data) != 0
        assertTrue("the splash icon has a background", !iconBackground)
    }

    /** The splash icon drawn on a [SIZE] square, as Android draws it on its 288dp square. */
    private fun render(dark: Boolean): Bitmap {
        val drawable = inMode(dark).getDrawable(R.drawable.splash_wordmark)!!
        val bitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        drawable.setBounds(0, 0, SIZE, SIZE)
        drawable.draw(Canvas(bitmap))
        return bitmap
    }

    private fun inMode(dark: Boolean): Context {
        val configuration = Configuration(context.resources.configuration)
        val night = if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        configuration.uiMode = (configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or night
        return context.createConfigurationContext(configuration)
    }

    private fun alpha(pixel: Int): Int = Color.alpha(pixel)

    private fun near(a: Int, b: Int): Boolean =
        abs(Color.red(a) - Color.red(b)) <= TOLERANCE && abs(Color.green(a) - Color.green(b)) <= TOLERANCE &&
            abs(Color.blue(a) - Color.blue(b)) <= TOLERANCE

    private fun hex(argb: Int): String = "#%08X".format(argb)

    /** The smallest box around the pixels [shown] picks. */
    private class Bounds(val left: Int, val top: Int, val right: Int, val bottom: Int) {
        val width: Float get() = (right - left + 1).toFloat()
        val height: Float get() = (bottom - top + 1).toFloat()

        companion object {
            fun of(bitmap: Bitmap, shown: (Int) -> Boolean): Bounds {
                var left = bitmap.width
                var top = bitmap.height
                var right = -1
                var bottom = -1
                for (y in 0 until bitmap.height) {
                    for (x in 0 until bitmap.width) {
                        if (!shown(bitmap.getPixel(x, y))) continue
                        left = minOf(left, x)
                        right = maxOf(right, x)
                        top = minOf(top, y)
                        bottom = maxOf(bottom, y)
                    }
                }
                check(right >= 0) { "the icon draws nothing" }
                return Bounds(left, top, right, bottom)
            }
        }
    }

    private companion object {
        /** 1px for each dp of the 288dp square Android draws the icon on. */
        const val SIZE = 288

        /** The wordmark's width, 178dp, over the square's. */
        const val WORDMARK_SHARE = 178f / 288f

        /** The circle Android shows of the square: 192dp of 288dp. */
        const val CIRCLE_SHARE = 192f / 288f

        /** Two pixels of the square either way, for the artwork's transparent border. */
        const val SHARE_SLACK = 2f / 288f

        /** Width over height of the wordmark images. */
        const val WORDMARK_ASPECT_RATIO = 2059f / 769f

        const val TOLERANCE = 8
        const val MIN_HALO_PIXELS = 200
    }
}
