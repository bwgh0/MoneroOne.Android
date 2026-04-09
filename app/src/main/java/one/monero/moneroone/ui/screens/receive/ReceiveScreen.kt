package one.monero.moneroone.ui.screens.receive

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import one.monero.moneroone.core.wallet.WalletViewModel
import androidx.compose.ui.graphics.toArgb
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import one.monero.moneroone.R
import one.monero.moneroone.ui.components.GlassCard
import one.monero.moneroone.ui.components.PrimaryButton
import one.monero.moneroone.ui.components.SecondaryButton
import one.monero.moneroone.ui.theme.MoneroOrange

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiveScreen(
    walletViewModel: WalletViewModel,
    onBack: () -> Unit,
    onSelectAddress: (() -> Unit)? = null
) {
    val walletState by walletViewModel.walletState.collectAsState()
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("monero_wallet", Context.MODE_PRIVATE) }

    // Use a refresh key to force re-read from prefs when screen resumes
    var refreshKey by remember { mutableIntStateOf(0) }
    val selectedAddressIndex = remember(refreshKey) {
        prefs.getInt("selected_address_index", 0)
    }

    // Re-read when screen resumes (e.g., returning from AddressPickerScreen)
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshKey++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Load subaddresses off the main thread
    var subaddresses by remember { mutableStateOf(walletViewModel.getSubaddresses()) }
    LaunchedEffect(refreshKey, walletState.receiveAddress) {
        subaddresses = withContext(Dispatchers.IO) { walletViewModel.getSubaddresses() }
    }

    // Derive address and label from subaddresses list
    // subaddresses[0] = primary address, subaddresses[1+] = subaddresses
    val address: String
    val addressLabel: String
    val sub = subaddresses.getOrNull(selectedAddressIndex)
    if (sub != null) {
        address = sub.address
        addressLabel = if (selectedAddressIndex == 0) "Main Address" else "Subaddress #$selectedAddressIndex"
    } else {
        // Saved index is out of bounds or list not loaded yet — fall back to primary
        address = subaddresses.firstOrNull()?.address ?: ""
        addressLabel = "Main Address"
        if (subaddresses.isNotEmpty() && selectedAddressIndex != 0) {
            LaunchedEffect(Unit) {
                prefs.edit().putInt("selected_address_index", 0).apply()
            }
        }
    }

    var requestAmount by remember { mutableStateOf("") }
    var isFiatMode by remember { mutableStateOf(false) }
    var fiatAmount by remember { mutableStateOf("") }
    val currentPrice by walletViewModel.currentPrice.collectAsState()
    val selectedCurrency by walletViewModel.selectedCurrency.collectAsState()
    val xmrPrice = currentPrice?.price
    val currencySymbol = selectedCurrency.symbol

    val qrData = remember(address, requestAmount) {
        if (address.isBlank()) ""
        else if (requestAmount.isBlank()) address
        else "monero:$address?tx_amount=$requestAmount"
    }

    // Generate QR code off the main thread
    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(qrData) {
        if (qrData.isNotBlank()) {
            qrBitmap = withContext(Dispatchers.Default) {
                generateQRCode(qrData, 512, context)
            }
        } else {
            qrBitmap = null
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Receive XMR",
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
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            // QR Code
            GlassCard(
                modifier = Modifier.size(280.dp),
                cornerRadius = 20.dp
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val bitmap = qrBitmap
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "QR Code",
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(12.dp))
                        )
                    } else {
                        Text(
                            text = "Generating...",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // "Request Amount (optional)" label row with currency toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Request Amount (optional)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.weight(1f))
                if (xmrPrice != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(MoneroOrange.copy(alpha = 0.1f))
                            .clickable {
                                if (isFiatMode) {
                                    isFiatMode = false
                                } else {
                                    val xmrVal = requestAmount.toDoubleOrNull() ?: 0.0
                                    fiatAmount = if (xmrVal > 0) "%.2f".format(xmrVal * xmrPrice) else ""
                                    isFiatMode = true
                                }
                            }
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text("⇅", style = MaterialTheme.typography.labelMedium, color = MoneroOrange)
                        Text(
                            text = if (isFiatMode) "XMR" else selectedCurrency.code.uppercase(),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MoneroOrange
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Amount input field
            if (isFiatMode) {
                OutlinedTextField(
                    value = fiatAmount,
                    onValueChange = {
                        if (it.isEmpty() || it.matches(Regex("^\\d*\\.?\\d{0,2}$"))) {
                            fiatAmount = it
                            val price = xmrPrice
                            if (price != null && price > 0) {
                                val fiatVal = it.toDoubleOrNull() ?: 0.0
                                val xmr = fiatVal / price
                                requestAmount = if (xmr > 0) "%.12f".format(xmr).trimEnd('0').trimEnd('.') else ""
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("0.00") },
                    prefix = { Text(currencySymbol, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    trailingIcon = {
                        if (fiatAmount.isNotBlank()) {
                            IconButton(onClick = { fiatAmount = ""; requestAmount = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MoneroOrange, cursorColor = MoneroOrange, unfocusedBorderColor = MaterialTheme.colorScheme.outline),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )
            } else {
                OutlinedTextField(
                    value = requestAmount,
                    onValueChange = {
                        if (it.isEmpty() || it.matches(Regex("^\\d*\\.?\\d*$"))) requestAmount = it
                    },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("0.0") },
                    suffix = { Text("XMR", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    trailingIcon = {
                        if (requestAmount.isNotBlank()) {
                            IconButton(onClick = { requestAmount = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MoneroOrange, cursorColor = MoneroOrange, unfocusedBorderColor = MaterialTheme.colorScheme.outline),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Address card (truncated, matching iOS)
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                onClick = onSelectAddress
            ) {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = addressLabel,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (address.length > 20) "${address.take(12)}. . .${address.takeLast(8)}" else address.ifBlank { "Loading..." },
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (onSelectAddress != null) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "Select address",
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Copy / Share buttons (tall glass cards with icon above text, matching iOS)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                GlassCard(
                    modifier = Modifier.weight(1f).height(90.dp),
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val copyText = if (requestAmount.isNotBlank()) qrData else address
                        val clip = ClipData.newPlainText("Monero Address", copyText)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Copy", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
                    }
                }

                GlassCard(
                    modifier = Modifier.weight(1f).height(90.dp),
                    onClick = {
                        val shareText = if (requestAmount.isNotBlank()) qrData else address
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, shareText)
                        }
                        context.startActivity(Intent.createChooser(intent, "Share Address"))
                    }
                ) {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, tint = MoneroOrange, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Share", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium, color = MoneroOrange)
                    }
                }
            }

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

private fun generateQRCode(data: String, size: Int, context: Context): Bitmap? {
    return try {
        val hints = mapOf(
            EncodeHintType.MARGIN to 1,
            EncodeHintType.CHARACTER_SET to "UTF-8",
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.H // High error correction for logo overlay
        )
        val writer = QRCodeWriter()
        val bitMatrix = writer.encode(data, BarcodeFormat.QR_CODE, size, size, hints)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bitmap.setPixel(
                    x, y,
                    if (bitMatrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE
                )
            }
        }

        // Add Monero logo overlay in center (22% of QR size)
        addMoneroLogoOverlay(bitmap, context)
    } catch (e: Exception) {
        null
    }
}

private fun addMoneroLogoOverlay(qrBitmap: Bitmap, context: Context): Bitmap {
    val size = qrBitmap.width
    val logoSize = (size * 0.22).toInt()
    val radius = logoSize / 2f

    val result = qrBitmap.copy(Bitmap.Config.ARGB_8888, true)
    val canvas = android.graphics.Canvas(result)
    val cx = size / 2f
    val cy = size / 2f

    // White circle background (slightly larger for border)
    val bgPaint = android.graphics.Paint().apply {
        isAntiAlias = true
        color = android.graphics.Color.WHITE
    }
    canvas.drawCircle(cx, cy, radius + 4f, bgPaint)

    // Draw logo into a circle-clipped bitmap
    val logoDrawable = ContextCompat.getDrawable(context, R.drawable.monero_logo) ?: return result
    val clipped = Bitmap.createBitmap(logoSize, logoSize, Bitmap.Config.ARGB_8888)
    val clipCanvas = android.graphics.Canvas(clipped)

    // Circle clip mask
    val maskPaint = android.graphics.Paint().apply { isAntiAlias = true }
    clipCanvas.drawCircle(radius, radius, radius, maskPaint)
    maskPaint.xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_IN)

    // Draw the drawable centered, filling the circle
    val logoBitmap = Bitmap.createBitmap(logoSize, logoSize, Bitmap.Config.ARGB_8888)
    val logoCanvas = android.graphics.Canvas(logoBitmap)
    logoDrawable.setBounds(0, 0, logoSize, logoSize)
    logoDrawable.draw(logoCanvas)
    clipCanvas.drawBitmap(logoBitmap, 0f, 0f, maskPaint)

    // Place centered on QR
    canvas.drawBitmap(clipped, cx - radius, cy - radius, null)
    return result
}
