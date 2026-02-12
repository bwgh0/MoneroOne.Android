package one.monero.moneroone.core.util

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.LocalContext

/**
 * Returns a pair of (hasPermission, requestPermission lambda).
 * On Android < 13, notifications are always permitted.
 */
@Composable
fun rememberNotificationPermission(): Pair<Boolean, () -> Unit> {
    val context = LocalContext.current

    val hasPermission = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* result handled via recomposition */ }

    val requestPermission = remember {
        {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    return Pair(hasPermission, requestPermission)
}
