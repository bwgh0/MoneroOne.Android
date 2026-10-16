package one.monero.moneroone.ui.screens.onboarding

import one.monero.moneroone.core.locale.tr
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.ReplayCircleFilled
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import one.monero.moneroone.ui.components.AnimatedMoneroLogo
import one.monero.moneroone.ui.components.PrimaryButton
import one.monero.moneroone.ui.components.ProminentButton
import one.monero.moneroone.ui.components.SquatHeight

@Composable
fun WelcomeScreen(
    onCreateWallet: () -> Unit,
    onRestoreWallet: () -> Unit,
    onPairKeystone: () -> Unit = {}
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // iOS WelcomeView: a 240 hero on a tall window, 120 on a squat one,
        // so the buttons still fit a 360x640 phone.
        val heroSize = if (maxHeight < SquatHeight) 120.dp else 240.dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(modifier = Modifier.weight(1f))

            // Hero: the glass Monero logo with the iOS entrance, float, glow
            // and shine (tokens.json motion.hero), as on iOS WelcomeView.
            AnimatedMoneroLogo(size = heroSize)

            Spacer(modifier = Modifier.height(32.dp))

            // Title
            Text(
                text = "Monero One",
                style = MaterialTheme.typography.headlineLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Subtitle
            Text(
                text = tr("Simple. Private. Secure."),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.weight(1f))

            // Create Wallet: the one filled button, as on iOS
            ProminentButton(
                onClick = onCreateWallet,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.AddCircle, contentDescription = null, modifier = Modifier.size(20.dp))
                Text(text = tr("Create New Wallet"))
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Restore Wallet: glass with a label-color title, as on iOS
            PrimaryButton(
                onClick = onRestoreWallet,
                modifier = Modifier.fillMaxWidth(),
                contentColor = MaterialTheme.colorScheme.onSurface
            ) {
                Icon(Icons.Filled.ReplayCircleFilled, contentDescription = null, modifier = Modifier.size(20.dp))
                Text(text = tr("Restore Wallet"))
            }

            Spacer(modifier = Modifier.height(12.dp))

            PrimaryButton(
                onClick = onPairKeystone,
                modifier = Modifier.fillMaxWidth(),
                contentColor = MaterialTheme.colorScheme.onSurface
            ) {
                Icon(Icons.Filled.QrCodeScanner, contentDescription = null, modifier = Modifier.size(20.dp))
                Text(text = "Pair Keystone")
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
