package com.bitchat.android.geohash

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.bitchat.android.identity.UserProfileManager
import com.bitchat.android.mesh.BluetoothMeshService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

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
 * Broadcasts user geolocation every 15 minutes over mesh and collects locations from peers.
 * Admins and teachers use this data to render the org map.
 */
class LocationSharingService private constructor(private val context: Context) {

  companion object {
    private const val TAG = "LocationSharing"
    private const val INTERVAL_MS = 15 * 60 * 1000L
    const val GEOLOC_PREFIX = "[GEOLOC]:"

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

  private val _userLocations = MutableStateFlow<Map<String, UserLocationEntry>>(emptyMap())
  val userLocations: StateFlow<Map<String, UserLocationEntry>> = _userLocations.asStateFlow()

  private var sharingJob: Job? = null

  fun start(meshService: BluetoothMeshService, scope: CoroutineScope) {
    sharingJob?.cancel()
    val profile = UserProfileManager.getInstance(context)
    profile.ensureStaticId(meshService.myPeerID)

    sharingJob = scope.launch {
      while (isActive) {
        broadcastLocation(meshService)
        delay(INTERVAL_MS)
      }
    }
  }

  fun stop() {
    sharingJob?.cancel()
    sharingJob = null
  }

  fun handleIncomingMessage(content: String, senderPeerId: String?): Boolean {
    if (!content.startsWith(GEOLOC_PREFIX)) return false
    val entry = LocationSharingService.parseGeolocMessage(content, senderPeerId) ?: return true
    _userLocations.value = _userLocations.value + (entry.staticId to entry)
    return true
  }

  private fun broadcastLocation(meshService: BluetoothMeshService) {
    val location = getCurrentLocation() ?: return
    val profile = UserProfileManager.getInstance(context)
    val staticId = profile.getStaticId() ?: meshService.myPeerID
    val fio = profile.getFio()
    val role = profile.getRole().name
    val payload = LocationSharingService.buildGeolocPayload(staticId, fio, location.latitude, location.longitude, role)
    try {
      meshService.sendMessage(payload)
      _userLocations.value = _userLocations.value + (
        staticId to UserLocationEntry(
          staticId = staticId,
          fio = fio,
          latitude = location.latitude,
          longitude = location.longitude,
          role = role,
          peerId = meshService.myPeerID,
          timestampMs = System.currentTimeMillis()
        )
      )
      Log.d(TAG, "Broadcast location for $staticId")
    } catch (e: Exception) {
      Log.e(TAG, "Failed to broadcast location", e)
    }
  }

  private fun getCurrentLocation(): Location? {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
      != PackageManager.PERMISSION_GRANTED
    ) return null

    val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    return try {
      manager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
        ?: manager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
    } catch (_: SecurityException) {
      null
    }
  }
}
