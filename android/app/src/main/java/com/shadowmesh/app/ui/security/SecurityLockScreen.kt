package com.shadowmesh.app.ui.security

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shadowmesh.app.VPNManagerViewModel

@Composable
fun SecurityLockScreen(
    onUnlocked: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val viewModel: VPNManagerViewModel = viewModel()
    var showPinPad by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (context is FragmentActivity) {
            viewModel.authenticateWithBiometrics(
                activity = context,
                onSuccess = { onUnlocked() },
                onFail = { showPinPad = true }
            )
        } else {
            showPinPad = true
        }
    }

    if (showPinPad) {
        com.shadowmesh.app.ui.settings.PINPadScreen(
            title = "App Locked",
            subtitle = "Enter standard or duress PIN",
            onPinComplete = { pin ->
                if (viewModel.verifyPin(pin)) {
                    onUnlocked()
                } else {
                    // Logic for wrong PIN or Duress
                    if (pin == "9999") {
                        // Duress handled by verifyPin triggering wipe
                    } else {
                        // TODO: Show "Wrong PIN" error
                    }
                }
            },
            onCancel = { }
        )
    } else {
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(Color(0xFF0C0C14)),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = "Locked",
                tint = Color(0xFF10B981),
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "Authenticating...",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White
            )
        }
    }
}
