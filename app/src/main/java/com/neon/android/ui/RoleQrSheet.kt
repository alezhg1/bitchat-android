package com.neon.android.ui

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.camera.compose.CameraXViewfinder
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.viewfinder.core.ImplementationMode
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.zxing.BarcodeFormat
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.neon.android.core.ui.component.sheet.BitchatBottomSheet
import com.neon.android.core.ui.component.sheet.BitchatSheetTopBar
import com.neon.android.core.ui.component.sheet.BitchatSheetTitle
import com.neon.android.identity.UserRole
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import androidx.core.graphics.createBitmap
import androidx.core.graphics.set

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoleQrScannerSheet(
    isPresented: Boolean,
    expectedRole: UserRole,
    onDismiss: () -> Unit,
    onScanned: (UserRole) -> Unit,
    modifier: Modifier = Modifier
) {
    if (!isPresented) return

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    val roleKeyManager = remember { com.neon.android.identity.RoleKeyManager.getInstance(context) }
    var scanError by remember { mutableStateOf<String?>(null) }

    BitchatBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier
    ) {
        BitchatSheetTopBar(
            onClose = onDismiss,
            title = { BitchatSheetTitle("Сканировать QR · ${expectedRole.displayNameRu}") }
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                when (expectedRole) {
                    UserRole.ADMIN -> "Наведите камеру на QR администратора (выдаётся оператором лагеря)."
                    UserRole.TEACHER -> "Попросите администратора открыть QR преподавателя в настройках и отсканируйте его."
                    UserRole.STUDENT -> ""
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(320.dp),
                contentAlignment = Alignment.Center
            ) {
                QrCameraScannerBox(
                    modifier = Modifier.fillMaxSize(),
                    onScan = { raw ->
                        val granted = roleKeyManager.grantFromQrPayload(raw)
                        when {
                            granted == expectedRole -> {
                                scanError = null
                                onScanned(granted)
                                onDismiss()
                            }
                            granted != null -> scanError = "Это QR для роли «${granted.displayNameRu}», нужен «${expectedRole.displayNameRu}»"
                            else -> scanError = "Неверный или повреждённый QR"
                        }
                    }
                )
            }

            scanError?.let { err ->
                Text(err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoleQrDisplaySheet(
    isPresented: Boolean,
    role: UserRole,
    qrPayload: String?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!isPresented) return

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    BitchatBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier
    ) {
        BitchatSheetTopBar(
            onClose = onDismiss,
            title = { BitchatSheetTitle("QR · ${role.displayNameRu}") }
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (qrPayload.isNullOrBlank()) {
                Text(
                    "QR недоступен. Пересоберите приложение с `tools/generate_role_keys.ps1`.",
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
            } else {
                Text(
                    "Покажите этот код будущему преподавателю для сканирования при входе.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 2.dp
                ) {
                    RoleQrCodeImage(data = qrPayload, size = 240.dp, modifier = Modifier.padding(16.dp))
                }
                Text(
                    role.displayNameRu,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Только офлайн, локально. Не отправляйте скриншот в чаты.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun QrCameraScannerBox(
    modifier: Modifier = Modifier,
    onScan: (String) -> Unit
) {
    val permissionState = rememberPermissionState(android.Manifest.permission.CAMERA)

    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    ) {
        if (permissionState.status.isGranted) {
            RoleQrCameraPreview(onScan = onScan, modifier = Modifier.fillMaxSize())
        } else {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Default.QrCode2, contentDescription = null, modifier = Modifier.size(48.dp))
                Spacer(Modifier.height(12.dp))
                Text("Нужен доступ к камере для сканирования QR", textAlign = TextAlign.Center)
                Spacer(Modifier.height(12.dp))
                Button(onClick = { permissionState.launchPermissionRequest() }) {
                    Text("Разрешить камеру")
                }
            }
        }
    }
}

@Composable
private fun RoleQrCameraPreview(
    onScan: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var lastValid by remember { mutableStateOf<String?>(null) }
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    val cameraExecutor: ExecutorService = remember { Executors.newSingleThreadExecutor() }
    val surfaceRequests = remember { MutableStateFlow<SurfaceRequest?>(null) }
    val surfaceRequest by surfaceRequests.collectAsState(initial = null)
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val onCodeState = rememberUpdatedState(onScan)

    val analyzer = remember {
        RoleQrCodeAnalyzer { text ->
            mainHandler.post {
                if (text == lastValid) return@post
                lastValid = text
                onCodeState.value(text)
            }
        }
    }

    DisposableEffect(Unit) {
        val executor = ContextCompat.getMainExecutor(context)
        var cameraProvider: ProcessCameraProvider? = null
        cameraProviderFuture.addListener({
            val provider = cameraProviderFuture.get()
            cameraProvider = provider
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider { request -> surfaceRequests.value = request }
            }
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { it.setAnalyzer(cameraExecutor, analyzer) }
            runCatching {
                provider.unbindAll()
                provider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    analysis
                )
            }.onFailure {
                Log.w("RoleQrSheet", "Camera bind failed: ${it.message}")
            }
        }, executor)
        onDispose {
            surfaceRequests.value = null
            runCatching { cameraProvider?.unbindAll() }
            cameraExecutor.shutdown()
        }
    }

    surfaceRequest?.let { request ->
        CameraXViewfinder(
            surfaceRequest = request,
            implementationMode = ImplementationMode.EMBEDDED,
            modifier = modifier
        )
    }
}

@Composable
fun RoleQrCodeImage(
    data: String,
    size: Dp,
    modifier: Modifier = Modifier
) {
    val sizePx = with(LocalDensity.current) { size.toPx().toInt() }
    val bitmap = remember(data, sizePx) { generateRoleQrBitmap(data, sizePx) }
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "QR код роли",
            modifier = modifier.size(size)
        )
    }
}

private fun generateRoleQrBitmap(data: String, sizePx: Int): Bitmap? {
    if (data.isBlank() || sizePx <= 0) return null
    return try {
        val matrix = QRCodeWriter().encode(data, BarcodeFormat.QR_CODE, sizePx, sizePx)
        bitmapFromMatrix(matrix)
    } catch (_: Exception) {
        null
    }
}

private fun bitmapFromMatrix(matrix: BitMatrix): Bitmap {
    val width = matrix.width
    val height = matrix.height
    val bitmap = createBitmap(width, height)
    for (x in 0 until width) {
        for (y in 0 until height) {
            bitmap[x, y] = if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE
        }
    }
    return bitmap
}

private class RoleQrCodeAnalyzer(
    private val onCode: (String) -> Unit
) : ImageAnalysis.Analyzer {
    private val scanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()
    )

    @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image ?: run {
            imageProxy.close()
            return
        }
        val input = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        scanner.process(input)
            .addOnSuccessListener { barcodes ->
                barcodes.firstOrNull()?.rawValue?.takeIf { it.isNotBlank() }?.let(onCode)
            }
            .addOnCompleteListener { imageProxy.close() }
    }
}
