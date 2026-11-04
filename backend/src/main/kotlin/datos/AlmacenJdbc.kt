package com.terapid.physiogestion.datos

import org.slf4j.LoggerFactory
import java.net.URI
import java.net.URLDecoder
import java.sql.Connection
import java.sql.DriverManager

/**
 * BASE DE DATOS DEL SERVIDOR (SRV-08). Guarda las colecciones en una sola tabla:
 *
 *   documentos(coleccion, id, datos JSON, actualizado)   llave primaria (coleccion, id)
 *
 * En Railway se usa PostgreSQL (variable DATABASE_URL); las pruebas usan SQLite con
 * el mismo SQL. Así los usuarios, las sesiones y las mediciones sobreviven a los
 * reinicios del contenedor, que en el modo Serverless ocurren cada vez que se duerme.
 *
 * Una sola conexión compartida (el tráfico de una clínica es bajo); si se cae, se reabre.
 */
class AlmacenJdbc(
    private val url: String,
    private val usuario: String? = null,
    private val contrasena: String? = null,
) : AlmacenDocumentos {
    override val tipo: String = url.removePrefix("jdbc:").substringBefore(':')
    private val log = LoggerFactory.getLogger("BaseDatos")
    private var conexion: Connection? = null

    init {
        conAbierta { c ->
            c.createStatement().use {
                it.executeUpdate(
                    """
                    CREATE TABLE IF NOT EXISTS documentos (
                        coleccion   VARCHAR(60)  NOT NULL,
                        id          VARCHAR(120) NOT NULL,
                        datos       TEXT         NOT NULL,
                        actualizado TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        PRIMARY KEY (coleccion, id)
                    )
                    """.trimIndent()
                )
            }
        }
        log.info("Base de datos lista ({})", tipo)
    }

    override fun leerTodos(coleccion: String): List<String> = conAbierta { c ->
        c.prepareStatement("SELECT datos FROM documentos WHERE coleccion = ? ORDER BY id").use { ps ->
            ps.setString(1, coleccion)
            ps.executeQuery().use { rs -> buildList { while (rs.next()) add(rs.getString(1)) } }
        }
    }

    /** INSERT … ON CONFLICT … DO UPDATE: funciona igual en PostgreSQL y en SQLite. */
    override fun guardar(coleccion: String, id: String, json: String) {
        conAbierta { c ->
            c.prepareStatement(
                """
                INSERT INTO documentos (coleccion, id, datos) VALUES (?, ?, ?)
                ON CONFLICT (coleccion, id) DO UPDATE SET datos = excluded.datos, actualizado = CURRENT_TIMESTAMP
                """.trimIndent()
            ).use { ps ->
                ps.setString(1, coleccion)
                ps.setString(2, id)
                ps.setString(3, json)
                ps.executeUpdate()
            }
        }
    }

    override fun borrar(coleccion: String, id: String) {
        conAbierta { c ->
            c.prepareStatement("DELETE FROM documentos WHERE coleccion = ? AND id = ?").use { ps ->
                ps.setString(1, coleccion)
                ps.setString(2, id)
                ps.executeUpdate()
            }
        }
    }

    @Synchronized
    private fun <T> conAbierta(accion: (Connection) -> T): T {
        val actual = conexion?.takeIf { !it.isClosed && it.isValid(2) }
            ?: DriverManager.getConnection(url, usuario, contrasena).also { conexion = it }
        return accion(actual)
    }

    fun cerrar() = synchronized(this) { conexion?.close(); conexion = null }

    companion object {
        /**
         * VARIABLE DE ENTORNO DATABASE_URL. Railway la da como
         * «postgresql://usuario:contraseña@host:puerto/base»; JDBC necesita
         * «jdbc:postgresql://host:puerto/base» con el usuario y la contraseña aparte.
         * Si no existe, el servidor trabaja en memoria (como en la etapa local).
         */
        fun desdeEntorno(): AlmacenDocumentos {
            val url = System.getenv("DATABASE_URL")?.takeIf { it.isNotBlank() } ?: return AlmacenEnMemoria()
            val (jdbc, usuario, contrasena) = convertirUrl(url)
            return AlmacenJdbc(jdbc, usuario ?: System.getenv("PGUSER"), contrasena ?: System.getenv("PGPASSWORD"))
        }

        /** «postgresql://u:p@host:5432/db» → («jdbc:postgresql://host:5432/db», «u», «p»). */
        fun convertirUrl(url: String): Triple<String, String?, String?> {
            if (url.startsWith("jdbc:")) return Triple(url, null, null)
            val uri = URI(url.replaceFirst("postgres://", "postgresql://"))
            val partes = uri.rawUserInfo.orEmpty().split(":", limit = 2).map { URLDecoder.decode(it, Charsets.UTF_8) }
            val puerto = if (uri.port > 0) uri.port else 5432
            val jdbc = "jdbc:postgresql://${uri.host}:$puerto${uri.path}" + (uri.rawQuery?.let { "?$it" } ?: "")
            return Triple(jdbc, partes.getOrNull(0)?.ifEmpty { null }, partes.getOrNull(1))
        }
    }
}
