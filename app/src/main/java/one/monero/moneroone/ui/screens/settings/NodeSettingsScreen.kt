package one.monero.moneroone.ui.screens.settings

import one.monero.moneroone.core.locale.tr
import android.content.Context
import android.os.Build
import android.view.View
import android.view.ViewTreeObserver
import android.view.Window
import android.view.WindowManager
import androidx.annotation.RequiresApi
import androidx.annotation.VisibleForTesting
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.currentCoroutineContext
import one.monero.moneroone.core.network.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
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
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import one.monero.moneroone.core.node.CustomNodeNames
import one.monero.moneroone.core.node.CustomNodeTls
import one.monero.moneroone.core.node.NodeBenchmark
import one.monero.moneroone.core.node.NodeCredentialStore
import one.monero.moneroone.core.node.NodeCredentials
import one.monero.moneroone.core.node.NodeInput
import one.monero.moneroone.core.node.parseNodeInput
import one.monero.moneroone.core.node.validateNodeCredentials
import one.monero.moneroone.core.wallet.DefaultNodes
import one.monero.moneroone.core.wallet.SecurePrefs
import one.monero.moneroone.ui.components.CapsuleShape
import one.monero.moneroone.ui.components.DismissTextButton
import one.monero.moneroone.ui.components.GlassCard
import one.monero.moneroone.ui.components.MoneroSwitch
import one.monero.moneroone.ui.components.MoneroTextField
import one.monero.moneroone.ui.components.moneroTextFieldColors
import one.monero.moneroone.ui.theme.ErrorRed
import one.monero.moneroone.ui.theme.MoneroOrange
import one.monero.moneroone.ui.theme.MoneroTheme
import one.monero.moneroone.ui.theme.SuccessGreen
import one.monero.moneroone.ui.theme.WarningYellow

/** [tls]: the app shows and checks the node as TLS ([DefaultNodes.isTls]). */
data class NodeInfo(
    val uri: String,
    val name: String,
    val isDefault: Boolean = false,
    val hasCredentials: Boolean = false,
    val tls: Boolean = false
)

/** What the user typed in the Add or Edit Node dialog. */
@Stable
internal class NodeDraft(name: String, uri: String, credentials: NodeCredentials?) {
    var name by mutableStateOf(name)
    var nodeUri by mutableStateOf(uri)
    var username by mutableStateOf(credentials?.username ?: "")
    var password by mutableStateOf(credentials?.password ?: "")
    var showAuth by mutableStateOf(credentials != null)
    var showPassword by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var credentialError by mutableStateOf<String?>(null)
}

/** The open node dialog: the node it edits (null for Add), its saved login, and the draft. */
internal class OpenNodeDialog(val editing: NodeInfo?, val previous: NodeCredentials?, val draft: NodeDraft)

/**
 * Holds the open node dialog. A theme change or a rotation recreates the
 * Activity: a ViewModel outlives that, so the dialog stays open with what the
 * user typed. The password stays in memory, never in the saved-state Bundle.
 */
internal class NodeDialogs : ViewModel() {
    var open by mutableStateOf<OpenNodeDialog?>(null)
}

private val DEFAULT_NODES = DefaultNodes.ALL.map { NodeInfo(it.uri, it.name, true, tls = it.tls) }

@Composable
fun NodeSettingsScreen(
    onBack: () -> Unit,
    onNodeChanged: () -> Unit = {}
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("monero_wallet", Context.MODE_PRIVATE) }
    // Opened lazily by the ViewModel long before this screen (PIN check), so
    // this is the process singleton, not a keystore round trip. Migrating
    // inline credentials must precede the selected-node read below.
    val credentialStore = remember {
        NodeCredentialStore(SecurePrefs.open(context)).also { it.migrateInline(prefs) }
    }
    val nodeNames = remember { CustomNodeNames(prefs) }
    val customTls = remember { CustomNodeTls(prefs) }
    var benchmarkAttempt by remember { mutableIntStateOf(0) }
    val torConfig by TorNetwork.config.collectAsState()
    val json = remember { Json { ignoreUnknownKeys = true } }

    val customNodes = remember { mutableStateListOf<NodeInfo>() }
    val latencyMap = remember { mutableStateMapOf<String, Long>() } // uri -> latency ms, negative = see NodeBenchmark
    var isBenchmarking by remember { mutableStateOf(false) }

    var selectedNode by remember {
        val savedUri = prefs.getString("selected_node", null)
        mutableStateOf(savedUri ?: DefaultNodes.initial(context))
    }

    var autoSelectEnabled by remember {
        mutableStateOf(prefs.getBoolean("auto_select_node", true))
    }

    val dialogs: NodeDialogs = viewModel()

    fun persistCustomNodes() {
        prefs.edit().putString("custom_nodes", json.encodeToString(customNodes.map { it.uri })).apply()
    }

    fun credentialsFor(node: NodeInfo): NodeCredentials? =
        if (node.isDefault) null else credentialStore.load(node.uri)

    // Load custom nodes from prefs
    LaunchedEffect(Unit) {
        val customJson = prefs.getString("custom_nodes", "[]")
        try {
            val nodes = json.decodeFromString<List<String>>(customJson ?: "[]")
            val marked = customTls.all()
            customNodes.clear()
            customNodes.addAll(nodes.map {
                NodeInfo(
                    it, nodeNames.displayName(it, tr("Custom Node")), false, credentialStore.has(it),
                    DefaultNodes.isTls(it, marked)
                )
            })
        } catch (e: Exception) {
            // Ignore parse errors
        }
    }

    // Benchmark all nodes on screen entry. Keyed on what a measurement uses, not
    // the names: a rename must not re-run auto-select, which can switch nodes.
    LaunchedEffect(customNodes.map { Triple(it.uri, it.hasCredentials, it.tls) }, torConfig, benchmarkAttempt) {
        isBenchmarking = true
        latencyMap.clear()
        val allNodes = (DEFAULT_NODES + DefaultNodes.TOR.map { NodeInfo(it.uri, it.name, true) } + customNodes)
            .filter { torConfig.enabled || !isOnionNode(it.uri) }
        val results = allNodes.map { node ->
            async {
                node.uri to NodeBenchmark.measure(node.uri, credentialsFor(node), torConfig, node.tls)
            }
        }.awaitAll()
        currentCoroutineContext().ensureActive()
        results.forEach { (uri, latency) ->
            latencyMap[uri] = latency
        }
        isBenchmarking = false

        // Auto-select fastest if enabled
        if (autoSelectEnabled) {
            val fastest = DefaultNodes.fastest(results.toMap())
            if (fastest != null && fastest != selectedNode) {
                selectedNode = fastest
                prefs.edit().putString("selected_node", fastest).apply()
                onNodeChanged()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MoneroTheme.colors.bgGrouped)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = tr("Back")
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = tr("Remote Node"),
                style = MaterialTheme.typography.headlineSmall
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Auto Select toggle, with no caption (iOS NodeSettingsView)
        GlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 16.dp, shadow = false) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = tr("Auto Select"),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
                MoneroSwitch(
                    checked = autoSelectEnabled,
                    onCheckedChange = { enabled ->
                        autoSelectEnabled = enabled
                        prefs.edit().putBoolean("auto_select_node", enabled).apply()
                        if (enabled) {
                            // Pick fastest reachable node
                            val fastest = DefaultNodes.fastest(latencyMap)
                            if (fastest != null && fastest != selectedNode) {
                                selectedNode = fastest
                                prefs.edit().putString("selected_node", fastest).apply()
                                onNodeChanged()
                            }
                        }
                    }
                )
            }
        }

        // Default Nodes
        SettingsSectionHeader(tr("Default Nodes"))

        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DEFAULT_NODES.forEach { node ->
                NodeItem(
                    node = node,
                    isSelected = node.uri == selectedNode,
                    isBenchmarking = isBenchmarking && node.uri !in latencyMap,
                    latencyMs = latencyMap[node.uri],
                    enabled = true,
                    onSelect = {
                        // Manual pick always wins: turn auto-select off instead of
                        // ignoring the tap (users stuck on a bad node couldn't escape).
                        if (autoSelectEnabled) {
                            autoSelectEnabled = false
                            prefs.edit().putBoolean("auto_select_node", false).apply()
                        }
                        val changed = selectedNode != node.uri
                        selectedNode = node.uri
                        prefs.edit().putString("selected_node", node.uri).apply()
                        if (changed) onNodeChanged()
                    },
                    onEdit = null,
                    onDelete = null
                )
            }
        }

        SettingsSectionHeader(tr("Tor Nodes"))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            DefaultNodes.TOR.forEach { entry ->
                NodeItem(node = NodeInfo(entry.uri, entry.name, true), isSelected = selectedNode == entry.uri,
                    isBenchmarking = torConfig.enabled && isBenchmarking && entry.uri !in latencyMap,
                    latencyMs = latencyMap[entry.uri], enabled = torConfig.enabled,
                    onSelect = {
                        if (autoSelectEnabled) {
                            autoSelectEnabled = false
                            prefs.edit().putBoolean("auto_select_node", false).apply()
                        }
                        val changed = selectedNode != entry.uri
                        selectedNode = entry.uri
                        prefs.edit().putString("selected_node", entry.uri).apply()
                        if (changed) onNodeChanged()
                    }, onEdit = null, onDelete = null)
            }
        }
        if (!torConfig.enabled) Text(tr("Requires Tor"), Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        // Custom Nodes
        SettingsSectionHeader(
            title = tr("Custom Nodes"),
            trailing = {
                IconButton(onClick = { dialogs.open = OpenNodeDialog(null, null, NodeDraft("", "", null)) }) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = tr("Add Node"),
                        tint = MoneroOrange
                    )
                }
            }
        )

        if (customNodes.isEmpty()) {
            GlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 16.dp, shadow = false) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = tr("No custom nodes added"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                customNodes.forEach { node ->
                    NodeItem(
                        node = node,
                        isSelected = node.uri == selectedNode,
                        isBenchmarking = isBenchmarking && node.uri !in latencyMap,
                        latencyMs = latencyMap[node.uri],
                        enabled = torConfig.enabled || !isOnionNode(node.uri),
                        onSelect = {
                            if (autoSelectEnabled) {
                                autoSelectEnabled = false
                                prefs.edit().putBoolean("auto_select_node", false).apply()
                            }
                            val changed = selectedNode != node.uri
                            selectedNode = node.uri
                            prefs.edit().putString("selected_node", node.uri).apply()
                            if (changed) onNodeChanged()
                        },
                        onEdit = {
                            val previous = credentialStore.load(node.uri)
                            // The name the row shows, so a node from before names can be saved as is.
                            // A TLS node shows its https://, as on iOS, so a save keeps it TLS.
                            val shownUri = if (node.tls) "https://${node.uri}" else node.uri
                            dialogs.open = OpenNodeDialog(node, previous, NodeDraft(node.name, shownUri, previous))
                        },
                        onDelete = {
                            customNodes.remove(node)
                            latencyMap.remove(node.uri)
                            credentialStore.remove(node.uri)
                            nodeNames.remove(node.uri)
                            customTls.remove(node.uri)
                            persistCustomNodes()

                            if (selectedNode == node.uri) {
                                val fallback = DefaultNodes.initial(context)
                                selectedNode = fallback
                                prefs.edit().putString("selected_node", fallback).apply()
                            }
                        }
                    )
                }
            }
        }

        // After the node lists, as on iOS. Every change reconnects the wallet on the new route.
        TorProxySection(torConfig, onRetry = { benchmarkAttempt++; onNodeChanged() }) { next ->
            if (!next.enabled && isOnionNode(selectedNode)) {
                selectedNode = DefaultNodes.TOR_OFF_FALLBACK
                prefs.edit().putString("selected_node", selectedNode).apply()
            }
            TorNetwork.save(next)
            latencyMap.clear()
            onNodeChanged()
        }

        Spacer(modifier = Modifier.height(32.dp))
    }

    // Add Node Dialog
    val openDialog = dialogs.open
    if (openDialog != null && openDialog.editing == null) {
        NodeDialog(
            title = tr("Add Node"),
            confirmLabel = tr("Add"),
            draft = openDialog.draft,
            takenUris = (customNodes.map { it.uri } + (DefaultNodes.ALL + DefaultNodes.TOR).map { it.uri }).toSet(),
            torEnabled = torConfig.enabled,
            onConfirm = { name, uri, credentials, tls ->
                credentialStore.save(uri, credentials)
                nodeNames.save(uri, name)
                customTls.save(uri, tls)
                benchmarkAttempt++
                customNodes.add(NodeInfo(uri, name, false, credentials != null, tls))
                persistCustomNodes()
                dialogs.open = null
            },
            onDismiss = { dialogs.open = null }
        )
    }

    // Edit Node Dialog
    openDialog?.editing?.let { node ->
        val previous = openDialog.previous
        NodeDialog(
            title = tr("Edit Node"),
            confirmLabel = tr("Save"),
            draft = openDialog.draft,
            takenUris = (customNodes.map { it.uri } + (DefaultNodes.ALL + DefaultNodes.TOR).map { it.uri }).toSet() - node.uri,
            torEnabled = torConfig.enabled,
            onConfirm = { name, uri, credentials, tls ->
                val uriChanged = uri != node.uri
                val loginChanged = credentials != previous
                val tlsChanged = tls != node.tls
                if (uriChanged) {
                    credentialStore.remove(node.uri)
                    latencyMap.remove(node.uri)
                }
                credentialStore.save(uri, credentials)
                nodeNames.update(node.uri, uri, name, tr("Custom Node"))
                customTls.save(uri, tls, oldUri = node.uri)
                // A rename alone changes no connection, so nothing is measured again.
                if (uriChanged || loginChanged || tlsChanged) benchmarkAttempt++
                val updated = NodeInfo(uri, name, false, credentials != null, tls)
                val index = customNodes.indexOf(node)
                if (index >= 0) customNodes[index] = updated else customNodes.add(updated)
                persistCustomNodes()

                val wasSelected = selectedNode == node.uri
                if (wasSelected && uriChanged) {
                    selectedNode = uri
                    prefs.edit().putString("selected_node", uri).apply()
                }
                // The kit only reads credentials at start, so a change to the
                // live node needs a restart just like a node switch does. A
                // rename alone keeps the wallet on its connection.
                if (wasSelected && (uriChanged || loginChanged)) onNodeChanged()
                dialogs.open = null
            },
            onDismiss = { dialogs.open = null }
        )
    }
}

@Composable
private fun NodeItem(
    node: NodeInfo,
    isSelected: Boolean,
    isBenchmarking: Boolean,
    latencyMs: Long?,
    enabled: Boolean,
    onSelect: () -> Unit,
    onEdit: (() -> Unit)?,
    onDelete: (() -> Unit)?
) {
    val alpha = if (enabled) 1f else 0.6f
    // Tor encrypts the whole path to an onion service.
    val tls = node.tls || isOnionNode(node.uri)

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = if (enabled) onSelect else null,
        cornerRadius = 16.dp,
        shadow = false
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Cloud,
                contentDescription = null,
                tint = (if (isSelected) MoneroOrange else MaterialTheme.colorScheme.onSurface).copy(alpha = alpha),
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = node.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (tls) Icons.Default.Lock else Icons.Default.LockOpen,
                        contentDescription = if (tls) tr("Encrypted") else tr("Unencrypted"),
                        tint = (if (tls) SuccessGreen else WarningYellow).copy(alpha = alpha),
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = shortOnionUri(node.uri),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (node.hasCredentials) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.Key,
                            contentDescription = tr("Requires authentication"),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }

            // Latency indicator
            if (isBenchmarking) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MoneroOrange,
                    strokeWidth = 2.dp
                )
            } else if (latencyMs != null) {
                LatencyBadge(latencyMs)
            }

            if (onEdit != null) {
                IconButton(onClick = onEdit) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = tr("Edit"),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            if (onDelete != null) {
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = tr("Delete"),
                        tint = ErrorRed,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            if (isSelected) {
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = tr("Selected"),
                    tint = MoneroOrange,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/** A 56-letter onion name keeps its start and its end on one line, as iOS truncates it in the middle. */
private fun shortOnionUri(uri: String): String {
    val end = uri.indexOf(".onion", ignoreCase = true)
    return if (!isOnionNode(uri) || end <= 16) uri else uri.take(8) + "…" + uri.substring(end - 6)
}

@Composable
private fun LatencyBadge(latencyMs: Long) {
    val caution = latencyMs in 200 until 500
    val (text, color) = when {
        latencyMs == NodeBenchmark.UNAUTHORIZED -> tr("Auth failed") to ErrorRed
        latencyMs < 0 -> tr("Unreachable") to ErrorRed
        latencyMs < 200 -> tr("%sms", latencyMs) to SuccessGreen
        caution -> tr("%sms", latencyMs) to WarningYellow
        else -> tr("%sms", latencyMs) to ErrorRed
    }

    // A chip tinted in its hue. Yellow is never a text color, so a slow
    // node's label keeps the label color on its yellow tint.
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = if (caution) MaterialTheme.colorScheme.onSurface else color,
        modifier = Modifier
            .clip(CapsuleShape)
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    )
}

/**
 * Add / edit sheet for a custom node: a name, the URI and an optional RPC
 * login, the same shape as the iOS AddCustomNodeView (Name, URL,
 * Authentication group). As on iOS, the name is required.
 */
@VisibleForTesting
@Composable
internal fun NodeDialog(
    title: String,
    confirmLabel: String,
    draft: NodeDraft,
    takenUris: Set<String>,
    torEnabled: Boolean,
    onConfirm: (name: String, uri: String, credentials: NodeCredentials?, tls: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    // The state lives in [draft], never here, so it outlives this composition.
    var name by draft::name
    var nodeUri by draft::nodeUri
    var username by draft::username
    var password by draft::password
    var showAuth by draft::showAuth
    var showPassword by draft::showPassword
    var error by draft::error
    var credentialError by draft::credentialError
    val parsed = parseNodeInput(nodeUri)
    val hasName = name.isNotBlank()
    // tokens.json: a field in a dialog (fillElevated) is #F2F2F7 light and
    // #3A3A3C dark, so it never matches the dialog and sits one step above
    // it in dark mode, as fields do on cards.
    val fieldColors = moneroTextFieldColors(
        containerColor = if (MoneroTheme.isDark) MaterialTheme.colorScheme.surfaceContainerHighest
        else MaterialTheme.colorScheme.surfaceVariant
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            // The dialog shrinks above the keyboard: the fields scroll, and Add/Save and Cancel stay in view.
            ResizeDialogForKeyboard()
            val scrollState = rememberScrollState()
            Column(modifier = Modifier.keepScrollWhenRemeasured(scrollState).verticalScroll(scrollState)) {
                MoneroTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(tr("Name")) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    colors = fieldColors,
                    modifier = Modifier.fillMaxWidth()
                )
                // Wider than the gap under the hint, so the URI hint reads with the URI field below it.
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = tr("Enter the node URI (e.g., node.example.com:18081)"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                MoneroTextField(
                    value = nodeUri,
                    onValueChange = {
                        nodeUri = it
                        error = null
                    },
                    label = { Text(tr("Node URI")) },
                    placeholder = { Text("host:port") },
                    singleLine = true,
                    isError = error != null,
                    supportingText = error?.let { { Text(it, color = ErrorRed) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    colors = fieldColors,
                    modifier = Modifier.fillMaxWidth()
                )
                if (parsed is NodeInput.Valid) {
                    // Tor encrypts the whole path to an onion service: it reads "Tor" or
                    // "Requires Tor", the words iOS uses for onion nodes, never "unencrypted".
                    val onion = isOnionNode(parsed.uri)
                    val tls = parsed.tls
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (tls || onion) Icons.Default.Lock else Icons.Default.LockOpen,
                            contentDescription = null,
                            tint = when {
                                onion -> if (torEnabled) SuccessGreen else MaterialTheme.colorScheme.onSurfaceVariant
                                tls -> SuccessGreen
                                else -> WarningYellow
                            },
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = when {
                                onion -> if (torEnabled) tr("Tor") else tr("Requires Tor")
                                tls -> tr("Connection will be encrypted (TLS)")
                                else -> tr("Connection will be unencrypted (HTTP)")
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = when {
                                onion -> MaterialTheme.colorScheme.onSurfaceVariant
                                tls -> SuccessGreen
                                else -> MaterialTheme.colorScheme.onSurface
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Authentication (collapsed unless the node already has a login)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showAuth = !showAuth }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Key,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = tr("Authentication"),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        imageVector = if (showAuth) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (showAuth) tr("Hide authentication") else tr("Show authentication"),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (showAuth) {
                    Spacer(modifier = Modifier.height(4.dp))
                    MoneroTextField(
                        value = username,
                        onValueChange = {
                            username = it
                            credentialError = null
                        },
                        label = { Text(tr("Username")) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                        colors = fieldColors,
                        modifier = Modifier.fillMaxWidth()
                    )
                    // Twice the label gap, so the Password label reads with its own field.
                    Spacer(modifier = Modifier.height(16.dp))
                    MoneroTextField(
                        value = password,
                        onValueChange = {
                            password = it
                            credentialError = null
                        },
                        label = { Text(tr("Password")) },
                        singleLine = true,
                        isError = credentialError != null,
                        supportingText = credentialError?.let { { Text(it, color = ErrorRed) } },
                        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            IconButton(onClick = { showPassword = !showPassword }) {
                                Icon(
                                    imageVector = if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (showPassword) tr("Hide password") else tr("Show password")
                                )
                            }
                        },
                        colors = fieldColors,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = tr("Only for nodes that need a login."),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    when (parsed) {
                        is NodeInput.Invalid -> error = parsed.message
                        is NodeInput.Valid -> {
                            val typedUsername = username.trim()
                            val typedPassword = password.trim()
                            val typed = typedUsername.isNotEmpty() || typedPassword.isNotEmpty()
                            when {
                                parsed.credentials != null && typed ->
                                    error = tr("Enter credentials in the fields below, not in the URI")
                                parsed.uri in takenUris ->
                                    error = tr("This node is already in the list")
                                else -> {
                                    val message = validateNodeCredentials(typedUsername, typedPassword)
                                    if (message != null) {
                                        credentialError = message
                                        showAuth = true
                                    } else {
                                        val credentials = parsed.credentials
                                            ?: if (typed) NodeCredentials(typedUsername, typedPassword) else null
                                        onConfirm(name.trim(), parsed.uri, credentials, parsed.tls)
                                    }
                                }
                            }
                        }
                    }
                },
                // As on iOS, Add and Save stay off until the node has a name.
                enabled = hasName
            ) {
                Text(confirmLabel, color = if (hasName) MoneroOrange else MoneroTheme.colors.gray)
            }
        },
        dismissButton = {
            DismissTextButton(onClick = onDismiss) {
                Text(tr("Cancel"))
            }
        }
    )
}

/**
 * Makes the dialog this is called in resize for the keyboard instead of panning. A dialog window pans by
 * default: it moves up until the focused field clears the keyboard, which leaves its buttons under the
 * keyboard. Resized, it fits above the keyboard, its scrolling content gives way, and its buttons stay in
 * view. When the dialog misses that resize, [KeyboardResizeCatchUp] gets it. Nothing changes while the
 * keyboard is closed.
 */
@Composable
@NonRestartableComposable
private fun ResizeDialogForKeyboard() {
    val window = (LocalView.current.parent as? DialogWindowProvider)?.window
    // On every composition: below API 31 the dialog puts its default mode back when it updates.
    SideEffect {
        val dialogWindow = window ?: return@SideEffect
        val mode = dialogWindow.attributes.softInputMode
        // ADJUST_RESIZE is deprecated for windows that fit the keyboard inset themselves. A floating
        // dialog is laid out by the system, which resizes it only in this mode.
        @Suppress("DEPRECATION")
        val resize = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        val adjust = WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST
        if ((mode and adjust) != resize) dialogWindow.setSoftInputMode((mode and adjust.inv()) or resize)
    }
    if (window != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        DisposableEffect(window) {
            val catchUp = KeyboardResizeCatchUp(window)
            onDispose { catchUp.remove() }
        }
    }
}

/**
 * Lays the dialog in [window] out again when it missed its resize for the keyboard. On API 36 the window
 * manager sometimes moves and shrinks the window above the keyboard, but the new frame never gets to the
 * dialog. The dialog then stays laid out at its full height: the fields cannot scroll, and the focused
 * field and the buttons sit under the keyboard. A window resized for the keyboard ends where the keyboard
 * starts, so a keyboard inset that stays shows the missed resize. Sending the window attributes again
 * makes the dialog fetch its frame and lay out to it. This tries a few times for each inset, so a window
 * that the system does not resize gets no loop.
 */
@RequiresApi(Build.VERSION_CODES.R)
private class KeyboardResizeCatchUp(private val window: Window) : ViewTreeObserver.OnPreDrawListener, Runnable {
    private val root: View = window.decorView
    private val observer: ViewTreeObserver = root.viewTreeObserver

    /** The keyboard inset that the checks run for. */
    private var inset = 0

    /** The times this sent the attributes for [inset]. */
    private var tries = 0

    init {
        observer.addOnPreDrawListener(this)
    }

    override fun onPreDraw(): Boolean {
        track(keyboardInset())
        return true
    }

    override fun run() {
        val now = keyboardInset()
        if (now != inset) {
            track(now)
        } else if (now > 0 && tries < MAX_TRIES) {
            tries++
            // The same attributes again: the dialog then asks the window manager for its frame.
            window.attributes = window.attributes
            if (tries < MAX_TRIES) root.postDelayed(this, SETTLE_MILLIS shl tries)
        }
    }

    fun remove() {
        root.removeCallbacks(this)
        if (observer.isAlive) observer.removeOnPreDrawListener(this)
        else root.viewTreeObserver.removeOnPreDrawListener(this)
    }

    private fun track(now: Int) {
        if (now == inset) return
        inset = now
        tries = 0
        root.removeCallbacks(this)
        // The system resize comes a few frames after the inset. Check only after it had time to come.
        if (now > 0) root.postDelayed(this, SETTLE_MILLIS)
    }

    private fun keyboardInset(): Int = root.rootWindowInsets?.getInsets(android.view.WindowInsets.Type.ime())?.bottom ?: 0

    private companion object {
        const val SETTLE_MILLIS = 100L
        const val MAX_TRIES = 3
    }
}

/**
 * Keeps the scroll position of [state] in a dialog resized for the keyboard. On each layout the system
 * measures that dialog twice: at full height first, where the fields fit and [state] falls back to 0, and
 * then at its frame above the keyboard. This puts the position back on the second pass, so a drag or the
 * scroll to a focused field holds, and the fields under the fold stay in reach.
 */
@VisibleForTesting
internal fun Modifier.keepScrollWhenRemeasured(state: ScrollState): Modifier = this then KeepScrollElement(state)

private data class KeepScrollElement(val state: ScrollState) : ModifierNodeElement<KeepScrollNode>() {
    override fun create() = KeepScrollNode(state)

    override fun update(node: KeepScrollNode) {
        node.state = state
    }
}

private class KeepScrollNode(var state: ScrollState) : Modifier.Node(), LayoutModifierNode {
    /** The position a measure pass cut back since the last placement. */
    private var cutFrom = 0

    override fun MeasureScope.measure(measurable: Measurable, constraints: Constraints): MeasureResult {
        // Read without observation: the position changes in every pass, and an observed read would
        // remeasure this node again and again.
        val before = Snapshot.withoutReadObservation { state.value }
        val placeable = measurable.measure(constraints)
        Snapshot.withoutReadObservation {
            if (state.value < before) {
                cutFrom = maxOf(cutFrom, before)
            } else if (cutFrom > state.value) {
                state.dispatchRawDelta((minOf(cutFrom, state.maxValue) - state.value).toFloat())
            }
        }
        return layout(placeable.width, placeable.height) {
            cutFrom = 0
            placeable.place(0, 0)
        }
    }
}
