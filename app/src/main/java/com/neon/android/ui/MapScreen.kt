package com.neon.android.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.ZoomOutMap
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neon.android.geohash.CampChatManager
import com.neon.android.geohash.LocationSharingService
import com.neon.android.geohash.UserLocationEntry
import com.neon.android.identity.UserRole
import com.neon.android.core.ui.component.sheet.BitchatBottomSheet
import com.neon.android.core.ui.component.sheet.BitchatSheetTopBar
import com.neon.android.core.ui.component.sheet.BitchatSheetTitle
import com.neon.android.ui.theme.ChatAvatar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

private data class MapUserDisplay(
  val staticId: String,
  val fio: String,
  val role: UserRole,
  val latitude: Double?,
  val longitude: Double?,
  val peerId: String?,
  val timestampMs: Long?,
  val isDirectMeshPeer: Boolean
)

private data class MapBounds(
  val minLat: Double,
  val maxLat: Double,
  val minLon: Double,
  val maxLon: Double
) {
  fun latSpan(): Double = max(maxLat - minLat, 1e-9)
  fun lonSpan(): Double = max(maxLon - minLon, 1e-9)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
  isPresented: Boolean,
  onDismiss: () -> Unit,
  viewModel: ChatViewModel,
  modifier: Modifier = Modifier
) {
  if (!isPresented) return

  val context = LocalContext.current
  val locationService = remember { LocationSharingService.getInstance(context) }
  val userLocations by locationService.userLocations.collectAsStateWithLifecycle()
  val connectedPeers by viewModel.connectedPeers.collectAsStateWithLifecycle()
  val peerNicknames by viewModel.peerNicknames.collectAsStateWithLifecycle()
  val myPeerId = viewModel.meshService.myPeerID

  val directPeers = remember(connectedPeers, myPeerId) {
    connectedPeers.filter { it != myPeerId }.toSet()
  }

  val displayUsers = remember(userLocations, directPeers, peerNicknames) {
    buildMapUserList(userLocations, directPeers, peerNicknames, myPeerId)
  }

  val campAnchor = remember { CampChatManager.getCampAnchorCoordinates(context) }
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

  val withGps = displayUsers.count { it.latitude != null }
  val meshDirect = displayUsers.count { it.isDirectMeshPeer }

  BitchatBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    modifier = modifier
  ) {
    BitchatSheetTopBar(
      onClose = onDismiss,
      title = { BitchatSheetTitle("Карта лагеря") },
      actions = {
        IconButton(onClick = { viewModel.refreshLocationSharing() }) {
          Icon(Icons.Default.Refresh, contentDescription = "Обновить геопозицию")
        }
      }
    )

    Column(modifier = Modifier.fillMaxSize()) {
      MapStatsBar(
        total = displayUsers.size,
        withGps = withGps,
        directMesh = meshDirect,
        intervalMin = (LocationSharingService.SHARE_INTERVAL_MS / 60_000).toInt()
      )

      MeshNetworkMap(
        users = displayUsers,
        campAnchor = campAnchor,
        myPeerId = myPeerId,
        modifier = Modifier
          .fillMaxWidth()
          .weight(0.52f)
          .padding(horizontal = 8.dp, vertical = 4.dp)
      )

      HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp))

      Text(
        "Все пользователи (${displayUsers.size})",
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
      )

      if (displayUsers.isEmpty()) {
        Text(
          "Ожидание участников mesh-сети. Геопозиция рассылается каждые " +
            "${LocationSharingService.SHARE_INTERVAL_MS / 60_000} мин через Bluetooth (multi-hop).",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )
      }

      LazyColumn(
        modifier = Modifier
          .fillMaxWidth()
          .weight(0.48f)
          .padding(horizontal = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = PaddingValues(bottom = 16.dp)
      ) {
        items(displayUsers, key = { it.staticId }) { user ->
          MapUserListRow(user)
        }
      }
    }
  }
}

@Composable
private fun MapStatsBar(
  total: Int,
  withGps: Int,
  directMesh: Int,
  intervalMin: Int
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 12.dp, vertical = 6.dp),
    horizontalArrangement = Arrangement.SpaceEvenly
  ) {
    StatChip("$total", "всего")
    StatChip("$withGps", "с GPS")
    StatChip("$directMesh", "mesh рядом")
    StatChip("${intervalMin} мин", "обновление")
  }
}

@Composable
private fun StatChip(value: String, label: String) {
  Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
  }
}

@Composable
private fun MeshNetworkMap(
  users: List<MapUserDisplay>,
  campAnchor: Pair<Double, Double>?,
  myPeerId: String,
  modifier: Modifier = Modifier
) {
  var scale by remember { mutableFloatStateOf(1f) }
  var offset by remember { mutableStateOf(Offset.Zero) }

  val located = users.filter { it.latitude != null && it.longitude != null }
  val bounds = remember(located, campAnchor) {
    computeMapBounds(located, campAnchor)
  }

  Surface(
    modifier = modifier,
    shape = MaterialTheme.shapes.large,
    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
  ) {
    Box(Modifier.fillMaxSize()) {
      if (located.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
              "Нет GPS-точек на карте",
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(4.dp))
            Text(
              "Данные приходят по mesh-сети (через участников)",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }
      } else {
        Canvas(
          modifier = Modifier
            .fillMaxSize()
            .padding(8.dp)
            .pointerInput(Unit) {
              detectTransformGestures { _, pan, zoom, _ ->
                scale = (scale * zoom).coerceIn(0.4f, 12f)
                offset += pan
              }
            }
        ) {
          withTransform({
            translate(offset.x, offset.y)
            scale(scale, scale, pivot = Offset(size.width / 2f, size.height / 2f))
          }) {
            drawMeshNetwork(
              users = located,
              bounds = bounds,
              campAnchor = campAnchor
            )
          }
        }
      }

      Column(
        modifier = Modifier
          .align(Alignment.TopEnd)
          .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
      ) {
        SmallFloatingButton(Icons.Default.Add) { scale = (scale * 1.25f).coerceAtMost(12f) }
        SmallFloatingButton(Icons.Default.Remove) { scale = (scale / 1.25f).coerceAtLeast(0.4f) }
        SmallFloatingButton(Icons.Default.ZoomOutMap) {
          scale = 1f
          offset = Offset.Zero
        }
      }

      MapLegend(modifier = Modifier.align(Alignment.BottomStart).padding(8.dp))
    }
  }
}

@Composable
private fun SmallFloatingButton(
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

@Composable
private fun MapLegend(modifier: Modifier = Modifier) {
  Surface(
    modifier = modifier,
    shape = MaterialTheme.shapes.small,
    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f)
  ) {
    Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
      LegendRow(Color(0xFF43A047), "Ученик")
      LegendRow(Color(0xFF1E88E5), "Учитель")
      LegendRow(Color(0xFFE53935), "Админ")
      Text(
        "— mesh-связь",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
  }
}

@Composable
private fun LegendRow(color: Color, label: String) {
  Row(verticalAlignment = Alignment.CenterVertically) {
    Box(Modifier.size(8.dp).background(color, CircleShape))
    Spacer(Modifier.width(6.dp))
    Text(label, style = MaterialTheme.typography.labelSmall)
  }
}

private fun DrawScope.drawMeshNetwork(
  users: List<MapUserDisplay>,
  bounds: MapBounds,
  campAnchor: Pair<Double, Double>?
) {
  val w = size.width
  val h = size.height
  val padLat = bounds.latSpan() * 0.12
  val padLon = bounds.lonSpan() * 0.12
  val minLat = bounds.minLat - padLat
  val maxLat = bounds.maxLat + padLat
  val minLon = bounds.minLon - padLon
  val maxLon = bounds.maxLon + padLon

  fun toOffset(lat: Double, lon: Double): Offset {
    val x = ((lon - minLon) / (maxLon - minLon)).toFloat() * w
    val y = (1f - ((lat - minLat) / (maxLat - minLat)).toFloat()) * h
    return Offset(x, y)
  }

  val positions = users.mapNotNull { user ->
    val lat = user.latitude ?: return@mapNotNull null
    val lon = user.longitude ?: return@mapNotNull null
    user to toOffset(lat, lon)
  }.toMap()

  // Mesh topology: connect direct BLE peers; link others via camp anchor (multi-hop aggregation)
  val anchorPoint = campAnchor?.let { toOffset(it.first, it.second) }
    ?: Offset(w / 2f, h / 2f)

  val directPeers = users.filter { it.isDirectMeshPeer && positions.containsKey(it) }
  val relayOnly = users.filter { !it.isDirectMeshPeer && positions.containsKey(it) }

  val meshLineEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)

  directPeers.forEach { peer ->
    val peerPos = positions[peer] ?: return@forEach
    drawLine(
      color = Color(0xFF43A047).copy(alpha = 0.55f),
      start = anchorPoint,
      end = peerPos,
      strokeWidth = 2.5f
    )
  }

  relayOnly.forEach { peer ->
    val peerPos = positions[peer] ?: return@forEach
    val nearestDirect = directPeers
      .mapNotNull { positions[it]?.let { pos -> it to pos } }
      .minByOrNull { (_, pos) -> hypot(pos.x - peerPos.x, pos.y - peerPos.y) }
    val hub = nearestDirect?.second ?: anchorPoint
    drawLine(
      color = Color(0xFF90A4AE).copy(alpha = 0.45f),
      start = hub,
      end = peerPos,
      strokeWidth = 1.5f,
      pathEffect = meshLineEffect
    )
  }

  // Inter-peer mesh links among direct neighbors (camp sub-network)
  for (i in directPeers.indices) {
    for (j in i + 1 until directPeers.size) {
      val a = positions[directPeers[i]] ?: continue
      val b = positions[directPeers[j]] ?: continue
      drawLine(
        color = Color(0xFF66BB6A).copy(alpha = 0.35f),
        start = a,
        end = b,
        strokeWidth = 1.2f,
        pathEffect = meshLineEffect
      )
    }
  }

  campAnchor?.let {
    drawCircle(
      color = Color(0xFF78909C).copy(alpha = 0.25f),
      radius = 18f,
      center = anchorPoint,
      style = Stroke(width = 2f, pathEffect = meshLineEffect)
    )
  }

  users.forEach { user ->
    val lat = user.latitude ?: return@forEach
    val lon = user.longitude ?: return@forEach
    val center = toOffset(lat, lon)
    val color = roleColor(user.role)
    val radius = if (user.isDirectMeshPeer) 12f else 9f
    if (user.isDirectMeshPeer) {
      drawCircle(
        color = color.copy(alpha = 0.25f),
        radius = radius + 6f,
        center = center
      )
    }
    drawCircle(color = color, radius = radius, center = center)
    drawCircle(
      color = Color.White,
      radius = radius,
      center = center,
      style = Stroke(width = 2f)
    )
  }
}

@Composable
private fun MapUserListRow(user: MapUserDisplay) {
  val roleColor = roleColor(user.role)
  val timeFormatter = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
  val ageText = user.timestampMs?.let { ts ->
    val ageMin = TimeUnit.MILLISECONDS.toMinutes(System.currentTimeMillis() - ts)
    when {
      ageMin < 1 -> "только что"
      ageMin < 60 -> "$ageMin мин назад"
      else -> timeFormatter.format(Date(ts))
    }
  } ?: "нет данных"

  Surface(
    shape = MaterialTheme.shapes.medium,
    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
    modifier = Modifier.fillMaxWidth()
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      ChatAvatar(name = user.fio, accentColor = roleColor, size = 40.dp)
      Spacer(Modifier.width(10.dp))
      Column(modifier = Modifier.weight(1f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(user.fio, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyMedium)
          if (user.isDirectMeshPeer) {
            Spacer(Modifier.width(6.dp))
            Surface(
              shape = MaterialTheme.shapes.extraSmall,
              color = Color(0xFF43A047).copy(alpha = 0.15f)
            ) {
              Text(
                "mesh",
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF2E7D32)
              )
            }
          }
        }
        Text(
          "${user.role.displayNameRu} · ${user.staticId.take(12)}…",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
          buildString {
            if (user.latitude != null && user.longitude != null) {
              append("GPS: %.5f, %.5f".format(user.latitude, user.longitude))
            } else {
              append("GPS: ожидание (данные идут через mesh)")
            }
            append(" · $ageText")
          },
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    }
  }
}

private fun buildMapUserList(
  locations: Map<String, UserLocationEntry>,
  directPeers: Set<String>,
  peerNicknames: Map<String, String>,
  myPeerId: String
): List<MapUserDisplay> {
  val byStaticId = locations.values.associateBy { it.staticId }.toMutableMap()

  directPeers.forEach { peerId ->
    val existing = byStaticId.values.find { it.peerId == peerId }
    if (existing == null) {
      val nick = peerNicknames[peerId] ?: peerId.take(8)
      byStaticId[peerId] = UserLocationEntry(
        staticId = peerId,
        fio = nick,
        latitude = 0.0,
        longitude = 0.0,
        role = UserRole.STUDENT.name,
        peerId = peerId,
        timestampMs = 0L
      )
    }
  }

  return byStaticId.values
    .map { entry ->
      val hasGps = entry.latitude != 0.0 || entry.longitude != 0.0
      MapUserDisplay(
        staticId = entry.staticId,
        fio = entry.fio.ifBlank { entry.staticId.take(8) },
        role = UserRole.fromString(entry.role),
        latitude = if (hasGps && entry.timestampMs > 0) entry.latitude else null,
        longitude = if (hasGps && entry.timestampMs > 0) entry.longitude else null,
        peerId = entry.peerId,
        timestampMs = entry.timestampMs.takeIf { it > 0 },
        isDirectMeshPeer = entry.peerId != null && entry.peerId in directPeers
          || entry.staticId in directPeers
      )
    }
    .sortedWith(
      compareByDescending<MapUserDisplay> { it.isDirectMeshPeer }
        .thenBy { it.role.ordinal }
        .thenBy { it.fio.lowercase() }
    )
}

private fun computeMapBounds(
  located: List<MapUserDisplay>,
  campAnchor: Pair<Double, Double>?
): MapBounds {
  val lats = located.mapNotNull { it.latitude }.toMutableList()
  val lons = located.mapNotNull { it.longitude }.toMutableList()
  campAnchor?.let {
    lats.add(it.first)
    lons.add(it.second)
  }
  if (lats.isEmpty()) {
    val (lat, lon) = campAnchor ?: (55.75 to 37.62)
    val delta = 0.0025 // ~250 m min view for camp
    return MapBounds(lat - delta, lat + delta, lon - delta, lon + delta)
  }
  var minLat = lats.min()
  var maxLat = lats.max()
  var minLon = lons.min()
  var maxLon = lons.max()
  if (minLat == maxLat) {
    minLat -= 0.0015
    maxLat += 0.0015
  }
  if (minLon == maxLon) {
    minLon -= 0.0015
    maxLon += 0.0015
  }
  return MapBounds(minLat, maxLat, minLon, maxLon)
}

private fun roleColor(role: UserRole): Color = when (role) {
  UserRole.ADMIN -> Color(0xFFE53935)
  UserRole.TEACHER -> Color(0xFF1E88E5)
  UserRole.STUDENT -> Color(0xFF43A047)
}
