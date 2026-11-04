package com.terapid.physiogestion.datos

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentHashMap

/**
 * PERSISTENCIA. Los servicios trabajan con mapas en memoria (rápidos) y escriben
 * cada cambio aquí. Al arrancar, leen lo guardado; si una colección está vacía,
 * se llena con los datos de ejemplo de resources/datos.
 *
 * Cada registro se guarda como un documento JSON dentro de una colección
 * (usuarios, sesiones, etc.), así agregar un campo nuevo no exige migrar tablas.
 *
 *  - [AlmacenEnMemoria]: pruebas y desarrollo; se pierde al reiniciar.
 *  - AlmacenPostgres: producción en Railway (variable DATABASE_URL); sobrevive a los reinicios (SRV-08).
 */
interface AlmacenDocumentos {
    /** Nombre corto para el chequeo de salud ("memoria", "postgres"). */
    val tipo: String

    fun leerTodos(coleccion: String): List<String>

    fun guardar(coleccion: String, id: String, json: String)

    fun borrar(coleccion: String, id: String)
}

class AlmacenEnMemoria : AlmacenDocumentos {
    override val tipo = "memoria"
    private val datos = ConcurrentHashMap<String, ConcurrentHashMap<String, String>>()

    override fun leerTodos(coleccion: String): List<String> = datos[coleccion]?.values?.toList().orEmpty()

    override fun guardar(coleccion: String, id: String, json: String) {
        datos.getOrPut(coleccion) { ConcurrentHashMap() }[id] = json
    }

    override fun borrar(coleccion: String, id: String) {
        datos[coleccion]?.remove(id)
    }
}

/** Colección tipada: convierte los objetos Kotlin a JSON y de regreso. */
class Coleccion<T>(
    private val almacen: AlmacenDocumentos,
    private val nombre: String,
    private val serializador: KSerializer<T>,
    private val json: Json,
    private val idDe: (T) -> String,
) {
    /** Lo guardado; si no hay nada, guarda y devuelve [semilla] (datos de ejemplo). */
    fun cargar(semilla: List<T> = emptyList()): List<T> {
        val guardados = almacen.leerTodos(nombre).map { json.decodeFromString(serializador, it) }
        if (guardados.isNotEmpty() || semilla.isEmpty()) return guardados
        semilla.forEach(::guardar)
        return semilla
    }

    fun guardar(valor: T) = almacen.guardar(nombre, idDe(valor), json.encodeToString(serializador, valor))

    fun borrar(id: String) = almacen.borrar(nombre, id)
}
