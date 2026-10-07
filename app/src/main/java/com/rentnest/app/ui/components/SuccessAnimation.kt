package com.rentnest.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private data class Particle(val angle: Float, val speed: Float, val color: Color, val size: Float, val spin: Float)

/** Bouncy check mark with a confetti burst. */
@Composable
fun SuccessAnimation(modifier: Modifier = Modifier) {
    val circle = remember { Animatable(0f) }
    val check = remember { Animatable(0f) }
    val burst = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        circle.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow))
    }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(250)
        check.animateTo(1f, tween(450, easing = FastOutSlowInEasing))
    }
    LaunchedEffect(Unit) { burst.animateTo(1f, tween(1600, easing = LinearOutSlowInEasing)) }
    val primary = MaterialTheme.colorScheme.primary
    val onPrimary = MaterialTheme.colorScheme.onPrimary
    val colors = listOf(primary, Color(0xFFFFB866), Color(0xFFE53950), Color(0xFF4F5B92), Color(0xFF48C9B0))
    val particles = remember { val r = Random(7); List(42) { Particle(r.nextFloat() * 6.283f, 0.5f + r.nextFloat(), colors[it % colors.size], 6f + r.nextFloat() * 8f, r.nextFloat() * 720f) } }
    Box(modifier.size(260.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(260.dp)) {
            val c = Offset(size.width / 2, size.height / 2)
            val t = burst.value
            particles.forEach { p ->
                val dist = p.speed * size.minDimension * 0.48f * t
                val pos = Offset(c.x + cos(p.angle) * dist, c.y + sin(p.angle) * dist + 120f * t * t)
                rotate(p.spin * t, pos) {
                    drawRect(p.color.copy(alpha = (1f - t).coerceIn(0f, 1f)), Offset(pos.x - p.size / 2, pos.y - p.size / 4), Size(p.size, p.size / 2))
                }
            }
            val radius = size.minDimension * 0.26f * circle.value
            drawCircle(primary, radius, c)
            val path = Path().apply {
                moveTo(c.x - radius * 0.42f, c.y + radius * 0.02f)
                lineTo(c.x - radius * 0.1f, c.y + radius * 0.32f)
                lineTo(c.x + radius * 0.45f, c.y - radius * 0.3f)
            }
            val measure = PathMeasure().apply { setPath(path, false) }
            val partial = Path()
            measure.getSegment(0f, measure.length * check.value, partial, true)
            drawPath(partial, onPrimary, style = Stroke(width = 10.dp.toPx() * circle.value.coerceAtMost(1f), cap = StrokeCap.Round))
        }
    }
}
