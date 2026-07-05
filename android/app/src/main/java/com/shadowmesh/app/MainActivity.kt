
package com.shadowmesh.app

import android.os.Bundle
import android.content.Context
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.fragment.app.FragmentActivity
import androidx.compose.animation.core.*
import androidx.compose.animation.core.AnimationConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.shadowmesh.app.ui.theme.ShadowMeshTheme
import uniffi.shadowmesh.ConnectionStatus
import uniffi.shadowmesh.TrafficModePreference
import uniffi.shadowmesh.SplitTunnelConfig
import uniffi.shadowmesh.VpnNode
import kotlin.math.min
import kotlin.math.max
import android.view.WindowManager
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.content.Intent
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.shadowmesh.app.ui.security.SecurityLockScreen
import com.shadowmesh.app.ui.decoy.NotesDecoyScreen

class MainActivity : FragmentActivity() {
    private val viewModel: VPNManagerViewModel by viewModels {
        VPNManagerViewModelFactory(this)
    }

    private fun performRootCheck() {
        if (RootDetection.isRooted(this)) {
            println("⚠️ Root detected! Showing security warning.")
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Security Warning")
                .setMessage("Your device appears to be rooted, which may compromise security. Some features will be disabled.")
                .setPositiveButton("OK", null)
                .show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        
        performRootCheck()
        
        setContent {
            ShadowMeshTheme {
                var isUnlocked by remember { mutableStateOf(false) }
                var isCamouflageMode by remember { mutableStateOf(false) }

                DisposableEffect(Unit) {
                    val receiver = object : BroadcastReceiver() {
                        override fun onReceive(context: Context?, intent: Intent?) {
                            isCamouflageMode = true
                            isUnlocked = false
                        }
                    }
                    registerReceiver(receiver, IntentFilter("com.shadowmesh.app.TRIGGER_DECOY"))
                    
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_STOP) {
                            if (!isCamouflageMode) isUnlocked = false
                        }
                    }
                    lifecycle.addObserver(observer)
                    
                    onDispose {
                        unregisterReceiver(receiver)
                        lifecycle.removeObserver(observer)
                    }
                }

                if (isCamouflageMode) {
                    NotesDecoyScreen(onExitDecoy = {
                        isCamouflageMode = false
                        isUnlocked = false
                    })
                } else if (!isUnlocked) {
                    SecurityLockScreen(onUnlocked = { isUnlocked = true })
                } else {
                    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                        MainScreen(viewModel = viewModel, modifier = Modifier.padding(innerPadding))
                    }
                }
            }
        }
    }
}

class VPNManagerViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(VPNManagerViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return VPNManagerViewModel(context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

@Composable
fun MainScreen(viewModel: VPNManagerViewModel, modifier: Modifier = Modifier) {
    val uiState by viewModel.uiState.collectAsState()
    var currentScreen by remember { mutableStateOf("Status") }

    Scaffold(
        modifier = modifier,
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = currentScreen == "Status",
                    onClick = { currentScreen = "Status" },
                    icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                    label = { Text("Home") }
                )
                NavigationBarItem(
                    selected = currentScreen == "Settings",
                    onClick = { currentScreen = "Settings" },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                    label = { Text("Settings") }
                )
            }
        }
    ) { paddingValues ->
        val uiState by viewModel.uiState.collectAsState()
        var showConsentDialog by remember { mutableStateOf(!viewModel.hasAcceptedVpnConsent()) }

        if (showConsentDialog) {
            AlertDialog(
                onDismissRequest = { /* Must accept to use app */ },
                title = { Text("VPN Permission Required") },
                text = { Text("ShadowMesh uses a VpnService to create a secure, encrypted tunnel to its mesh network. This is required to provide anonymous browsing and bypass network restrictions. No personal traffic data is ever logged or shared.") },
                confirmButton = {
                    Button(onClick = {
                        viewModel.acceptVpnConsent()
                        showConsentDialog = false
                    }) { Text("Accept & Continue") }
                }
            )
        }

        when {
            uiState.showActivationScreen -> com.shadowmesh.app.ui.security.ActivationScreen(
                viewModel = viewModel,
                modifier = Modifier.padding(paddingValues)
            )
            uiState.showCustomDNSSettings -> CustomDNSSettingsScreen(
                customDNSServers = uiState.customDNSServers,
                onAddDNS = { viewModel.addCustomDNSServer(it) },
                onRemoveDNS = { viewModel.removeCustomDNSServer(it) },
                onBack = { viewModel.setShowCustomDNSSettings(false) }
            )
            uiState.showVPNGuideModal -> VPNGuideScreen(
                onBack = { viewModel.setShowVPNGuideModal(false) }
            )
            currentScreen == "Status" -> StatusScreen(
                uiState = uiState,
                onToggleConnection = { viewModel.toggleConnection() },
                onSelectNode = { viewModel.selectNode(it) },
                onRefreshNodes = { viewModel.loadNodes() },
                onClearError = { viewModel.clearError() },
                modifier = Modifier.padding(paddingValues)
            )
            currentScreen == "Settings" -> SettingsScreen(
                uiState = uiState,
                onSetKillSwitch = { viewModel.setKillSwitch(it) },
                onSetTrafficModePreference = { viewModel.setTrafficModePreference(it) },
                onSetSplitTunnelConfig = { viewModel.setSplitTunnelConfig(it) },
                onEnableCamouflage = { viewModel.toggleCamouflageMode(true) },
                onShowCustomDNSSettings = { viewModel.setShowCustomDNSSettings(true) },
                onShowVPNGuide = { viewModel.setShowVPNGuideModal(true) },
                onShowQRScanner = { showQRScanner = true },
                modifier = Modifier.padding(paddingValues)
            )
        }
    }
}



@Composable
fun StatusScreen(
    uiState: VPNUiState,
    onToggleConnection: () -> Unit,
    onSelectNode: (VpnNode) -> Unit,
    onRefreshNodes: () -> Unit,
    onClearError: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isConnected = uiState.status == ConnectionStatus.CONNECTED
    val isConnecting = uiState.isConnecting

    if (uiState.errorMessage != null) {
        AlertDialog(
            onDismissRequest = onClearError,
            title = { Text("Error") },
            text = { Text(uiState.errorMessage) },
            confirmButton = {
                Button(onClick = onClearError) { Text("OK") }
            }
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0C0C14))
    ) {
        // Ambient Glows
        Box(
            modifier = Modifier
                .size(300.dp)
                .offset((-150).dp, (-150).dp)
                .background(
                    Color(0xFF6366F1).copy(alpha = 0.15f),
                    shape = CircleShape
                )
                .blur(60.dp)
        )
        Box(
            modifier = Modifier
                .size(250.dp)
                .offset(150.dp, 300.dp)
                .background(
                    Color(0xFFA855F7).copy(alpha = 0.1f),
                    shape = CircleShape
                )
                .blur(60.dp)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            HeaderView()

            LatencyVisualizer(
                latency = uiState.selectedNode?.latency ?: 0.0,
                status = uiState.status
            )

            ConnectButtonView(
                status = uiState.status,
                isConnecting = isConnecting,
                action = onToggleConnection
            )

            StatusInfoView(status = uiState.status)

            if (isConnected) {
                SpeedChart()
            }

            GuideBannerView()

            ServersListView(
                nodes = uiState.nodes,
                selectedNode = uiState.selectedNode,
                isLoading = uiState.isLoadingNodes,
                onSelect = onSelectNode,
                onRefresh = onRefreshNodes
            )
        }
    }
}

@Composable
fun LatencyVisualizer(latency: Double, status: ConnectionStatus) {
    val pulseScale by animateFloatAsState(
        targetValue = if (status == ConnectionStatus.CONNECTED) 1.2f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val statusColor = when (status) {
        ConnectionStatus.ERROR -> Color(0xFFEF4444)
        ConnectionStatus.CONNECTING_DIRECT -> Color(0xFF6366F1)
        ConnectionStatus.CONNECTING_FRAGMENTED -> Color(0xFFF59E0B)
        ConnectionStatus.CONNECTING_REALITY -> Color(0xFFA855F7)
        ConnectionStatus.CONNECTED -> if (latency < 50) Color(0xFF10B981) else if (latency < 150) Color(0xFFF59E0B) else Color(0xFFEF4444)
        else -> Color(0xFF71717A)
    }

    val latencyText = when (status) {
        ConnectionStatus.ERROR -> "Connection Failed"
        ConnectionStatus.DISCONNECTING -> "Stopping..."
        ConnectionStatus.DISCONNECTED -> "Offline"
        ConnectionStatus.CONNECTING_DIRECT -> "Direct Connect..."
        ConnectionStatus.CONNECTING_FRAGMENTED -> "DPI Bypass..."
        ConnectionStatus.CONNECTING_REALITY -> "Stealth Mode..."
        ConnectionStatus.CONNECTED -> if (latency < 50) "Ultra Low" else if (latency < 150) "Stable" else "High Latency"
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .size(220.dp)
                .scale(pulseScale)
                .background(
                    statusColor.copy(alpha = 0.3f),
                    shape = CircleShape
                )
        )

        Box(
            modifier = Modifier
                .size(180.dp)
                .background(Color(0xFF0F0F1A), shape = CircleShape)
                .border(1.dp, Color(0xFF23232F), shape = CircleShape),
            contentAlignment = Alignment.Center
        ) {
            val progress = min(latency, 300.0) / 300.0
            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokeWidth = 5.dp.toPx()
                val radius = (size.minDimension - strokeWidth) / 2f

                // Background circle
                drawCircle(
                    color = Color(0xFF23232F),
                    radius = radius,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(strokeWidth)
                )

                // Progress arc
                drawArc(
                    color = statusColor,
                    startAngle = -90f,
                    sweepAngle = (1 - progress).toFloat() * 360f,
                    useCenter = false,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                        strokeWidth,
                        cap = androidx.compose.ui.graphics.StrokeCap.Round
                    )
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = if (status == ConnectionStatus.CONNECTED) "${latency.toInt()}ms" else "--",
                    color = statusColor,
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                )
                Text(
                    text = latencyText,
                    color = Color.Gray,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }
        }
    }
}

@Composable
fun SpeedChart() {
    var downloadHistory by remember { mutableStateOf(listOf(0.0, 20.0, 15.0, 40.0, 35.0, 50.0, 45.0, 60.0)) }
    var uploadHistory by remember { mutableStateOf(listOf(0.0, 10.0, 8.0, 20.0, 18.0, 25.0, 22.0, 30.0)) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp)
            .background(Color.White.copy(alpha = 0.03f), shape = RoundedCornerShape(24.dp))
            .border(1.dp, Color.White.copy(alpha = 0.05f), shape = RoundedCornerShape(24.dp)),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            SpeedStat(
                icon = Icons.Default.ArrowDownward,
                label = "DOWNLOAD",
                value = "12.4 Mbps",
                color = Color(0xFF6366F1)
            )
            SpeedStat(
                icon = Icons.Default.ArrowUpward,
                label = "UPLOAD",
                value = "5.2 Mbps",
                color = Color(0xFFA855F7)
            )
        }

        ChartView(
            downloadHistory = downloadHistory,
            uploadHistory = uploadHistory,
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
        )
    }
}

@Composable
fun SpeedStat(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    color: Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(color.copy(alpha = 0.1f), shape = RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(14.dp)
            )
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = label,
                color = Color.Gray,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Text(
                text = value,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
            )
        }
    }
}

@Composable
fun ChartView(
    downloadHistory: List<Double>,
    uploadHistory: List<Double>,
    modifier: Modifier = Modifier
) {
    val maxVal = remember(downloadHistory, uploadHistory) {
        max(max(downloadHistory.maxOrNull() ?: 100.0, uploadHistory.maxOrNull() ?: 100.0), 100.0)
    }

    Box(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            val stepX = width / (downloadHistory.size - 1)

            // Download area
            val downloadPath = androidx.compose.ui.graphics.Path()
            downloadHistory.forEachIndexed { index, value ->
                val x = index * stepX
                val y = height - (value / maxVal).toFloat() * height
                if (index == 0) {
                    downloadPath.moveTo(x, y)
                } else {
                    downloadPath.lineTo(x, y)
                }
            }
            downloadPath.lineTo(width, height)
            downloadPath.lineTo(0f, height)
            downloadPath.close()

            drawPath(
                path = downloadPath,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF6366F1).copy(alpha = 0.3f),
                        Color.Transparent
                    )
                )
            )

            // Download line
            val downloadLinePath = androidx.compose.ui.graphics.Path()
            downloadHistory.forEachIndexed { index, value ->
                val x = index * stepX
                val y = height - (value / maxVal).toFloat() * height
                if (index == 0) {
                    downloadLinePath.moveTo(x, y)
                } else {
                    downloadLinePath.lineTo(x, y)
                }
            }
            drawPath(
                path = downloadLinePath,
                color = Color(0xFF6366F1),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
            )

            // Upload line
            val uploadLinePath = androidx.compose.ui.graphics.Path()
            val uploadStepX = width / (uploadHistory.size - 1)
            uploadHistory.forEachIndexed { index, value ->
                val x = index * uploadStepX
                val y = height - (value / maxVal).toFloat() * height
                if (index == 0) {
                    uploadLinePath.moveTo(x, y)
                } else {
                    uploadLinePath.lineTo(x, y)
                }
            }
            drawPath(
                path = uploadLinePath,
                color = Color(0xFFA855F7),
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = 1.5.dp.toPx(),
                    pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(
                        floatArrayOf(4.dp.toPx(), 2.dp.toPx()), 0f
                    )
                )
            )
        }
    }
}

@Composable
fun HeaderView() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                "SHADOWMESH",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )
            Text(
                "Anonymous • Mesh • VPN",
                color = Color.Gray,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.Logout,
            contentDescription = "Logout",
            tint = Color(0xFFEF4444),
            modifier = Modifier
                .padding(8.dp)
                .background(Color(0x1AEF4444), shape = RoundedCornerShape(12.dp))
                .padding(8.dp)
        )
    }
}

@Composable
fun ConnectButtonView(
    status: ConnectionStatus,
    isConnecting: Boolean,
    action: () -> Unit
) {
    val isConnected = status == ConnectionStatus.CONNECTED
    val pulseScale by animateFloatAsState(
        targetValue = if (isConnected || isConnecting) 1.8f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Box(contentAlignment = Alignment.Center) {
        if (isConnected || isConnecting) {
            Box(
                modifier = Modifier
                    .size(180.dp)
                    .scale(pulseScale)
                    .clip(CircleShape)
                    .border(
                        2.dp,
                        (if (isConnected) Color(0xFF10B981) else Color(0xFF6366F1)).copy(alpha = 0.3f),
                        CircleShape
                    )
            )
        }

        Box(
            modifier = Modifier
                .size(140.dp)
                .clip(CircleShape)
                .background(
                    brush = if (isConnected) {
                        Brush.verticalGradient(
                            colors = listOf(Color(0xFF10B981), Color(0xFF059669))
                        )
                    } else {
                        Brush.verticalGradient(
                            colors = listOf(Color(0xFF6366F1), Color(0xFF4F46E5))
                        )
                    }
                )
                .clickable(enabled = !isConnecting, onClick = action),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = if (isConnected) Icons.Default.Shield else Icons.Default.Power,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(48.dp)
                )
                Text(
                    text = if (isConnected) "SECURE" else "ON",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }
        }
    }
}

@Composable
fun StatusInfoView(status: ConnectionStatus) {
    val isConnected = status == ConnectionStatus.CONNECTED
    val statusText = when (status) {
        ConnectionStatus.DISCONNECTED -> "Ready to Connect"
        ConnectionStatus.CONNECTING_DIRECT -> "Connecting (Phase 1/3)..."
        ConnectionStatus.CONNECTING_FRAGMENTED -> "Bypassing DPI (Phase 2/3)..."
        ConnectionStatus.CONNECTING_REALITY -> "Applying REALITY (Phase 3/3)..."
        ConnectionStatus.CONNECTED -> "VPN Connection Active"
        ConnectionStatus.DISCONNECTING -> "Disconnecting..."
        ConnectionStatus.ERROR -> "Connection Error"
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            statusText,
            color = Color.White,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold
        )
        
        if (status.name.startsWith("CONNECTING", ignoreCase = true)) {
            val progress = when (status) {
                ConnectionStatus.CONNECTING_DIRECT -> 0.33f
                ConnectionStatus.CONNECTING_FRAGMENTED -> 0.66f
                ConnectionStatus.CONNECTING_REALITY -> 0.90f
                else -> 0f
            }
            LinearProgressIndicator(
                progress = progress,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 40.dp).height(4.dp),
                color = Color(0xFF6366F1),
                trackColor = Color.White.copy(alpha = 0.1f)
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(
                        if (isConnected) Color(0xFF10B981) else Color.Gray
                    )
            )
            Text(
                if (isConnected) "AES-256 Encrypted" else "Not Connected",
                color = if (isConnected) Color(0xFF10B981) else Color.Gray,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun StatsView(stats: uniffi.shadowmesh.ConnectionStats) {
    val received = stats.bytesReceived.toString()
    val sent = stats.bytesSent.toString()

    Column(
        modifier = Modifier
            .padding(vertical = 12.dp, horizontal = 24.dp)
            .background(Color.White.copy(alpha = 0.05f), shape = RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp)),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    "↓ $received",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Received",
                    color = Color.Gray,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    "↑ $sent",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Sent",
                    color = Color.Gray,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun GuideBannerView() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.05f), shape = RoundedCornerShape(24.dp))
            .border(
                1.dp,
                Color(0xFF6366F1).copy(alpha = 0.1f),
                shape = RoundedCornerShape(24.dp)
            )
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xFF6366F1).copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Shield,
                contentDescription = null,
                tint = Color(0xFF6366F1),
                modifier = Modifier.size(16.dp)
            )
        }

        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.weight(1f)
        ) {
            Text(
                "Maximum Sovereignty",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Enable Always-on VPN for 24/7 protection",
                color = Color.Gray,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = Color.Gray,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
fun ServersListView(
    nodes: List<VpnNode>,
    selectedNode: VpnNode?,
    isLoading: Boolean,
    onSelect: (VpnNode) -> Unit,
    onRefresh: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "AVAILABLE SERVERS",
                color = Color.Gray,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = Color(0xFF6366F1)
                )
            } else {
                IconButton(onClick = onRefresh) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh",
                        tint = Color(0xFF6366F1),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(15.dp)
        ) {
            items(nodes) { node ->
                ServerCard(
                    node = node,
                    isSelected = selectedNode?.id == node.id,
                    onClick = { onSelect(node) }
                )
            }
        }
    }
}

@Composable
fun ServerCard(
    node: VpnNode,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(140.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .border(
                width = 1.dp,
                color = if (isSelected) Color(0xFF6366F1).copy(alpha = 0.3f) else Color.Transparent,
                shape = RoundedCornerShape(24.dp)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(
                    if (isSelected) Color(0xFF6366F1).copy(alpha = 0.2f)
                    else Color.White.copy(alpha = 0.05f)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Storage,
                contentDescription = null,
                tint = if (isSelected) Color(0xFF6366F1) else Color.Gray,
                modifier = Modifier.size(18.dp)
            )
        }

        Text(
            node.name,
            color = if (isSelected) Color.White else Color.Gray,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(
                        if (node.latency < 100.0) Color(0xFF10B981) else Color(0xFFF59E0B)
                    )
            )
            Text(
                "${node.latency.toInt()}ms",
                color = Color.Gray,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun SettingsScreen(
    uiState: VPNUiState,
    onSetKillSwitch: (Boolean) -> Unit,
    onSetTrafficModePreference: (TrafficModePreference) -> Unit,
    onSetSplitTunnelConfig: (SplitTunnelConfig) -> Unit,
    onEnableCamouflage: () -> Unit,
    onShowCustomDNSSettings: () -> Unit,
    onShowVPNGuide: () -> Unit,
    onShowQRScanner: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showAppPicker by remember { mutableStateOf(false) }
    var showPinPad by remember { mutableStateOf(false) }
    var showCustomDNSScreen by remember { mutableStateOf(uiState.showCustomDNSSettings) }
    var showVPNGuideScreen by remember { mutableStateOf(uiState.showVPNGuideModal) }

    if (showCustomDNSScreen) {
        CustomDNSSettingsScreen(
            customDNSServers = uiState.customDNSServers,
            onAddDNS = { viewModel.addCustomDNSServer(it) },
            onRemoveDNS = { viewModel.removeCustomDNSServer(it) },
            onBack = { 
                showCustomDNSScreen = false
                viewModel.setShowCustomDNSSettings(false)
            }
        )
        return
    }

    if (showVPNGuideScreen) {
        VPNGuideScreen(
            onBack = { 
                showVPNGuideScreen = false
                viewModel.setShowVPNGuideModal(false)
            }
        )
        return
    }
    
    if (showAppPicker) {
        com.shadowmesh.app.ui.settings.AppPickerScreen(
            currentConfig = uiState.splitTunnelConfig,
            onSaveConfig = { config ->
                onSetSplitTunnelConfig(config)
                showAppPicker = false
            },
            onBack = { showAppPicker = false }
        )
        return
    }
    
    if (showPinPad) {
        com.shadowmesh.app.ui.settings.PINPadScreen(
            title = "Set Security PIN",
            subtitle = "Enter 4 digits",
            onPinComplete = { pin ->
                viewModel.setPin(pin)
                showPinPad = false
            },
            onCancel = { showPinPad = false }
        )
        return
    }

    val indigo  = Color(0xFF6366F1)
    val green   = Color(0xFF10B981)
    val amber   = Color(0xFFF59E0B)
    val purple  = Color(0xFFA855F7)
    val orange  = Color(0xFFF97316)
    val bgCard  = Color.White.copy(alpha = 0.04f)
    val border  = Color.White.copy(alpha = 0.07f)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0C0C14))
            .padding(horizontal = 20.dp)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Settings",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            color = Color.White
        )

        // VPN Guide Banner
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(bgCard, shape = RoundedCornerShape(20.dp))
                .border(1.dp, border, shape = RoundedCornerShape(20.dp))
                .padding(16.dp)
                .clickable(onClick = onShowVPNGuide),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFFF59E0B).copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Lightbulb,
                    contentDescription = null,
                    tint = Color(0xFFF59E0B),
                    modifier = Modifier.size(18.dp)
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    "VPN Setup Guide",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = Color.White
                )
                Text(
                    "Learn about kill switch & security features",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = Color.Gray.copy(alpha = 0.6f)
            )
        }

        // VPN Protection Card
        SettingsCardSection(title = "VPN PROTECTION", borderColor = border, bgColor = bgCard) {
            SettingsSwitchRow(
                icon = Icons.Default.Shield, iconColor = amber,
                label = "Kill Switch",
                subtitle = "Block traffic if VPN drops",
                checked = uiState.killSwitchEnabled,
                onChecked = onSetKillSwitch
            )
            SettingsDivider()
            SettingsNavRow(
                icon = Icons.Default.VerticalSplit, iconColor = indigo,
                label = "Split Tunneling",
                subtitle = if (uiState.splitTunnelConfig.enabled) "${uiState.splitTunnelConfig.appList.size} apps routed" else "Route specific apps",
                onClick = { showAppPicker = true }
            )
            SettingsDivider()
            SettingsNavRow(
                icon = Icons.Default.NetworkCheck, iconColor = green,
                label = "Custom DNS Servers",
                subtitle = if (uiState.customDNSServers.isEmpty()) "Use default (1.1.1.1, 8.8.8.8)" else "${uiState.customDNSServers.size} configured",
                onClick = onShowCustomDNSSettings
            )
            SettingsDivider()
            SettingsSectionLabel("Traffic Mode")
            Spacer(modifier = Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    Triple(TrafficModePreference.AUTO,    "Auto",    "Automatic DPI detection"),
                    Triple(TrafficModePreference.SPEED,   "Speed",   "Direct — fastest throughput"),
                    Triple(TrafficModePreference.STEALTH, "Stealth", "Fragmentation + REALITY bypass"),
                ).forEach { (pref, label, subtitle) ->
                    val color = when (pref) {
                        TrafficModePreference.AUTO    -> indigo
                        TrafficModePreference.SPEED   -> green
                        TrafficModePreference.STEALTH -> purple
                        else -> indigo
                    }
                    TrafficModeOption(
                        label = label,
                        subtitle = subtitle,
                        selected = uiState.trafficModePreference == pref,
                        accentColor = color,
                        onClick = { onSetTrafficModePreference(pref) }
                    )
                }
            }
        }

        // Security Card
        SettingsCardSection(title = "SECURITY", borderColor = border, bgColor = bgCard) {
            SettingsSwitchRow(
                icon = Icons.Default.Lock, iconColor = green,
                label = "Security Lock",
                subtitle = "Require PIN or biometrics on open",
                checked = uiState.isSecurityLockEnabled,
                onChecked = { /* Force through PIN set flow for now */ showPinPad = true }
            )
            SettingsDivider()
            SettingsNavRow(
                icon = Icons.Default.Dialpad, iconColor = purple,
                label = "Set PIN & Duress PIN",
                subtitle = "Configure primary and panic PIN",
                onClick = { showPinPad = true }
            )
            SettingsDivider()
            SettingsNavRow(
                icon = Icons.Default.Masks, iconColor = orange,
                label = "Camouflage Mode",
                subtitle = "Appear as a Notes app to observers",
                onClick = onEnableCamouflage
            )
        }

        // Desktop Pairing Card
        SettingsCardSection(title = "DESKTOP PAIRING", borderColor = border, bgColor = bgCard) {
            SettingsNavRow(
                icon = Icons.Default.QrCodeScanner, iconColor = indigo,
                label = "Scan QR Code",
                subtitle = "Pair with ShadowMesh Desktop",
                onClick = onShowQRScanner
            )
        }

        // Pairing Card
        SettingsCardSection(title = "DESKTOP PAIRING", borderColor = border, bgColor = bgCard) {
            SettingsNavRow(
                icon = Icons.Default.QrCodeScanner, iconColor = indigo,
                label = "Scan QR Code",
                subtitle = "Pair with ShadowMesh Desktop"
            )
        }

        // About Card
        SettingsCardSection(title = "ABOUT", borderColor = border, bgColor = bgCard) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.semantics { contentDescription = "ShadowMesh version 4.4.0" }
            ) {
                Icon(Icons.Default.Info, contentDescription = null,
                    tint = Color.Gray, modifier = Modifier.size(20.dp))
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("ShadowMesh v4.4.0",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = Color.White)
                    Text("Anonymous by Design",
                        style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }
            }
        }
    }
}

@Composable
fun SettingsCardSection(
    title: String,
    borderColor: Color,
    bgColor: Color,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, fontSize = 11.sp, fontWeight = FontWeight.Bold,
            color = Color.Gray, letterSpacing = 1.5.sp)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(bgColor, RoundedCornerShape(20.dp))
                .border(1.dp, borderColor, RoundedCornerShape(20.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content
        )
    }
}

@Composable
fun SettingsSwitchRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color,
    label: String,
    subtitle: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = "$label: ${if (checked) "on" else "off"}. $subtitle" },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .background(iconColor.copy(alpha = 0.12f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) { Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(16.dp)) }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(label, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = Color.White)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
        }
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

@Composable
fun CustomDNSSettingsScreen(
    customDNSServers: List<String>,
    onAddDNS: (String) -> Unit,
    onRemoveDNS: (String) -> Unit,
    onBack: () -> Unit
) {
    var newDNSEntry by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Custom DNS Servers", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0C0C14))
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0C0C14))
                .padding(paddingValues)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Add DNS Entry
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = newDNSEntry,
                    onValueChange = { newDNSEntry = it },
                    label = { Text("DNS Server", color = Color.Gray) },
                    placeholder = { Text("e.g. 8.8.8.8", color = Color.Gray) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = Color(0xFF6366F1),
                        focusedContainerColor = Color.White.copy(alpha = 0.05f),
                        unfocusedContainerColor = Color.White.copy(alpha = 0.05f),
                        focusedBorderColor = Color(0xFF6366F1),
                        unfocusedBorderColor = Color.White.copy(alpha = 0.1f)
                    )
                )
                Button(
                    onClick = {
                        if (newDNSEntry.isNotBlank()) {
                            onAddDNS(newDNSEntry.trim())
                            newDNSEntry = ""
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1))
                ) {
                    Text("Add DNS Server", color = Color.White)
                }
            }

            // List of DNS Servers
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Configured Servers (${customDNSServers.size})",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (customDNSServers.isEmpty()) {
                    Text(
                        "No custom DNS servers configured. Using defaults.",
                        color = Color.Gray,
                        style = MaterialTheme.typography.bodySmall
                    )
                } else {
                    customDNSServers.forEach { dns ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.White.copy(alpha = 0.04f), RoundedCornerShape(12.dp))
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(dns, color = Color.White, style = MaterialTheme.typography.bodyMedium)
                            IconButton(onClick = { onRemoveDNS(dns) }) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Remove DNS server",
                                    tint = Color(0xFFEF4444)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun VPNGuideScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("VPN Setup Guide", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0C0C14))
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0C0C14))
                .padding(paddingValues)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            GuideSection(
                icon = Icons.Default.Shield,
                iconColor = Color(0xFFF59E0B),
                title = "Kill Switch",
                description = "Blocks all internet traffic if your VPN connection drops, preventing leaks."
            )

            Divider(color = Color.White.copy(alpha = 0.1f))

            GuideSection(
                icon = Icons.Default.Lock,
                iconColor = Color(0xFF10B981),
                title = "Security Lock",
                description = "Requires PIN or biometric authentication to open ShadowMesh."
            )

            Divider(color = Color.White.copy(alpha = 0.1f))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color(0xFF6366F1).copy(alpha = 0.12f), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Speed, contentDescription = null, tint = Color(0xFF6366F1))
                    }
                    Text(
                        "Traffic Modes",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(start = 48.dp)) {
                    Text("• Auto: Optimized based on DPI detection", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                    Text("• Speed: Direct — fastest throughput", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                    Text("• Stealth: Fragmentation + REALITY bypass", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                }
            }

            Divider(color = Color.White.copy(alpha = 0.1f))

            GuideSection(
                icon = Icons.Default.Masks,
                iconColor = Color(0xFFA855F7),
                title = "Camouflage Mode",
                description = "Appears as a Notes app to avoid suspicion."
            )
        }
    }
}

@Composable
fun GuideSection(icon: androidx.compose.ui.graphics.vector.ImageVector, iconColor: Color, title: String, description: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(iconColor.copy(alpha = 0.12f), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = iconColor)
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                title,
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                description,
                color = Color.Gray,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
fun SettingsNavRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color,
    label: String,
    subtitle: String,
    onClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .semantics { contentDescription = "$label. $subtitle. Double-tap to open." },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .background(iconColor.copy(alpha = 0.12f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) { Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(16.dp)) }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(label, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = Color.White)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
        }
        Icon(Icons.Default.ChevronRight, contentDescription = null,
            tint = Color.Gray.copy(alpha = 0.5f), modifier = Modifier.size(18.dp))
    }
}

@Composable
fun SettingsDivider() {
    Divider(color = Color.White.copy(alpha = 0.06f), thickness = 1.dp)
}

@Composable
fun SettingsSectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
        color = Color.Gray, letterSpacing = 0.5.sp)
}

@Composable
fun TrafficModeOption(
    label: String,
    subtitle: String,
    selected: Boolean,
    accentColor: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) accentColor.copy(alpha = 0.09f) else Color.White.copy(alpha = 0.03f))
            .border(1.dp, if (selected) accentColor.copy(alpha = 0.3f) else Color.Transparent, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(14.dp)
            .semantics {
                contentDescription = "$label mode: $subtitle"
                if (selected) stateDescription = "selected"
            },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label,
                color = if (selected) accentColor else Color.White,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                fontSize = 14.sp)
            Text(subtitle, color = Color.Gray, fontSize = 11.sp)
        }
        if (selected) {
            Icon(Icons.Default.CheckCircle, contentDescription = "Selected",
                tint = accentColor, modifier = Modifier.size(20.dp))
        }
    }
}
