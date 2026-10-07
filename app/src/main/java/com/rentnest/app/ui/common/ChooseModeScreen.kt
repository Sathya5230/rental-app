package com.rentnest.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.rentnest.app.domain.model.AppMode

@Composable
fun ChooseModeScreen(onModeChosen: (AppMode) -> Unit, onAdminSignIn: () -> Unit, viewModel: SessionActionsViewModel = hiltViewModel()) {
    Scaffold { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(24.dp), verticalArrangement = Arrangement.Center) {
            Text("Welcome to RentNest", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(8.dp))
            Text("Pick your dates, send a request, and collect your gear once the store approves it.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(32.dp))
            ModeCard(Icons.Rounded.Search, "I want to rent", "Browse gear nearby, book by the day or week.", Color(0xFF00695C)) {
                viewModel.chooseMode(AppMode.CUSTOMER) { onModeChosen(AppMode.CUSTOMER) }
            }
            Spacer(Modifier.height(24.dp))
            TextButton(onClick = onAdminSignIn, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Icon(Icons.Rounded.AdminPanelSettings, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Store admin? Sign in")
            }
        }
    }
}

@Composable
private fun ModeCard(icon: ImageVector, title: String, body: String, accent: Color, onClick: () -> Unit) {
    Card(onClick = onClick, shape = MaterialTheme.shapes.medium, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(56.dp).clip(CircleShape).background(accent), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = Color.White)
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Rounded.ArrowForward, null)
        }
    }
}
