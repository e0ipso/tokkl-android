package com.github.e0ipso.tokkl.ui.theme

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.runtime.Composable

/**
 * The system bar insets a scrolling screen keeps outside its scroll, as its Scaffold's
 * `contentWindowInsets`: the status bar and the sides. Text never scrolls under the status bar's
 * icons. The screen consumes these and puts `navigationBarsPadding` inside the scroll, which then
 * adds only the navigation bar's height at the bottom. Text scrolls under the transparent bar,
 * and the last line can still scroll clear of it.
 */
val ScrollingScreenInsets: WindowInsets
    @Composable get() = WindowInsets.systemBars.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
