package pe.pagobox.app.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import pe.pagobox.app.AppContainer
import pe.pagobox.app.ui.banks.BankSourcesScreen
import pe.pagobox.app.ui.dashboard.DashboardScreen
import pe.pagobox.app.ui.devices.DeviceManagementScreen
import pe.pagobox.app.ui.diagnostics.DiagnosticsScreen
import pe.pagobox.app.ui.diagnostics.NotificationDiagnosticsScreen
import pe.pagobox.app.ui.tests.PhysicalTestsScreen
import pe.pagobox.app.ui.history.EventDetailScreen
import pe.pagobox.app.ui.history.HistoryScreen
import pe.pagobox.app.ui.onboarding.OnboardingScreen
import pe.pagobox.app.ui.settings.BusinessSettingsScreen
import pe.pagobox.app.ui.settings.SettingsScreen
import pe.pagobox.app.ui.voice.VoiceSettingsScreen

private object Routes {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val HISTORY = "history"
    const val DEVICES = "devices"
    const val SETTINGS = "settings"
    const val EVENT = "event/{eventId}"
    const val BANKS = "banks"
    const val VOICE = "voice"
    const val DIAGNOSTICS = "diagnostics"
    const val BUSINESS = "business"
    const val NOTIFICATION_DIAGNOSTICS = "notification_diagnostics"
    const val PHYSICAL_TESTS = "physical_tests"

    fun event(id: String) = "event/$id"
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(Routes.HOME, "Inicio", Icons.Filled.Home),
    Tab(Routes.HISTORY, "Historial", Icons.AutoMirrored.Filled.ReceiptLong),
    Tab(Routes.DEVICES, "Equipo", Icons.Filled.Speaker),
    Tab(Routes.SETTINGS, "Ajustes", Icons.Filled.Settings),
)

private val titles = mapOf(
    Routes.HOME to "PAGOBOX",
    Routes.HISTORY to "Historial",
    Routes.DEVICES to "Equipo PAGOBOX",
    Routes.SETTINGS to "Configuración",
    Routes.EVENT to "Detalle del evento",
    Routes.BANKS to "Bancos",
    Routes.VOICE to "Voz",
    Routes.DIAGNOSTICS to "Diagnóstico",
    Routes.BUSINESS to "Negocio",
    Routes.NOTIFICATION_DIAGNOSTICS to "Diagnóstico de notificaciones",
    Routes.PHYSICAL_TESTS to "Pruebas físicas",
    Routes.ONBOARDING to "Configuración inicial",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PagoboxApp(container: AppContainer, onboardingDone: Boolean) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val isTab = tabs.any { it.route == route }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(titles[route] ?: "PAGOBOX") },
                navigationIcon = {
                    if (!isTab && route != Routes.ONBOARDING) {
                        IconButton(onClick = { nav.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (isTab) {
                NavigationBar {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = route == tab.route,
                            onClick = { nav.navigateTab(tab.route) },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = if (onboardingDone) Routes.HOME else Routes.ONBOARDING,
            modifier = Modifier.padding(padding),
        ) {
            composable(Routes.ONBOARDING) {
                OnboardingScreen(container) {
                    nav.navigate(Routes.HOME) { popUpTo(Routes.ONBOARDING) { inclusive = true } }
                }
            }
            composable(Routes.HOME) {
                DashboardScreen(
                    container,
                    onOpenEvent = { nav.navigate(Routes.event(it)) },
                    onOpenDiagnostics = { nav.navigate(Routes.DIAGNOSTICS) },
                )
            }
            composable(Routes.HISTORY) { HistoryScreen(container) { nav.navigate(Routes.event(it)) } }
            composable(Routes.DEVICES) { DeviceManagementScreen(container) }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    container,
                    onBanks = { nav.navigate(Routes.BANKS) },
                    onVoice = { nav.navigate(Routes.VOICE) },
                    onDiagnostics = { nav.navigate(Routes.DIAGNOSTICS) },
                    onBusiness = { nav.navigate(Routes.BUSINESS) },
                    onNotificationDiagnostics = { nav.navigate(Routes.NOTIFICATION_DIAGNOSTICS) },
                    onPhysicalTests = { nav.navigate(Routes.PHYSICAL_TESTS) },
                )
            }
            composable(Routes.EVENT, arguments = listOf(navArgument("eventId") { type = NavType.StringType })) {
                EventDetailScreen(container, it.arguments?.getString("eventId").orEmpty())
            }
            composable(Routes.BANKS) { BankSourcesScreen(container) { nav.navigate(Routes.NOTIFICATION_DIAGNOSTICS) } }
            composable(Routes.NOTIFICATION_DIAGNOSTICS) { NotificationDiagnosticsScreen(container) }
            composable(Routes.PHYSICAL_TESTS) { PhysicalTestsScreen(container) }
            composable(Routes.VOICE) { VoiceSettingsScreen(container) }
            composable(Routes.DIAGNOSTICS) { DiagnosticsScreen(container) }
            composable(Routes.BUSINESS) { BusinessSettingsScreen(container) { nav.popBackStack() } }
        }
    }
}

private fun NavHostController.navigateTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
