package com.github.e0ipso.tokkl.navhost

import android.content.Intent
import com.github.e0ipso.tokkl.navigation.FeatureRequests
import com.github.e0ipso.tokkl.navigation.LinkIntent
import com.github.e0ipso.tokkl.navigation.NavigationRequest

/**
 * The pages that one of MainActivity's intents asks to open, as each of the [features] reads its
 * own taps from it ([FeatureRequests.readLinks]), such as a widget's page or a notification's tap.
 * It reads only what [LinkIntent] gives it, so it has no Android in it.
 */
internal class DeepLinks(private val features: List<FeatureRequests>) {

    /** Every extra [parse] reads. MainActivity removes them once read, so a rotation opens nothing again. */
    val extras: List<String> get() = features.flatMap { it.linkExtras }

    fun parse(intent: LinkIntent): List<NavigationRequest> = features.flatMap { it.readLinks(intent) }
}

/**
 * The pages this intent asks to open ([DeepLinks]). Removes the extras that name them, so they
 * open nothing again. The action and the data stay, so the activity keeps the identity it was
 * started with.
 */
internal fun Intent.takeDeepLinks(links: DeepLinks): List<NavigationRequest> {
    val intent = this
    val requests = links.parse(
        object : LinkIntent {
            override val action: String? get() = intent.action

            override fun flag(key: String) = intent.getBooleanExtra(key, false)

            override fun text(key: String) = intent.getStringExtra(key)

            override fun number(key: String) = if (intent.hasExtra(key)) intent.getLongExtra(key, 0) else null
        },
    )
    links.extras.forEach(::removeExtra)
    return requests
}
