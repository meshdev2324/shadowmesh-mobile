package com.shadowmesh.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun PINPadScreen(
    title: String = "Enter PIN",
    subtitle: String = "",
    onPinComplete: (String) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    var currentPin by remember { mutableStateOf("") }
    val maxPinLength = 4

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0C0C14))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            color = Color.White
        )
        if (subtitle.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray
            )
        }

        Spacer(modifier = Modifier.height(48.dp))

        // PIN Dots
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(maxPinLength) { index ->
                val isFilled = index < currentPin.length
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(if (isFilled) Color.White else Color.DarkGray)
                )
            }
        }

        Spacer(modifier = Modifier.height(64.dp))

        // Numpad
        val buttons = listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
            listOf("Cancel", "0", "Del")
        )

        buttons.forEach { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                modifier = Modifier.padding(bottom = 24.dp)
            ) {
                row.forEach { btn ->
                    if (btn == "Del") {
                        IconButton(
                            onClick = {
                                if (currentPin.isNotEmpty()) {
                                    currentPin = currentPin.dropLast(1)
                                }
                            },
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.05f))
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = "Delete", tint = Color.White)
                        }
                    } else if (btn == "Cancel") {
                        TextButton(
                            onClick = onCancel,
                            modifier = Modifier.size(72.dp)
                        ) {
                            Text("Cancel", color = Color.White)
                        }
                    } else {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.1f))
                                .clickable {
                                    if (currentPin.length < maxPinLength) {
                                        currentPin += btn
                                        if (currentPin.length == maxPinLength) {
                                            onPinComplete(currentPin)
                                            currentPin = ""
                                        }
                                    }
                                }
                        ) {
                            Text(
                                text = btn,
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}
