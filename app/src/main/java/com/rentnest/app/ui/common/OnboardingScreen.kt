package com.rentnest.app.ui.common

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rentnest.app.domain.model.AppMode
import com.rentnest.app.domain.DEMO_USER_ID
import com.rentnest.app.domain.repository.CatalogRepository
import com.rentnest.app.domain.repository.SessionRepository
import com.rentnest.app.ui.components.PrimaryButton
import com.rentnest.app.ui.components.carouselGestureExclusion
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SessionActionsViewModel @Inject constructor(
    private val session: SessionRepository,
    private val catalog: CatalogRepository,
) : ViewModel() {
    fun completeOnboarding(done: () -> Unit) = viewModelScope.launch { session.completeOnboarding(); done() }
    /** Saves [phone] as the customer's number, used for rental requests and overdue SMS. */
    fun logIn(phone: String, done: () -> Unit) = viewModelScope.launch {
        catalog.updatePhone(DEMO_USER_ID, phone)
        session.logIn()
        done()
    }
    fun chooseMode(mode: AppMode, done: () -> Unit) = viewModelScope.launch { session.chooseMode(mode); done() }
}

private data class Slide(val icon: ImageVector, val title: String, val body: String)

private val slides = listOf(
    Slide(Icons.Rounded.TravelExplore, "Rent anything, nearby", "Cameras, tools, camping kits and more from trusted local providers."),
    Slide(Icons.Rounded.EventAvailable, "Book in seconds", "Live availability, simple date picking and transparent pricing. No surprises."),
    Slide(Icons.Rounded.Storefront, "Approved by the store", "Your request goes to the store. Once it's approved, pay the advance at pickup and you're set."),
)

@Composable
fun OnboardingScreen(onFinished: () -> Unit, viewModel: SessionActionsViewModel = hiltViewModel()) {
    val pager = rememberPagerState(pageCount = { slides.size })
    val scope = rememberCoroutineScope()
    val finish = { viewModel.completeOnboarding(onFinished); Unit }
    Scaffold { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(24.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = finish) { Text("Skip") }
            }
            HorizontalPager(pager, Modifier.weight(1f).carouselGestureExclusion(160.dp)) { page ->
                val s = slides[page]
                Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Hero(s.icon)
                    Spacer(Modifier.height(40.dp))
                    Text(s.title, style = MaterialTheme.typography.headlineLarge, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(12.dp))
                    Text(s.body, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                }
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalArrangement = Arrangement.Center) {
                repeat(slides.size) { i ->
                    val width by animateDpAsState(if (pager.currentPage == i) 28.dp else 8.dp, label = "dot")
                    Box(
                        Modifier.padding(4.dp).height(8.dp).width(width).clip(CircleShape)
                            .background(if (pager.currentPage == i) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                    )
                }
            }
            val last = pager.currentPage == slides.lastIndex
            PrimaryButton(
                text = if (last) "Get started" else "Next",
                onClick = { if (last) finish() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun Hero(icon: ImageVector) {
    val float = rememberInfiniteTransition(label = "float")
    val dy by float.animateFloat(-8f, 8f, infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "dy")
    val primary = MaterialTheme.colorScheme.primary
    Box(Modifier.size(260.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.size(220.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)))
        Box(
            Modifier.size(150.dp).offset(y = dy.dp).clip(CircleShape)
                .background(Brush.linearGradient(listOf(primary, primary.copy(alpha = 0.7f)))),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(72.dp)) }
        Box(Modifier.align(Alignment.TopEnd).offset(x = (-20).dp, y = (30 - dy).dp).size(36.dp).clip(CircleShape).background(Color(0xFFFFB866)))
        Box(Modifier.align(Alignment.BottomStart).offset(x = 24.dp, y = (-30 + dy).dp).size(24.dp).clip(CircleShape).background(MaterialTheme.colorScheme.tertiary))
    }
}
