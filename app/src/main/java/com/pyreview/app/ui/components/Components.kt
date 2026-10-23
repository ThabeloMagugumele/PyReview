package com.pyreview.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pyreview.app.R
import com.pyreview.app.data.review.Severity
import com.pyreview.app.ui.theme.CodeTextStyle
import com.pyreview.app.ui.theme.LocalStatusColors

@Composable
fun CodeBlock(
    lines: List<String>,
    modifier: Modifier = Modifier,
    firstLineNumber: Int = 1,
    highlights: Map<Int, Color> = emptyMap(),
    markers: Map<Int, Color> = emptyMap(),
) {
    val gutterWidth = ((firstLineNumber + lines.size).toString().length * 9 + 12).dp
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(Modifier.padding(vertical = 8.dp)) {
            Column {
                lines.indices.forEach { i ->
                    val number = firstLineNumber + i
                    Row(Modifier.background(highlights[number] ?: Color.Transparent)) {
                        Box(Modifier.width(3.dp).size(3.dp, 20.dp).background(markers[number] ?: Color.Transparent))
                        Text(
                            text = number.toString(),
                            style = CodeTextStyle,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            textAlign = TextAlign.End,
                            modifier = Modifier.width(gutterWidth).padding(end = 10.dp),
                        )
                    }
                }
            }
            Column(Modifier.horizontalScroll(rememberScrollState())) {
                lines.indices.forEach { i ->
                    val number = firstLineNumber + i
                    Text(
                        text = lines[i].ifEmpty { " " },
                        style = CodeTextStyle,
                        softWrap = false,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .background(highlights[number] ?: Color.Transparent)
                            .widthIn(min = 600.dp)
                            .padding(end = 16.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun CodeRow(
    number: Int,
    text: String,
    modifier: Modifier = Modifier,
    background: Color = Color.Transparent,
    marker: Color = Color.Transparent,
) {
    Row(
        modifier
            .fillMaxWidth()
            .background(background)
            .padding(vertical = 1.dp),
    ) {
        Box(Modifier.width(3.dp).height(20.dp).background(marker))
        Text(
            text = number.toString(),
            style = CodeTextStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            textAlign = TextAlign.End,
            modifier = Modifier.width(40.dp).padding(end = 10.dp),
        )
        Text(text.ifEmpty { " " }, style = CodeTextStyle, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
    }
}

@Composable
fun SeverityDot(severity: Severity, modifier: Modifier = Modifier) {
    Box(modifier.size(8.dp).clip(CircleShape).background(LocalStatusColors.current.accent(severity)))
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(top = 16.dp, bottom = 8.dp),
    )
}

@Composable
fun OfflineBanner(message: String, modifier: Modifier = Modifier) {
    val colors = LocalStatusColors.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(colors.warningContainer)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(R.drawable.ic_cloud_off), contentDescription = null, tint = colors.warning, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
fun NoticeCard(
    text: String,
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.primary,
    container: Color = MaterialTheme.colorScheme.surfaceVariant,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(container)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.padding(top = 6.dp).size(8.dp).clip(CircleShape).background(accent))
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
fun OutlinedPanel(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp)),
    ) { content() }
}
