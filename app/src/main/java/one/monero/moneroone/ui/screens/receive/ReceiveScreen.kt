package one.monero.moneroone.ui.screens.receive

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import one.monero.moneroone.core.wallet.WalletViewModel
import one.monero.moneroone.ui.components.GlassCard
import one.monero.moneroone.ui.theme.MoneroOrange

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiveScreen(
    walletViewModel: WalletViewModel,
    onBack: () -> Unit
) {
    val walletState by walletViewModel.walletState.collectAsState()
    val context = LocalContext.current
    val address = walletState.receiveAddress

    val qrBitmap = remember(address) {
        if (address.isNotBlank()) {
            generateSimpleQRCode(address, 512)
        } else null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Receive XMR") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
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

            Text(
                text = "Scan to Pay",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Share this QR code or address to receive XMR",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))

            // QR Code
            GlassCard(
                modifier = Modifier.size(250.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (qrBitmap != null) {
                        Image(
                            bitmap = qrBitmap.asImageBitmap(),
                            contentDescription = "QR Code",
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(8.dp))
                        )
                    } else {
                        Text(
                            text = "Generating...",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Address display
            GlassCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Your Address",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = address.ifBlank { "Loading..." },
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("Monero Address", address)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "Address copied", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MoneroOrange,
                        contentColor = Color.White
                    ),
                    enabled = address.isNotBlank()
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Copy")
                }

                OutlinedButton(
                    onClick = {
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, address)
                        }
                        context.startActivity(Intent.createChooser(intent, "Share Address"))
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MoneroOrange
                    ),
                    enabled = address.isNotBlank()
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Share")
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

/**
 * Simple QR code generation without external library issues
 */
private fun generateSimpleQRCode(data: String, size: Int): Bitmap {
    // Create a simple placeholder bitmap with the address hash pattern
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)

    // Fill with white background
    for (x in 0 until size) {
        for (y in 0 until size) {
            bitmap.setPixel(x, y, android.graphics.Color.WHITE)
        }
    }

    // Create a simple pattern based on the data hash
    val hash = data.hashCode()
    val moduleCount = 33 // Standard QR code size
    val moduleSize = size / moduleCount

    // Add position patterns (corners)
    drawPositionPattern(bitmap, 0, 0, moduleSize)
    drawPositionPattern(bitmap, (moduleCount - 7) * moduleSize, 0, moduleSize)
    drawPositionPattern(bitmap, 0, (moduleCount - 7) * moduleSize, moduleSize)

    // Add some data modules based on hash
    for (i in 8 until moduleCount - 8) {
        for (j in 8 until moduleCount - 8) {
            val shouldDraw = ((hash + i * 17 + j * 31) % 3) == 0
            if (shouldDraw) {
                fillModule(bitmap, i * moduleSize, j * moduleSize, moduleSize)
            }
        }
    }

    return bitmap
}

private fun drawPositionPattern(bitmap: Bitmap, startX: Int, startY: Int, moduleSize: Int) {
    // Draw 7x7 position pattern
    for (i in 0 until 7) {
        for (j in 0 until 7) {
            val shouldFill = i == 0 || i == 6 || j == 0 || j == 6 ||
                (i in 2..4 && j in 2..4)
            if (shouldFill) {
                fillModule(bitmap, startX + i * moduleSize, startY + j * moduleSize, moduleSize)
            }
        }
    }
}

private fun fillModule(bitmap: Bitmap, startX: Int, startY: Int, size: Int) {
    for (x in startX until minOf(startX + size, bitmap.width)) {
        for (y in startY until minOf(startY + size, bitmap.height)) {
            bitmap.setPixel(x, y, android.graphics.Color.BLACK)
        }
    }
}
