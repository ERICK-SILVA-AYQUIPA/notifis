package pe.pagobox.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import pe.pagobox.app.ui.navigation.PagoboxApp
import pe.pagobox.app.ui.theme.PagoboxTheme

class MainActivity : ComponentActivity() {
    /** null mientras se lee la configuración; el splash se mantiene visible hasta tenerla (E5: sin runBlocking). */
    private val onboardingDone = MutableStateFlow<Boolean?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = container
        splash.setKeepOnScreenCondition { onboardingDone.value == null }
        lifecycleScope.launch {
            onboardingDone.value = container.settings.settings.first().onboardingDone
        }
        setContent {
            val done by onboardingDone.collectAsStateWithLifecycle()
            PagoboxTheme {
                done?.let { PagoboxApp(container, it) }
            }
        }
    }
}
