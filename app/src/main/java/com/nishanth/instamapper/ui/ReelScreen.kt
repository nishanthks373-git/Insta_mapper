package com.nishanth.instamapper.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.nishanth.instamapper.MainViewModel
import com.nishanth.instamapper.PlaceAction
import com.nishanth.instamapper.data.Actions
import com.nishanth.instamapper.data.ClimateService
import com.nishanth.instamapper.data.MONTHS
import com.nishanth.instamapper.data.ReelInfo
import com.nishanth.instamapper.data.SeasonReport
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

private data class ReelAction(val emoji: String, val title: String, val subtitle: String, val onClick: () -> Unit)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReelScreen(vm: MainViewModel) {
    val reel = vm.reel ?: return
    val ctx = LocalContext.current
    BackHandler { vm.closeReel() }

    val actions = listOf(
        ReelAction("📍", "Add spot to map", "Save it or open in Google Maps") { vm.requestPlace(PlaceAction.SAVE) },
        ReelAction("🌤️", "Best time to visit", "Best months & golden hour") { vm.requestPlace(PlaceAction.SEASON) },
        ReelAction("⬇️", "Download video", if (reel.videoUrl != null) "Save to Downloads" else "Needs Instagram sign-in") {
            when {
                Actions.downloadVideo(ctx, reel) -> Actions.toast(ctx, "Downloading to Downloads/InstaMapper…")
                !vm.loggedIn -> {
                    Actions.toast(ctx, "Sign in to Instagram to enable downloads")
                    vm.showLogin = true
                }
                else -> Actions.toast(ctx, "Instagram didn't provide a video file for this reel")
            }
        },
        ReelAction("🎵", "Find original audio", reel.audio?.title ?: "Find the song") { vm.showAudio = true },
        ReelAction("🧭", "Directions", "Navigate with Google Maps") { vm.requestPlace(PlaceAction.DIRECTIONS) },
        ReelAction("🏨", "Stays & food nearby", "Hotels and restaurants") { vm.requestPlace(PlaceAction.NEARBY) },
        ReelAction("📤", "Share spot", "Send the place to friends") { vm.requestPlace(PlaceAction.SHARE) },
        ReelAction("🔤", "Translate caption", "Opens Google Translate") {
            reel.caption?.let { Actions.translate(ctx, it) } ?: Actions.toast(ctx, "This reel has no caption")
        },
        ReelAction("📋", "Copy caption", "Caption with hashtags") {
            reel.caption?.let { Actions.copy(ctx, "Caption", it) } ?: Actions.toast(ctx, "This reel has no caption")
        },
        ReelAction("▶️", "Open in Instagram", "Watch the reel") { Actions.openUrl(ctx, reel.url) },
    )

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Reel details") },
            navigationIcon = {
                IconButton(onClick = { vm.closeReel() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            },
        )
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { ReelHeader(reel) }
            if (!vm.loggedIn && (reel.audio == null || reel.videoUrl == null)) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                        Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Sign in to Instagram to unlock audio, tagged location and downloads.",
                                Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                            TextButton(onClick = { vm.showLogin = true }) { Text("Sign in") }
                        }
                    }
                }
            }
            item {
                Text(
                    "What do you want to do?",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            items(actions.chunked(2)) { row ->
                Row(
                    Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    row.forEach { ActionCard(it, Modifier.weight(1f).fillMaxHeight()) }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }

    vm.placeSheet?.let { action -> PlaceSheet(vm, action, reel) { vm.placeSheet = null } }
    if (vm.showSeason) SeasonSheet(vm) { vm.showSeason = false }
    if (vm.showAudio) AudioSheet(vm, reel) { vm.showAudio = false }
}

@Composable
private fun ReelHeader(reel: ReelInfo) {
    var expanded by remember { mutableStateOf(false) }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Row(Modifier.padding(12.dp)) {
            AsyncImage(
                model = reel.thumbnailUrl,
                contentDescription = "Reel thumbnail",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(width = 96.dp, height = 150.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("@${reel.username ?: "unknown"}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                reel.fullName?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                val stats = listOfNotNull(
                    reel.views?.let { "▶ ${compact(it)}" },
                    reel.likes?.let { "♥ ${compact(it)}" },
                    reel.comments?.let { "💬 ${compact(it)}" },
                ).joinToString("   ")
                if (stats.isNotEmpty()) Text(stats, style = MaterialTheme.typography.bodyMedium)
                reel.takenAt?.let {
                    Text(
                        SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(it * 1000)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                reel.location?.let {
                    Text("📍 ${it.name}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                }
                reel.audio?.let { a ->
                    Text(
                        "🎵 " + listOfNotNull(a.title, a.artist).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        reel.caption?.let { cap ->
            Text(
                cap,
                maxLines = if (expanded) Int.MAX_VALUE else 4,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
            )
        }
    }
}

@Composable
private fun ActionCard(a: ReelAction, modifier: Modifier) {
    Card(
        onClick = a.onClick,
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(a.emoji, fontSize = 26.sp)
            Spacer(Modifier.height(6.dp))
            Text(a.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                a.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ---- Place picker ---------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaceSheet(vm: MainViewModel, action: PlaceAction, reel: ReelInfo, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var query by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(action.title, style = MaterialTheme.typography.titleLarge)
            Text(
                "Which place is this reel about?",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (vm.placesLoading) LinearProgressIndicator(Modifier.fillMaxWidth())

            vm.places.orEmpty().forEach { p ->
                val selected = vm.selectedPlace?.key == p.key
                Surface(
                    onClick = { vm.selectedPlace = p },
                    shape = RoundedCornerShape(12.dp),
                    color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = selected, onClick = { vm.selectedPlace = p })
                        Column(Modifier.weight(1f)) {
                            Text(p.name, style = MaterialTheme.typography.titleSmall)
                            p.address?.let { Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                            Text(p.source, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
            vm.placeMessage?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Search a different place") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { vm.searchPlace(query) }),
                trailingIcon = {
                    IconButton(onClick = { vm.searchPlace(query) }) { Icon(Icons.Filled.Search, contentDescription = "Search") }
                },
            )

            val p = vm.selectedPlace
            val full = Modifier.fillMaxWidth()
            when (action) {
                PlaceAction.SAVE -> {
                    Button(onClick = { p?.let { vm.saveSpot(it); onDismiss() } }, enabled = p != null, modifier = full) {
                        Text("Save to My Spots")
                    }
                    OutlinedButton(onClick = { p?.let { Actions.openInMaps(ctx, it) } }, enabled = p != null, modifier = full) {
                        Text("Open in Google Maps")
                    }
                    Text(
                        "Tip: in Google Maps, tap Save to add it to one of your lists. You can also export all My Spots as a KML file for Google My Maps.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                PlaceAction.SEASON -> Button(onClick = { p?.let { vm.openSeason(it) } }, enabled = p != null, modifier = full) {
                    Text("Check the best time to visit")
                }
                PlaceAction.DIRECTIONS -> Button(onClick = { p?.let { Actions.directions(ctx, it) } }, enabled = p != null, modifier = full) {
                    Text("Start directions")
                }
                PlaceAction.NEARBY -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { p?.let { Actions.nearby(ctx, it, "hotels") } }, enabled = p != null, modifier = Modifier.weight(1f)) {
                        Text("🏨 Stays")
                    }
                    Button(onClick = { p?.let { Actions.nearby(ctx, it, "restaurants") } }, enabled = p != null, modifier = Modifier.weight(1f)) {
                        Text("🍽️ Food")
                    }
                }
                PlaceAction.SHARE -> Button(
                    onClick = {
                        p?.let {
                            Actions.share(ctx, "📍 ${it.name}\n${Actions.mapsUrl(it)}\n\nSpotted in this reel: ${reel.url}")
                        }
                    },
                    enabled = p != null,
                    modifier = full,
                ) { Text("Share this spot") }
            }
        }
    }
}

// ---- Season --------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SeasonSheet(vm: MainViewModel, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Best time to visit", style = MaterialTheme.typography.titleLarge)
            vm.selectedPlace?.let {
                Text(it.name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            }
            val report = vm.season
            val err = vm.seasonError
            when {
                vm.seasonLoading -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text("Reading 5 years of weather history…")
                }
                err != null -> Text(err, color = MaterialTheme.colorScheme.error)
                report != null -> SeasonContent(report, vm.settings.aiEnabled)
            }
        }
    }
}

@Composable
private fun SeasonContent(r: SeasonReport, aiEnabled: Boolean) {
    var selected by remember(r) { mutableIntStateOf(r.best.first()) }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        r.best.forEach { m ->
            Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.primaryContainer) {
                Text(
                    MONTHS[m - 1],
                    Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
    Text(r.summary, style = MaterialTheme.typography.bodyMedium)

    MonthChart(r, selected) { selected = it }
    val m = r.months[selected - 1]
    Text(
        "${m.name}: highs ~${m.avgHigh.roundToInt()}°C, lows ~${m.avgLow.roundToInt()}°C · " +
            "${m.rainyDays.roundToInt()} rainy days · ${m.rainMm.roundToInt()} mm rain · score ${m.score}/100",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    if (r.sunrise != null && r.sunset != null) {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("☀️ Best time of day", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSecondaryContainer)
                Text(
                    "Golden hour today: ${r.sunrise}–${ClimateService.shift(r.sunrise, 60)} and " +
                        "${ClimateService.shift(r.sunset, -60)}–${r.sunset} (local time). " +
                        "Go early in the morning on a weekday for soft light and fewer crowds.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
    }

    r.aiAdvice?.let { advice ->
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("✨ Local tips", fontWeight = FontWeight.SemiBold)
                Text(advice, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
    r.aiError?.let { Text("AI tips unavailable: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
    if (!aiEnabled) {
        Text(
            "Add a Gemini or Claude API key in Settings to also get festival, crowd and local tips.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Text(
        "Weather: Open-Meteo, 5-year daily averages. Score favours 20–28 °C highs and few rainy days.",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun MonthChart(r: SeasonReport, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(160.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        r.months.forEach { m ->
            val color = when (m.month) {
                in r.best -> MaterialTheme.colorScheme.primary
                in r.avoid -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.45f)
            }
            val isSel = m.month == selected
            Column(
                Modifier.weight(1f).fillMaxHeight().clickable { onSelect(m.month) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
            ) {
                Text("${m.avgHigh.roundToInt()}°", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height((110f * (m.score / 100f).coerceIn(0.05f, 1f)).dp)
                        .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                        .background(color)
                )
                Text(
                    m.name.take(1),
                    fontSize = 12.sp,
                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

// ---- Audio ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AudioSheet(vm: MainViewModel, reel: ReelInfo, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Find the original audio", style = MaterialTheme.typography.titleLarge)
            val a = reel.audio
            val full = Modifier.fillMaxWidth()
            if (a != null) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                    Column(Modifier.padding(14.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(if (a.isOriginal) "Original audio" else "Song", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        Text(a.title ?: "Unknown title", style = MaterialTheme.typography.titleMedium)
                        a.artist?.let { Text(if (a.isOriginal) "by @$it" else it, style = MaterialTheme.typography.bodyMedium) }
                    }
                }
                if (a.isOriginal) {
                    Text(
                        "This reel uses the creator's own audio. The audio page lists every reel using it — the oldest one is the original.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                a.audioId?.let { id ->
                    Button(onClick = { Actions.openUrl(ctx, "https://www.instagram.com/reels/audio/$id/") }, modifier = full) {
                        Text("Open audio page on Instagram")
                    }
                }
                val q = a.searchQuery
                if (q.isNotBlank() && !a.isOriginal) {
                    OutlinedButton(onClick = { Actions.openUrl(ctx, "https://open.spotify.com/search/${android.net.Uri.encode(q)}") }, modifier = full) {
                        Text("Search on Spotify")
                    }
                    OutlinedButton(onClick = { Actions.openUrl(ctx, "https://music.youtube.com/search?q=${android.net.Uri.encode(q)}") }, modifier = full) {
                        Text("Search on YouTube Music")
                    }
                    OutlinedButton(onClick = { Actions.openUrl(ctx, "https://www.youtube.com/results?search_query=${android.net.Uri.encode(q)}") }, modifier = full) {
                        Text("Search on YouTube")
                    }
                }
            } else {
                Text(
                    "Instagram didn't share the audio details for this reel." +
                        if (!vm.loggedIn) " Sign in to Instagram in Settings, then open the reel again." else "",
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (!vm.loggedIn) {
                    Button(onClick = { onDismiss(); vm.showLogin = true }, modifier = full) { Text("Sign in to Instagram") }
                }
            }
            OutlinedButton(
                onClick = { Actions.openApp(ctx, "com.shazam.android", "https://play.google.com/store/apps/details?id=com.shazam.android") },
                modifier = full,
            ) { Text("Identify with Shazam while the reel plays") }
        }
    }
}

private fun compact(n: Long): String = when {
    n >= 1_000_000 -> String.format(Locale.US, "%.1fM", n / 1_000_000.0)
    n >= 10_000 -> String.format(Locale.US, "%.0fK", n / 1_000.0)
    n >= 1_000 -> String.format(Locale.US, "%.1fK", n / 1_000.0)
    else -> n.toString()
}
