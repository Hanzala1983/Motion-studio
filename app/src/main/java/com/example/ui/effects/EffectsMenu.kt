package com.example.ui.effects

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AppliedEffect

@Composable
fun EffectsMenu(
    layerId: String = "",
    appliedEffects: List<AppliedEffect> = emptyList(),
    onAddEffectClick: () -> Unit,
    onUpdateEffect: (AppliedEffect) -> Unit = {},
    onRemoveEffect: (String) -> Unit = {},
    onBack: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1B1D25))
    ) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .background(Color(0xFF262934))
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White,
                    modifier = Modifier
                        .size(22.dp)
                        .clickable(onClick = onBack)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text("Effects", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            }
            Text(
                if (appliedEffects.isEmpty()) "0 active" else "${appliedEffects.size} active",
                color = Color(0xFF90A4AE),
                fontSize = 12.sp
            )
        }

        // Applied effects list
        if (appliedEffects.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    text = "No effects applied.\nTap '+ Add Effect' to explore.",
                    color = Color(0xFF606D7B),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(appliedEffects, key = { it.id }) { effect ->
                    AppliedEffectCard(
                        effect = effect,
                        onUpdate = onUpdateEffect,
                        onRemove = { onRemoveEffect(effect.id) }
                    )
                }
            }
        }

        // Add Effect Button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .background(Color(0xFF262C3A), RoundedCornerShape(8.dp))
                    .border(1.dp, Color(0xFF3E4658), RoundedCornerShape(8.dp))
                    .clickable(onClick = onAddEffectClick),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Effect", tint = Color(0xFF2BC1E0), modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Add Effect", color = Color(0xFF2BC1E0), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
