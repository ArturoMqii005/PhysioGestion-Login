// Ficha del paciente: datos, citas y, para el personal clínico, rutina, sesiones, mediciones y escaleras.
import { api, normalizar } from "../api.js";
import { boton, chipCita, chipSemaforo, encabezado, fechaCorta, fechaHora, fechaLarga, h, indicador, vacio } from "../ui.js";
import { asignarFisio, barra, fechaCita } from "./pacientes.js";

const DIFICULTAD = { BAJA: "Baja", MEDIA: "Media", ALTA: "Alta" };

export async function mostrar(vista, ctx) {
  const id = Number(ctx.params[0]);
  const rol = ctx.usuario.rol;
  const esFisio = rol === "FISIOTERAPEUTA";
  const gestiona = rol !== "FISIOTERAPEUTA";
  const clinico = rol !== "RECEPCIONISTA";

  // El fisioterapeuta ve el historial completo; administración, lo reciente que trae la ficha.
  const [ficha, sesiones, resultados, escaleras, catalogo] = await Promise.all([
    api.get("/pacientes/" + id).then(normalizar.ficha),
    esFisio ? api.get(`/sesiones?idPaciente=${id}&tamano=50`) : null,
    esFisio ? api.get("/resultados-ejercicio?idPaciente=" + id) : null,
    esFisio ? api.get("/pruebas-escaleras?idPaciente=" + id) : null,
    clinico ? api.get("/ejercicios").catch(() => []) : [],
  ]);
  if (!ctx.sigueVigente()) return;

  const p = ficha.resumen;
  const nombresEjercicio = new Map(catalogo.map((e) => [e.id, e.nombre]));
  (ficha.rutina?.ejercicios || []).forEach((e) => nombresEjercicio.set(e.id, e.nombre));
  const listaSesiones = sesiones || ficha.sesionesRecientes;
  const listaEscaleras = escaleras || ficha.pruebasEscaleras;

  const recargar = () => mostrar(vista, ctx);
  const acciones = [];
  if (gestiona) {
    acciones.push(boton("Agendar cita", () => ctx.ir(`#/agenda?nueva=1&paciente=${id}`)));
    acciones.push(boton(p.fisioterapeutaId ? "Cambiar fisioterapeuta" : "Asignar fisioterapeuta", () => asignarFisio(p, recargar), { tipo: p.fisioterapeutaId ? "borde" : "primario" }));
  }
  if (esFisio) acciones.push(boton(ficha.rutina ? "Editar rutina" : "Crear rutina", () => ctx.ir(`#/pacientes/${id}/rutina`), { tipo: "primario" }));

  const indicadores = [
    indicador(p.fisioterapeuta || "Sin asignar", "fisioterapeuta", p.fisioterapeuta ? "" : "tono-pendiente"),
    indicador(p.proximaCita ? fechaCita(p.proximaCita) : "—", "próxima cita"),
  ];
  if (clinico) {
    indicadores.push(indicador(p.cumplimientoPromedio != null ? p.cumplimientoPromedio + "%" : "—", "cumplimiento (últimas 5)"));
    indicadores.push(indicador(p.semaforo ? chipSemaforo(p.semaforo) : "—", "última sesión"));
  }

  const secciones = [
    encabezado(p.nombre, p.tieneCuenta ? (p.correo || "Con cuenta en la app") : "Aún no crea su cuenta en la app", ...acciones),
    h("p", null, h("a", { href: "#/pacientes", class: "enlace" }, "‹ Volver a pacientes")),
    h("div", { class: "indicadores" }, indicadores),
    seccionCitas(ficha.citas),
  ];

  if (!clinico) {
    secciones.push(h("p", { class: "nota tarjeta" }, "La rutina, el dolor y las sesiones solo los ve el personal clínico."));
  } else {
    secciones.push(seccionRutina(ficha.rutina, esFisio, id));
    secciones.push(seccionSesiones(listaSesiones, ficha.rutina, nombresEjercicio, esFisio));
    if (esFisio) secciones.push(seccionMediciones(resultados, nombresEjercicio));
    secciones.push(seccionEscaleras(listaEscaleras));
  }
  vista.replaceChildren(...secciones);
}

function seccionCitas(citas) {
  return h("section", { class: "tarjeta" },
    h("h2", null, "Citas"),
    citas.length
      ? h("ul", { class: "lista" }, citas.map((c) => h("li", { class: "fila" },
          h("span", { class: "hora" }, c.hora),
          h("span", { class: "crece" }, h("strong", null, capital(fechaLarga(c.fecha))), h("span", { class: "tenue bloque" }, `${c.fisioterapeuta} · ${c.motivo}`)),
          chipCita(c.estado))))
      : vacio("Sin citas próximas"));
}

function seccionRutina(rutina, esFisio, id) {
  if (!rutina) {
    return h("section", { class: "tarjeta" }, h("h2", null, "Rutina"),
      vacio("Todavía no tiene rutina", esFisio ? "Crea su rutina para que la vea en la app." : "Su fisioterapeuta todavía no le asigna ejercicios."),
      esFisio ? h("a", { class: "boton enlace-boton", href: `#/pacientes/${id}/rutina` }, "Crear rutina") : null);
  }
  return h("section", { class: "tarjeta" },
    h("div", { class: "seccion-titulo" }, h("h2", null, "Rutina"), h("span", { class: "tenue" }, "Vigente desde " + fechaCorta(rutina.vigenteDesde))),
    h("dl", { class: "datos" },
      h("div", null, h("dt", null, "Meta funcional"), h("dd", null, rutina.metaFuncional)),
      h("div", null, h("dt", null, "Límite de dolor"), h("dd", null, rutina.umbralDolor + " de 10")),
      h("div", null, h("dt", null, "Fisioterapeuta"), h("dd", null, rutina.fisioterapeuta))),
    h("div", { class: "tabla-scroll" }, h("table", { class: "tabla" },
      h("thead", null, h("tr", null, ["Ejercicio", "Zona", "Series × reps", "Descanso", "Ángulo", "Medición"].map((c) => h("th", { scope: "col" }, c)))),
      h("tbody", null, rutina.ejercicios.map((e) => h("tr", null,
        h("td", { "data-etiqueta": "Ejercicio" }, h("strong", null, e.nombre), h("span", { class: "tenue bloque" }, e.indicaciones)),
        h("td", { "data-etiqueta": "Zona" }, e.zonaCorporal),
        h("td", { "data-etiqueta": "Series × reps" }, `${e.series} × ${e.repeticiones}`),
        h("td", { "data-etiqueta": "Descanso" }, e.descansoSegundos + " s"),
        h("td", { "data-etiqueta": "Ángulo" }, e.anguloObjetivo ? e.anguloObjetivo + "°" : "—"),
        h("td", { "data-etiqueta": "Medición" }, medicion(e))))))));
}

function medicion(e) {
  const tipos = [];
  if (e.medibleConSensores) tipos.push("Sensores");
  if (e.admiteVerificacionPorCamara) tipos.push("Cámara");
  return tipos.length ? tipos.join(" y ") : h("span", { class: "tenue" }, "Manual");
}

function seccionSesiones(sesiones, rutina, nombres, completo) {
  const titulo = h("div", { class: "seccion-titulo" },
    h("h2", null, "Sesiones en casa"),
    h("span", { class: "tenue" }, completo ? `${sesiones.length} registradas` : "Las 5 más recientes"));
  if (!sesiones.length) return h("section", { class: "tarjeta" }, titulo, vacio("Sin sesiones registradas", "Las sesiones que el paciente registre en la app aparecen aquí."));

  const ordenadas = [...sesiones].sort((a, b) => b.fechaHoraInicio.localeCompare(a.fechaHoraInicio));
  return h("section", { class: "tarjeta" },
    titulo,
    sesiones.length > 1 ? graficaDolor([...ordenadas].reverse(), rutina?.umbralDolor) : null,
    h("div", { class: "tabla-scroll" }, h("table", { class: "tabla" },
      h("thead", null, h("tr", null, ["Fecha", "Dolor", "Dificultad", "Cumplimiento", "Ejercicios", "Estado"].map((c) => h("th", { scope: "col" }, c)))),
      h("tbody", null, ordenadas.map((s) => h("tr", null,
        h("td", { "data-etiqueta": "Fecha" }, fechaHora(s.fechaHoraInicio),
          s.observacion ? h("span", { class: "tenue bloque" }, "«" + s.observacion + "»") : null),
        h("td", { "data-etiqueta": "Dolor" }, `${s.dolorAntes} → ${s.dolorDespues}`),
        h("td", { "data-etiqueta": "Dificultad" }, DIFICULTAD[s.dificultad] || s.dificultad),
        h("td", { "data-etiqueta": "Cumplimiento" }, barra(s.porcentajeCumplimiento)),
        h("td", { "data-etiqueta": "Ejercicios" }, resumenEjercicios(s.ejercicios, nombres)),
        h("td", { "data-etiqueta": "Estado" }, chipSemaforo(s.semaforo))))))));
}

function resumenEjercicios(ejercicios, nombres) {
  const verificados = ejercicios.filter((e) => e.verificado).length;
  const titulo = ejercicios.map((e) => `${nombres.get(e.idEjercicio) || "Ejercicio " + e.idEjercicio}: ${e.repeticionesRealizadas} reps` +
    (e.metodo ? ` (${e.metodo === "CAMARA" ? "cámara" : "sensores"})` : "")).join("\n");
  const hechos = ejercicios.length === 1 ? "1 hecho" : `${ejercicios.length} hechos`;
  const medidos = verificados === 1 ? "1 medido" : `${verificados} medidos`;
  return h("span", { title: titulo }, hechos, verificados ? h("span", { class: "tenue bloque" }, medidos) : null);
}

/** Gráfica SVG del dolor después de cada sesión, con el límite del fisioterapeuta. */
function graficaDolor(sesiones, umbral) {
  const ancho = 640, alto = 180, mx = 32, my = 16;
  const n = sesiones.length;
  const x = (i) => mx + (n === 1 ? 0 : (i * (ancho - mx * 2)) / (n - 1));
  const y = (v) => my + ((10 - v) * (alto - my * 2)) / 10;
  const svg = (tag, attrs, ...hijos) => {
    const el = document.createElementNS("http://www.w3.org/2000/svg", tag);
    for (const [k, v] of Object.entries(attrs)) el.setAttribute(k, v);
    hijos.forEach((c) => el.append(c));
    return el;
  };
  const puntos = sesiones.map((s, i) => `${x(i)},${y(s.dolorDespues)}`).join(" ");
  const figura = svg("svg", { viewBox: `0 0 ${ancho} ${alto}`, class: "grafica", role: "img",
    "aria-label": `Dolor después de cada sesión, de ${n} sesiones. Última: ${sesiones[n - 1].dolorDespues} de 10.` },
    ...[0, 5, 10].map((v) => svg("g", {},
      svg("line", { x1: mx, x2: ancho - mx, y1: y(v), y2: y(v), class: "grafica-guia" }),
      svg("text", { x: 4, y: y(v) + 4, class: "grafica-texto" }, String(v)))),
    umbral ? svg("line", { x1: mx, x2: ancho - mx, y1: y(umbral), y2: y(umbral), class: "grafica-umbral" }) : "",
    umbral ? svg("text", { x: ancho - mx, y: y(umbral) - 6, class: "grafica-texto grafica-umbral-texto", "text-anchor": "end" }, `Límite ${umbral}`) : "",
    svg("polyline", { points: puntos, class: "grafica-linea" }),
    ...sesiones.map((s, i) => svg("circle", { cx: x(i), cy: y(s.dolorDespues), r: 4, class: "grafica-punto" + (umbral && s.dolorDespues >= umbral ? " alto" : "") },
      svg("title", {}, `${fechaHora(s.fechaHoraInicio)}: dolor ${s.dolorDespues}`))));
  return h("figure", { class: "figura" }, figura, h("figcaption", { class: "tenue" }, "Dolor después de cada sesión (0 a 10), de la más antigua a la más reciente."));
}

function seccionMediciones(resultados, nombres) {
  const titulo = h("div", { class: "seccion-titulo" }, h("h2", null, "Mediciones con sensores y cámara"));
  if (!resultados || !resultados.length) return h("section", { class: "tarjeta" }, titulo, vacio("Sin mediciones", "Cuando el paciente mida un ejercicio con el teléfono, el resultado aparece aquí."));
  const lista = [...resultados].sort((a, b) => b.id - a.id).slice(0, 30);
  return h("section", { class: "tarjeta" }, titulo,
    h("div", { class: "tabla-scroll" }, h("table", { class: "tabla" },
      h("thead", null, h("tr", null, ["Ejercicio", "Repeticiones", "Ángulo máximo", "Duración", "Método", "Resultado"].map((c) => h("th", { scope: "col" }, c)))),
      h("tbody", null, lista.map((r) => h("tr", null,
        h("td", { "data-etiqueta": "Ejercicio" }, nombres.get(r.idEjercicio) || "Ejercicio " + r.idEjercicio),
        h("td", { "data-etiqueta": "Repeticiones" }, `${r.repeticiones} de ${r.repeticionesObjetivo}`),
        h("td", { "data-etiqueta": "Ángulo máximo" }, r.anguloMaximo + "°" + (r.anguloObjetivo ? ` (meta ${r.anguloObjetivo}°)` : "")),
        h("td", { "data-etiqueta": "Duración" }, duracion(r.duracionSegundos)),
        h("td", { "data-etiqueta": "Método" }, r.metodo === "CAMARA" ? "Cámara" : "Sensores"),
        h("td", { "data-etiqueta": "Resultado" }, r.cumpleObjetivo
          ? h("span", { class: "chip chip-exito" }, "Cumple")
          : h("span", { class: "chip chip-pendiente" }, "Por debajo"))))))));
}

function seccionEscaleras(pruebas) {
  const titulo = h("div", { class: "seccion-titulo" }, h("h2", null, "Pruebas de escaleras"));
  if (!pruebas || !pruebas.length) return h("section", { class: "tarjeta" }, titulo, vacio("Sin pruebas de escaleras", "Se registran desde la app con el barómetro del teléfono."));
  return h("section", { class: "tarjeta" }, titulo,
    h("div", { class: "tabla-scroll" }, h("table", { class: "tabla" },
      h("thead", null, h("tr", null, ["Fecha", "Pisos", "Altura subida", "Escalones", "Pasos", "Duración"].map((c) => h("th", { scope: "col" }, c)))),
      h("tbody", null, pruebas.map((p) => h("tr", null,
        h("td", { "data-etiqueta": "Fecha" }, p.fecha.length > 10 ? fechaHora(p.fecha) : fechaCorta(p.fecha)),
        h("td", { "data-etiqueta": "Pisos" }, p.pisos),
        h("td", { "data-etiqueta": "Altura subida" }, (p.alturaSubidaCm / 100).toFixed(1) + " m"),
        h("td", { "data-etiqueta": "Escalones" }, "≈ " + p.escalonesEstimados),
        h("td", { "data-etiqueta": "Pasos" }, p.pasos ?? "—"),
        h("td", { "data-etiqueta": "Duración" }, duracion(p.duracionSegundos))))))));
}

function duracion(segundos) {
  const m = Math.floor(segundos / 60);
  const s = segundos % 60;
  return m ? `${m} min ${s} s` : `${s} s`;
}

function capital(texto) {
  return texto.charAt(0).toUpperCase() + texto.slice(1);
}
