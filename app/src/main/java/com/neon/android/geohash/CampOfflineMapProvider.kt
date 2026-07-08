package com.neon.android.geohash

import android.content.Context
import android.util.Log
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import java.io.File

/**
 * Offline-capable terrain basemap (OpenTopoMap) around the camp anchor.
 * Tiles are cached to app storage on first online use, then work without internet.
 */
object CampOfflineMapProvider {

    private const val TAG = "CampOfflineMap"

    /** OpenTopoMap — relief, trails, forests; good for camp orientation. */
    val topoTileSource = XYTileSource(
        "OpenTopoMap",
        10, 18, 256, ".png",
        arrayOf("https://tile.opentopomap.org/")
    )

    fun configure(context: Context) {
        val ctx = context.applicationContext
        Configuration.getInstance().userAgentValue = ctx.packageName
        Configuration.getInstance().osmdroidBasePath = File(ctx.filesDir, "osmdroid")
        Configuration.getInstance().osmdroidTileCache = File(ctx.cacheDir, "osmdroid_tiles")
        Configuration.getInstance().expirationOverrideDuration = 1000L * 60 * 60 * 24 * 30
    }

    fun setupMapView(mapView: MapView) {
        mapView.setTileSource(topoTileSource)
        mapView.setMultiTouchControls(true)
        mapView.isTilesScaledToDpi = true
        mapView.setBuiltInZoomControls(false)
    }

    fun campBoundingBox(anchor: Pair<Double, Double>, deltaDeg: Double = 0.012): BoundingBox {
        val (lat, lon) = anchor
        return BoundingBox(
            lat + deltaDeg,
            lon + deltaDeg,
            lat - deltaDeg,
            lon - deltaDeg
        )
    }

    fun centerMap(mapView: MapView, anchor: Pair<Double, Double>, zoom: Double = 15.0) {
        mapView.controller.setZoom(zoom)
        mapView.controller.setCenter(GeoPoint(anchor.first, anchor.second))
    }

    fun fitUsers(mapView: MapView, anchor: Pair<Double, Double>?, points: List<Pair<Double, Double>>) {
        val all = buildList {
            anchor?.let { add(it) }
            addAll(points)
        }
        if (all.isEmpty()) return
        if (all.size == 1) {
            centerMap(mapView, all.first(), 16.0)
            return
        }
        val lats = all.map { it.first }
        val lons = all.map { it.second }
        val pad = 0.0015
        val box = BoundingBox(
            lats.max() + pad,
            lons.max() + pad,
            lats.min() - pad,
            lons.min() - pad
        )
        mapView.zoomToBoundingBox(box, true)
    }

    fun prefetchCampTiles(context: Context, anchor: Pair<Double, Double>) {
        configure(context)
        Log.d(TAG, "Map configured for camp at ${anchor.first}, ${anchor.second} — tiles cache on first online view")
    }
}
