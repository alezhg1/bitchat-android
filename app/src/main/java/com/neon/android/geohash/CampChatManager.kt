package com.neon.android.geohash

import android.content.Context
import android.location.Location
import android.util.Log
import org.json.JSONObject
import java.io.InputStreamReader

/**
 * Single camp-wide group chat: one geohash cell (~1.2 km at precision 6) for the whole site.
 *
 * Transport (hybrid, like Telegram groups but offline-first):
 * - **BLE mesh** — primary; multi-hop covers the camp without internet.
 * - **Nostr geohash** — optional extension when relays are reachable (same room id).
 *
 * Configure fixed coordinates in [CONFIG_ASSET] for all devices at one camp,
 * or leave lat/lon at 0 to capture anchor from first GPS fix on each device.
 */
object CampChatManager {

    private const val TAG = "CampChatManager"
    private const val PREFS = "camp_chat"
    private const val KEY_GEOHASH = "camp_geohash"
    private const val KEY_NAME = "camp_name"
    private const val CONFIG_ASSET = "camp_config.json"

    /** Mesh tag for camp group (local UI / join hint; wire payload is still broadcast). */
    const val CAMP_MESH_CHANNEL = "#лагерь"

    /** Staff-only channel; only teachers and admins may post. */
    const val TEACHERS_CHANNEL = "#преподы"

    fun canPostInChannel(role: com.neon.android.identity.UserRole, channel: String?): Boolean {
        if (channel == TEACHERS_CHANNEL) {
            return role == com.neon.android.identity.UserRole.ADMIN ||
                role == com.neon.android.identity.UserRole.TEACHER
        }
        return true
    }

    /** Default geohash length: NEIGHBORHOOD ≈ 1.2 km — fits a typical summer camp. */
    const val DEFAULT_PRECISION = 6

    data class CampConfig(
        val name: String,
        val latitude: Double,
        val longitude: Double,
        val precision: Int,
        val autoCaptureFromGps: Boolean
    )

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun loadConfig(context: Context): CampConfig {
        return try {
            context.assets.open(CONFIG_ASSET).use { stream ->
                val json = JSONObject(InputStreamReader(stream).readText())
                CampConfig(
                    name = json.optString("name", "Лагерь"),
                    latitude = json.optDouble("latitude", 0.0),
                    longitude = json.optDouble("longitude", 0.0),
                    precision = json.optInt("precision", DEFAULT_PRECISION).coerceIn(2, 8),
                    autoCaptureFromGps = json.optBoolean("autoCaptureFromGps", true)
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Using default camp config", e)
            CampConfig("Лагерь", 0.0, 0.0, DEFAULT_PRECISION, true)
        }
    }

    fun getDisplayName(context: Context): String {
        val stored = prefs(context).getString(KEY_NAME, null)
        if (!stored.isNullOrBlank()) return stored
        return loadConfig(context).name
    }

    fun getCampGeohash(context: Context): String? =
        prefs(context).getString(KEY_GEOHASH, null)?.takeIf { it.isNotBlank() }

    fun getCampChannel(context: Context): GeohashChannel? {
        val hash = getCampGeohash(context) ?: return null
        val precision = hash.length
        val level = when {
            precision <= 2 -> GeohashChannelLevel.REGION
            precision <= 4 -> GeohashChannelLevel.PROVINCE
            precision == 5 -> GeohashChannelLevel.CITY
            precision == 6 -> GeohashChannelLevel.NEIGHBORHOOD
            precision == 7 -> GeohashChannelLevel.BLOCK
            else -> GeohashChannelLevel.BUILDING
        }
        return GeohashChannel(level, hash)
    }

    fun geoStorageKey(context: Context): String? =
        getCampGeohash(context)?.let { "geo:$it" }

    fun isCampChannel(context: Context, channel: ChannelID?): Boolean {
        if (channel !is ChannelID.Location) return false
        val camp = getCampGeohash(context) ?: return false
        return channel.channel.geohash.equals(camp, ignoreCase = true)
    }

    fun isCampActive(context: Context, selectedLocation: ChannelID?, currentNamedChannel: String?): Boolean =
        currentNamedChannel == null && isCampChannel(context, selectedLocation)

    /**
     * Resolve camp geohash from config coordinates or first GPS fix; persist and return hash.
     */
    fun ensureCampAnchor(context: Context, location: Location? = null): String? {
        getCampGeohash(context)?.let { return it }

        val config = loadConfig(context)
        val lat: Double
        val lon: Double

        if (config.latitude != 0.0 || config.longitude != 0.0) {
            lat = config.latitude
            lon = config.longitude
            Log.d(TAG, "Camp anchor from config: $lat, $lon")
        } else if (config.autoCaptureFromGps && location != null) {
            lat = location.latitude
            lon = location.longitude
            Log.d(TAG, "Camp anchor captured from GPS: $lat, $lon")
        } else {
            return null
        }

        val hash = Geohash.encode(lat, lon, config.precision).lowercase()
        prefs(context).edit()
            .putString(KEY_GEOHASH, hash)
            .putString(KEY_NAME, config.name)
            .apply()
        Log.i(TAG, "Camp geohash set: $hash (${config.name})")
        return hash
    }

    /** Call after GPS updates; auto-selects camp channel when anchor becomes available. */
    fun onLocationUpdate(context: Context, location: Location) {
        val hash = ensureCampAnchor(context, location) ?: return
        val manager = LocationChannelManager.getInstance(context)
        val channel = getCampChannel(context) ?: return
        val current = manager.selectedChannel.value
        if (current is ChannelID.Mesh) {
            manager.select(ChannelID.Location(channel))
            Log.d(TAG, "Auto-selected camp channel: $hash")
        }
    }

    fun mergeCampTimeline(
        meshMessages: List<com.neon.android.model.BitchatMessage>,
        geoMessages: List<com.neon.android.model.BitchatMessage>
    ): List<com.neon.android.model.BitchatMessage> {
        val merged = LinkedHashMap<String, com.neon.android.model.BitchatMessage>()
        (meshMessages + geoMessages)
            .sortedBy { it.timestamp.time }
            .forEach { msg ->
                val key = com.neon.android.mesh.MessageDedup.contentKey(msg)
                merged[key] = msg
            }
        return merged.values.toList()
    }

    fun selectCampChannel(context: Context): Boolean {
        val channel = getCampChannel(context) ?: return false
        LocationChannelManager.getInstance(context).select(ChannelID.Location(channel))
        return true
    }

    /** Camp center for map bounds when few/no GPS points (config coords or geohash center). */
    fun getCampAnchorCoordinates(context: Context): Pair<Double, Double>? {
        val config = loadConfig(context)
        if (config.latitude != 0.0 || config.longitude != 0.0) {
            return config.latitude to config.longitude
        }
        getCampGeohash(context)?.let { hash ->
            return Geohash.decodeToCenter(hash)
        }
        return null
    }
}
