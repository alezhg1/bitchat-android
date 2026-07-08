package com.neon.android.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neon.android.geohash.LocationSharingService
import com.neon.android.geohash.UserLocationEntry
import com.neon.android.identity.UserRole
import com.neon.android.core.ui.component.sheet.BitchatBottomSheet
import com.neon.android.core.ui.component.sheet.BitchatSheetTopBar
import com.neon.android.core.ui.component.sheet.BitchatSheetTitle
import kotlin.math.max
import kotlin.math.min

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
  var refreshKey by remember { mutableIntStateOf(0) }

  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  val locations = remember(userLocations, refreshKey) { userLocations.values.toList() }

  BitchatBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    modifier = modifier
  ) {
    BitchatSheetTopBar(
      onClose = onDismiss,
      title = { BitchatSheetTitle("Карта пользователей") },
      actions = {
        IconButton(onClick = { refreshKey++ }) {
          Icon(Icons.Default.Refresh, contentDescription = "Обновить")
        }
      }
    )

    Column(modifier = Modifier.fillMaxSize()) {
      OfflineLocationMap(
        locations = locations,
        modifier = Modifier
          .fillMaxWidth()
          .weight(1f)
          .padding(8.dp)
      )

      LazyColumn(
        modifier = Modifier
          .fillMaxWidth()
          .heightIn(max = 180.dp)
          .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
      ) {
        items(locations.sortedBy { it.fio }) { entry ->
          UserLocationRow(entry)
        }
        if (locations.isEmpty()) {
          item {
            Text(
              "Ожидание геопозиции (обновление каждые 15 мин через mesh)",
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
private fun OfflineLocationMap(
  locations: List<UserLocationEntry>,
  modifier: Modifier = Modifier
) {
  Surface(
    modifier = modifier,
    shape = MaterialTheme.shapes.large,
    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
  ) {
    if (locations.isEmpty()) {
      Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
          "Нет данных геопозиции",
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
      return@Surface
    }

    val lats = locations.map { it.latitude }
    val lons = locations.map { it.longitude }
    val minLat = lats.min()
    val maxLat = lats.max()
    val minLon = lons.min()
    val maxLon = lons.max()
    val latPad = max(0.001, (maxLat - minLat) * 0.15)
    val lonPad = max(0.001, (maxLon - minLon) * 0.15)

    Canvas(modifier = Modifier.fillMaxSize().padding(12.dp)) {
      val w = size.width
      val h = size.height
      locations.forEach { entry ->
        val x = ((entry.longitude - (minLon - lonPad)) / ((maxLon + lonPad) - (minLon - lonPad))).toFloat() * w
        val y = (1f - ((entry.latitude - (minLat - latPad)) / ((maxLat + latPad) - (minLat - latPad))).toFloat()) * h
        val color = when (UserRole.fromString(entry.role)) {
          UserRole.ADMIN -> Color(0xFFE53935)
          UserRole.TEACHER -> Color(0xFF1E88E5)
          UserRole.STUDENT -> Color(0xFF43A047)
        }
        drawCircle(color = color, radius = 10f, center = Offset(x, y))
        drawCircle(color = Color.White, radius = 10f, center = Offset(x, y), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f))
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
    shape = MaterialTheme.shapes.medium,
    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
    modifier = Modifier.fillMaxWidth()
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Box(
        modifier = Modifier
          .size(10.dp)
          .background(roleColor, CircleShape)
      )
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
