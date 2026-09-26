package one.monero.moneroone.ui.screens.settings

import android.content.Context
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
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

data class NodeInfo(
    val uri: String,
    val name: String,
    val isDefault: Boolean = false,
    val hasCredentials: Boolean = false
)

private val DEFAULT_NODES = DefaultNodes.ALL.map { NodeInfo(it.uri, it.name, true) }

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
    val scope = rememberCoroutineScope()
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

    var showAddNodeDialog by remember { mutableStateOf(false) }
    var editingNode by remember { mutableStateOf<NodeInfo?>(null) }

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
            customNodes.clear()
            customNodes.addAll(nodes.map { NodeInfo(it, "Custom Node", false, credentialStore.has(it)) })
        } catch (e: Exception) {
            // Ignore parse errors
        }
    }

    // Benchmark all nodes on screen entry
    LaunchedEffect(customNodes.size) {
        isBenchmarking = true
        val allNodes = DEFAULT_NODES + customNodes
        val results = allNodes.map { node ->
            async {
                node.uri to NodeBenchmark.measure(node.uri, credentialsFor(node))
            }
        }.awaitAll()
        results.forEach { (uri, latency) ->
            latencyMap[uri] = latency
        }
        isBenchmarking = false

        // Auto-select fastest if enabled
        if (autoSelectEnabled) {
            val fastest = results
                .filter { it.second >= 0 }
                .minByOrNull { it.second }
            if (fastest != null && fastest.first != selectedNode) {
                selectedNode = fastest.first
                prefs.edit().putString("selected_node", fastest.first).apply()
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
                    contentDescription = "Back"
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Remote Node",
                style = MaterialTheme.typography.headlineSmall
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Select a remote node for blockchain sync.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Auto-Select Toggle
        GlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 16.dp, shadow = false) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Auto-Select",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Automatically use the fastest node",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                MoneroSwitch(
                    checked = autoSelectEnabled,
                    onCheckedChange = { enabled ->
                        autoSelectEnabled = enabled
                        prefs.edit().putBoolean("auto_select_node", enabled).apply()
                        if (enabled) {
                            // Pick fastest reachable node
                            val fastest = latencyMap.entries
                                .filter { it.value >= 0 }
                                .minByOrNull { it.value }
                            if (fastest != null && fastest.key != selectedNode) {
                                selectedNode = fastest.key
                                prefs.edit().putString("selected_node", fastest.key).apply()
                                onNodeChanged()
                            }
                        }
                    }
                )
            }
        }

        // Default Nodes
        SettingsSectionHeader("Default Nodes")

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

        // Custom Nodes
        SettingsSectionHeader(
            title = "Custom Nodes",
            trailing = {
                IconButton(onClick = { showAddNodeDialog = true }) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add Node",
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
                        text = "No custom nodes added",
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
                        enabled = true,
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
                        onEdit = { editingNode = node },
                        onDelete = {
                            customNodes.remove(node)
                            latencyMap.remove(node.uri)
                            credentialStore.remove(node.uri)
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

        Spacer(modifier = Modifier.height(32.dp))
    }

    // Add Node Dialog
    if (showAddNodeDialog) {
        NodeDialog(
            title = "Add Custom Node",
            confirmLabel = "Add",
            initialUri = "",
            initialCredentials = null,
            takenUris = (customNodes.map { it.uri } + DefaultNodes.URIS).toSet(),
            onConfirm = { uri, credentials ->
                credentialStore.save(uri, credentials)
                customNodes.add(NodeInfo(uri, "Custom Node", false, credentials != null))
                persistCustomNodes()
                showAddNodeDialog = false
                // Benchmark the new node
                scope.launch {
                    latencyMap[uri] = NodeBenchmark.measure(uri, credentials)
                }
            },
            onDismiss = { showAddNodeDialog = false }
        )
    }

    // Edit Node Dialog
    editingNode?.let { node ->
        val previous = remember(node.uri) { credentialStore.load(node.uri) }
        NodeDialog(
            title = "Edit Custom Node",
            confirmLabel = "Save",
            initialUri = node.uri,
            initialCredentials = previous,
            takenUris = (customNodes.map { it.uri } + DefaultNodes.URIS).toSet() - node.uri,
            onConfirm = { uri, credentials ->
                val uriChanged = uri != node.uri
                if (uriChanged) {
                    credentialStore.remove(node.uri)
                    latencyMap.remove(node.uri)
                }
                credentialStore.save(uri, credentials)
                val updated = NodeInfo(uri, "Custom Node", false, credentials != null)
                val index = customNodes.indexOf(node)
                if (index >= 0) customNodes[index] = updated else customNodes.add(updated)
                persistCustomNodes()

                val wasSelected = selectedNode == node.uri
                if (wasSelected && uriChanged) {
                    selectedNode = uri
                    prefs.edit().putString("selected_node", uri).apply()
                }
                // The kit only reads credentials at start, so a change to the
                // live node needs a restart just like a node switch does.
                if (wasSelected && (uriChanged || credentials != previous)) onNodeChanged()
                editingNode = null
                scope.launch {
                    latencyMap[uri] = NodeBenchmark.measure(uri, credentials)
                }
            },
            onDismiss = { editingNode = null }
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
    val tls = DefaultNodes.isTls(node.uri)

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
                        contentDescription = if (tls) "Encrypted" else "Unencrypted",
                        tint = (if (tls) SuccessGreen else WarningYellow).copy(alpha = alpha),
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = node.uri,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha)
                    )
                    if (node.hasCredentials) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.Key,
                            contentDescription = "Requires authentication",
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
                        contentDescription = "Edit",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            if (onDelete != null) {
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = ErrorRed,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            if (isSelected) {
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = MoneroOrange,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun LatencyBadge(latencyMs: Long) {
    val caution = latencyMs in 200 until 500
    val (text, color) = when {
        latencyMs == NodeBenchmark.UNAUTHORIZED -> "Auth failed" to ErrorRed
        latencyMs < 0 -> "Unreachable" to ErrorRed
        latencyMs < 200 -> "${latencyMs}ms" to SuccessGreen
        caution -> "${latencyMs}ms" to WarningYellow
        else -> "${latencyMs}ms" to ErrorRed
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
 * Add / edit sheet for a custom node: URI plus an optional RPC login, the
 * same shape as the iOS AddCustomNodeView (Name, URL, Authentication group).
 */
@Composable
private fun NodeDialog(
    title: String,
    confirmLabel: String,
    initialUri: String,
    initialCredentials: NodeCredentials?,
    takenUris: Set<String>,
    onConfirm: (String, NodeCredentials?) -> Unit,
    onDismiss: () -> Unit
) {
    var nodeUri by remember { mutableStateOf(initialUri) }
    var username by remember { mutableStateOf(initialCredentials?.username ?: "") }
    var password by remember { mutableStateOf(initialCredentials?.password ?: "") }
    var showAuth by remember { mutableStateOf(initialCredentials != null) }
    var showPassword by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var credentialError by remember { mutableStateOf<String?>(null) }
    val parsed = parseNodeInput(nodeUri)
    val fieldColors = moneroTextFieldColors()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = "Enter the node URI (e.g., node.example.com:18081)",
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
                    label = { Text("Node URI") },
                    placeholder = { Text("host:port") },
                    singleLine = true,
                    isError = error != null,
                    supportingText = error?.let { { Text(it, color = ErrorRed) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    colors = fieldColors,
                    modifier = Modifier.fillMaxWidth()
                )
                if (parsed is NodeInput.Valid) {
                    val tls = DefaultNodes.isTls(parsed.uri)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (tls) Icons.Default.Lock else Icons.Default.LockOpen,
                            contentDescription = null,
                            tint = if (tls) SuccessGreen else WarningYellow,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (tls) {
                                "Connection will be encrypted (TLS)"
                            } else {
                                "Connection will be unencrypted (HTTP)"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (tls) SuccessGreen else MaterialTheme.colorScheme.onSurface
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
                        text = "Authentication",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        imageVector = if (showAuth) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (showAuth) "Hide authentication" else "Show authentication",
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
                        label = { Text("Username") },
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
                        label = { Text("Password") },
                        singleLine = true,
                        isError = credentialError != null,
                        supportingText = credentialError?.let { { Text(it, color = ErrorRed) } },
                        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            IconButton(onClick = { showPassword = !showPassword }) {
                                Icon(
                                    imageVector = if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (showPassword) "Hide password" else "Show password"
                                )
                            }
                        },
                        colors = fieldColors,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Only needed for nodes that require RPC credentials",
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
                                    error = "Enter credentials in the fields below, not in the URI"
                                parsed.uri in takenUris ->
                                    error = "This node is already in the list"
                                else -> {
                                    val message = validateNodeCredentials(typedUsername, typedPassword)
                                    if (message != null) {
                                        credentialError = message
                                        showAuth = true
                                    } else {
                                        val credentials = parsed.credentials
                                            ?: if (typed) NodeCredentials(typedUsername, typedPassword) else null
                                        onConfirm(parsed.uri, credentials)
                                    }
                                }
                            }
                        }
                    }
                }
            ) {
                Text(confirmLabel, color = MoneroOrange)
            }
        },
        dismissButton = {
            DismissTextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
