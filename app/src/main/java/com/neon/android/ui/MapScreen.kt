package com.neon.android.ui

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neon.android.geohash.LocationSharingService
import com.neon.android.geohash.UserLocationEntry
import com.neon.android.identity.UserRole
import com.neon.android.core.ui.component.sheet.BitchatBottomSheet
import com.neon.android.core.ui.component.sheet.BitchatSheetTopBar
import com.neon.android.core.ui.component.sheet.BitchatSheetTitle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
  isPresented: Boolean,
  onDismiss: () -> Unit,
  modifier: Modifier = Modifier
) {
  if (!isPresented) return

  val context = LocalContext.current
  val locationService = remember { LocationSharingService.getInstance(context) }
  val userLocations by locationService.userLocations.collectAsStateWithLifecycle()
  var mapKey by remember { mutableIntStateOf(0) }

  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

  BitchatBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    modifier = modifier
  ) {
    BitchatSheetTopBar(
      title = { BitchatSheetTitle("Карта пользователей") },
      actions = {
        IconButton(onClick = { mapKey++ }) {
          Icon(Icons.Default.Refresh, contentDescription = "Обновить")
        }
        IconButton(onClick = onDismiss) {
          Icon(Icons.Default.Close, contentDescription = "Закрыть")
        }
      }
    )

    Column(modifier = Modifier.fillMaxSize()) {
      AndroidView(
        factory = { ctx ->
          WebView(ctx).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            webViewClient = WebViewClient()
          }
        },
        update = { webView ->
          webView.loadDataWithBaseURL(
            "https://localhost/",
            buildMapHtml(userLocations.values.toList()),
            "text/html",
            "UTF-8",
            null
          )
        },
        modifier = Modifier
          .fillMaxWidth()
          .weight(1f)
          .padding(horizontal = 8.dp),
        key = mapKey
      )

      LazyColumn(
        modifier = Modifier
          .fillMaxWidth()
          .heightIn(max = 180.dp)
          .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
      ) {
        items(userLocations.values.toList().sortedBy { it.fio }) { entry ->
          UserLocationRow(entry)
        }
        if (userLocations.isEmpty()) {
          item {
            Text(
              "Ожидание геопозиции пользователей (обновление каждые 15 мин)",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(8.dp)
            )
          }
        }
      }
    }
  }
}

@Composable
private fun UserLocationRow(entry: UserLocationEntry) {
  val roleColor = when (UserRole.fromString(entry.role)) {
    UserRole.ADMIN -> Color(0xFFE53935)
    UserRole.TEACHER -> Color(0xFF1E88E5)
    UserRole.STUDENT -> Color(0xFF43A047)
  }
  Surface(
    shape = MaterialTheme.shapes.small,
    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
    modifier = Modifier.fillMaxWidth()
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Surface(
        modifier = Modifier.size(8.dp),
        shape = MaterialTheme.shapes.extraSmall,
        color = roleColor
      ) {}
      Spacer(Modifier.width(8.dp))
      Column(modifier = Modifier.weight(1f)) {
        Text(entry.fio, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyMedium)
        Text(
          "${UserRole.fromString(entry.role).displayNameRu} · ID: ${entry.staticId}",
          style = MaterialTheme.typography.bodySmall,
          fontFamily = FontFamily.Monospace,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    }
  }
}

private fun buildMapHtml(locations: List<UserLocationEntry>): String {
  val markers = locations.joinToString(",\n") { entry ->
    val color = when (UserRole.fromString(entry.role)) {
      UserRole.ADMIN -> "#E53935"
      UserRole.TEACHER -> "#1E88E5"
      UserRole.STUDENT -> "#43A047"
    }
    """{lat:${entry.latitude},lng:${entry.longitude},label:"${entry.fio.replace("\"", "'")}",color:"$color",id:"${entry.staticId}"}"""
  }
  val center = locations.firstOrNull()?.let { "${it.latitude},${it.longitude}" } ?: "55.7558,37.6173"
  return """
<!DOCTYPE html>
<html>
<head>
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css"/>
<script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
<style>html,body,#map{height:100%;margin:0;padding:0}</style>
</head>
<body>
<div id="map"></div>
<script>
var map = L.map('map').setView([$center], 12);
L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {maxZoom:19}).addTo(map);
var markers = [$markers];
markers.forEach(function(m) {
  var icon = L.divIcon({
    className:'',
    html:'<div style="background:'+m.color+';width:14px;height:14px;border-radius:50%;border:2px solid white;box-shadow:0 1px 4px rgba(0,0,0,.4)"></div>',
    iconSize:[14,14], iconAnchor:[7,7]
  });
  L.marker([m.lat,m.lng],{icon:icon}).addTo(map).bindPopup('<b>'+m.label+'</b><br>ID: '+m.id);
});
if(markers.length>1){var group=L.featureGroup(markers.map(function(m){return L.marker([m.lat,m.lng])}));map.fitBounds(group.getBounds().pad(0.2));}
</script>
</body>
</html>
  """.trimIndent()
}
