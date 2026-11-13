package pe.pagobox.app

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import pe.pagobox.app.announce.AnnouncementCoordinator
import pe.pagobox.app.data.local.PagoboxDatabase
import pe.pagobox.app.data.preferences.SettingsRepository
import pe.pagobox.app.data.repositories.DiagnosticsLogger
import pe.pagobox.app.data.repositories.PaymentRepository
import pe.pagobox.app.device.BleDeviceLink
import pe.pagobox.app.device.DeviceAudioUploader
import pe.pagobox.app.device.DeviceManager
import pe.pagobox.app.diagnostics.HealthMonitor
import pe.pagobox.app.diagnostics.NotificationDiagnostics
import pe.pagobox.app.diagnostics.PackageInspector
import pe.pagobox.app.payment.PaymentProcessor
import pe.pagobox.app.system.ShortWakeLock
import pe.pagobox.app.voice.AndroidVoiceEngine

/**
 * Inyección de dependencias manual: un único lugar donde se construye el grafo de
 * objetos, sin agregar un framework de DI (sección 17: sin dependencias innecesarias).
 */
class AppContainer(context: Context) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val database = PagoboxDatabase.create(context)
    val log = DiagnosticsLogger(database.diagnosticLogs(), scope)
    val settings = SettingsRepository(database.appSettings())
    val payments = PaymentRepository(database)
    val voice = AndroidVoiceEngine(context)
    val wakeLock = ShortWakeLock(context)

    val bleLink = BleDeviceLink(context, log)
    val devices = DeviceManager(bleLink, database.devices(), DeviceAudioUploader(), log, scope)

    val announcements = AnnouncementCoordinator(
        context = context,
        payments = payments,
        settings = settings,
        voiceDao = database.voiceProfiles(),
        templateDao = database.phraseTemplates(),
        historyDao = database.announcementHistory(),
        voice = voice,
        devices = devices,
        wakeLock = wakeLock,
        log = log,
        scope = scope,
    )

    val packageInspector = PackageInspector(context)
    val notificationDiagnostics = NotificationDiagnostics(database.notificationSamples())

    val processor = PaymentProcessor(
        repository = payments,
        assignmentDao = database.packageAssignments(),
        statusDao = database.sourceStatus(),
        unknownAppDao = database.unknownFinancialApps(),
        wakeLock = wakeLock,
        settings = settings,
        diagnostics = notificationDiagnostics,
        inspector = packageInspector,
        lastAckedSequence = { devices.lastAckedSequence() },
        log = log,
        scope = scope,
        onIncome = { announcements.notifyNewIncome() },
    )

    /** Canal B: eventos oficiales desde el backend propio (si está configurado). */
    val officialSync = pe.pagobox.app.payment.OfficialEventsSync(settings, processor, log, scope)

    /** Estado del servicio lector, informado por el propio servicio. */
    val listenerConnected = kotlinx.coroutines.flow.MutableStateFlow(false)

    val health = HealthMonitor(context, this)

    /** Fin del modo diagnóstico (en memoria, para que el lector no consulte la base en cada notificación). */
    @Volatile private var diagnosticUntil: Long = 0L

    fun diagnosticModeActive(): Boolean = System.currentTimeMillis() < diagnosticUntil

    fun start() {
        scope.launch {
            settings.settings.collect { diagnosticUntil = it.diagnosticUntil?.toEpochMilli() ?: 0L }
        }
        processor.start()
        officialSync.start()
        devices.start()
        announcements.start()
    }
}
