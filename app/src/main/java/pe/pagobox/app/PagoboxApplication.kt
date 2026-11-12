package pe.pagobox.app

import android.app.Application
import pe.pagobox.app.work.MaintenanceWorker

class PagoboxApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.start()
        MaintenanceWorker.schedule(this)
    }
}

val android.content.Context.container: AppContainer
    get() = (applicationContext as PagoboxApplication).container
