package com.github.e0ipso.tokkl

import android.content.pm.LauncherApps
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Process
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The icon a launcher gets for the installed app, through the same API launchers use. */
class LauncherIconTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun launcherGetsAnAdaptiveIconForMainActivity() {
        val entries = context.getSystemService(LauncherApps::class.java)
            .getActivityList(context.packageName, Process.myUserHandle())
        assertEquals(listOf(MainActivity::class.java.name), entries.map { it.componentName.className })

        val icon = entries.single().getIcon(0)

        assertTrue("the icon is a ${icon.javaClass.name}", icon is AdaptiveIconDrawable)
        icon as AdaptiveIconDrawable
        // The app's own layers, not a platform wrapper around a legacy icon.
        assertEquals(context.getColor(R.color.ic_launcher_background), (icon.background as? ColorDrawable)?.color)
        val artwork = (context.getDrawable(R.mipmap.ic_launcher_foreground) as BitmapDrawable).bitmap
        val foreground = (icon.foreground as? BitmapDrawable)?.bitmap
        assertTrue("the foreground is not Tokkl's artwork", foreground?.sameAs(artwork) == true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            assertNotNull("no monochrome layer for themed icons", icon.monochrome)
        }
    }
}
