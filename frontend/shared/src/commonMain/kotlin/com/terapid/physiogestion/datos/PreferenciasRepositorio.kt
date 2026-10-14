package com.terapid.physiogestion.datos

import com.terapid.physiogestion.datos.local.AlmacenPreferencias
import com.terapid.physiogestion.datos.local.ClavesAlmacen
import com.terapid.physiogestion.plataforma.ahoraEnMilisegundos
import com.terapid.physiogestion.plataforma.claveDelDia
import com.terapid.physiogestion.servicio.SeguimientoServicio

/**
 * Todo lo que la app guarda en PREFERENCIAS: la racha, la base del contador
 * de pasos, la dirección del servidor y la configuración de la cámara.
 */
class PreferenciasRepositorio(
    private val preferencias: AlmacenPreferencias,
    private val servicio: SeguimientoServicio,
    private val reloj: () -> Long = ::ahoraEnMilisegundos,
) {
    // ---- Racha de días consecutivos ----

    /** Racha vigente: si el último día activo no fue hoy ni ayer, ya se rompió. */
    fun racha(pacienteId: Int): Int {
        val ultimo = preferencias.leerTexto(ClavesAlmacen.ultimoDiaActivo(pacienteId)).ifBlank { null }
        val (hoy, ayer) = hoyYAyer()
        return if (ultimo == hoy || ultimo == ayer) preferencias.leerEntero(ClavesAlmacen.rachaDias(pacienteId)) else 0
    }

    /** Se llama al guardar una sesión. Devuelve la racha nueva. */
    fun registrarDiaActivo(pacienteId: Int): Int {
        val (hoy, ayer) = hoyYAyer()
        val nueva = servicio.calcularRacha(
            rachaActual = preferencias.leerEntero(ClavesAlmacen.rachaDias(pacienteId)),
            ultimoDiaActivo = preferencias.leerTexto(ClavesAlmacen.ultimoDiaActivo(pacienteId)).ifBlank { null },
            hoy = hoy,
            ayer = ayer,
        )
        preferencias.guardarEntero(ClavesAlmacen.rachaDias(pacienteId), nueva)
        preferencias.guardarTexto(ClavesAlmacen.ultimoDiaActivo(pacienteId), hoy)
        return nueva
    }

    // ---- Contador de pasos ----

    /** Convierte la lectura del sensor en pasos de hoy, guardando la base del día. */
    fun pasosDeHoy(lectura: Long, cuentaDesdeInicioDelDia: Boolean): Long {
        if (cuentaDesdeInicioDelDia) return lectura
        val hoy = claveDelDia(reloj())
        val base = if (preferencias.leerTexto(ClavesAlmacen.PASOS_DIA_BASE) == hoy) {
            preferencias.leerLargo(ClavesAlmacen.PASOS_VALOR_BASE)
        } else {
            null
        }
        val (pasos, nuevaBase) = servicio.pasosDelDia(lectura, base)
        if (nuevaBase != base) {
            preferencias.guardarTexto(ClavesAlmacen.PASOS_DIA_BASE, hoy)
            preferencias.guardarLargo(ClavesAlmacen.PASOS_VALOR_BASE, nuevaBase)
        }
        return pasos
    }

    // ---- Conexión ----

    fun urlServidorGuardada(): String? = preferencias.leerTexto(ClavesAlmacen.URL_SERVIDOR).ifBlank { null }

    fun guardarUrlServidor(url: String) = preferencias.guardarTexto(ClavesAlmacen.URL_SERVIDOR, url.trim())

    fun olvidarUrlServidor() = preferencias.borrar(ClavesAlmacen.URL_SERVIDOR)

    fun registrarSincronizacion() = preferencias.guardarLargo(ClavesAlmacen.FECHA_ULTIMA_SINCRONIZACION, reloj())

    fun ultimaSincronizacion(): Long? =
        preferencias.leerLargo(ClavesAlmacen.FECHA_ULTIMA_SINCRONIZACION, 0L).takeIf { it > 0L }

    // ---- Configuración ----

    var verificacionCamaraActiva: Boolean
        get() = preferencias.leerBooleano(ClavesAlmacen.VERIFICACION_CAMARA_ACTIVA, false)
        set(valor) = preferencias.guardarBooleano(ClavesAlmacen.VERIFICACION_CAMARA_ACTIVA, valor)

    private fun hoyYAyer(): Pair<String, String> {
        val ahora = reloj()
        return claveDelDia(ahora) to claveDelDia(ahora - UN_DIA_MS)
    }

    private companion object {
        const val UN_DIA_MS = 24L * 60 * 60 * 1000
    }
}
