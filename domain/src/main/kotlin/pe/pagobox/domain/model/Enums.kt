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

/** Estados del ciclo de vida de un evento (sección 2.3 de la especificación). */
enum class EventState {
    RECEIVED,
    PARSED,
    INCOME_CANDIDATE,
    VERIFIED,
    ANNOUNCED,
    DUPLICATE,
    REJECTED,
    PENDING_REVIEW,
    DELIVERY_FAILED,
}

/** Separación entre pagos detectados (por notificación) y verificados (por fuente oficial). */
enum class VerificationStatus { DETECTED, VERIFIED }

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
