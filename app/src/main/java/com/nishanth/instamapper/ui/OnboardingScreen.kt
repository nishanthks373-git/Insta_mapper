package com.nishanth.instamapper.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.MaterialTheme
import kotlinx.coroutines.launch

private data class IntroPage(val emoji: String, val title: String, val body: String, val colors: List<Color>)

private val pages = listOf(
    IntroPage(
        "📍", "Every travel reel, mapped",
        "Seen a stunning place on Instagram? Share the reel to Insta Mapper and find out where it is.",
        listOf(Color(0xFF833AB4), Color(0xFFE1306C), Color(0xFFF77737)),
    ),
    IntroPage(
        "📤", "Share → Insta Mapper",
        "In Instagram, tap the share arrow on any reel, then choose Insta Mapper. Or copy the link and paste it here.",
        listOf(Color(0xFF0072FF), Color(0xFF00C6FF), Color(0xFF38EF7D)),
    ),
    IntroPage(
        "✨", "Plan the trip in one tap",
        "Pin spots to your own map, see the best months and golden hour, get directions, find the song and save the video.",
        listOf(Color(0xFFF7971E), Color(0xFFFF5F6D), Color(0xFFC33764)),
    ),
    IntroPage(
        "🔐", "Optional: sign in to Instagram",
        "Instagram shares much more (exact location, audio, video) when you're signed in. It happens on Instagram's own page and stays on your phone. You can do it later in Settings.",
        listOf(Color(0xFF11998E), Color(0xFF0083B0), Color(0xFF4A00E0)),
    ),
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(onFinish: () -> Unit, onSignIn: () -> Unit) {
    val pager = rememberPagerState { pages.size }
    val scope = rememberCoroutineScope()
    val page = pages[pager.currentPage]

    Box(Modifier.fillMaxSize().background(animatedBrush(page.colors, 5000))) {
        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { i ->
            val p = pages[i]
            Column(
                Modifier.fillMaxSize().statusBarsPadding().padding(32.dp).padding(bottom = 120.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier
                        .offset(y = floatOffset(10.dp, 1600))
                        .size(150.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.22f)),
                    contentAlignment = Alignment.Center,
                ) { Text(p.emoji, fontSize = 76.sp) }
                Spacer(Modifier.height(40.dp))
                Text(
                    p.title,
                    color = Color.White,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    p.body,
                    color = Color.White.copy(alpha = 0.92f),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                )
            }
        }

        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pages.indices.forEach { i ->
                    val selected = i == pager.currentPage
                    val w by animateDpAsState(if (selected) 26.dp else 8.dp, label = "dot")
                    val c by animateColorAsState(if (selected) Color.White else Color.White.copy(alpha = 0.45f), label = "dotc")
                    Box(Modifier.height(8.dp).width(w).clip(RoundedCornerShape(50)).background(c))
                }
            }
            val last = pager.currentPage == pages.lastIndex
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White)
                    .then(
                        Modifier.padding(0.dp)
                    ),
            ) {
                TextButton(
                    onClick = {
                        if (last) onFinish() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
                    },
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                ) {
                    Text(
                        if (last) "Start exploring" else "Next",
                        color = page.colors.first(),
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
            if (last) {
                TextButton(onClick = { onFinish(); onSignIn() }) {
                    Text("Sign in to Instagram now", color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            } else {
                TextButton(onClick = onFinish) { Text("Skip", color = Color.White.copy(alpha = 0.85f)) }
            }
        }
    }
}
