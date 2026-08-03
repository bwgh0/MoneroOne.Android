package one.monero.moneroone.ui.screens.settings

import android.content.Context
import android.widget.Toast
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import one.monero.moneroone.core.wallet.DefaultNodes
import one.monero.moneroone.ui.components.GlassCard
import one.monero.moneroone.ui.theme.ErrorRed
import one.monero.moneroone.ui.theme.MoneroOrange
import one.monero.moneroone.ui.theme.SuccessGreen
import one.monero.moneroone.ui.theme.WarningYellow
import java.net.HttpURLConnection
import java.net.URL

data class NodeInfo(
    val uri: String,
    val name: String,
    val isDefault: Boolean = false
)

private val DEFAULT_NODES = DefaultNodes.ALL.map { NodeInfo(it.uri, it.name, true) }

@Composable
fun NodeSettingsScreen(
    onBack: () -> Unit,
    onNodeChanged: () -> Unit = {}
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("monero_wallet", Context.MODE_PRIVATE) }
    val scope = rememberCoroutineScope()
    val json = remember { Json { ignoreUnknownKeys = true } }

    val customNodes = remember { mutableStateListOf<NodeInfo>() }
    val latencyMap = remember { mutableStateMapOf<String, Long>() } // uri -> latency ms, -1 = unreachable
    var isBenchmarking by remember { mutableStateOf(false) }

    var selectedNode by remember {
        val savedUri = prefs.getString("selected_node", null)
        mutableStateOf(savedUri ?: DefaultNodes.initial(context))
    }

    var autoSelectEnabled by remember {
        mutableStateOf(prefs.getBoolean("auto_select_node", true))
    }

    var showAddNodeDialog by remember { mutableStateOf(false) }

    // Load custom nodes from prefs
    LaunchedEffect(Unit) {
        val customJson = prefs.getString("custom_nodes", "[]")
        try {
            val nodes = json.decodeFromString<List<String>>(customJson ?: "[]")
            customNodes.clear()
            customNodes.addAll(nodes.map { NodeInfo(it, "Custom Node", false) })
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
                node.uri to benchmarkNode(node.uri)
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
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Select a remote node for blockchain sync.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            modifier = Modifier.padding(horizontal = 4.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Auto-Select Toggle
        GlassCard(modifier = Modifier.fillMaxWidth()) {
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
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
                Switch(
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
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = MoneroOrange,
                        uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                        uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Default Nodes
        SectionLabel("DEFAULT NODES")

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
                    onDelete = null
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Custom Nodes
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SectionLabel("CUSTOM NODES")
            IconButton(onClick = { showAddNodeDialog = true }) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add Node",
                    tint = MoneroOrange
                )
            }
        }

        if (customNodes.isEmpty()) {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No custom nodes added",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
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
                        onDelete = {
                            customNodes.remove(node)
                            latencyMap.remove(node.uri)
                            val uris = customNodes.map { it.uri }
                            prefs.edit().putString(
                                "custom_nodes",
                                json.encodeToString(uris)
                            ).apply()

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
        AddNodeDialog(
            onConfirm = { uri ->
                val newNode = NodeInfo(uri, "Custom Node", false)
                customNodes.add(newNode)
                val uris = customNodes.map { it.uri }
                prefs.edit().putString(
                    "custom_nodes",
                    json.encodeToString(uris)
                ).apply()
                showAddNodeDialog = false
                // Benchmark the new node
                scope.launch {
                    latencyMap[uri] = benchmarkNode(uri)
                }
            },
            onDismiss = { showAddNodeDialog = false }
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
        letterSpacing = MaterialTheme.typography.labelMedium.letterSpacing * 1.5f,
        modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp)
    )
}

@Composable
private fun NodeItem(
    node: NodeInfo,
    isSelected: Boolean,
    isBenchmarking: Boolean,
    latencyMs: Long?,
    enabled: Boolean,
    onSelect: () -> Unit,
    onDelete: (() -> Unit)?
) {
    val alpha = if (enabled) 1f else 0.6f
    val tls = DefaultNodes.isTls(node.uri)

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = if (enabled) onSelect else null
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
                    color = (if (isSelected) MoneroOrange else MaterialTheme.colorScheme.onSurface).copy(alpha = alpha)
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
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f * alpha)
                    )
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
    val (text, color) = when {
        latencyMs < 0 -> "Unreachable" to ErrorRed
        latencyMs < 200 -> "${latencyMs}ms" to SuccessGreen
        latencyMs < 500 -> "${latencyMs}ms" to WarningYellow
        else -> "${latencyMs}ms" to ErrorRed
    }

    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = color,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

@Composable
private fun AddNodeDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var nodeUri by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val parsed = parseNodeInput(nodeUri)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Custom Node") },
        text = {
            Column {
                Text(
                    text = "Enter the node URI (e.g., node.example.com:18081)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
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
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MoneroOrange,
                        cursorColor = MoneroOrange
                    ),
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
                            color = if (tls) SuccessGreen else WarningYellow
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    when (parsed) {
                        is NodeInput.Valid -> onConfirm(parsed.uri)
                        is NodeInput.Invalid -> error = parsed.message
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = MoneroOrange)
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

private sealed interface NodeInput {
    data class Valid(val uri: String) : NodeInput
    data class Invalid(val message: String) : NodeInput
}

// Standard hostname labels, with underscores tolerated for LAN hosts that use them.
private val NODE_HOST_REGEX = Regex(
    "^[A-Za-z0-9_]([A-Za-z0-9_-]{0,61}[A-Za-z0-9_])?" +
        "(\\.[A-Za-z0-9_]([A-Za-z0-9_-]{0,61}[A-Za-z0-9_])?)*$"
)

/**
 * Validates custom node input and normalizes it to the [user:pass@]host:port
 * form MoneroKit's node parser accepts (IPv6 hosts in brackets). Loopback and
 * private-range hosts are deliberately allowed: self-hosted nodes are supported.
 */
private fun parseNodeInput(raw: String): NodeInput {
    var value = raw.trim()
    if (value.isEmpty()) return NodeInput.Invalid("Node URI required")
    if (value.any { it.isWhitespace() }) return NodeInput.Invalid("URI must not contain spaces")

    var https = false
    when {
        value.startsWith("http://", ignoreCase = true) -> value = value.substring(7)
        value.startsWith("https://", ignoreCase = true) -> {
            https = true
            value = value.substring(8)
        }
        value.contains("://") -> return NodeInput.Invalid("Only http:// or https:// nodes are supported")
    }
    value = value.removeSuffix("/")
    if (value.contains('/')) return NodeInput.Invalid("Use host:port only, without a path")

    var credentials = ""
    if (value.count { it == '@' } > 1) return NodeInput.Invalid("Credentials must be user:pass@host:port")
    val at = value.indexOf('@')
    if (at >= 0) {
        val userPass = value.substring(0, at)
        value = value.substring(at + 1)
        val colon = userPass.indexOf(':')
        if (colon <= 0 || colon != userPass.lastIndexOf(':') || colon == userPass.length - 1) {
            return NodeInput.Invalid("Credentials must be user:pass@host:port")
        }
        credentials = "$userPass@"
    }

    val hostPart: String
    val portText: String
    if (value.startsWith("[")) {
        val end = value.indexOf(']')
        val host = if (end > 1) value.substring(1, end) else ""
        if (host.isEmpty() || !host.contains(':') || !host.all { it in "0123456789abcdefABCDEF:." }) {
            return NodeInput.Invalid("Invalid IPv6 address")
        }
        val rest = value.substring(end + 1)
        if (!rest.startsWith(":") || rest.length == 1) {
            return NodeInput.Invalid("Include port (e.g., :18081)")
        }
        portText = rest.substring(1)
        hostPart = "[$host]"
    } else {
        val colon = value.lastIndexOf(':')
        if (colon == -1 || colon == value.length - 1) {
            return NodeInput.Invalid("Include port (e.g., :18081)")
        }
        val host = value.substring(0, colon)
        portText = value.substring(colon + 1)
        if (host.contains(':')) return NodeInput.Invalid("Wrap IPv6 addresses in brackets, e.g. [::1]:18081")
        if (host.isEmpty()) return NodeInput.Invalid("Host required")
        if (host.length > 253 || !NODE_HOST_REGEX.matches(host)) return NodeInput.Invalid("Invalid host name")
        hostPart = host
    }

    val port = if (portText.all { it.isDigit() }) portText.toIntOrNull() else null
    if (port == null || port !in 1..65535) return NodeInput.Invalid("Port must be between 1 and 65535")
    // MoneroKit only speaks TLS on port 443, so any other https:// input would
    // silently downgrade to cleartext — reject instead.
    if (https && port != 443) return NodeInput.Invalid("TLS nodes must use port 443")

    return NodeInput.Valid("$credentials$hostPart:$port")
}

/**
 * Benchmarks a node by measuring the round-trip time for a /get_info request.
 * Returns latency in ms, or -1 if unreachable.
 */
private suspend fun benchmarkNode(uri: String): Long = withContext(Dispatchers.IO) {
    try {
        val start = System.currentTimeMillis()
        val scheme = if (DefaultNodes.isTls(uri)) "https" else "http"
        val url = URL("$scheme://$uri/get_info")
        val connection = url.openConnection() as HttpURLConnection
        connection.connectTimeout = 5000
        connection.readTimeout = 5000
        connection.requestMethod = "GET"

        val responseCode = connection.responseCode
        connection.disconnect()

        // Only HTTP 200 counts: a node answering 403 (e.g. a CDN or restricted
        // proxy in front) is not usable by wallet2 RPC, and auto-select must
        // never save a node the wallet layer can't actually talk to.
        if (responseCode == HttpURLConnection.HTTP_OK) {
            System.currentTimeMillis() - start
        } else {
            -1L
        }
    } catch (e: Exception) {
        -1L
    }
}
