package pe.pagobox.app.data.repositories

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import pe.pagobox.app.data.local.DiagnosticLogDao
import pe.pagobox.app.data.local.DiagnosticLogEntity

/** Registro centralizado de errores y eventos técnicos (tabla DiagnosticLogs). */
class DiagnosticsLogger(private val dao: DiagnosticLogDao, private val scope: CoroutineScope) {
    fun info(component: String, message: String) = log("INFO", component, message)
    fun warn(component: String, message: String) = log("WARN", component, message)
    fun error(component: String, message: String, error: Throwable? = null) =
        log("ERROR", component, message + (error?.let { ": ${it.javaClass.simpleName} ${it.message}" } ?: ""))

    private fun log(level: String, component: String, message: String) {
        when (level) {
            "ERROR" -> Log.e("PAGOBOX/$component", message)
            "WARN" -> Log.w("PAGOBOX/$component", message)
            else -> Log.i("PAGOBOX/$component", message)
        }
        scope.launch {
            dao.insert(DiagnosticLogEntity(timestamp = System.currentTimeMillis(), level = level, component = component, message = message))
        }
    }
}
