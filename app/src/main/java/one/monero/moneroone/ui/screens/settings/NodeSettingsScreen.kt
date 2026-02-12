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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import one.monero.moneroone.ui.components.GlassCard
import one.monero.moneroone.ui.theme.ErrorRed
import one.monero.moneroone.ui.theme.MoneroOrange
import one.monero.moneroone.ui.theme.SuccessGreen
import java.net.HttpURLConnection
import java.net.URL

data class NodeInfo(
    val uri: String,
    val name: String,
    val isDefault: Boolean = false
)

private val DEFAULT_NODES = listOf(
    NodeInfo("xmr-node.cakewallet.com:18081", "Cake Wallet", true),
    NodeInfo("node.sethforprivacy.com:18089", "Seth For Privacy", true),
    NodeInfo("nodes.hashvault.pro:18081", "HashVault", true),
    NodeInfo("node.community.rino.io:18081", "RINO Community", true)
)

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

    var selectedNode by remember {
        val savedUri = prefs.getString("selected_node", DEFAULT_NODES.first().uri)
        mutableStateOf(savedUri ?: DEFAULT_NODES.first().uri)
    }

    var showAddNodeDialog by remember { mutableStateOf(false) }
    var testingNode by remember { mutableStateOf<String?>(null) }

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

        Spacer(modifier = Modifier.height(24.dp))

        // Default Nodes
        SectionLabel("DEFAULT NODES")

        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DEFAULT_NODES.forEach { node ->
                NodeItem(
                    node = node,
                    isSelected = node.uri == selectedNode,
                    isTesting = testingNode == node.uri,
                    onSelect = {
                        val changed = selectedNode != node.uri
                        selectedNode = node.uri
                        prefs.edit().putString(
                            "selected_node",
                            node.uri
                        ).apply()
                        if (changed) onNodeChanged()
                    },
                    onTest = {
                        testingNode = node.uri
                        scope.launch {
                            val success = testNodeConnection(node.uri)
                            testingNode = null
                            Toast.makeText(
                                context,
                                if (success) "Connected successfully" else "Connection failed",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
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
                        isTesting = testingNode == node.uri,
                        onSelect = {
                            val changed = selectedNode != node.uri
                            selectedNode = node.uri
                            prefs.edit().putString(
                                "selected_node",
                                node.uri
                            ).apply()
                            if (changed) onNodeChanged()
                        },
                        onTest = {
                            testingNode = node.uri
                            scope.launch {
                                val success = testNodeConnection(node.uri)
                                testingNode = null
                                Toast.makeText(
                                    context,
                                    if (success) "Connected successfully" else "Connection failed",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        },
                        onDelete = {
                            customNodes.remove(node)
                            val uris = customNodes.map { it.uri }
                            prefs.edit().putString(
                                "custom_nodes",
                                json.encodeToString(uris)
                            ).apply()

                            // If deleted node was selected, switch to default
                            if (selectedNode == node.uri) {
                                selectedNode = DEFAULT_NODES.first().uri
                                prefs.edit().putString(
                                    "selected_node",
                                    DEFAULT_NODES.first().uri
                                ).apply()
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
    isTesting: Boolean,
    onSelect: () -> Unit,
    onTest: () -> Unit,
    onDelete: (() -> Unit)?
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onSelect
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
                tint = if (isSelected) MoneroOrange else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = node.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = if (isSelected) MoneroOrange else MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = node.uri,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }

            if (isTesting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MoneroOrange,
                    strokeWidth = 2.dp
                )
            } else {
                TextButton(onClick = onTest) {
                    Text("Test", color = MoneroOrange)
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
private fun AddNodeDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var nodeUri by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

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
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (nodeUri.isBlank()) {
                        error = "Node URI required"
                    } else if (!nodeUri.contains(":")) {
                        error = "Include port (e.g., :18081)"
                    } else {
                        onConfirm(nodeUri.trim())
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

private suspend fun testNodeConnection(uri: String): Boolean = withContext(Dispatchers.IO) {
    try {
        val url = URL("http://$uri/get_info")
        val connection = url.openConnection() as HttpURLConnection
        connection.connectTimeout = 5000
        connection.readTimeout = 5000
        connection.requestMethod = "GET"

        val responseCode = connection.responseCode
        connection.disconnect()

        responseCode == HttpURLConnection.HTTP_OK || responseCode == HttpURLConnection.HTTP_FORBIDDEN
    } catch (e: Exception) {
        false
    }
}
