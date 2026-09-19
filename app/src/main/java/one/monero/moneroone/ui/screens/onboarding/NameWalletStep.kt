package one.monero.moneroone.ui.screens.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import one.monero.moneroone.ui.screens.wallet.EmojiPickerGrid
import one.monero.moneroone.ui.theme.MoneroOrange

/**
 * Naming step at the END of the create/restore flows (iOS d4414c2):
 * emoji circle + name field prefilled with the next default name +
 * "you can change this later".
 */
@Composable
fun NameWalletStep(
    defaultName: String,
    buttonLabel: String,
    isBusy: Boolean,
    onDone: (name: String, emoji: String) -> Unit
) {
    var name by remember { mutableStateOf(defaultName) }
    var emoji by remember { mutableStateOf("💰") }
    var showEmojiPicker by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Name Your Wallet",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.height(24.dp))

        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .clickable { showEmojiPicker = !showEmojiPicker },
            contentAlignment = Alignment.Center
        ) {
            Text(text = emoji, fontSize = 44.sp)
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Tap to pick an icon",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        )

        if (showEmojiPicker) {
            Spacer(modifier = Modifier.height(12.dp))
            EmojiPickerGrid(
                selected = emoji,
                onSelect = {
                    emoji = it
                    showEmojiPicker = false
                }
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Wallet name") },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MoneroOrange,
                cursorColor = MoneroOrange
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "You can change the name and icon later.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = { onDone(name.trim().ifEmpty { defaultName }, emoji) },
            enabled = !isBusy,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MoneroOrange,
                contentColor = Color.White,
                disabledContainerColor = MoneroOrange.copy(alpha = 0.4f)
            )
        ) {
            Text(
                text = if (isBusy) "Working..." else buttonLabel,
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}
