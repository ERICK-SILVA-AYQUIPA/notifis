package pe.pagobox.backend

import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import pe.pagobox.backend.config.BackendConfig
import pe.pagobox.backend.http.pagoboxBackend
import pe.pagobox.backend.ingest.WebhookIngestion
import pe.pagobox.backend.providers.Providers
import pe.pagobox.backend.store.JdbcIntegrationStore

fun main() {
    val config = BackendConfig.fromEnv()
    val store = JdbcIntegrationStore(config.databaseUrl)
    val providers = Providers.all()
    val ingestion = WebhookIngestion(config, providers, store)
    for (env in config.enabledEnvironments) for (p in providers) {
        val ready = config.credentialsFor(p.id, env).has(p.requiredCredentials)
        println("PAGOBOX backend · ${env.path} · ${p.id.displayName}: ${if (ready) "credenciales presentes" else "sin configurar"}")
    }
    embeddedServer(Netty, port = config.port) { pagoboxBackend(config, ingestion) }.start(wait = true)
}
