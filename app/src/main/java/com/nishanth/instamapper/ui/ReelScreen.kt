package com.nishanth.instamapper.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.nishanth.instamapper.MainViewModel
import com.nishanth.instamapper.PlaceAction
import com.nishanth.instamapper.data.Actions
import com.nishanth.instamapper.data.ClimateService
import com.nishanth.instamapper.data.Locales
import com.nishanth.instamapper.data.MONTHS
import com.nishanth.instamapper.data.Place
import com.nishanth.instamapper.data.ReelInfo
import com.nishanth.instamapper.data.SeasonReport
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Marker
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

private data class ReelAction(
    val emoji: String,
    val title: String,
    val subtitle: String,
    val colors: List<Color>,
    val onClick: () -> Unit,
)

@Composable
fun ReelScreen(vm: MainViewModel) {
    val reel = vm.reel ?: return
    val ctx = LocalContext.current
    BackHandler { vm.closeReel() }

    fun startDownload() {
        when {
            Actions.downloadVideo(ctx, reel) -> Actions.toast(ctx, "⬇️ Downloading to Downloads/InstaMapper…")
            !vm.loggedIn -> {
                Actions.toast(ctx, "Sign in to Instagram to enable downloads")
                vm.showLogin = true
            }
            else -> Actions.toast(ctx, "Instagram didn't provide a video file for this reel")
        }
    }

    val storagePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startDownload() else Actions.toast(ctx, "Storage permission is needed to save videos")
    }

    val actions = listOf(
        ReelAction("📍", "Add to map", "Save it or open in Google Maps", Palette.candy) { vm.requestPlace(PlaceAction.SAVE) },
        ReelAction("🌤️", "Best time to visit", "Best months & golden hour", Palette.gold) { vm.requestPlace(PlaceAction.SEASON) },
        ReelAction("⬇️", "Download video", if (reel.videoUrl != null) "Save to your phone" else "Needs Instagram sign-in", Palette.ocean) {
            val needsPermission = Build.VERSION.SDK_INT < 29 && reel.videoUrl != null &&
                ContextCompat.checkSelfPermission(ctx, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
            if (needsPermission) storagePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE) else startDownload()
        },
        ReelAction("🎵", "Original audio", reel.audio?.title ?: "Find the song", Palette.grape) { vm.showAudio = true },
        ReelAction("🧭", "Directions", "Navigate there", Palette.mint) { vm.requestPlace(PlaceAction.DIRECTIONS) },
        ReelAction("🏨", "Stays & food", "Hotels and restaurants nearby", Palette.sunset) { vm.requestPlace(PlaceAction.NEARBY) },
        ReelAction("📤", "Share spot", "Send the place to friends", Palette.sky) { vm.requestPlace(PlaceAction.SHARE) },
        ReelAction("🌐", "Translate", "Caption in your language", Palette.berry) {
            reel.caption?.let { Actions.translate(ctx, it) } ?: Actions.toast(ctx, "This reel has no caption")
        },
        ReelAction("📋", "Copy caption", "With all hashtags", Palette.lime) {
            reel.caption?.let { Actions.copy(ctx, "Caption", it) } ?: Actions.toast(ctx, "This reel has no caption")
        },
        ReelAction("▶️", "Open in Instagram", "Watch the reel", Palette.insta) { Actions.openUrl(ctx, reel.url) },
    )

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { ReelHero(reel, onBack = { vm.closeReel() }, onShare = { Actions.share(ctx, reel.url) }) }

        item {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Quick info pills
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    reel.location?.let {
                        Pill("📍 ${it.name}", Palette.chip[0], Modifier.weight(1f, fill = false)) { vm.requestPlace(PlaceAction.SAVE) }
                    }
                    reel.audio?.let { a ->
                        Pill("🎵 ${a.title ?: "Audio"}", Palette.chip[2], Modifier.weight(1f, fill = false)) { vm.showAudio = true }
                    }
                }
                reel.caption?.let { CaptionCard(it) }
                if (!vm.loggedIn && (reel.audio == null || reel.videoUrl == null || reel.location == null)) {
                    TintCard(Palette.insta) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("🔓", fontSize = 24.sp)
                            Spacer(Modifier.width(10.dp))
                            Text(
                                "Sign in to Instagram to unlock the tagged location, audio and download.",
                                Modifier.weight(1f),
                                style = MaterialTheme.typography.bodySmall,
                            )
                            TextButton(onClick = { vm.showLogin = true }) { Text("Sign in", fontWeight = FontWeight.Bold) }
                        }
                    }
                }
            }
        }

        if (reel.hashtags.isNotEmpty()) {
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    itemsIndexed(reel.hashtags) { i, tag ->
                        Pill("#$tag", Palette.chip[i % Palette.chip.size]) {
                            Actions.openUrl(ctx, "https://www.instagram.com/explore/tags/${Uri.encode(tag)}/")
                        }
                    }
                }
            }
        }

        item { SectionTitle("What do you want to do?", "🎯", Modifier.padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 10.dp)) }

        itemsIndexed(actions.chunked(2)) { rowIndex, row ->
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                row.forEachIndexed { col, a ->
                    Staggered(rowIndex * 2 + col, Modifier.weight(1f)) {
                        GradientTile(a.emoji, a.title, a.subtitle, a.colors, onClick = a.onClick)
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }

    vm.placeSheet?.let { action -> PlaceSheet(vm, action, reel) { vm.placeSheet = null } }
    if (vm.showSeason) SeasonSheet(vm) { vm.showSeason = false }
    if (vm.showAudio) AudioSheet(vm, reel) { vm.showAudio = false }
}

@Composable
private fun ReelHero(reel: ReelInfo, onBack: () -> Unit, onShare: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(320.dp)
            .clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
            .background(animatedBrush(InstaColors)),
    ) {
        AsyncImage(
            model = reel.thumbnailUrl,
            contentDescription = "Reel thumbnail",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = 0.45f),
                    0.35f to Color.Transparent,
                    0.6f to Color.Transparent,
                    1f to Color.Black.copy(alpha = 0.8f),
                )
            )
        )
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            HeroButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White) }
            HeroButton(onClick = onShare) { Icon(Icons.Filled.Share, "Share reel", tint = Color.White) }
        }
        Column(Modifier.align(Alignment.BottomStart).padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "@${reel.username ?: "instagram"}",
                color = Color.White,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
            )
            reel.fullName?.let { Text(it, color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodyMedium) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                reel.views?.let { GlassPill("▶ ${compact(it)}") }
                reel.likes?.let { GlassPill("♥ ${compact(it)}") }
                reel.comments?.let { GlassPill("💬 ${compact(it)}") }
                reel.takenAt?.let { GlassPill(SimpleDateFormat("d MMM yy", Locale.getDefault()).format(Date(it * 1000))) }
            }
        }
    }
}

@Composable
private fun HeroButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier.size(42.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.35f)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun GlassPill(text: String) {
    Box(
        Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.22f)).padding(horizontal = 10.dp, vertical = 5.dp)
    ) { Text(text, color = Color.White, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold) }
}

@Composable
private fun CaptionCard(caption: String) {
    var expanded by remember { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable { expanded = !expanded }
            .animateContentSize()
            .padding(16.dp),
    ) {
        Text(
            caption,
            maxLines = if (expanded) Int.MAX_VALUE else 3,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium,
        )
        if (caption.length > 120 || caption.lines().size > 3) {
            Text(
                if (expanded) "Show less" else "Read more",
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(top = 6.dp),
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
            Text(action.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                "Which place is this reel about? Tap to pick, or search.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (vm.placesLoading) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("Finding the spot…", color = MaterialTheme.colorScheme.primary)
                }
                LinearProgressIndicator(Modifier.fillMaxWidth().clip(RoundedCornerShape(50)))
            }

            vm.selectedPlace?.let { PlacePreviewMap(it) }

            vm.places.orEmpty().forEachIndexed { i, p ->
                val selected = vm.selectedPlace?.key == p.key
                val accent = Palette.chip[i % Palette.chip.size]
                Surface(
                    onClick = { vm.selectedPlace = p },
                    shape = RoundedCornerShape(16.dp),
                    color = if (selected) accent.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = if (selected) Modifier.border(2.dp, accent, RoundedCornerShape(16.dp)) else Modifier,
                ) {
                    Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = selected, onClick = { vm.selectedPlace = p })
                        Column(Modifier.weight(1f)) {
                            Text(p.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            p.address?.let { Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                            Text(p.source, style = MaterialTheme.typography.labelSmall, color = accent, fontWeight = FontWeight.SemiBold)
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
                shape = RoundedCornerShape(16.dp),
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
                    GradientButton("📌 Save to My Spots", full, Palette.candy, enabled = p != null) {
                        p?.let { vm.saveSpot(it); onDismiss() }
                    }
                    OutlinedButton(onClick = { p?.let { Actions.openInMaps(ctx, it) } }, enabled = p != null, modifier = full) {
                        Text("Open in Google Maps")
                    }
                    Text(
                        "Tip: in Google Maps tap Save to add it to a list. You can also export all My Spots as KML for Google My Maps.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                PlaceAction.SEASON -> GradientButton("🌤️ Show the best time to visit", full, Palette.gold, enabled = p != null) {
                    p?.let { vm.openSeason(it) }
                }
                PlaceAction.DIRECTIONS -> GradientButton("🧭 Start directions", full, Palette.mint, enabled = p != null) {
                    p?.let { Actions.directions(ctx, it) }
                }
                PlaceAction.NEARBY -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    GradientButton("🏨 Stays", Modifier.weight(1f), Palette.sunset, enabled = p != null) {
                        p?.let { Actions.nearby(ctx, it, "hotels") }
                    }
                    GradientButton("🍽️ Food", Modifier.weight(1f), Palette.candy, enabled = p != null) {
                        p?.let { Actions.nearby(ctx, it, "restaurants") }
                    }
                }
                PlaceAction.SHARE -> GradientButton("📤 Share this spot", full, Palette.sky, enabled = p != null) {
                    p?.let { Actions.share(ctx, "📍 ${it.name}\n${Actions.mapsUrl(it)}\n\nSpotted in this reel: ${reel.url}") }
                }
            }
        }
    }
}

/** Small, non-interactive map so you can see you've picked the right spot. */
@Composable
private fun PlacePreviewMap(place: Place) {
    val ctx = LocalContext.current
    val map = remember { createMap(ctx) }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { map.onDetach() } }
    AndroidView(
        factory = {
            map.apply {
                setMultiTouchControls(false)
                isClickable = false
                setOnTouchListener { _, _ -> true }
            }
        },
        modifier = Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(18.dp)),
        update = { m ->
            m.overlays.removeAll { it is Marker }
            val gp = GeoPoint(place.lat, place.lng)
            m.overlays.add(Marker(m).apply {
                position = gp
                title = place.name
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            })
            m.controller.setZoom(12.5)
            m.controller.setCenter(gp)
            m.invalidate()
        },
    )
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
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(animatedBrush(Palette.gold + Palette.sunset)).padding(18.dp)
            ) {
                Column {
                    Text("🌤️ Best time to visit", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                    vm.selectedPlace?.let {
                        Text(it.name, color = Color.White.copy(alpha = 0.92f), style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
            val report = vm.season
            val err = vm.seasonError
            when {
                vm.seasonLoading -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text("Reading 5 years of weather history…")
                }
                err != null -> Text("😕 $err", color = MaterialTheme.colorScheme.error)
                report != null -> SeasonContent(report, vm.settings.aiEnabled)
            }
        }
    }
}

@Composable
private fun SeasonContent(r: SeasonReport, aiEnabled: Boolean) {
    var selected by remember(r) { mutableIntStateOf(r.best.first()) }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Go in", fontWeight = FontWeight.Bold)
        r.best.forEach { m -> SolidPill("⭐ ${MONTHS[m - 1]}", Palette.mint) }
    }
    Text(r.summary, style = MaterialTheme.typography.bodyMedium)

    Text("Tap a month", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    MonthChart(r, selected) { selected = it }

    val m = r.months[selected - 1]
    TintCard(listOf(scoreColor(m.score), scoreColor(m.score))) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("${m.name} · score ${m.score}/100", fontWeight = FontWeight.Bold, color = scoreColor(m.score))
            Text(
                "🌡️ ${Locales.temp(m.avgHigh)} / ${Locales.temp(m.avgLow)}   🌧️ ${m.rainyDays.roundToInt()} rainy days · ${Locales.rain(m.rainMm)}",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }

    if (r.sunrise != null && r.sunset != null) {
        TintCard(Palette.sunset) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("🌅 Best time of day", fontWeight = FontWeight.Bold)
                Text(
                    "Golden hour today: ${r.sunrise}–${ClimateService.shift(r.sunrise, 60)} and " +
                        "${ClimateService.shift(r.sunset, -60)}–${r.sunset} (local time there). " +
                        "Arrive early on a weekday for soft light and fewer people.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }

    r.aiAdvice?.let { advice ->
        TintCard(Palette.grape) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("✨ Local tips", fontWeight = FontWeight.Bold)
                Text(advice, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
    r.aiError?.let { Text("AI tips unavailable: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
    if (!aiEnabled) {
        Text(
            "💡 Add a free Gemini key in Settings to also get festival, crowd and local tips.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Text(
        "Weather: Open-Meteo, 5-year averages. Score favours mild highs and few rainy days.",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Red → amber → green depending on how good the month is. */
private fun scoreColor(score: Int): Color {
    val bad = Color(0xFFE53935)
    val mid = Color(0xFFFFA000)
    val good = Color(0xFF00A86B)
    val t = score / 100f
    return if (t < 0.55f) lerp(bad, mid, (t / 0.55f).coerceIn(0f, 1f)) else lerp(mid, good, ((t - 0.55f) / 0.45f).coerceIn(0f, 1f))
}

@Composable
private fun MonthChart(r: SeasonReport, selected: Int, onSelect: (Int) -> Unit) {
    var started by remember(r) { mutableStateOf(false) }
    LaunchedEffect(r) { started = true }
    Row(
        Modifier.fillMaxWidth().height(170.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        r.months.forEachIndexed { i, m ->
            val frac by animateFloatAsState(
                if (started) (m.score / 100f).coerceIn(0.06f, 1f) else 0f,
                tween(durationMillis = 700, delayMillis = i * 45),
                label = "bar",
            )
            val isSel = m.month == selected
            val c = scoreColor(m.score)
            Column(
                Modifier.weight(1f).fillMaxHeight().clickable { onSelect(m.month) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
            ) {
                if (m.month in r.best) Text("⭐", fontSize = 10.sp)
                Text(Locales.tempShort(m.avgHigh), fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height((112f * frac).dp)
                        .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                        .background(Brush.verticalGradient(listOf(c, c.copy(alpha = 0.55f))))
                        .then(if (isSel) Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)) else Modifier)
                )
                Text(
                    m.name.take(1),
                    fontSize = 12.sp,
                    fontWeight = if (isSel) FontWeight.ExtraBold else FontWeight.Normal,
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
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val a = reel.audio
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(animatedBrush(Palette.grape + Palette.candy)).padding(18.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(58.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.25f)),
                        contentAlignment = Alignment.Center,
                    ) { Text("🎵", fontSize = 28.sp) }
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(
                            when {
                                a == null -> "Audio"
                                a.isOriginal -> "Original audio"
                                else -> "Song"
                            },
                            color = Color.White.copy(alpha = 0.85f),
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Text(
                            a?.title ?: "Not shared by Instagram",
                            color = Color.White,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                        )
                        a?.artist?.let {
                            Text(if (a.isOriginal) "by @$it" else it, color = Color.White.copy(alpha = 0.9f))
                        }
                    }
                }
            }

            val full = Modifier.fillMaxWidth()
            if (a != null) {
                if (a.isOriginal) {
                    Text(
                        "This reel uses the creator's own audio. The audio page lists every reel using it — the oldest one is the original.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                a.audioId?.let { id ->
                    GradientButton("Open audio page on Instagram", full, Palette.insta) {
                        Actions.openUrl(ctx, "https://www.instagram.com/reels/audio/$id/")
                    }
                }
                val q = a.searchQuery
                if (q.isNotBlank() && !a.isOriginal) {
                    BrandButton("Search on Spotify", Palette.spotify) { Actions.openUrl(ctx, "https://open.spotify.com/search/${Uri.encode(q)}") }
                    BrandButton("Search on YouTube Music", Palette.youtube) { Actions.openUrl(ctx, "https://music.youtube.com/search?q=${Uri.encode(q)}") }
                    BrandButton("Search on YouTube", Color(0xFFCC0000)) { Actions.openUrl(ctx, "https://www.youtube.com/results?search_query=${Uri.encode(q)}") }
                }
            } else {
                Text(
                    "Instagram didn't share the audio details for this reel." +
                        if (!vm.loggedIn) " Sign in to Instagram, then open the reel again." else "",
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (!vm.loggedIn) {
                    GradientButton("Sign in to Instagram", full, Palette.insta) { onDismiss(); vm.showLogin = true }
                }
            }
            OutlinedButton(
                onClick = { Actions.openApp(ctx, "com.shazam.android", "https://play.google.com/store/apps/details?id=com.shazam.android") },
                modifier = full,
            ) { Text("🔍 Identify with Shazam while the reel plays") }
        }
    }
}

@Composable
private fun BrandButton(text: String, color: Color, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(50.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = Color.White),
    ) { Text(text, fontWeight = FontWeight.Bold) }
}

private fun compact(n: Long): String = when {
    n >= 1_000_000 -> String.format(Locale.US, "%.1fM", n / 1_000_000.0)
    n >= 10_000 -> String.format(Locale.US, "%.0fK", n / 1_000.0)
    n >= 1_000 -> String.format(Locale.US, "%.1fK", n / 1_000.0)
    else -> n.toString()
}
