package com.nishanth.instamapper.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/** Shrinks slightly while pressed – makes tiles feel tappable. */
fun Modifier.pressScale(source: MutableInteractionSource): Modifier = composed {
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.94f else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "press",
    )
    graphicsLayer { scaleX = scale; scaleY = scale }
}

/** A gradient that slowly drifts, for headers. */
@Composable
fun animatedBrush(colors: List<Color>, durationMs: Int = 7000): Brush {
    val t = rememberInfiniteTransition(label = "gradient")
    val shift by t.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMs, easing = LinearEasing), RepeatMode.Reverse),
        label = "shift",
    )
    return Brush.linearGradient(
        colors,
        start = Offset(-400f + shift * 800f, 0f),
        end = Offset(600f + shift * 800f, 1000f),
    )
}

/** Gentle up/down float for decorative emoji. */
@Composable
fun floatOffset(amplitude: Dp = 6.dp, durationMs: Int = 1400): Dp {
    val t = rememberInfiniteTransition(label = "float")
    val v by t.animateFloat(
        initialValue = -1f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMs, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "floatV",
    )
    return amplitude * v
}

/** Fades and slides children in one after another. */
@Composable
fun Staggered(index: Int, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(45L * index)
        shown = true
    }
    AnimatedVisibility(
        visible = shown,
        modifier = modifier,
        enter = fadeIn(tween(380)) + slideInVertically(tween(380)) { it / 3 } + scaleIn(tween(380), initialScale = 0.92f),
    ) {
        content()
    }
}

@Composable
fun GradientTile(
    emoji: String,
    title: String,
    subtitle: String,
    colors: List<Color>,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val haptics = LocalHapticFeedback.current
    Box(
        modifier
            .pressScale(source)
            .clip(RoundedCornerShape(22.dp))
            .background(Brush.linearGradient(colors))
            .clickable(interactionSource = source, indication = LocalIndication.current) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .heightIn(min = 118.dp),
    ) {
        // Big faded emoji in the corner for depth
        Text(
            emoji,
            fontSize = 64.sp,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = 14.dp, y = 14.dp)
                .rotate(-12f)
                .alpha(0.22f),
        )
        Column(Modifier.padding(14.dp)) {
            Box(
                Modifier.size(42.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.25f)),
                contentAlignment = Alignment.Center,
            ) { Text(emoji, fontSize = 22.sp) }
            Spacer(Modifier.height(10.dp))
            Text(title, color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
            Text(
                subtitle,
                color = Color.White.copy(alpha = 0.88f),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun GradientButton(
    text: String,
    modifier: Modifier = Modifier,
    colors: List<Color> = InstaColors,
    loading: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    Box(
        modifier
            .pressScale(source)
            .clip(RoundedCornerShape(16.dp))
            .background(if (enabled) animatedBrush(colors, 3500) else Brush.linearGradient(listOf(Color.Gray, Color.Gray)))
            .clickable(interactionSource = source, indication = LocalIndication.current, enabled = enabled && !loading, onClick = onClick)
            .heightIn(min = 54.dp)
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (loading) {
                CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                Spacer(Modifier.width(12.dp))
            }
            Text(text, color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
fun Pill(text: String, color: Color, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    Box(
        modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.16f))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(text, color = color, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun SolidPill(text: String, colors: List<Color>, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(50))
            .background(Brush.horizontalGradient(colors))
            .padding(horizontal = 14.dp, vertical = 7.dp),
    ) {
        Text(text, color = Color.White, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun SectionTitle(text: String, emoji: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(emoji, fontSize = 20.sp)
        Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}

/** Card with a soft gradient tint and coloured left accent. */
@Composable
fun TintCard(colors: List<Color>, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(colors.map { it.copy(alpha = 0.16f) }))
    ) {
        Box(Modifier.padding(16.dp)) { content() }
    }
}
