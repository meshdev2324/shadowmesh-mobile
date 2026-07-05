package com.shadowmesh.app.ui.settings

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import uniffi.shadowmesh.SplitTunnelConfig

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppPickerScreen(
    currentConfig: SplitTunnelConfig,
    onSaveConfig: (SplitTunnelConfig) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val packageManager = context.packageManager
    var installedApps by remember { mutableStateOf<List<ApplicationInfo>>(emptyList()) }
    var selectedApps by remember { mutableStateOf(currentConfig.appList.toSet()) }
    var isEnabled by remember { mutableStateOf(currentConfig.enabled) }
    var mode by remember { mutableStateOf(currentConfig.mode) } // "include" or "exclude"

    LaunchedEffect(Unit) {
        val apps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 }
            .sortedBy { packageManager.getApplicationLabel(it).toString().lowercase() }
        installedApps = apps
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Split Tunneling", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = {
                        onSaveConfig(SplitTunnelConfig(isEnabled, mode, selectedApps.toList()))
                        onBack()
                    }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0C0C14))
            )
        },
        containerColor = Color(0xFF0C0C14)
    ) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues).padding(horizontal = 16.dp)) {
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Enable Split Tunneling", color = Color.White, fontWeight = FontWeight.Bold)
                    Text("Route specific apps through the VPN", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = isEnabled, onCheckedChange = { isEnabled = it })
            }
            
            if (isEnabled) {
                Spacer(modifier = Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = mode == "exclude",
                        onClick = { mode = "exclude" },
                        label = { Text("Exclude selected") },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color(0xFF6366F1), selectedLabelColor = Color.White)
                    )
                    FilterChip(
                        selected = mode == "include",
                        onClick = { mode = "include" },
                        label = { Text("Include selected") },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color(0xFF6366F1), selectedLabelColor = Color.White)
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(installedApps) { appInfo ->
                        val packageName = appInfo.packageName
                        val appName = packageManager.getApplicationLabel(appInfo).toString()
                        val isSelected = selectedApps.contains(packageName)
                        
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = { checked ->
                                    selectedApps = if (checked) {
                                        selectedApps + packageName
                                    } else {
                                        selectedApps - packageName
                                    }
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(appName, color = Color.White, fontWeight = FontWeight.Medium)
                                Text(packageName, color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}
