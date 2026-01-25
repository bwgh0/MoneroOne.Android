package one.monero.moneroone

import android.app.Application
import timber.log.Timber

class MoneroOneApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // Initialize Timber for logging
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        Timber.d("MoneroOne Application started")
    }
}
