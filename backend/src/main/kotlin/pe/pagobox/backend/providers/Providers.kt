package pe.pagobox.backend.providers

import pe.pagobox.backend.providers.mercadopago.MercadoPagoAdapter

/**
 * Proveedores con adaptador implementado. Solo integraciones reales y documentadas; ninguna simulada.
 * Izipay no está aquí: su IPN está documentada, pero faltan los campos de importe confirmados y la
 * afiliación comercial (ver docs/hibrido/proveedores-canal-b.md).
 */
object Providers {
    fun all(): List<ProviderAdapter> = listOf(MercadoPagoAdapter())
}
