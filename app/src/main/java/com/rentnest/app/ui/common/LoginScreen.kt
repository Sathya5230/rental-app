package com.rentnest.app.ui.common

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cottage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.rentnest.app.ui.components.PrimaryButton
import kotlinx.coroutines.delay

@Composable
fun LoginScreen(onLoggedIn: () -> Unit, viewModel: SessionActionsViewModel = hiltViewModel()) {
    var phone by rememberSaveable { mutableStateOf("") }
    var otpStep by rememberSaveable { mutableStateOf(false) }
    var otp by rememberSaveable { mutableStateOf("") }
    var verifying by remember { mutableStateOf(false) }
    LaunchedEffect(verifying) {
        if (verifying) { delay(800); viewModel.logIn(onLoggedIn) }
    }
    Scaffold { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(24.dp).imePadding()) {
            Spacer(Modifier.height(48.dp))
            Icon(Icons.Rounded.Cottage, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
            Spacer(Modifier.height(24.dp))
            Text("Welcome to RentNest", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(8.dp))
            AnimatedContent(otpStep, label = "step") { isOtp ->
                Column {
                    if (!isOtp) {
                        Text("Sign in with your mobile number to continue.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(32.dp))
                        OutlinedTextField(
                            value = phone,
                            onValueChange = { v -> phone = v.filter(Char::isDigit).take(10) },
                            label = { Text("Mobile number") },
                            prefix = { Text("+91 ") },
                            singleLine = true,
                            shape = MaterialTheme.shapes.small,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(24.dp))
                        PrimaryButton("Send OTP", onClick = { otpStep = true }, enabled = phone.length == 10, modifier = Modifier.fillMaxWidth())
                    } else {
                        Text("Enter the 4-digit code sent to +91 ${phone.take(5)} ${phone.drop(5)}", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(32.dp))
                        OtpField(otp) { otp = it }
                        Spacer(Modifier.height(12.dp))
                        Text("Demo mode: any 4 digits work.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(24.dp))
                        PrimaryButton("Verify & continue", onClick = { verifying = true }, enabled = otp.length == 4, loading = verifying, modifier = Modifier.fillMaxWidth())
                        TextButton(onClick = { otpStep = false; otp = "" }, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Change number") }
                    }
                }
            }
        }
    }
}

@Composable
private fun OtpField(value: String, onChange: (String) -> Unit) {
    BasicTextField(
        value = value,
        onValueChange = { onChange(it.filter(Char::isDigit).take(4)) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        decorationBox = {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                repeat(4) { i ->
                    val focused = i == value.length
                    Box(
                        Modifier.size(60.dp).border(
                            if (focused) 2.dp else 1.dp,
                            if (focused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            MaterialTheme.shapes.small,
                        ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(value.getOrNull(i)?.toString() ?: "", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                    }
                }
            }
        },
    )
}
