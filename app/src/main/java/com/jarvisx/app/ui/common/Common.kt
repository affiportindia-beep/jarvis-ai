package com.jarvisx.app.ui.common

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jarvisx.app.ui.theme.Jx

/** The JARVIS X core: concentric rings. Animates only while [active], to save battery. */
@Composable
fun CoreOrb(active: Boolean, size: Dp = 120.dp, modifier: Modifier = Modifier) {
    var p = 0.92f
    var sweep = 0f
    if (active) {
        // Only an active orb runs an animation; an idle one is drawn once.
        val transition = rememberInfiniteTransition(label = "orb")
        val pulse by transition.animateFloat(
            initialValue = 0.85f, targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Reverse), label = "pulse",
        )
        val angle by transition.animateFloat(
            initialValue = 0f, targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing)), label = "sweep",
        )
        p = pulse
        sweep = angle
    }
    Canvas(modifier.size(size)) {
        val c = Offset(this.size.width / 2, this.size.height / 2)
        val r = this.size.minDimension / 2
        drawCircle(Jx.AccentDim.copy(alpha = 0.25f), radius = r * p, center = c)
        drawCircle(Jx.Accent, radius = r * 0.92f * p, center = c, style = Stroke(width = 3.dp.toPx()))
        drawCircle(Jx.Violet, radius = r * 0.6f, center = c, style = Stroke(width = 2.dp.toPx()))
        if (active) {
            drawArc(
                Jx.Accent, startAngle = sweep, sweepAngle = 70f, useCenter = false,
                topLeft = Offset(c.x - r * 0.76f, c.y - r * 0.76f),
                size = androidx.compose.ui.geometry.Size(r * 1.52f, r * 1.52f),
                style = Stroke(width = 2.dp.toPx()),
            )
        }
        drawCircle(Jx.Accent, radius = r * 0.24f * p, center = c)
    }
}

@Composable
fun Card(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, Jx.Outline, RoundedCornerShape(14.dp)),
        color = Jx.Surface,
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(Modifier.padding(14.dp), content = content)
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = Jx.Text, modifier = Modifier.padding(bottom = 6.dp))
}

@Composable
fun KeyValue(key: String, value: String) {
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(color = Jx.TextDim)) { append("$key  ") }
            withStyle(SpanStyle(color = Jx.Text)) { append(value) }
        },
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(vertical = 2.dp),
    )
}

/** Tiny Markdown subset for model answers: **bold**, _italic_, `code`; hides stray <think> tags. */
fun markdownLite(raw: String): AnnotatedString {
    // Reasoning ("Think harder") is hidden; while it is still running, show a placeholder.
    val closed = raw.replace(Regex("(?s)<think>.*?</think>"), "")
    val text = (if (closed.contains("<think>")) closed.substringBefore("<think>") + "Thinking…" else closed)
        .replace("</think>", "").trimStart()
    return buildAnnotatedString {
        var i = 0
        while (i < text.length) {
            when {
                text.startsWith("**", i) -> {
                    val end = text.indexOf("**", i + 2)
                    if (end > i) { withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(text.substring(i + 2, end)) }; i = end + 2 }
                    else { append(text[i]); i++ }
                }
                text[i] == '`' -> {
                    val end = text.indexOf('`', i + 1)
                    if (end > i) { withStyle(SpanStyle(fontFamily = FontFamily.Monospace, color = Jx.Accent)) { append(text.substring(i + 1, end)) }; i = end + 1 }
                    else { append(text[i]); i++ }
                }
                text[i] == '_' && (i == 0 || text[i - 1].isWhitespace() || text[i - 1] == '\n') -> {
                    val end = text.indexOf('_', i + 1)
                    if (end > i + 1 && !text.substring(i + 1, end).contains('\n')) {
                        withStyle(SpanStyle(fontStyle = FontStyle.Italic, color = Jx.TextDim)) { append(text.substring(i + 1, end)) }; i = end + 1
                    } else { append(text[i]); i++ }
                }
                else -> { append(text[i]); i++ }
            }
        }
    }
}
