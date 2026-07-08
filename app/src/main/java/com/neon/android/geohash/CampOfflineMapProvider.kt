package com.neon.android.geohash

import android.content.Context
import android.util.Log
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import java.io.File

/**
 * Offline-capable city-style map (OpenStreetMap) around the camp anchor.
 * Tiles cache on first online view; pan/zoom via OSMDroid multi-touch.
 */
object CampOfflineMapProvider {

    private const val TAG = "CampOfflineMap"
    const val DEFAULT_ZOOM = 16.0
    const val MIN_ZOOM = 14.0
    const val MAX_ZOOM = 19.0

    fun configure(context: Context) {
        val ctx = context.applicationContext
        Configuration.getInstance().userAgentValue = ctx.packageName
        Configuration.getInstance().osmdroidBasePath = File(ctx.filesDir, "osmdroid")
        Configuration.getInstance().osmdroidTileCache = File(ctx.cacheDir, "osmdroid_tiles")
        Configuration.getInstance().expirationOverrideDuration = 1000L * 60 * 60 * 24 * 30
    }

    fun setupMapView(mapView: MapView) {
        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.setMultiTouchControls(true)
        mapView.isTilesScaledToDpi = true
        mapView.setBuiltInZoomControls(false)
        mapView.isHorizontalMapRepetitionEnabled = false
        mapView.isVerticalMapRepetitionEnabled = false
        mapView.minZoomLevel = MIN_ZOOM
        mapView.maxZoomLevel = MAX_ZOOM
        mapView.controller.setZoom(DEFAULT_ZOOM)
    }

    fun centerMap(mapView: MapView, anchor: Pair<Double, Double>, zoom: Double = DEFAULT_ZOOM) {
        mapView.controller.setZoom(zoom.coerceIn(MIN_ZOOM, MAX_ZOOM))
        mapView.controller.setCenter(GeoPoint(anchor.first, anchor.second))
    }

    fun zoomIn(mapView: MapView) {
        mapView.controller.zoomIn()
    }

    fun zoomOut(mapView: MapView) {
        mapView.controller.zoomOut()
    }

    fun fitCampArea(mapView: MapView, anchor: Pair<Double, Double>) {
        val (lat, lon) = anchor
        val delta = 0.004
        val box = BoundingBox(lat + delta, lon + delta, lat - delta, lon - delta)
        mapView.zoomToBoundingBox(box, false)
        val z = mapView.zoomLevelDouble
        if (z < MIN_ZOOM) mapView.controller.setZoom(DEFAULT_ZOOM)
    }

    fun prefetchCampTiles(context: Context, anchor: Pair<Double, Double>) {
        configure(context)
        Log.d(TAG, "OSM map ready for ${anchor.first}, ${anchor.second}")
    }
}
