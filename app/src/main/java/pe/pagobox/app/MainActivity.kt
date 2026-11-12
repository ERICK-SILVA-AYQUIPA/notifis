package pe.pagobox.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import pe.pagobox.app.ui.navigation.PagoboxApp
import pe.pagobox.app.ui.theme.PagoboxTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = container
        // Lectura única y rápida de una fila de configuración, mientras se muestra el splash.
        val onboardingDone = runBlocking { container.settings.settings.first().onboardingDone }
        setContent {
            PagoboxTheme {
                PagoboxApp(container, onboardingDone)
            }
        }
    }
}
