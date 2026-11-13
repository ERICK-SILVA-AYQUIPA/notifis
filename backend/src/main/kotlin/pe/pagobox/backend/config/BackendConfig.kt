package pe.pagobox.backend.config

import pe.pagobox.domain.model.ProviderId

/** Entornos separados: credenciales, rutas y datos distintos. Un evento de sandbox nunca es un pago real. */
enum class DeployEnvironment(val path: String) {
    SANDBOX("sandbox"),
    PRODUCTION("production"),
    ;

    companion object {
        fun fromPath(path: String): DeployEnvironment? = entries.firstOrNull { it.path == path }
    }
}

/** Credenciales de un proveedor en un entorno. Nunca se registran en logs ni se devuelven por la API. */
class ProviderCredentials(private val values: Map<String, String>) {
    operator fun get(key: String): String? = values[key]?.takeIf { it.isNotBlank() }
    fun has(keys: Set<String>): Boolean = keys.all { get(it) != null }
    val keys: Set<String> get() = values.filterValues { it.isNotBlank() }.keys
    override fun toString(): String = "ProviderCredentials(keys=$keys)"
}

/**
 * Configuración del backend. **Todo secreto llega por variables de entorno del servidor**; nada se
 * guarda en el repositorio, en el APK ni en el frontend.
 *
 * Variables:
 * - `PAGOBOX_ENVIRONMENTS`: entornos habilitados, p. ej. `sandbox` o `sandbox,production` (por defecto `sandbox`).
 * - `PAGOBOX_DATABASE_URL`: JDBC (por defecto `jdbc:sqlite:pagobox-backend.db`; en producción, PostgreSQL).
 * - `PAGOBOX_PORT` (8080), `PAGOBOX_REQUIRE_HTTPS` (true), `PAGOBOX_MAX_BODY_BYTES` (65536).
 * - `PAGOBOX_SIGNATURE_TOLERANCE_SECONDS`: antigüedad máxima de la marca de tiempo firmada (vacío = no se exige).
 * - `PAGOBOX_DEVICE_TOKENS_SANDBOX` / `_PRODUCTION`: SHA-256 (hex) de los tokens de los celulares autorizados,
 *   separados por coma. El servidor nunca guarda el token en claro.
 * - Credenciales: `PAGOBOX_<PROVEEDOR>_<ENTORNO>_<CLAVE>`, p. ej.
 *   `PAGOBOX_MERCADO_PAGO_SANDBOX_WEBHOOK_SECRET`, `PAGOBOX_MERCADO_PAGO_SANDBOX_ACCESS_TOKEN`.
 */
data class BackendConfig(
    val port: Int = 8080,
    val databaseUrl: String = "jdbc:sqlite:pagobox-backend.db",
    val enabledEnvironments: Set<DeployEnvironment> = setOf(DeployEnvironment.SANDBOX),
    val credentials: Map<Pair<ProviderId, DeployEnvironment>, ProviderCredentials> = emptyMap(),
    val requireHttps: Boolean = true,
    val maxBodyBytes: Int = 64 * 1024,
    val signatureToleranceSeconds: Long? = null,
    /** SHA-256 (hex) de los tokens de cada celular PAGOBOX autorizado a leer eventos, por entorno. */
    val deviceTokenHashes: Map<DeployEnvironment, Set<String>> = emptyMap(),
) {
    fun credentialsFor(provider: ProviderId, env: DeployEnvironment): ProviderCredentials =
        credentials[provider to env] ?: ProviderCredentials(emptyMap())

    companion object {
        fun fromEnv(env: Map<String, String> = System.getenv()): BackendConfig {
            val environments = (env["PAGOBOX_ENVIRONMENTS"] ?: "sandbox").split(',')
                .mapNotNull { DeployEnvironment.fromPath(it.trim().lowercase()) }.toSet()
            val creds = mutableMapOf<Pair<ProviderId, DeployEnvironment>, ProviderCredentials>()
            for (provider in ProviderId.entries) for (de in DeployEnvironment.entries) {
                val prefix = "PAGOBOX_${provider.name}_${de.name}_"
                val values = env.filterKeys { it.startsWith(prefix) }.mapKeys { it.key.removePrefix(prefix) }
                if (values.isNotEmpty()) creds[provider to de] = ProviderCredentials(values)
            }
            return BackendConfig(
                port = env["PAGOBOX_PORT"]?.toIntOrNull() ?: 8080,
                databaseUrl = env["PAGOBOX_DATABASE_URL"] ?: "jdbc:sqlite:pagobox-backend.db",
                enabledEnvironments = environments,
                credentials = creds,
                requireHttps = env["PAGOBOX_REQUIRE_HTTPS"]?.lowercase() != "false",
                maxBodyBytes = env["PAGOBOX_MAX_BODY_BYTES"]?.toIntOrNull() ?: 64 * 1024,
                signatureToleranceSeconds = env["PAGOBOX_SIGNATURE_TOLERANCE_SECONDS"]?.toLongOrNull(),
                deviceTokenHashes = DeployEnvironment.entries.associateWith { de ->
                    env["PAGOBOX_DEVICE_TOKENS_${de.name}"].orEmpty().split(',').map { it.trim().lowercase() }.filter { it.length == 64 }.toSet()
                },
            )
        }
    }
}
