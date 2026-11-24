// Utilidades de interfaz: creación de elementos, formatos de fecha, modales y avisos.
// Todo el texto que viene del servidor se inserta como texto (nunca como HTML).

/** Crea un elemento: h("button", { class: "boton", onClick: fn }, "Guardar"). */
export function h(etiqueta, props, ...hijos) {
  const el = document.createElement(etiqueta);
  let valor;
  for (const [clave, v] of Object.entries(props || {})) {
    if (v === null || v === undefined || v === false) continue;
    if (clave.startsWith("on") && typeof v === "function") el.addEventListener(clave.slice(2).toLowerCase(), v);
    else if (clave === "class") el.className = v;
    else if (clave === "value") valor = v;
    else if (clave === "checked" || clave === "selected" || clave === "disabled") el[clave] = Boolean(v);
    else if (v === true) el.setAttribute(clave, "");
    else el.setAttribute(clave, String(v));
  }
  agregar(el, hijos);
  if (valor !== undefined) el.value = valor;
  return el;
}

function agregar(el, hijos) {
  for (const hijo of hijos.flat(Infinity)) {
    if (hijo === null || hijo === undefined || hijo === false) continue;
    el.append(hijo instanceof Node ? hijo : String(hijo));
  }
}

// ------------------------------------------------------------------ Fechas

const MESES = ["enero", "febrero", "marzo", "abril", "mayo", "junio", "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre"];
const DIAS = ["domingo", "lunes", "martes", "miércoles", "jueves", "viernes", "sábado"];

/** Fecha local de hoy como "2026-11-24". */
export function hoyISO() {
  return aISO(new Date());
}

export function aISO(fecha) {
  const m = String(fecha.getMonth() + 1).padStart(2, "0");
  const d = String(fecha.getDate()).padStart(2, "0");
  return `${fecha.getFullYear()}-${m}-${d}`;
}

/** "2026-11-24" -> Date local (sin corrimiento por zona horaria). */
export function deISO(texto) {
  const [a, m, d] = texto.slice(0, 10).split("-").map(Number);
  return new Date(a, m - 1, d);
}

export function sumarDias(texto, dias) {
  const f = deISO(texto);
  f.setDate(f.getDate() + dias);
  return aISO(f);
}

/** "martes 24 de noviembre". */
export function fechaLarga(texto) {
  const f = deISO(texto);
  return `${DIAS[f.getDay()]} ${f.getDate()} de ${MESES[f.getMonth()]}`;
}

/** "24 nov 2026". */
export function fechaCorta(texto) {
  if (!texto) return "—";
  const f = texto.length > 10 ? new Date(texto) : deISO(texto);
  if (Number.isNaN(f.getTime())) return texto;
  return `${f.getDate()} ${MESES[f.getMonth()].slice(0, 3)} ${f.getFullYear()}`;
}

/** "24 nov, 10:30" para fechas con hora (ISO). */
export function fechaHora(texto) {
  if (!texto) return "—";
  const f = new Date(texto);
  if (Number.isNaN(f.getTime())) return texto;
  const hh = String(f.getHours()).padStart(2, "0");
  const mm = String(f.getMinutes()).padStart(2, "0");
  return `${f.getDate()} ${MESES[f.getMonth()].slice(0, 3)}, ${hh}:${mm}`;
}

/** Hora de término de una cita: ("10:00", 50) -> "10:50". */
export function horaFin(hora, minutos) {
  const [hh, mm] = hora.split(":").map(Number);
  const total = hh * 60 + mm + minutos;
  return `${String(Math.floor(total / 60)).padStart(2, "0")}:${String(total % 60).padStart(2, "0")}`;
}

/** "hace 5 min", "hace 2 h", "ayer", "hace 3 días". */
export function haceCuanto(ms) {
  const seg = Math.max(0, Math.round((Date.now() - ms) / 1000));
  if (seg < 60) return "hace un momento";
  const min = Math.round(seg / 60);
  if (min < 60) return `hace ${min} min`;
  const horas = Math.round(min / 60);
  if (horas < 24) return `hace ${horas} h`;
  const dias = Math.round(horas / 24);
  if (dias === 1) return "ayer";
  if (dias < 30) return `hace ${dias} días`;
  return fechaCorta(new Date(ms).toISOString());
}

export function saludo() {
  const hora = new Date().getHours();
  if (hora < 12) return "Buenos días";
  if (hora < 19) return "Buenas tardes";
  return "Buenas noches";
}

// ------------------------------------------------------------------ Piezas de la vista

/** Título de la página con subtítulo y botones a la derecha. */
export function encabezado(titulo, subtitulo, ...acciones) {
  return h("header", { class: "encabezado" },
    h("div", null,
      h("h1", { tabindex: "-1" }, titulo),
      subtitulo ? h("p", { class: "subtitulo" }, subtitulo) : null),
    acciones.length ? h("div", { class: "acciones" }, acciones) : null);
}

export function boton(texto, onClick, { tipo = "borde", titulo, chico = false } = {}) {
  const clase = { primario: "boton", borde: "boton-borde", peligro: "boton-borde peligro", texto: "boton-texto" }[tipo];
  return h("button", { type: "button", class: clase + (chico ? " chico" : ""), title: titulo, onClick }, texto);
}

export function cargando(texto = "Cargando…") {
  return h("div", { class: "estado-vista", role: "status" }, h("span", { class: "girando", "aria-hidden": "true" }), texto);
}

export function vacio(titulo, texto) {
  return h("div", { class: "vacio" }, h("p", { class: "vacio-titulo" }, titulo), texto ? h("p", null, texto) : null);
}

/** Muestra un error dentro de la vista con botón para reintentar. */
export function mostrarError(contenedor, error, reintentar) {
  contenedor.replaceChildren(
    h("div", { class: "vacio error-vista", role: "alert" },
      h("p", { class: "vacio-titulo" }, "No se pudo cargar"),
      h("p", null, error?.message || "Algo salió mal."),
      reintentar ? boton("Reintentar", reintentar) : null));
}

export function indicador(valor, etiqueta, tono) {
  const esTexto = typeof valor === "string" && valor.length > 6;
  return h("div", { class: "tarjeta indicador" + (tono ? " " + tono : "") + (esTexto ? " con-texto" : "") },
    h("span", { class: "indicador-valor" }, valor),
    h("span", { class: "indicador-etiqueta" }, etiqueta));
}

const ESTADOS_CITA = {
  PENDIENTE: ["Por confirmar", "chip-pendiente"],
  CONFIRMADA: ["Confirmada", "chip-confirmada"],
  ATENDIDA: ["Atendida", "chip-exito"],
  CANCELADA: ["Cancelada", "chip-apagado"],
  NO_ASISTIO: ["No asistió", "chip-alerta"],
};

export function chipCita(estado) {
  const [texto, clase] = ESTADOS_CITA[estado] || [estado, ""];
  return h("span", { class: "chip " + clase }, texto);
}

const SEMAFOROS = { VERDE: ["Bien", "chip-exito"], AMARILLO: ["Atención", "chip-pendiente"], ROJO: ["Dolor alto", "chip-alerta"] };

export function chipSemaforo(semaforo) {
  if (!semaforo) return h("span", { class: "tenue" }, "—");
  const [texto, clase] = SEMAFOROS[semaforo] || [semaforo, ""];
  return h("span", { class: "chip " + clase }, h("span", { class: "punto", "aria-hidden": "true" }), texto);
}

export function campo(etiqueta, control, ayuda) {
  const id = control.id || "c" + Math.random().toString(36).slice(2, 9);
  control.id = id;
  return h("div", { class: "campo" },
    h("label", { for: id }, etiqueta),
    control,
    ayuda ? h("p", { class: "ayuda" }, ayuda) : null);
}

export function entrada(props) {
  return h("input", { class: "control", ...props });
}

export function selector(opciones, props = {}) {
  return h("select", { class: "control", ...props },
    opciones.map(([valor, texto]) => h("option", { value: String(valor), selected: String(valor) === String(props.value ?? "") }, texto)));
}

// ------------------------------------------------------------------ Avisos y modales

let zonaAvisos;

/** Aviso breve abajo de la pantalla. */
export function notificar(texto, tipo = "exito") {
  if (!zonaAvisos) {
    zonaAvisos = h("div", { class: "zona-avisos", role: "status", "aria-live": "polite" });
    document.body.append(zonaAvisos);
  }
  const aviso = h("div", { class: "toast " + tipo }, texto);
  zonaAvisos.append(aviso);
  setTimeout(() => aviso.classList.add("saliendo"), 3200);
  setTimeout(() => aviso.remove(), 3600);
}

/**
 * Modal con formulario. alEnviar puede lanzar un error (se muestra dentro del modal)
 * o devolver un valor: la promesa se resuelve con ese valor al cerrar. Cancelar -> null.
 */
export function modalFormulario({ titulo, contenido, textoEnviar = "Guardar", peligro = false, ancho = false, alEnviar }) {
  return new Promise((resolver) => {
    const error = h("p", { class: "error", role: "alert" });
    const enviar = h("button", { type: "submit", class: peligro ? "boton peligro" : "boton" }, textoEnviar);
    const cancelar = h("button", { type: "button", class: "boton-borde" }, "Cancelar");
    const formulario = h("form", { class: "modal-cuerpo", novalidate: true },
      h("h2", null, titulo),
      contenido,
      error,
      h("div", { class: "modal-acciones" }, cancelar, enviar));
    const dialogo = h("dialog", { class: "modal" + (ancho ? " ancho" : "") }, formulario);
    let resultado = null;

    const cerrar = () => { dialogo.close(); };
    dialogo.addEventListener("close", () => { dialogo.remove(); resolver(resultado); });
    cancelar.addEventListener("click", cerrar);
    formulario.addEventListener("submit", async (e) => {
      e.preventDefault();
      error.textContent = "";
      enviar.disabled = true;
      const textoOriginal = enviar.textContent;
      enviar.textContent = "Guardando…";
      try {
        const r = await alEnviar(formulario);
        resultado = r === undefined ? true : r;
        cerrar();
      } catch (err) {
        error.textContent = err.message || "Algo salió mal.";
      } finally {
        enviar.disabled = false;
        enviar.textContent = textoOriginal;
      }
    });

    document.body.append(dialogo);
    dialogo.showModal();
    const primero = formulario.querySelector("input:not([type=hidden]), select, textarea");
    (primero || enviar).focus();
  });
}

/** Modal informativo con un solo botón. */
export function modalInfo(titulo, contenido, textoCerrar = "Listo") {
  return new Promise((resolver) => {
    const cerrar = h("button", { type: "button", class: "boton" }, textoCerrar);
    const dialogo = h("dialog", { class: "modal" },
      h("div", { class: "modal-cuerpo" }, h("h2", null, titulo), contenido, h("div", { class: "modal-acciones" }, cerrar)));
    cerrar.addEventListener("click", () => dialogo.close());
    dialogo.addEventListener("close", () => { dialogo.remove(); resolver(); });
    document.body.append(dialogo);
    dialogo.showModal();
    cerrar.focus();
  });
}

export function confirmar({ titulo, texto, textoSi = "Sí, continuar", peligro = false, alConfirmar }) {
  return modalFormulario({
    titulo,
    contenido: h("p", { class: "nota" }, texto),
    textoEnviar: textoSi,
    peligro,
    alEnviar: alConfirmar || (async () => true),
  });
}

/** Copia texto al portapapeles y avisa. */
export async function copiar(texto) {
  try {
    await navigator.clipboard.writeText(texto);
    notificar("Copiado");
  } catch (_) {
    notificar("No se pudo copiar; selecciónalo y cópialo a mano.", "error");
  }
}

/** Valor de un campo del formulario por nombre. */
export function valor(formulario, nombre) {
  const el = formulario.elements.namedItem(nombre);
  return el ? el.value.trim() : "";
}
