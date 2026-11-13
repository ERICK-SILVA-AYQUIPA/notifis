package pe.pagobox.engine.integrations

import pe.pagobox.domain.model.ProviderId

/**
 * Lo que se sabe de cada proveedor oficial, tomado de docs/hibrido/proveedores-canal-b.md (fuentes oficiales).
 * Lo no verificado se dice como tal. Este catálogo es descriptivo: **nunca** indica que una integración
 * esté conectada; eso solo lo informa el backend tras procesar un webhook real comprobado.
 */
data class ProviderInfo(
    val id: ProviderId,
    /** Medios de pago que el proveedor permite COBRAR por su propio canal. */
    val methods: String,
    /** Qué NO cubre: aclara que no consulta todos los pagos recibidos en la cuenta del comercio. */
    val coverage: String,
    val affiliation: String,
    val credentials: String,
    val environments: String,
    val webhook: String,
    /** El backend de PAGOBOX tiene un adaptador real para este proveedor. */
    val adapterImplemented: Boolean,
)

object IntegrationCatalog {
    val providers: List<ProviderInfo> = listOf(
        ProviderInfo(
            id = ProviderId.MERCADO_PAGO,
            methods = "Cobro con Yape (Checkout API, payment_method_id «yape»), tarjetas y otros medios de Mercado Pago.",
            coverage = "Solo confirma pagos creados con su API. No permite consultar los yapeos que llegan " +
                "directamente a tu número de Yape.",
            affiliation = "Cuenta de Mercado Pago Perú y una aplicación en «Tus integraciones». Costos: no verificados.",
            credentials = "Access token y clave secreta del webhook, solo en el servidor (nunca en este celular).",
            environments = "Credenciales de prueba de la cuenta productiva (sandbox) y credenciales de producción.",
            webhook = "Firmado con x-signature (HMAC-SHA256); los pagos con QR no van firmados y se confirman " +
                "consultando el pago en la API.",
            adapterImplemented = true,
        ),
        ProviderInfo(
            id = ProviderId.IZIPAY,
            methods = "Cobro con tarjeta, Yape, Plin (Interbank), QR y link de pago por su pasarela.",
            coverage = "Solo confirma pagos hechos en su pasarela. No se encontró una API para consultar todos " +
                "los Yape/Plin recibidos.",
            affiliation = "Requiere afiliación comercial con Izipay. Requisitos y comisiones: no verificados.",
            credentials = "Identificador de tienda, contraseña, clave pública y clave HMAC, solo en el servidor.",
            environments = "Test y producción con credenciales separadas.",
            webhook = "IPN con kr-answer y kr-hash (HMAC-SHA256 con la contraseña). Reintentos: no verificados.",
            adapterImplemented = false,
        ),
    )

    fun info(id: ProviderId): ProviderInfo = providers.first { it.id == id }
}

/** Estado informado por el backend (`GET /api/v1/integrations`). */
enum class BackendIntegrationStatus { NOT_IMPLEMENTED, NOT_CONFIGURED, AWAITING_FIRST_EVENT, ACTIVE, ERROR }

data class ProviderLiveStatus(
    val provider: ProviderId,
    val environment: String,
    val status: BackendIntegrationStatus,
    val credentialsConfigured: Boolean,
    val webhookPath: String?,
    val lastCommunicationAt: Long?,
    val lastEventAt: Long?,
    val lastError: String?,
)

/** Resumen que ve el comerciante para un proveedor. */
data class IntegrationSummary(val label: String, val active: Boolean, val detail: String)

object IntegrationPanel {
    /**
     * «Activa» solo si el backend lo dice tras un webhook real comprobado. Sin backend, sin respuesta o con
     * cualquier otro estado, la integración se muestra como no conectada.
     */
    fun summarize(info: ProviderInfo, backendConfigured: Boolean, live: ProviderLiveStatus?): IntegrationSummary {
        if (!info.adapterImplemented && live?.status != BackendIntegrationStatus.ACTIVE) {
            return IntegrationSummary("No disponible", false, "PAGOBOX aún no tiene esta integración.")
        }
        if (!backendConfigured) {
            return IntegrationSummary("No conectada", false, "Configura el servidor de integraciones en esta pantalla.")
        }
        if (live == null) {
            return IntegrationSummary("Sin datos", false, "El servidor aún no respondió el estado de este proveedor.")
        }
        val sandbox = if (live.environment == "production") "" else " (sandbox)"
        return when (live.status) {
            BackendIntegrationStatus.ACTIVE -> IntegrationSummary("Activa$sandbox", true, "Último webhook comprobado recibido.")
            BackendIntegrationStatus.AWAITING_FIRST_EVENT -> IntegrationSummary(
                "Esperando primer webhook$sandbox", false,
                "Credenciales configuradas en el servidor; aún no llega ningún webhook comprobado.",
            )
            BackendIntegrationStatus.ERROR -> IntegrationSummary("Con errores$sandbox", false, live.lastError ?: "Error sin detalle.")
            BackendIntegrationStatus.NOT_CONFIGURED -> IntegrationSummary(
                "No conectada$sandbox", false, "Faltan las credenciales del proveedor en el servidor.",
            )
            BackendIntegrationStatus.NOT_IMPLEMENTED -> IntegrationSummary("No disponible", false, "El servidor no tiene esta integración.")
        }
    }
}
