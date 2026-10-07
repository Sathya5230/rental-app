package com.rentnest.app.ui.components

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier

@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }
val LocalNavAnimatedScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/** Item art morphs from card to details. Keys must be unique per screen. */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedArt(key: String?): Modifier {
    val shared = LocalSharedTransitionScope.current
    val anim = LocalNavAnimatedScope.current
    if (key == null || shared == null || anim == null) return this
    return with(shared) { this@sharedArt.sharedElement(rememberSharedContentState(key), anim) }
}
