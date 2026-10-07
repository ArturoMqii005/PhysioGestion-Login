package com.terapid.physiogestion

/**
 * Punto de arranque (generado por el asistente de Ktor). Inicia el motor Netty,
 * que lee application.yaml (puerto 8080) y carga Application.module().
 */
fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}
