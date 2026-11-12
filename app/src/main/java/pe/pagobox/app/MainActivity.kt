package pe.pagobox.app

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import pe.pagobox.app.ui.DiagnosticsScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as PagoboxApplication
        setContent {
            DiagnosticsScreen(
                app = app,
                onOpenNotificationAccess = {
                    startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                },
            )
        }
    }
}
