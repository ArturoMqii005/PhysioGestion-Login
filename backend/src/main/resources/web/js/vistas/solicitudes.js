// Solicitudes de cambio de cita que mandan los pacientes desde la app. Recepción las aprueba o rechaza.
import { api } from "../api.js";
import { boton, campo, encabezado, entrada, fechaHora, fechaLarga, h, hoyISO, modalFormulario, notificar, selector, sumarDias, vacio, valor } from "../ui.js";

const ESTADOS = [["PENDIENTE", "Pendientes"], ["", "Todas"]];
const CHIP = { PENDIENTE: ["Pendiente", "chip-pendiente"], APROBADA: ["Aprobada", "chip-exito"], RECHAZADA: ["Rechazada", "chip-apagado"] };

export async function mostrar(vista, ctx) {
  let filtro = "PENDIENTE";
  const contenido = h("div", { class: "lista-citas" });
  const chips = h("div", { class: "filtros", role: "group", "aria-label": "Filtrar solicitudes" },
    ESTADOS.map(([clave, nombre]) => h("button", { type: "button", class: "filtro", "aria-pressed": String(clave === filtro),
      onClick: (e) => {
        filtro = clave;
        chips.querySelectorAll("button").forEach((b) => b.setAttribute("aria-pressed", String(b === e.currentTarget)));
        cargar();
      } }, nombre)));

  vista.replaceChildren(
    encabezado("Solicitudes de cambio", "Los pacientes piden mover su cita cuando faltan menos de 24 horas o ya la cambiaron dos veces."),
    chips,
    contenido);

  async function cargar() {
    contenido.replaceChildren(h("p", { class: "tenue" }, "Cargando…"));
    const lista = await api.get("/solicitudes-reagenda" + (filtro ? "?estado=" + filtro : ""));
    if (!ctx.sigueVigente()) return;
    if (!lista.length) {
      contenido.replaceChildren(vacio(filtro ? "No hay solicitudes pendientes" : "Todavía no hay solicitudes", "Cuando un paciente pida cambiar su cita aparecerá aquí."));
      return;
    }
    contenido.replaceChildren(...lista.map(tarjeta));
  }

  function tarjeta(s) {
    const [texto, clase] = CHIP[s.estado] || [s.estado, ""];
    const propuesta = s.fechaPropuesta && s.horaPropuesta ? `${fechaLarga(s.fechaPropuesta)} a las ${s.horaPropuesta}` : "Sin horario específico";
    return h("article", { class: "tarjeta cita" },
      h("div", { class: "cita-hora" }, h("strong", null, s.horaActual), h("span", { class: "tenue" }, fechaLarga(s.fechaActual).split(" ").slice(1).join(" "))),
      h("div", { class: "cita-datos" },
        h("a", { class: "enlace fuerte", href: "#/pacientes/" + s.pacienteId }, s.paciente),
        h("span", { class: "tenue bloque" }, `${s.fisioterapeuta} · pedida ${fechaHora(s.creada)}`),
        h("span", { class: "bloque" }, "Propone: ", h("strong", null, propuesta)),
        h("span", { class: "nota-cita bloque" }, "«" + s.mensaje + "»"),
        s.respuesta ? h("span", { class: "tenue bloque" }, `Respuesta de ${s.atendidaPor || "recepción"}: ${s.respuesta}`) : null),
      h("div", { class: "cita-estado" }, h("span", { class: "chip " + clase }, texto)),
      s.estado === "PENDIENTE"
        ? h("div", { class: "cita-acciones" },
            boton("Aprobar y mover", () => aprobar(s), { chico: true }),
            boton("Rechazar", () => rechazar(s), { chico: true, tipo: "peligro" }))
        : null);
  }

  async function aprobar(s) {
    const fisioterapeutas = await api.get("/fisioterapeutas");
    const selFisio = selector(fisioterapeutas.map((f) => [f.id, f.nombre]), { name: "fisio", value: s.fisioterapeutaId });
    const fecha = entrada({ name: "fecha", type: "date", min: hoyISO(), max: sumarDias(hoyISO(), 120), value: s.fechaPropuesta || s.fechaActual });
    const hora = h("select", { class: "control", name: "hora" });
    const ayuda = h("p", { class: "ayuda" });
    const nota = entrada({ name: "nota", type: "text", maxlength: "200", placeholder: "Opcional. Se le envía al paciente." });

    let pedido = 0;
    async function cargarHoras() {
      const mio = ++pedido;
      hora.replaceChildren(h("option", { value: "" }, "Buscando horas libres…"));
      hora.disabled = true;
      try {
        const q = new URLSearchParams({ fecha: fecha.value, fisioterapeutaId: selFisio.value });
        const horas = await api.get(`/citas/${s.citaId}/libres?` + q);
        if (mio !== pedido) return;
        hora.replaceChildren(...(horas.length ? horas.map((x) => h("option", { value: x, selected: x === s.horaPropuesta }, x)) : [h("option", { value: "" }, "Sin horas libres")]));
        hora.disabled = !horas.length;
        ayuda.textContent = s.horaPropuesta && fecha.value === s.fechaPropuesta && !horas.includes(s.horaPropuesta)
          ? `Las ${s.horaPropuesta} ya no están libres; elige otra hora.` : "";
      } catch (e) {
        if (mio === pedido) { hora.replaceChildren(h("option", { value: "" }, "Sin horas")); ayuda.textContent = e.message; }
      }
    }
    fecha.addEventListener("change", cargarHoras);
    selFisio.addEventListener("change", cargarHoras);
    cargarHoras();

    const ok = await modalFormulario({
      titulo: "Aprobar y mover la cita",
      ancho: true,
      contenido: [
        h("p", { class: "nota" }, `${s.paciente}: «${s.mensaje}»`),
        h("div", { class: "rejilla-2" }, campo("Fecha", fecha), campo("Hora", hora)),
        ayuda,
        campo("Fisioterapeuta", selFisio),
        campo("Mensaje para el paciente", nota),
      ],
      textoEnviar: "Aprobar",
      alEnviar: (f) => {
        if (!valor(f, "hora")) throw new Error("Elige una hora libre.");
        return api.post(`/solicitudes-reagenda/${s.id}/resolver`, {
          aprobar: true, fecha: valor(f, "fecha"), hora: valor(f, "hora"), fisioterapeutaId: Number(valor(f, "fisio")), nota: valor(f, "nota") || null,
        });
      },
    });
    if (ok) { notificar("Solicitud aprobada; la cita se movió"); ctx.actualizarAvisos(); cargar(); }
  }

  async function rechazar(s) {
    const nota = h("textarea", { class: "control", name: "nota", rows: "3", maxlength: "200", required: true,
      placeholder: "Por ejemplo: no hay horas libres esa semana; te llamamos para ofrecerte otra." });
    const ok = await modalFormulario({
      titulo: "Rechazar solicitud",
      contenido: [h("p", { class: "nota" }, `La cita de ${s.paciente} se queda igual. Explícale por qué.`), campo("Motivo", nota)],
      textoEnviar: "Rechazar",
      peligro: true,
      alEnviar: (f) => {
        if (valor(f, "nota").length < 3) throw new Error("Escribe el motivo para el paciente.");
        return api.post(`/solicitudes-reagenda/${s.id}/resolver`, { aprobar: false, nota: valor(f, "nota") });
      },
    });
    if (ok) { notificar("Solicitud rechazada; se le avisó al paciente"); cargar(); }
  }

  await cargar();
}
