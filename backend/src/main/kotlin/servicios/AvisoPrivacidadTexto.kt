package com.terapid.physiogestion.servicios

import com.terapid.physiogestion.modelos.AvisoPrivacidad
import com.terapid.physiogestion.modelos.SeccionAviso

/**
 * AVISO DE PRIVACIDAD (USR-21). La app trata datos de salud, que la LFPDPPP
 * considera datos personales sensibles (art. 2, fr. VI); para tratarlos se
 * necesita el consentimiento expreso del titular (art. 8). La app muestra este
 * texto y guarda qué versión aceptó el paciente.
 *
 * Texto base del proyecto escolar: la clínica debe revisarlo con su área legal
 * antes de usar la app con pacientes reales.
 */
object AvisoPrivacidadTexto {
    val vigente = AvisoPrivacidad(
        version = AutenticacionServicio.VERSION_AVISO,
        titulo = "Aviso de privacidad de PhysioGestion",
        secciones = listOf(
            SeccionAviso(
                "Responsable",
                "Clínica Terapid By Farmacias es responsable del tratamiento de tus datos personales en la aplicación PhysioGestion.",
            ),
            SeccionAviso(
                "Datos que se recaban",
                "Nombre, usuario, correo electrónico y, como datos personales sensibles, información de tu salud: tu rutina de ejercicios, " +
                    "el nivel de dolor que registras, los resúmenes de las mediciones con los sensores, la cámara y el barómetro, y tus pasos.",
            ),
            SeccionAviso(
                "Para qué se usan",
                "Para que tu fisioterapeuta dé seguimiento a tu tratamiento a distancia, ajuste tu rutina y te avise si algo no va bien. " +
                    "No se usan con fines publicitarios ni se venden a terceros.",
            ),
            SeccionAviso(
                "Cámara y sensores",
                "La imagen de la cámara se analiza dentro de tu teléfono: no se graba ni se envía. Al servidor solo llegan resúmenes " +
                    "(repeticiones, ángulo, tiempo), nunca las lecturas crudas.",
            ),
            SeccionAviso(
                "Transferencias",
                "Tus datos solo se comparten con el personal de la clínica que te atiende y con los proveedores que alojan el servicio, " +
                    "que están obligados a protegerlos.",
            ),
            SeccionAviso(
                "Tus derechos ARCO",
                "Puedes pedir el acceso, la rectificación o la cancelación de tus datos, u oponerte a su uso, en la recepción de la clínica. " +
                    "También puedes retirar tu consentimiento; en ese caso dejaremos de dar seguimiento a tu rutina desde la app.",
            ),
            SeccionAviso(
                "Consentimiento",
                "Al tocar «Acepto» otorgas tu consentimiento expreso para el tratamiento de tus datos personales sensibles con las finalidades descritas.",
            ),
        ),
    )
}
