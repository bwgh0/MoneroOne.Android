package one.monero.moneroone.ui.screens.wallet

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import one.monero.moneroone.R
import one.monero.moneroone.core.locale.tr
import one.monero.moneroone.core.locale.trRes
import one.monero.moneroone.core.username.*
import one.monero.moneroone.core.wallet.WalletInfo
import one.monero.moneroone.core.wallet.WalletViewModel
import one.monero.moneroone.ui.theme.MoneroOrange
import one.monero.moneroone.ui.components.rememberReduceMotion
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.core.content.FileProvider
import java.io.File
import kotlin.math.roundToInt

@Composable
fun UsernameCard(handle: String, externalAddress: String, modifier: Modifier = Modifier, still: Boolean = false) {
    val orange = MoneroOrange
    val tilt = rememberUsernameTilt(!still && !rememberReduceMotion())
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val ink = if (dark) Color(0xFFECEDEF) else Color(0xFF363C40)
    val secondary = if (dark) Color(0xFFADB4B9) else Color(0xFF687176)
    val lip = if (dark) Color(0xFFA5410B) else Color(0xFFB74C17)
    BoxWithConstraints(modifier.fillMaxWidth().aspectRatio(1000f / 600f).graphicsLayer {
        rotationY = tilt.x * 3; rotationX = -tilt.y * 2
    }.clearAndSetSemantics {
        contentDescription = tr("%s, Monero One username card. %s", handle, externalAddress)
    }) {
        val s = maxWidth.value / 1000f
        // Type follows the card width, not the text size, as on iOS: the card is a picture of a card.
        val density = LocalDensity.current
        fun cardSp(n: Float) = with(density) { (n * s).dp.toSp() }
        val shape = RoundedCornerShape((30*s).dp)
        Box(Modifier.fillMaxSize().drawWithContent {
            drawContent()
            // Core's darker underside: a lip just below the bottom edge, over the shadow (iOS 4f1966e).
            val r = CornerRadius((30*s).dp.toPx())
            val card = Path().apply { addRoundRect(RoundRect(size.toRect(), r)) }
            val below = Path().apply { addRoundRect(RoundRect(size.toRect().translate(0f, (3*s).dp.toPx()), r)) }
            drawPath(Path.combine(PathOperation.Difference, below, card), lip)
        }.shadow((14*s).dp, shape).clip(shape)
            .background(Brush.linearGradient(if (dark) listOf(Color(0xFF343B3F), Color(0xFF262C30), Color(0xFF1A2025))
                else listOf(Color(0xFFF0F2F2), Color(0xFFE4E7E8), Color(0xFFD5DADD))))
            .border((1.5f*s).dp, Color.White.copy(alpha = if (dark) 0.12f else 0.6f), shape)) {
            Box(Modifier.fillMaxSize().padding((44*s).dp).clip(RoundedCornerShape((18*s).dp))
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = if (dark) 0.1f else 0.025f), Color.White.copy(alpha = if (dark) 0.01f else 0.14f))))
                .border((2*s).dp, Color.Black.copy(alpha = if (dark) 0.25f else 0.1f), RoundedCornerShape((18*s).dp)))
            Canvas(Modifier.fillMaxSize()) {
                drawRect(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.12f), Color.Transparent),
                    center = Offset(size.width*(0.3f+tilt.x*0.2f), size.height*(0.18f+tilt.y*0.2f)), radius = size.width*0.7f))
            }
            Image(painterResource(R.drawable.monero_mark), contentDescription = null,
                modifier = Modifier.offset((87*s).dp, (87*s).dp).size((48*s).dp))
            Text("MONERO ONE", color = secondary, fontSize = cardSp(17f), fontWeight = FontWeight.Medium, letterSpacing = cardSp(2f),
                modifier = Modifier.offset((88*s).dp, (93*s).dp).width((824*s).dp), textAlign = TextAlign.End)
            Text(buildAnnotatedString { withStyle(SpanStyle(color = MoneroOrange)) { append("@") }; withStyle(SpanStyle(color = ink)) { append(handle) } },
                // Inter's widest capitals need this fixed size to stay inside the inset at 15 characters.
                fontSize = cardSp(51f), fontWeight = FontWeight.SemiBold, letterSpacing = cardSp(-1.8f), maxLines = 1,
                modifier = Modifier.offset((88*s).dp, (248*s).dp))
            Text(externalAddress, color = secondary, fontSize = cardSp(38f), letterSpacing = cardSp(-0.5f), maxLines = 1,
                modifier = Modifier.offset((88*s).dp, (328*s).dp))
            Canvas(Modifier.fillMaxSize()) {
                val scale = size.width / 1000f
                drawLine(orange, Offset(88*scale, 510*scale), Offset(116*scale, 510*scale), 4*scale)
                drawLine(orange, Offset(884*scale, 510*scale), Offset(912*scale, 510*scale), 4*scale)
                val edge = Path().apply {
                    moveTo(0f, size.height - 22*scale)
                    quadraticTo(0f, size.height - 2*scale, 30*scale, size.height - 2*scale)
                    lineTo(size.width - 30*scale, size.height - 2*scale)
                    quadraticTo(size.width, size.height - 2*scale, size.width, size.height - 22*scale)
                }
                drawPath(edge, orange, style = Stroke(5*scale))
            }
        }
    }
}

/**
 * Records the card as drawn for the share image, with a margin around it so the lip and the
 * shadow show: 40 on a 1000-wide card, as iOS renders 1000 x 600 with padding 40.
 * Pass the modifier to [UsernameCard]; the function returns the share bitmap.
 */
@Composable
internal fun rememberUsernameCardShare(): Pair<Modifier, suspend () -> android.graphics.Bitmap> {
    val layer = rememberGraphicsLayer()
    val shareLayer = rememberGraphicsLayer()
    val density = LocalDensity.current
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    return remember(layer, shareLayer, density, dark) {
        var card = Size.Zero
        var margin = 0f
        val capture = Modifier.drawWithContent {
            margin = (size.width * 0.04f).roundToInt().toFloat()
            card = size
            layer.record(IntSize((size.width + 2 * margin).roundToInt(), (size.height + 2 * margin).roundToInt())) {
                translate(margin, margin) { this@drawWithContent.drawContent() }
            }
            translate(-margin, -margin) { drawLayer(layer) }
        }
        capture to suspend {
            // The card's Modifier.shadow does not reach the bitmap, so the share image draws the
            // iOS card shadow under it: black 15% (35% dark), radius 14, y 12 on a 1000-wide card.
            val s = card.width / 1000f
            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = (if (dark) Color(0xFF262C30) else Color(0xFFE4E7E8)).toArgb()
                setShadowLayer(14 * s, 0f, 12 * s, Color.Black.copy(alpha = if (dark) 0.35f else 0.15f).toArgb())
            }
            shareLayer.record(density, LayoutDirection.Ltr, layer.size) {
                drawIntoCanvas { it.nativeCanvas.drawRoundRect(margin, margin, margin + card.width, margin + card.height, 30 * s, 30 * s, paint) }
                drawLayer(layer)
            }
            shareLayer.toImageBitmap().asAndroidBitmap()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsernameSheet(wallet: WalletInfo, session: Long, wallets: WalletViewModel, model: UsernameViewModel,
                  binding: UsernameBinding?, onDismiss: () -> Unit) {
    val state by model.state.collectAsState()
    val busy = binding == null && state.stage == UsernameStage.CLAIMING
    val (cardCapture, shareCard) = rememberUsernameCardShare()
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true, confirmValueChange = { !busy })
    ModalBottomSheet(onDismissRequest = { if (!busy) onDismiss() }, sheetState = sheet,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().padding(horizontal = 24.dp).padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(24.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                if (binding == null && state.stage == UsernameStage.CONFIRM && model.store.state.value.pending == null) {
                    TextButton(onClick = model::choose) { Text(tr("Back")) }
                } else Spacer(Modifier.width(48.dp))
                TextButton(onClick = onDismiss, enabled = !busy) { Text(tr("Done")) }
            }
            if (binding != null) {
                Text(tr("Your Username"), style = MaterialTheme.typography.titleLarge)
                UsernameCard(binding.displayHandle, binding.externalAddress, cardCapture)
                UsernameWalletNote(wallet)
                UsernameActions(binding, shareCard)
            } else when (state.stage) {
                UsernameStage.WELCOME -> {
                    Icon(Icons.Default.AlternateEmail, null, tint = MoneroOrange, modifier = Modifier.padding(top = 24.dp).size(100.dp))
                    Text(tr("Get Your Username."), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    Text(tr("A short username people can use to pay you."), color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                    UsernamePrimary(tr("Choose My Username"), action = model::choose)
                    TextButton(onClick = onDismiss) { Text(tr("Not Now")) }
                }
                UsernameStage.CHOOSE -> {
                    Text(tr("Choose Your Username"), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                    val valid = remember(state.input) { runCatching { OahCore.normalize(state.input) }.isSuccess }
                    OutlinedTextField(state.input, model::input, modifier = Modifier.fillMaxWidth(), prefix = { Text("@", color = MoneroOrange) },
                        singleLine = true, textStyle = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false, keyboardType = KeyboardType.Ascii),
                        placeholder = { Text(tr("username")) })
                    Text(usernameValidation(state.input), style = MaterialTheme.typography.bodySmall, color = if (valid || state.input.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MoneroOrange)
                    UsernameWalletNote(wallet)
                    UsernamePrimary(tr("Continue"), enabled = valid, action = model::confirm)
                }
                UsernameStage.NO_FREE_CLAIM -> {
                    Text(tr("A free username isn't available on this phone."), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                    UsernamePrimary(tr("Done"), action = onDismiss)
                }
                else -> {
                    val claimed = state.binding
                    if (state.stage == UsernameStage.REVEALED) Text(tr("It's Yours."), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                    UsernameCard(claimed?.displayHandle ?: state.input, claimed?.externalAddress ?: "${state.input.lowercase(java.util.Locale.ROOT)}@monero.one", cardCapture, still = busy)
                    UsernameWalletNote(wallet)
                    if (busy) {
                        CircularProgressIndicator(color = MoneroOrange, modifier = Modifier.size(24.dp))
                        Text(trRes(R.string.username_claiming, "Claiming…"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else if (claimed != null) {
                        UsernameActions(claimed, shareCard)
                        UsernamePrimary(tr("Done"), action = onDismiss)
                    } else {
                        Text(tr("Usernames are permanent."))
                        Text(tr("This is a local development claim."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        UsernamePrimary(tr("Claim @%s", state.input)) { model.claim(wallet, session, wallets) }
                    }
                }
            }
            if (binding == null && state.error != null && state.stage != UsernameStage.NO_FREE_CLAIM) {
                Text(state.error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable private fun UsernameWalletNote(wallet: WalletInfo) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(tr("Payments go to"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("${wallet.emoji} ${wallet.name}", fontWeight = FontWeight.Medium)
    }
}
@Composable private fun UsernamePrimary(title: String, enabled: Boolean = true, action: () -> Unit) {
    Button(onClick = action, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp),
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.onSurface, contentColor = MaterialTheme.colorScheme.surface)) {
        Text(title, fontWeight = FontWeight.SemiBold)
    }
}
@Composable private fun UsernameActions(binding: UsernameBinding, shareCard: suspend () -> android.graphics.Bitmap) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var menu by remember { mutableStateOf(false) }
    var copied by remember { mutableStateOf(false) }
    var sharing by remember { mutableStateOf(false) }
    var shareError by remember { mutableStateOf(false) }
    fun copy(value: String) {
        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Username", value))
        menu = false; copied = true
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.weight(1f)) {
                OutlinedButton(onClick = { menu = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp)) {
                    Icon(Icons.Default.ContentCopy, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(tr("Copy"))
                }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem(text = { Text("${binding.shortName} · Monero One") }, onClick = { copy(binding.shortName) })
                    DropdownMenuItem(text = { Text(tr("%s · Other apps", binding.externalAddress)) }, onClick = { copy(binding.externalAddress) })
                    DropdownMenuItem(text = { Text(tr("Share link")) }, onClick = { copy(binding.shareUrl) })
                }
            }
            OutlinedButton(onClick = {
                sharing = true; shareError = false
                scope.launch {
                    try {
                        val bitmap = shareCard()
                        val uri = withContext(Dispatchers.IO) {
                            val directory = File(context.cacheDir, "shared").apply { mkdirs() }
                            val file = File(directory, "username-card.png")
                            file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                            FileProvider.getUriForFile(context, "${context.packageName}.qrshare", file)
                        }
                        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                            type = "image/png"; putExtra(Intent.EXTRA_STREAM, uri)
                            putExtra(Intent.EXTRA_TEXT, "${binding.shortName}\n${binding.externalAddress}\n${binding.shareUrl}")
                            clipData = ClipData.newUri(context.contentResolver, "Username card", uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }, tr("Share username")))
                    } catch (_: Exception) { shareError = true }
                    finally { sharing = false }
                }
            }, enabled = !sharing, modifier = Modifier.weight(1f).heightIn(min = 54.dp)) {
                Icon(Icons.Default.IosShare, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(tr("Share"))
            }
        }
        if (copied) Text(tr("Copied"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (shareError) Text(tr("The card couldn't be shared. Please try again."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
}

/** Follows the same relative attitude as iOS, and stops when the sheet or app leaves. */
@Composable private fun rememberUsernameTilt(enabled: Boolean): Offset {
    val context = LocalContext.current
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    var tilt by remember { mutableStateOf(Offset.Zero) }
    DisposableEffect(context, lifecycle, enabled) {
        val manager = context.getSystemService(Context.SENSOR_SERVICE) as android.hardware.SensorManager
        val sensor = manager.getDefaultSensor(android.hardware.Sensor.TYPE_GAME_ROTATION_VECTOR)
        val rotation = FloatArray(9); val orientation = FloatArray(3)
        var initial: Offset? = null
        val listener = object : android.hardware.SensorEventListener {
            override fun onAccuracyChanged(sensor: android.hardware.Sensor?, accuracy: Int) { }
            override fun onSensorChanged(event: android.hardware.SensorEvent) {
                android.hardware.SensorManager.getRotationMatrixFromVector(rotation, event.values)
                android.hardware.SensorManager.getOrientation(rotation, orientation)
                val current = Offset(orientation[2], orientation[1])
                if (initial == null) initial = current
                val delta = current - initial!!
                tilt = Offset((delta.x*2).coerceIn(-1f,1f), (delta.y*2).coerceIn(-1f,1f))
            }
        }
        fun update() {
            manager.unregisterListener(listener); initial = null; tilt = Offset.Zero
            if (enabled && sensor != null && lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) {
                manager.registerListener(listener, sensor, 33_333)
            }
        }
        val observer = androidx.lifecycle.LifecycleEventObserver { _,_ -> update() }
        lifecycle.addObserver(observer); update()
        onDispose { lifecycle.removeObserver(observer); manager.unregisterListener(listener) }
    }
    return tilt
}
private fun usernameValidation(input: String): String {
    if (input.isEmpty()) return tr("3–15 characters. Letters, numbers and _.")
    return try { OahCore.normalize(input); tr("3–15 characters. Availability is checked when you claim.") }
    catch (e: OahException) { when (e.code) {
        "handle.too_short" -> tr("Use at least 3 characters.")
        "handle.too_long" -> tr("Use no more than 15 characters.")
        "handle.first_not_letter" -> tr("Start with a letter.")
        "handle.double_underscore" -> tr("Use only one _ in a row.")
        "handle.blocked" -> tr("This username is reserved to protect users from scams.")
        else -> tr("Use only a–z, 0–9 and _.")
    } }
}
