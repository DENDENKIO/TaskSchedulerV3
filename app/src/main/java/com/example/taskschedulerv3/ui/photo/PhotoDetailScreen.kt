package com.example.taskschedulerv3.ui.photo

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoDetailScreen(navController: NavController, photoId: Int) {
    Scaffold(topBar = { TopAppBar(title = { Text("写真詳細") }) }) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Phase 5 で実装予定")
        }
    }
}
