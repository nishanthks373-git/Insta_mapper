package com.nishanth.instamapper.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import com.nishanth.instamapper.MainViewModel
import com.nishanth.instamapper.Tab
import com.nishanth.instamapper.data.Actions
import com.nishanth.instamapper.data.Place
import com.nishanth.instamapper.data.Spot
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.io.File

@Composable
fun SpotsScreen(vm: MainViewModel) {
    val ctx = LocalContext.current
    var toDelete by remember { mutableStateOf<Spot?>(null) }
    val listState = rememberLazyListState()

    // When a spot is focused (e.g. just saved), scroll its card into view.
    LaunchedEffect(vm.focusedSpotId) {
        val i = vm.spots.indexOfFirst { it.id == vm.focusedSpotId }
        if (i >= 0) listState.animateScrollToItem(i)
    }

    Column(Modifier.fillMaxSize()) {
        // Header
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
                .background(animatedBrush(Palette.ocean + Palette.mint))
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 18.dp),
        ) {
            Text(
                "🧭",
                fontSize = 64.sp,
                color = Color.White.copy(alpha = 0.4f),
                modifier = Modifier.align(Alignment.CenterEnd).offset(y = floatOffset(5.dp, 2000)),
            )
            Column {
                Text("My Spots", color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
                Text(
                    when (vm.spots.size) {
                        0 -> "Your travel map starts here"
                        1 -> "1 place saved"
                        else -> "${vm.spots.size} places saved"
                    },
                    color = Color.White.copy(alpha = 0.9f),
                )
                if (vm.spots.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color.White.copy(alpha = 0.25f))
                            .clickable { Actions.shareFile(ctx, vm.exportKml(), "application/vnd.google-earth.kml+xml") }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text("📤 Export for Google My Maps", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }

        if (vm.spots.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("🗺️", fontSize = 72.sp, modifier = Modifier.offset(y = floatOffset(8.dp)))
                Spacer(Modifier.height(16.dp))
                Text("No spots yet", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Open a reel and tap “Add to map” to start building your own travel map.",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(20.dp))
                GradientButton("✨ Explore a reel", Modifier.fillMaxWidth()) { vm.tab = Tab.HOME }
            }
            return@Column
        }

        SpotsMap(
            spots = vm.spots,
            focusedId = vm.focusedSpotId,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .height(300.dp)
                .clip(RoundedCornerShape(22.dp)),
        )

        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            itemsIndexed(vm.spots, key = { _, s -> s.id }) { i, s ->
                val accent = Palette.chip[i % Palette.chip.size]
                val focused = s.id == vm.focusedSpotId
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(if (focused) accent.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceContainer)
                        .then(if (focused) Modifier.border(2.dp, accent, RoundedCornerShape(18.dp)) else Modifier)
                        .clickable { vm.focusedSpotId = s.id }
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.width(5.dp).height(52.dp).clip(RoundedCornerShape(50)).background(accent))
                    Spacer(Modifier.width(10.dp))
                    AsyncImage(
                        model = s.thumbnailUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(54.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Brush.linearGradient(listOf(accent, accent.copy(alpha = 0.5f)))),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(s.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        s.address?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        s.username?.let {
                            Text("from @$it", style = MaterialTheme.typography.labelSmall, color = accent, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    SmallRound("🗺️") { Actions.openInMaps(ctx, s.toPlace()) }
                    if (s.reelUrl.isNotBlank()) SmallRound("▶️") { Actions.openUrl(ctx, s.reelUrl) }
                    IconButton(onClick = { toDelete = s }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }

    toDelete?.let { s ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Remove spot?") },
            text = { Text("“${s.name}” will be removed from My Spots.") },
            confirmButton = { TextButton(onClick = { vm.deleteSpot(s); toDelete = null }) { Text("Remove") } },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SmallRound(emoji: String, onClick: () -> Unit) {
    Box(
        Modifier
            .padding(start = 4.dp)
            .size(38.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(emoji, fontSize = 16.sp) }
}

private fun Spot.toPlace() = Place(name, lat, lng, address, "Saved", exact = true, mapsQuery = mapsQuery)

@Composable
private fun SpotsMap(spots: List<Spot>, focusedId: String?, modifier: Modifier) {
    val ctx = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val mapView = remember { createMap(ctx) }
    val fitted = remember { mutableListOf<String>() }

    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> Unit
            }
        }
        lifecycle.addObserver(obs)
        onDispose {
            lifecycle.removeObserver(obs)
            mapView.onDetach()
        }
    }

    AndroidView(factory = { mapView }, modifier = modifier, update = { map ->
        map.overlays.removeAll { it is Marker }
        var focusedMarker: Marker? = null
        val points = spots.map { s ->
            val gp = GeoPoint(s.lat, s.lng)
            val marker = Marker(map).apply {
                position = gp
                title = s.name
                snippet = s.username?.let { "from @$it" }
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            }
            if (s.id == focusedId) focusedMarker = marker
            map.overlays.add(marker)
            gp
        }
        map.invalidate()
        val ids = spots.map { it.id }
        val fm = focusedMarker
        map.post {
            if (fm != null) {
                map.controller.animateTo(fm.position, 13.0, 900L)
                fm.showInfoWindow()
            } else if (ids != fitted) {
                if (points.size == 1) {
                    map.controller.setZoom(11.0)
                    map.controller.setCenter(points.first())
                } else if (points.size > 1) {
                    runCatching { map.zoomToBoundingBox(BoundingBox.fromGeoPoints(points).increaseByScale(1.3f), true) }
                }
            }
            fitted.clear()
            fitted.addAll(ids)
        }
    })
}

internal fun createMap(ctx: Context): MapView {
    Configuration.getInstance().apply {
        userAgentValue = ctx.packageName
        osmdroidBasePath = File(ctx.cacheDir, "osmdroid")
        osmdroidTileCache = File(ctx.cacheDir, "osmdroid/tiles")
    }
    return MapView(ctx).apply {
        setTileSource(TileSourceFactory.MAPNIK)
        setMultiTouchControls(true)
        minZoomLevel = 2.0
        controller.setZoom(3.0)
    }
}
