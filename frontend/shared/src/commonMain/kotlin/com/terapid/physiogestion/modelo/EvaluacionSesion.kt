package com.terapid.physiogestion.modelo

/**
 * Aviso que se muestra cuando el dolor registrado es alto (RFX-09, RD-08).
 * No es un diagnóstico (RD-06): solo sugiere suspender y comunicarse con la clínica.
 */
data class AlertaDolor(
    val motivo: String,
    val recomendacion: String,
)

/** Resultado que calcula el servicio al guardar una sesión. */
data class EvaluacionSesion(
    val cumplimiento: Int,
    val puntos: Int,
    val alerta: AlertaDolor?,
)
