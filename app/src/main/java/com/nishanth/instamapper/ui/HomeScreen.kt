package com.nishanth.instamapper.ui

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.nishanth.instamapper.MainViewModel
import com.nishanth.instamapper.data.Actions
import com.nishanth.instamapper.data.RecentReel
import kotlinx.coroutines.delay

private val loadingLines = listOf(
    "Opening the reel…", "Reading the caption…", "Looking for the location…",
    "Checking the audio…", "Almost there…",
)

private data class Feature(val emoji: String, val title: String, val text: String, val colors: List<Color>)

private val features = listOf(
    Feature("📍", "Map it", "Pin the spot or open it in Google Maps", Palette.candy),
    Feature("🌤️", "Best time", "Best months and golden hour", Palette.gold),
    Feature("⬇️", "Save video", "Download the reel to your phone", Palette.ocean),
    Feature("🎵", "Find the song", "Original audio, Spotify, YouTube", Palette.grape),
    Feature("🧭", "Go there", "Directions, stays and food nearby", Palette.mint),
    Feature("🌐", "Translate", "Captions in your language", Palette.sky),
)

@Composable
fun HomeScreen(vm: MainViewModel) {
    val ctx = LocalContext.current
    val focus = LocalFocusManager.current

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        // ---- Hero
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(bottomStart = 36.dp, bottomEnd = 36.dp))
                .background(animatedBrush(InstaColors))
                .statusBarsPadding()
                .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 64.dp),
        ) {
            Text(
                "🗺️",
                fontSize = 90.sp,
                modifier = Modifier.align(Alignment.TopEnd).offset(x = 18.dp, y = floatOffset(8.dp, 2200)),
                color = Color.White.copy(alpha = 0.5f),
            )
            Column {
                Text(
                    "📍",
                    fontSize = 36.sp,
                    modifier = Modifier.offset(y = floatOffset(5.dp)),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Insta Mapper",
                    style = MaterialTheme.typography.headlineLarge,
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Turn travel reels into real trips.",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White.copy(alpha = 0.92f),
                )
            }
        }

        // ---- Link card (overlaps the hero)
        Card(
            modifier = Modifier.padding(horizontal = 18.dp).offset(y = (-40).dp),
            shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
            elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                OutlinedTextField(
                    value = vm.input,
                    onValueChange = { vm.input = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Paste a reel link") },
                    placeholder = { Text("instagram.com/reel/…") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    leadingIcon = { Text("🔗", fontSize = 18.sp) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { focus.clearFocus(); vm.analyze() }),
                    trailingIcon = {
                        TextButton(onClick = {
                            val clip = readClipboard(ctx)
                            if (clip.isNullOrBlank()) Actions.toast(ctx, "Clipboard is empty — copy a reel link first")
                            else vm.input = clip
                        }) { Text("Paste", fontWeight = FontWeight.Bold) }
                    },
                )
                GradientButton(
                    text = if (vm.loading) "Reading reel…" else "✨ Explore this reel",
                    loading = vm.loading,
                    modifier = Modifier.fillMaxWidth(),
                ) { focus.clearFocus(); vm.analyze() }

                AnimatedVisibility(vm.loading, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                    LoadingTicker()
                }
                AnimatedVisibility(vm.error != null, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                    Box(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.errorContainer).padding(14.dp)
                    ) {
                        Text("😕 ${vm.error.orEmpty()}", color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

        Column(
            Modifier.offset(y = (-24).dp).padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            vm.update?.let { u ->
                TintCard(Palette.mint) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🚀", fontSize = 28.sp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Update available (v${u.version})", fontWeight = FontWeight.Bold)
                            Text("Get the newest features and fixes.", style = MaterialTheme.typography.bodySmall)
                        }
                        TextButton(onClick = {
                            Actions.openUrl(ctx, u.apkUrl ?: com.nishanth.instamapper.BuildConfig.RELEASES_URL)
                        }) { Text("Download", fontWeight = FontWeight.Bold) }
                    }
                }
            }

            if (!vm.loggedIn) {
                TintCard(Palette.insta) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🔓", fontSize = 28.sp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Unlock location, audio & video", fontWeight = FontWeight.Bold)
                            Text(
                                "Signed out, Instagram mostly shares just the caption.",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        TextButton(onClick = { vm.showLogin = true }) { Text("Sign in", fontWeight = FontWeight.Bold) }
                    }
                }
            }

            if (vm.recents.isNotEmpty()) {
                SectionTitle("Recent reels", "🕘")
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(end = 8.dp),
                ) {
                    items(vm.recents, key = { it.url }) { r ->
                        RecentCard(r, onOpen = { vm.analyze(r.url) }, onRemove = { vm.removeRecent(r) })
                    }
                }
            }

            SectionTitle("What you can do", "🎯")
            features.chunked(2).forEachIndexed { row, pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    pair.forEachIndexed { col, f ->
                        Staggered(row * 2 + col, Modifier.weight(1f)) {
                            GradientTile(f.emoji, f.title, f.text, f.colors) {
                                Actions.toast(ctx, "Paste or share a reel first, then pick “${f.title}”")
                            }
                        }
                    }
                }
            }

            TintCard(Palette.sky) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("💡 Fastest way", fontWeight = FontWeight.Bold)
                    Text(
                        "In Instagram, tap ➤ Share on a reel → choose Insta Mapper. It opens here with everything ready.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun LoadingTicker() {
    var i by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1300)
            i = (i + 1) % loadingLines.size
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LinearProgressIndicator(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(50)),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.primaryContainer,
        )
        AnimatedContent(targetState = loadingLines[i], transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "ticker") {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun RecentCard(r: RecentReel, onOpen: () -> Unit, onRemove: () -> Unit) {
    Box(
        Modifier
            .size(width = 140.dp, height = 190.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(Palette.berry))
            .clickable(onClick = onOpen),
    ) {
        AsyncImage(
            model = r.thumbnailUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.78f)))
            )
        )
        Box(
            Modifier.align(Alignment.TopEnd).padding(6.dp).size(26.dp).clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.4f)).clickable(onClick = onRemove),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Filled.Close, contentDescription = "Remove", tint = Color.White, modifier = Modifier.size(16.dp)) }
        Column(Modifier.align(Alignment.BottomStart).padding(10.dp)) {
            r.place?.let {
                Text("📍 $it", color = Color.White, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text("@${r.username ?: "reel"}", color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.labelSmall, maxLines = 1)
            r.caption?.let {
                Text(it, color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

private fun readClipboard(ctx: Context): String? {
    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    return cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(ctx)?.toString()
}
