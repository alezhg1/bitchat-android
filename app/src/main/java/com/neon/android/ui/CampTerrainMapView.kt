package com.neon.android.ui

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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

    LaunchedEffect(campAnchor) {
        campAnchor?.let { CampOfflineMapProvider.prefetchCampTiles(context, it) }
    }

    val located = users.filter { it.latitude != 0.0 || it.longitude != 0.0 }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            CampOfflineMapProvider.configure(ctx)
            MapView(ctx).also { map ->
                CampOfflineMapProvider.setupMapView(map)
                campAnchor?.let { CampOfflineMapProvider.centerMap(map, it, 15.0) }
            }
        },
        update = { map ->
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
            val points = located.map { it.latitude to it.longitude }
            CampOfflineMapProvider.fitUsers(map, campAnchor, points)
            map.invalidate()
        },
        onRelease = { map -> map.onDetach() }
    )
}
