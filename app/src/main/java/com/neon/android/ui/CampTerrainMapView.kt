package com.neon.android.ui

import android.view.ViewGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.ZoomOutMap
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.neon.android.geohash.CampOfflineMapProvider
import com.neon.android.geohash.UserLocationEntry
import com.neon.android.identity.UserRole
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

@Composable
fun CampTerrainMapView(
    users: List<UserLocationEntry>,
    campAnchor: Pair<Double, Double>?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var mapRef by remember { mutableStateOf<MapView?>(null) }
    var didInitialCenter by remember { mutableStateOf(false) }

    LaunchedEffect(campAnchor) {
        campAnchor?.let { CampOfflineMapProvider.prefetchCampTiles(context, it) }
    }

    val located = remember(users) {
        users.filter { it.latitude != 0.0 || it.longitude != 0.0 }
    }

    Box(modifier = modifier.clip(RoundedCornerShape(12.dp))) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                CampOfflineMapProvider.configure(ctx)
                MapView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    CampOfflineMapProvider.setupMapView(this)
                    campAnchor?.let { CampOfflineMapProvider.centerMap(this, it) }
                    mapRef = this
                    didInitialCenter = campAnchor != null
                }
            },
            update = { map ->
                mapRef = map
                if (!didInitialCenter && campAnchor != null) {
                    CampOfflineMapProvider.centerMap(map, campAnchor)
                    didInitialCenter = true
                }
                map.overlays.removeAll { it is Marker }
                located.forEach { entry ->
                    val marker = Marker(map).apply {
                        position = GeoPoint(entry.latitude, entry.longitude)
                        title = entry.fio
                        snippet = UserRole.fromString(entry.role).displayNameRu
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    }
                    map.overlays.add(marker)
                }
                map.invalidate()
            },
            onRelease = { map ->
                map.onDetach()
                mapRef = null
            }
        )

        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            SmallMapButton(Icons.Default.Add) { mapRef?.let { CampOfflineMapProvider.zoomIn(it) } }
            SmallMapButton(Icons.Default.Remove) { mapRef?.let { CampOfflineMapProvider.zoomOut(it) } }
            SmallMapButton(Icons.Default.ZoomOutMap) {
                mapRef?.let { map ->
                    campAnchor?.let { anchor -> CampOfflineMapProvider.fitCampArea(map, anchor) }
                }
            }
        }
    }
}

@Composable
private fun SmallMapButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    FilledIconButton(
        onClick = onClick,
        modifier = Modifier.size(36.dp),
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
        )
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
    }
}
