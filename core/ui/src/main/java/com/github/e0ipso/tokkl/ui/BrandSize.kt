package com.github.e0ipso.tokkl.ui

import androidx.annotation.DrawableRes
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.e0ipso.tokkl.core.ui.R

/**
 * The sizes of [Brand], as the wordmark's width. Only Tokkl's lg from Brand.tsx ships: the wordmark
 * shows on sign-in and the launch splash only, never in a page header or a top bar, so sm and md
 * had no screen. Each size has its own images, so none is drawn scaled down from a larger size.
 * The image names are aliases: brand_images.xml in res/values and in each density's values folder
 * points them at one file per density. res/raw/brand_images_source.txt records where the files
 * come from.
 */
enum class BrandSize(internal val wordmarkWidth: Dp, @DrawableRes internal val wordmark: Int) {
    /** Tokkl's lg, on its own above a screen's content, as on sign-in. */
    LARGE(260.dp, R.drawable.tokkl_wordmark_lg),
}
