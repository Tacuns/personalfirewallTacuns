package com.sentinel.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sentinel.ui.theme.TextMuted

@Composable
fun HelpIcon(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String = "Help"
) {
    IconButton(
        onClick = onClick,
        modifier = modifier.size(24.dp)
    ) {
        Icon(
            imageVector = Icons.Default.HelpOutline,
            contentDescription = contentDescription,
            tint = TextMuted,
            modifier = Modifier.size(14.dp)
        )
    }
}
