package com.neon.android.geohash

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.neon.android.identity.UserProfileManager
import com.neon.android.mesh.BluetoothMeshService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume

data class UserLocationEntry(
  val staticId: String,
  val fio: String,
  val latitude: Double,
  val longitude: Double,
  val role: String,
  val peerId: String?,
  val timestampMs: Long
)

/**
 * Broadcasts user geolocation every [SHARE_INTERVAL_MS] over BLE mesh (multi-hop relay)
 * and collects locations from peers. Admins/teachers render the org map.
 */
class LocationSharingService private constructor(private val context: Context) {

  companion object {
    private const val TAG = "LocationSharing"
    /** 5 minutes — frequent enough for camp map; still gentle on BLE battery. */
    const val SHARE_INTERVAL_MS = 5 * 60 * 1000L
    private const val INTERVAL_MS = SHARE_INTERVAL_MS
    const val GEOLOC_PREFIX = "[GEOLOC]:"
    private const val CACHE_FILE = "user_locations_cache.json"

    @Volatile
    private var INSTANCE: LocationSharingService? = null

    fun getInstance(context: Context): LocationSharingService {
      return INSTANCE ?: synchronized(this) {
        INSTANCE ?: LocationSharingService(context.applicationContext).also { INSTANCE = it }
      }
    }

    fun buildGeolocPayload(
      staticId: String,
      fio: String,
      lat: Double,
      lon: Double,
      role: String
    ): String = "$GEOLOC_PREFIX$staticId|$fio|$lat|$lon|$role|${System.currentTimeMillis()}"

    fun parseGeolocMessage(content: String, senderPeerId: String?): UserLocationEntry? {
      if (!content.startsWith(GEOLOC_PREFIX)) return null
      val parts = content.removePrefix(GEOLOC_PREFIX).split("|")
      if (parts.size < 5) return null
      return try {
        UserLocationEntry(
          staticId = parts[0],
          fio = parts[1],
          latitude = parts[2].toDouble(),
          longitude = parts[3].toDouble(),
          role = parts[4],
          peerId = senderPeerId,
          timestampMs = parts.getOrNull(5)?.toLongOrNull() ?: System.currentTimeMillis()
        )
      } catch (_: Exception) {
        null
      }
    }
  }

  private val gson = Gson()
  private val locationProvider = FusedLocationProvider(context)
  private val cacheFile = File(context.filesDir, CACHE_FILE)

  private val _userLocations = MutableStateFlow<Map<String, UserLocationEntry>>(emptyMap())
  val userLocations: StateFlow<Map<String, UserLocationEntry>> = _userLocations.asStateFlow()

  private var sharingJob: Job? = null
  private var ioScope: CoroutineScope? = null

  init {
    loadCachedLocations()
  }

  fun start(meshService: BluetoothMeshService, scope: CoroutineScope) {
    sharingJob?.cancel()
    ioScope = scope
    val profile = UserProfileManager.getInstance(context)
    profile.ensureStaticId(meshService.myPeerID)

    sharingJob = scope.launch {
      broadcastLocation(meshService)
      while (isActive) {
        delay(INTERVAL_MS)
        broadcastLocation(meshService)
      }
    }
  }

  fun stop() {
    sharingJob?.cancel()
    sharingJob = null
  }

  /** Request an immediate GEOLOC broadcast (e.g. admin map refresh). */
  fun requestImmediateBroadcast(meshService: BluetoothMeshService) {
    val scope = ioScope ?: return
    scope.launch { broadcastLocation(meshService) }
  }

  fun handleIncomingMessage(content: String, senderPeerId: String?): Boolean {
    if (!content.startsWith(GEOLOC_PREFIX)) return false
    val entry = parseGeolocMessage(content, senderPeerId) ?: return true
    mergeLocation(entry)
    return true
  }

  private fun mergeLocation(entry: UserLocationEntry) {
    val existing = _userLocations.value[entry.staticId]
    if (existing != null && existing.timestampMs > entry.timestampMs) return
    _userLocations.value = _userLocations.value + (entry.staticId to entry)
    persistLocations()
  }

  private suspend fun broadcastLocation(meshService: BluetoothMeshService) {
    val profile = UserProfileManager.getInstance(context)
    val location = getCurrentLocation() ?: run {
      Log.w(TAG, "No location available to broadcast")
      return
    }
    val staticId = profile.getStaticId() ?: meshService.myPeerID
    val fio = profile.getFio()
    val role = profile.getRole().name
    val payload = buildGeolocPayload(staticId, fio, location.latitude, location.longitude, role)
    try {
      meshService.sendMessage(payload)
      mergeLocation(
        UserLocationEntry(
          staticId = staticId,
          fio = fio,
          latitude = location.latitude,
          longitude = location.longitude,
          role = role,
          peerId = meshService.myPeerID,
          timestampMs = System.currentTimeMillis()
        )
      )
      Log.d(TAG, "Broadcast location for $staticId via mesh")
    } catch (e: Exception) {
      Log.e(TAG, "Failed to broadcast location", e)
    }
  }

  private suspend fun getCurrentLocation(): Location? {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
      != PackageManager.PERMISSION_GRANTED
    ) return null

    return suspendCancellableCoroutine { cont ->
      locationProvider.requestFreshLocation { location ->
        if (location != null) {
          cont.resume(location)
        } else {
          locationProvider.getLastKnownLocation { fallback ->
            cont.resume(fallback)
          }
        }
      }
    }
  }

  private fun loadCachedLocations() {
    try {
      if (!cacheFile.exists()) return
      val type = object : TypeToken<List<UserLocationEntry>>() {}.type
      val list: List<UserLocationEntry> = gson.fromJson(cacheFile.readText(), type) ?: emptyList()
      _userLocations.value = list.associateBy { it.staticId }
    } catch (e: Exception) {
      Log.w(TAG, "Failed to load cached locations", e)
    }
  }

  private fun persistLocations() {
    ioScope?.launch(Dispatchers.IO) {
      try {
        val list = _userLocations.value.values.toList()
        cacheFile.writeText(gson.toJson(list))
      } catch (e: Exception) {
        Log.w(TAG, "Failed to persist locations", e)
      }
    }
  }
}
