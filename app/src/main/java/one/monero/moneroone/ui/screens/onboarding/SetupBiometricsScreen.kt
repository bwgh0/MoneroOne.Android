package one.monero.moneroone.ui.screens.onboarding

import androidx.biometric.BiometricManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import one.monero.moneroone.core.wallet.WalletViewModel
import one.monero.moneroone.ui.theme.MoneroOrange

@Composable
fun SetupBiometricsScreen(
    walletViewModel: WalletViewModel,
    onContinue: () -> Unit
) {
    val context = LocalContext.current

    val biometricAvailable = BiometricManager.from(context)
        .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
            BiometricManager.BIOMETRIC_SUCCESS

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.weight(1f))

        Icon(
            imageVector = Icons.Default.Fingerprint,
            contentDescription = "Biometrics",
            modifier = Modifier.size(96.dp),
            tint = if (biometricAvailable) MoneroOrange
                   else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f)
        )

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = if (biometricAvailable) "Enable Biometrics?"
                   else "Biometrics Unavailable",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = if (biometricAvailable)
                       "Unlock your wallet quickly and securely with biometrics instead of entering your PIN."
                   else
                       "This device does not support biometric authentication. You can enable it later in Settings if you set up biometrics on your device.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
        )

        Spacer(modifier = Modifier.weight(1f))

        if (biometricAvailable) {
            Button(
                onClick = {
                    walletViewModel.setBiometricsEnabled(true)
                    onContinue()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MoneroOrange,
                    contentColor = Color.White
                )
            ) {
                Text(
                    text = "Enable Biometrics",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            TextButton(
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Skip for Now",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
            }
        } else {
            Button(
                onClick = onContinue,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MoneroOrange,
                    contentColor = Color.White
                )
            ) {
                Text(
                    text = "Continue",
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}
