package com.neon.android.onboarding

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.LocationManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

/**
 * Manages Location Services enable/disable state and user prompts.
 * Broadcast monitoring is scoped to the location-check onboarding screen only.
 */
class LocationStatusManager(
    private val activity: ComponentActivity,
    private val context: Context,
    private val onLocationEnabled: () -> Unit,
    private val onLocationDisabled: (String) -> Unit
) {

    companion object {
        private const val TAG = "LocationStatusManager"
    }

    private var locationSettingsLauncher: ActivityResultLauncher<Intent>? = null
    private var locationManager: LocationManager? = null

    init {
        setupLocationManager()
        setupLocationSettingsLauncher()
    }

    private fun setupLocationManager() {
        try {
            locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            Log.d(TAG, "LocationManager initialized: ${locationManager != null}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize LocationManager", e)
            locationManager = null
        }
    }

    private fun setupLocationSettingsLauncher() {
        locationSettingsLauncher = activity.registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            val isEnabled = isLocationEnabled()
            Log.d(TAG, "Location settings request result: $isEnabled (result code: ${result.resultCode})")
            if (isEnabled) {
                onLocationEnabled()
            } else {
                onLocationDisabled(
                    "Location services are required for Bluetooth scanning on Android. Please enable location services to continue."
                )
            }
        }
    }

    fun isLocationEnabled(): Boolean {
        return try {
            locationManager?.let { lm ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    lm.isLocationEnabled
                } else {
                    lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                        lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
                }
            } ?: false
        } catch (e: Exception) {
            Log.w(TAG, "Error checking location enabled state: ${e.message}")
            false
        }
    }

    fun checkLocationStatus(): LocationStatus {
        Log.d(TAG, "Checking location services status")
        return when {
            locationManager == null -> {
                Log.e(TAG, "LocationManager not available on this device")
                LocationStatus.NOT_AVAILABLE
            }
            !isLocationEnabled() -> {
                Log.w(TAG, "Location services are disabled")
                LocationStatus.DISABLED
            }
            else -> {
                Log.d(TAG, "Location services are enabled and ready")
                LocationStatus.ENABLED
            }
        }
    }

    fun requestEnableLocation() {
        Log.d(TAG, "Requesting user to enable location services")
        try {
            val enableLocationIntent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            locationSettingsLauncher?.launch(enableLocationIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to request location enable", e)
            onLocationDisabled("Failed to open location settings: ${e.message}")
        }
    }

    fun handleLocationStatus(status: LocationStatus) {
        when (status) {
            LocationStatus.ENABLED -> {
                Log.d(TAG, "Location services enabled, proceeding")
                onLocationEnabled()
            }
            LocationStatus.DISABLED -> {
                Log.d(TAG, "Location services disabled, requesting enable")
                requestEnableLocation()
            }
            LocationStatus.NOT_AVAILABLE -> {
                Log.e(TAG, "Location services not available")
                onLocationDisabled("Location services are not available on this device.")
            }
        }
    }

    fun getStatusMessage(status: LocationStatus): String {
        return when (status) {
            LocationStatus.ENABLED -> "Location services are enabled and ready"
            LocationStatus.DISABLED ->
                "Location services are disabled. Please enable location services for Bluetooth scanning."
            LocationStatus.NOT_AVAILABLE -> "Location services are not available on this device."
        }
    }

    fun getDiagnostics(): String {
        return buildString {
            appendLine("Location Services Status Diagnostics:")
            appendLine("LocationManager available: ${locationManager != null}")
            appendLine("Location services enabled: ${isLocationEnabled()}")
            appendLine("Current status: ${checkLocationStatus()}")
            appendLine("Android version: ${Build.VERSION.SDK_INT}")
            locationManager?.let { lm ->
                try {
                    appendLine("GPS provider enabled: ${lm.isProviderEnabled(LocationManager.GPS_PROVIDER)}")
                    appendLine("Network provider enabled: ${lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)}")
                } catch (e: Exception) {
                    appendLine("Provider details: [Error: ${e.message}]")
                }
            }
        }
    }

    fun logLocationStatus() {
        Log.d(TAG, getDiagnostics())
    }

    /**
     * Monitors location provider changes while the location-check screen is visible.
     * Does not invoke onboarding callbacks directly — only reports status changes.
     */
    fun monitorLocationState(
        context: Context,
        onLocationStateChanged: (LocationStatus) -> Unit
    ): BroadcastReceiver {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == LocationManager.MODE_CHANGED_ACTION ||
                    intent?.action == LocationManager.PROVIDERS_CHANGED_ACTION
                ) {
                    Log.d(TAG, "Location settings changed, checking status")
                    onLocationStateChanged(checkLocationStatus())
                }
            }
        }

        val filter = IntentFilter().apply {
            addAction(LocationManager.MODE_CHANGED_ACTION)
            addAction(LocationManager.PROVIDERS_CHANGED_ACTION)
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        onLocationStateChanged(checkLocationStatus())
        return receiver
    }
}

enum class LocationStatus {
    ENABLED,
    DISABLED,
    NOT_AVAILABLE
}
