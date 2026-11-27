package ir.namak.sms.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 20.dp,
    tintColor: Color = MaterialTheme.colorScheme.surface,
    alpha: Float = 0.78f,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)
    val borderBrush = Brush.verticalGradient(
        listOf(
            Color.White.copy(alpha = 0.45f),
            MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
        )
    )

    Column(
        modifier = modifier
            .clip(shape)
            .background(tintColor.copy(alpha = alpha))
            .border(width = 1.dp, brush = borderBrush, shape = shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(14.dp),
        content = content
    )
}

@Composable
fun ContactAvatar(
    name: String?,
    address: String,
    size: Dp = 46.dp
) {
    val initial = name?.trim()?.firstOrNull()?.toString()
        ?: address.trim().firstOrNull()?.toString()
        ?: "ن"

    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(
                Brush.linearGradient(
                    listOf(
                        MaterialTheme.colorScheme.primary,
                        MaterialTheme.colorScheme.secondary
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initial,
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
    }
}
