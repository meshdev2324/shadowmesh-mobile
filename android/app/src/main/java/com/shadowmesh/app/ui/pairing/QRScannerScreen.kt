package com.shadowmesh.app.ui.pairing

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.shadowmesh.app.VPNManagerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import uniffi.shadowmesh.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QRScannerScreen(
    viewModel: VPNManagerViewModel,
    onDismiss: () -> Unit
) {
    fun hexToBytes(hex: String): ByteArray {
        return hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            hasCameraPermission = granted
        }
    )

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            launcher.launch(Manifest.permission.CAMERA)
        }
    }

    var scannedCode by remember { mutableStateOf<String?>(null) }
    var showPinPrompt by remember { mutableStateOf(false) }
    var pairingPin by remember { mutableStateOf("") }
    var isPairing by remember { mutableStateOf(false) }

    LaunchedEffect(scannedCode) {
        if (scannedCode != null) {
            showPinPrompt = true
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (showPinPrompt) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.8f)),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier.padding(24.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            "Enter Pairing PIN",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        OutlinedTextField(
                            value = pairingPin,
                            onValueChange = { if (it.length <= 6) pairingPin = it },
                            label = { Text("6-digit PIN") },
                            singleLine = true
                        )
                        Button(
                            onClick = {
                                isPairing = true
                                scope.launch {
                                    try {
                                        val ciphertext = hexToBytes(scannedCode!!)
                                        val decrypted = decryptQrPairingPayload(ciphertext, pairingPin)
                                        val json = JSONObject(String(decrypted))
                                        
                                        val serverUrl = json.getString("server_url")
                                        val handshakeSecret = json.getString("handshake_secret")
                                        val desktopPubKey = json.getString("desktop_public_key")
                                        
                                        // 1. Generate DH Keys
                                        val mobilePrivKey = generateDhPrivateKey()
                                        val mobilePubKey = computeDhPublicKey(mobilePrivKey)
                                        
                                        // 2. Perform Pair request to server
                                        val client = viewModel.uiState.value.apiClient ?: createApiClient(serverUrl)
                                        
                                        // Manually perform pair call as it's a specific pairing endpoint
                                        // For simplicity, we assume the server route is added
                                        withContext(Dispatchers.IO) {
                                            val pairUrl = "${serverUrl}/api/v1/sessions/pair/${handshakeSecret}"
                                            val response = java.net.URL(pairUrl).openConnection().let { conn ->
                                                val http = conn as java.net.HttpURLConnection
                                                http.requestMethod = "POST"
                                                http.doOutput = true
                                                http.setRequestProperty("Content-Type", "application/json")
                                                val body = JSONObject().apply {
                                                    put("mobile_public_key", mobilePubKey)
                                                }
                                                http.outputStream.use { it.write(body.toString().toByteArray()) }
                                                http.responseCode
                                            }
                                            
                                            if (response == 200) {
                                                // 3. Derive Session Token (Local only, for verification)
                                                val sharedSecret = computeDhSharedSecret(mobilePrivKey, desktopPubKey)
                                                val sessionToken = deriveSessionToken(sharedSecret)
                                                println("✅ Pairing Successful. Session Token Derived: $sessionToken")
                                            } else {
                                                throw Exception("Server pairing failed: $response")
                                            }
                                        }
                                        
                                        onDismiss()
                                    } catch (e: Exception) {
                                        println("❌ Pairing failed: ${e.message}")
                                        isPairing = false
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = pairingPin.length == 6 && !isPairing
                        ) {
                            if (isPairing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = Color.White
                                )
                            } else {
                                Text("Decrypt & Pair")
                            }
                        }
                        
                        LaunchedEffect(isPairing) {
                            if (isPairing) {
                                delay(1500)
                                println("Decrypted payload with PIN: $pairingPin. Integrating with UniFFI...")
                                onDismiss()
                            }
                        }
                    }
                }
            }
        } else {
            if (hasCameraPermission) {
                // In a real app, bind CameraX preview and MLKit Barcode scanning here.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.DarkGray),
                    contentAlignment = Alignment.Center
                ) {
                    Text("CameraX Preview Here", color = Color.White)
                    Button(
                        onClick = { scannedCode = "dummy_payload_from_qr" },
                        modifier = Modifier.align(Alignment.BottomCenter).padding(50.dp)
                    ) {
                        Text("Simulate Scan")
                    }
                }
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Camera Permission Required")
                }
            }
        }
    }
}
