package com.trencadis.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun PresetPanel(
    presetNames: List<String>,
    onSavePreset: (String) -> Unit,
    onLoadPreset: (String) -> Unit,
    onDeletePreset: (String) -> Unit,
    onSharePreset: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var presetName by remember { mutableStateOf("") }
    var showDeleteConfirm by remember { mutableStateOf<String?>(null) }
    
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp))
            .background(Color(0xCC1A2E1A))
            .padding(12.dp)
            .width(260.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Header
        Text(
            text = "💾 PRESETS",
            color = Color(0xFF4CAF50),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
        
        // Save section
        Text(
            text = "SAVE NEW",
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 10.sp
        )
        
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Name input field
            BasicTextField(
                value = presetName,
                onValueChange = { presetName = it.take(30) },
                textStyle = TextStyle(
                    color = Color.White,
                    fontSize = 12.sp
                ),
                cursorBrush = SolidColor(Color(0xFF4CAF50)),
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFF2A3E2A))
                    .border(1.dp, Color(0xFF4CAF50).copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                decorationBox = { innerTextField ->
                    Box {
                        if (presetName.isEmpty()) {
                            Text(
                                text = "Enter name...",
                                color = Color.White.copy(alpha = 0.4f),
                                fontSize = 12.sp
                            )
                        }
                        innerTextField()
                    }
                }
            )
            
            // Save button
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        if (presetName.isNotBlank()) Color(0xFF4CAF50) 
                        else Color(0xFF2A3E2A)
                    )
                    .clickable(enabled = presetName.isNotBlank()) {
                        onSavePreset(presetName.trim())
                        presetName = ""
                    }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "SAVE",
                    color = if (presetName.isNotBlank()) Color.White else Color.White.copy(alpha = 0.4f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        
        Spacer(modifier = Modifier.height(4.dp))
        
        // Load section
        Text(
            text = "LOAD PRESET",
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 10.sp
        )
        
        if (presetNames.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFF2A3E2A)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No presets saved yet",
                    color = Color.White.copy(alpha = 0.4f),
                    fontSize = 11.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 200.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFF2A3E2A)),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                contentPadding = PaddingValues(4.dp)
            ) {
                items(presetNames) { name ->
                    PresetItem(
                        name = name,
                        showDeleteConfirm = showDeleteConfirm == name,
                        onLoad = { onLoadPreset(name) },
                        onShare = { onSharePreset(name) },
                        onDeleteClick = { showDeleteConfirm = name },
                        onDeleteConfirm = {
                            onDeletePreset(name)
                            showDeleteConfirm = null
                        },
                        onDeleteCancel = { showDeleteConfirm = null }
                    )
                }
            }
        }
    }
}

@Composable
private fun PresetItem(
    name: String,
    showDeleteConfirm: Boolean,
    onLoad: () -> Unit,
    onShare: () -> Unit,
    onDeleteClick: () -> Unit,
    onDeleteConfirm: () -> Unit,
    onDeleteCancel: () -> Unit
) {
    if (showDeleteConfirm) {
        // Delete confirmation view
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFF4A2A2A))
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Delete?",
                color = Color(0xFFFF6B6B),
                fontSize = 11.sp
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFFFF6B6B))
                        .clickable(onClick = onDeleteConfirm)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text("YES", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFF4A4A4A))
                        .clickable(onClick = onDeleteCancel)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text("NO", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    } else {
        // Normal preset item view
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFF3A4E3A))
                .clickable(onClick = onLoad)
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = name,
                color = Color.White,
                fontSize = 12.sp,
                modifier = Modifier.weight(1f)
            )
            
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                // Share button
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFF3A4A5A))
                        .clickable(onClick = onShare),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "↗",
                        color = Color(0xFF6BB6FF),
                        fontSize = 14.sp
                    )
                }
                
                // Delete button
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFF5A3A3A))
                        .clickable(onClick = onDeleteClick),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "✕",
                        color = Color(0xFFFF6B6B),
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}
