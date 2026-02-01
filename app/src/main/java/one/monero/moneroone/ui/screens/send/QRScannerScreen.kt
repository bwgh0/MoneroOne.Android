package one.monero.moneroone.ui.screens.send

import android.Manifest
import android.content.pm.PackageManager
import android.view.HapticFeedbackConstants
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.common.util.concurrent.ListenableFuture
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import one.monero.moneroone.ui.components.GlassCard
import one.monero.moneroone.ui.components.PrimaryButton
import one.monero.moneroone.ui.theme.MoneroOrange
import timber.log.Timber
import java.util.concurrent.Executors

/**
 * Parsed data from a Monero URI.
 */
data class MoneroUriData(
    val address: String,
    val amount: String? = null,
    val recipientName: String? = null,
    val description: String? = null,
    val paymentId: String? = null
)

/**
 * Parses a Monero URI string into its components.
 * Format: monero:<address>?tx_amount=<amount>&recipient_name=<name>&tx_description=<desc>
 */
fun parseMoneroUri(uri: String): MoneroUriData? {
    val trimmed = uri.trim()

    // Handle both monero: URI format and plain addresses
    val address = when {
        trimmed.startsWith("monero:", ignoreCase = true) -> {
            val withoutScheme = trimmed.substring(7)
            val queryStart = withoutScheme.indexOf('?')
            if (queryStart == -1) withoutScheme else withoutScheme.substring(0, queryStart)
        }
        trimmed.startsWith("4") || trimmed.startsWith("8") -> trimmed.split("?")[0]
        else -> return null
    }

    // Validate address format (basic check)
    if (!address.startsWith("4") && !address.startsWith("8")) {
        return null
    }
    if (address.length < 95) {
        return null
    }

    // Parse query parameters
    val queryStart = trimmed.indexOf('?')
    var amount: String? = null
    var recipientName: String? = null
    var description: String? = null
    var paymentId: String? = null

    if (queryStart != -1) {
        val queryString = trimmed.substring(queryStart + 1)
        val params = queryString.split("&")
        for (param in params) {
            val parts = param.split("=", limit = 2)
            if (parts.size == 2) {
                val key = parts[0].lowercase()
                val value = java.net.URLDecoder.decode(parts[1], "UTF-8")
                when (key) {
                    "tx_amount", "amount" -> amount = value
                    "recipient_name" -> recipientName = value
                    "tx_description", "description", "message" -> description = value
                    "tx_payment_id", "payment_id" -> paymentId = value
                }
            }
        }
    }

    return MoneroUriData(
        address = address,
        amount = amount,
        recipientName = recipientName,
        description = description,
        paymentId = paymentId
    )
}

/**
 * ML Kit barcode analyzer for QR codes.
 */
private class QrCodeAnalyzer(
    private val onQrCodeDetected: (String) -> Unit
) : ImageAnalysis.Analyzer {

    private val scanner = BarcodeScanning.getClient(
        com.google.mlkit.vision.barcode.BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()
    )

    @Volatile
    private var hasDetected = false

    @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        if (hasDetected) {
            imageProxy.close()
            return
        }

        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val image = InputImage.fromMediaImage(
                mediaImage,
                imageProxy.imageInfo.rotationDegrees
            )

            scanner.process(image)
                .addOnSuccessListener { barcodes ->
                    if (!hasDetected && barcodes.isNotEmpty()) {
                        val rawValue = barcodes.first().rawValue
                        if (rawValue != null) {
                            hasDetected = true
                            onQrCodeDetected(rawValue)
                        }
                    }
                }
                .addOnCompleteListener {
                    imageProxy.close()
                }
        } else {
            imageProxy.close()
        }
    }
}

/**
 * Camera preview composable using CameraX.
 */
@Composable
private fun CameraPreview(
    modifier: Modifier = Modifier,
    onQrCodeDetected: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }

    DisposableEffect(Unit) {
        onDispose {
            executor.shutdown()
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            val previewView = PreviewView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }

            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
            cameraProviderFuture.addListener({
                val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

                val preview = Preview.Builder()
                    .build()
                    .also { it.setSurfaceProvider(previewView.surfaceProvider) }

                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also {
                        it.setAnalyzer(executor, QrCodeAnalyzer(onQrCodeDetected))
                    }

                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                        imageAnalysis
                    )
                } catch (e: Exception) {
                    Timber.e(e, "Camera bind failed")
                }
            }, ContextCompat.getMainExecutor(ctx))

            previewView
        },
        update = { }
    )
}

/**
 * Viewfinder overlay with iOS-style orange corner markers.
 */
@Composable
private fun ViewfinderOverlay(
    modifier: Modifier = Modifier
) {
    val overlayColor = Color.Black.copy(alpha = 0.5f)
    val cornerColor = MoneroOrange
    val cornerLength = 30.dp
    val cornerStroke = 4.dp
    val cutoutCornerRadius = 12.dp
    val cutoutSize = 240.dp

    Canvas(modifier = modifier.fillMaxSize()) {
        val canvasWidth = size.width
        val canvasHeight = size.height

        // Calculate cutout position (centered)
        val cutoutSizePx = cutoutSize.toPx()
        val cutoutLeft = (canvasWidth - cutoutSizePx) / 2
        val cutoutTop = (canvasHeight - cutoutSizePx) / 2
        val cutoutRight = cutoutLeft + cutoutSizePx
        val cutoutBottom = cutoutTop + cutoutSizePx
        val cornerRadiusPx = cutoutCornerRadius.toPx()

        // Draw semi-transparent overlay with cutout
        val cutoutPath = Path().apply {
            addRoundRect(
                RoundRect(
                    rect = Rect(cutoutLeft, cutoutTop, cutoutRight, cutoutBottom),
                    cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx)
                )
            )
        }

        clipPath(cutoutPath, clipOp = ClipOp.Difference) {
            drawRect(
                color = overlayColor,
                size = Size(canvasWidth, canvasHeight)
            )
        }

        // Draw corner markers
        val cornerLengthPx = cornerLength.toPx()
        val strokeWidthPx = cornerStroke.toPx()
        val strokeStyle = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)

        // Top-left corner
        drawLine(
            color = cornerColor,
            start = Offset(cutoutLeft, cutoutTop + cornerRadiusPx),
            end = Offset(cutoutLeft, cutoutTop + cornerLengthPx),
            strokeWidth = strokeWidthPx,
            cap = StrokeCap.Round
        )
        drawLine(
            color = cornerColor,
            start = Offset(cutoutLeft + cornerRadiusPx, cutoutTop),
            end = Offset(cutoutLeft + cornerLengthPx, cutoutTop),
            strokeWidth = strokeWidthPx,
            cap = StrokeCap.Round
        )

        // Top-right corner
        drawLine(
            color = cornerColor,
            start = Offset(cutoutRight, cutoutTop + cornerRadiusPx),
            end = Offset(cutoutRight, cutoutTop + cornerLengthPx),
            strokeWidth = strokeWidthPx,
            cap = StrokeCap.Round
        )
        drawLine(
            color = cornerColor,
            start = Offset(cutoutRight - cornerRadiusPx, cutoutTop),
            end = Offset(cutoutRight - cornerLengthPx, cutoutTop),
            strokeWidth = strokeWidthPx,
            cap = StrokeCap.Round
        )

        // Bottom-left corner
        drawLine(
            color = cornerColor,
            start = Offset(cutoutLeft, cutoutBottom - cornerRadiusPx),
            end = Offset(cutoutLeft, cutoutBottom - cornerLengthPx),
            strokeWidth = strokeWidthPx,
            cap = StrokeCap.Round
        )
        drawLine(
            color = cornerColor,
            start = Offset(cutoutLeft + cornerRadiusPx, cutoutBottom),
            end = Offset(cutoutLeft + cornerLengthPx, cutoutBottom),
            strokeWidth = strokeWidthPx,
            cap = StrokeCap.Round
        )

        // Bottom-right corner
        drawLine(
            color = cornerColor,
            start = Offset(cutoutRight, cutoutBottom - cornerRadiusPx),
            end = Offset(cutoutRight, cutoutBottom - cornerLengthPx),
            strokeWidth = strokeWidthPx,
            cap = StrokeCap.Round
        )
        drawLine(
            color = cornerColor,
            start = Offset(cutoutRight - cornerRadiusPx, cutoutBottom),
            end = Offset(cutoutRight - cornerLengthPx, cutoutBottom),
            strokeWidth = strokeWidthPx,
            cap = StrokeCap.Round
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QRScannerScreen(
    onBack: () -> Unit,
    onScanned: (MoneroUriData) -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    var scanError by remember { mutableStateOf<String?>(null) }
    var hasScanned by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Scan Monero Address",
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                ),
                modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (!hasCameraPermission) {
                // Permission request UI
                GlassCard(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = null,
                            tint = MoneroOrange,
                            modifier = Modifier.height(48.dp)
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "Camera Permission Required",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "To scan QR codes, please allow camera access.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        PrimaryButton(
                            onClick = {
                                permissionLauncher.launch(Manifest.permission.CAMERA)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            color = MoneroOrange
                        ) {
                            Text(
                                text = "Grant Permission",
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            } else if (scanError != null) {
                // Error UI
                GlassCard(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = scanError ?: "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        PrimaryButton(
                            onClick = {
                                scanError = null
                                hasScanned = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                            color = MoneroOrange
                        ) {
                            Text(
                                text = "Try Again",
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            } else {
                // Camera preview with viewfinder
                GlassCard(
                    modifier = Modifier.size(280.dp)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        CameraPreview(
                            modifier = Modifier.fillMaxSize(),
                            onQrCodeDetected = { rawValue ->
                                if (!hasScanned) {
                                    hasScanned = true
                                    Timber.d("Scanned QR: $rawValue")

                                    // Haptic feedback
                                    view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)

                                    val parsed = parseMoneroUri(rawValue)
                                    if (parsed != null) {
                                        onScanned(parsed)
                                    } else {
                                        scanError = "Invalid Monero address or QR code"
                                    }
                                }
                            }
                        )

                        ViewfinderOverlay(
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "Position QR code within the frame",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
