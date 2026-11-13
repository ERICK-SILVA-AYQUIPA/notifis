package pe.pagobox.domain.model

/** Entidades financieras soportadas por la arquitectura (no implica compatibilidad verificada). */
enum class SourceId(val displayName: String) {
    YAPE("Yape"),
    PLIN("Plin"),
    BCP("BCP"),
    INTERBANK("Interbank"),
    BBVA("BBVA"),
    SCOTIABANK("Scotiabank"),
    BANCO_NACION("Banco de la Nación"),
    OTHER("Otra entidad"),
}

/** Tipo de operación que describe una notificación. */
enum class EventType {
    INCOMING_TRANSFER,
    OUTGOING_TRANSFER,
    PAYMENT_REQUEST,
    PROMOTION,
    OTHER,
}

/** Canal por el que llegó el evento (arquitectura híbrida). */
enum class SourceChannel(val label: String) {
    /** Canal A: notificación leída en el celular. Nunca es prueba oficial de pago. */
    ANDROID_NOTIFICATION("Notificación Android"),
    /** Canal B: webhook o consulta autenticada a un proveedor autorizado, procesado en el backend. */
    OFFICIAL_WEBHOOK("Proveedor oficial"),
}

/** Proveedores oficiales del Canal B. Que exista aquí no significa que esté conectado. */
enum class ProviderId(val displayName: String) {
    MERCADO_PAGO("Mercado Pago"),
    IZIPAY("Izipay"),
}

/**
 * Nivel de confianza / verificación del evento. Es independiente del anuncio ([AnnouncementStatus]):
 * un evento ya anunciado como posible ingreso puede confirmarse después.
 */
enum class VerificationStatus(val label: String) {
    /** Recibido y aún no clasificado. */
    DETECTED("Detectado"),
    /** Notificación Android con forma de ingreso. No verificado. */
    INCOME_CANDIDATE("Posible ingreso"),
    /** Falta información (p. ej. importe) o se espera la respuesta de un proveedor. */
    PENDING_VERIFICATION("Pendiente de verificación"),
    /** Confirmado por un proveedor autorizado (firma válida y/o consulta autenticada). Solo Canal B. */
    PROVIDER_CONFIRMED("Confirmado por el proveedor"),
    REJECTED("Descartado"),
    /** Otro evento representa el mismo pago (ver [PaymentEvent.duplicateOf]). */
    DUPLICATE("Duplicado"),
}

/** Estado del anuncio (voz / equipo), separado de la verificación. */
enum class AnnouncementStatus { PENDING, DELIVERED, ANNOUNCED, FAILED, SKIPPED }

/**
 * Estado de compatibilidad de una entidad (registro individual por entidad).
 * Nunca se asume: cada avance requiere evidencia (ver docs/compatibilidad-bancaria.md).
 */
enum class CompatibilityStatus(val label: String, val processesPayments: Boolean) {
    /** No se conoce el paquete oficial de la app en este celular. */
    NOT_CONFIGURED("Sin configurar", false),
    /** Paquete identificado, pero no hay formatos reales: se recopilan muestras, no se anuncia. */
    WAITING_FOR_SAMPLE("Esperando muestras", false),
    /** Hay formatos escritos a partir de muestras reales anonimizadas. */
    CONFIGURED("Configurado", true),
    /** Los formatos superan las pruebas unitarias con sus muestras. */
    LAB_TESTED("Probado en laboratorio", true),
    /** Probado en al menos un celular físico, con evidencia documentada. */
    DEVICE_TESTED("Probado en celular", true),
    /** Superó el protocolo completo de pruebas reales documentadas. */
    VERIFIED_COMPATIBLE("Compatible verificado", true),
    /** La app empezó a enviar formatos desconocidos: se sigue procesando solo lo reconocido. */
    FORMAT_CHANGED("Formato cambiado", true),
    /** Desactivado temporalmente (manual o por incidencia). */
    TEMPORARILY_UNAVAILABLE("No disponible temporalmente", false),
}

/** Política de confiabilidad (sección 7): estados que la app comunica al comerciante. */
enum class SystemHealth { OPERATIONAL, DEGRADED, DISCONNECTED, UNSUPERVISED }
