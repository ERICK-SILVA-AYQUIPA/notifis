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

/** Estado real de compatibilidad de una entidad: nunca se asume, se comprueba. */
enum class CompatibilityStatus {
    /** No hay reglas basadas en notificaciones reales. No se procesa. */
    NOT_IMPLEMENTED,
    /** Hay reglas, pero aún no superaron las pruebas específicas. */
    IN_TESTING,
    /** Superó las pruebas específicas para un modelo/versión documentados. */
    VERIFIED,
}

/** Política de confiabilidad (sección 7): estados que la app comunica al comerciante. */
enum class SystemHealth { OPERATIONAL, DEGRADED, DISCONNECTED, UNSUPERVISED }
