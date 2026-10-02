package com.sentinel.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sentinel.ui.theme.BgBorder
import com.sentinel.ui.theme.BgCard
import com.sentinel.ui.theme.CyanPrimary
import com.sentinel.ui.theme.TextPrimary
import com.sentinel.ui.theme.TextSecondary

/**
 * Plain-English explanation shown when a user taps a [HelpIcon].
 * Keep every field short and simple — see the project help-writing rule (any reader, any country, first read).
 */
data class HelpContent(
    val title: String,
    val whatItIs: String,
    val whatItDoes: String,
    val why: String,
    val benefit: String,
    val bestPractices: List<String> = emptyList()
)

@Composable
private fun HelpBody(content: HelpContent) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(content.whatItIs, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        Text(content.whatItDoes, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        Text(content.why, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        Text(content.benefit, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        if (content.bestPractices.isNotEmpty()) {
            HorizontalDivider(color = BgBorder, thickness = 0.5.dp)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                content.bestPractices.forEach { tip ->
                    Text("• $tip", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                }
            }
        }
    }
}

@Composable
fun HelpDialog(content: HelpContent, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BgCard,
        title = {
            Text(
                text = content.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        },
        text = { HelpBody(content) },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = CyanPrimary)
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpBottomSheet(content: HelpContent, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = BgCard,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(BgBorder)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = content.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            HelpBody(content)
        }
    }
}
