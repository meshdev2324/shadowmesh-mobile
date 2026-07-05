package com.shadowmesh.app.ui.decoy

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesDecoyScreen(
    onExitDecoy: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Notes", color = Color.Black, fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        containerColor = Color.White,
        modifier = modifier.fillMaxSize()
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            Text("Shopping List", fontWeight = FontWeight.Bold, color = Color.Black)
            Spacer(modifier = Modifier.height(8.dp))
            Text("- Milk\n- Eggs\n- Bread\n\n(Tap 5 times here to exit decoy)", color = Color.DarkGray)
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Button(onClick = onExitDecoy, colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent)) {
                Text("Exit Decoy", color = Color.Transparent)
            }
        }
    }
}
