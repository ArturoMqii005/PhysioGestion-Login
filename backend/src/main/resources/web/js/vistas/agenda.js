// Agenda del día: recepción y administración agendan, confirman, reprograman y cancelan;
// el fisioterapeuta ve sus citas y marca la asistencia.
import { api, normalizar } from "../api.js";
import {
  boton, campo, chipCita, confirmar, encabezado, entrada, fechaLarga, h, hoyISO, horaFin, indicador,
  modalFormulario, notificar, selector, sumarDias, vacio, valor,
} from "../ui.js";

const DURACIONES = [30, 45, 50, 60, 90];

export async function mostrar(vista, ctx) {
  const gestiona = ctx.usuario.rol !== "FISIOTERAPEUTA";
  let fecha = esFecha(ctx.consulta.get("fecha")) ? ctx.consulta.get("fecha") : hoyISO();
  let filtroFisio = ctx.consulta.get("fisio") || "";
  const fisioterapeutas = await api.get("/fisioterapeutas");
  if (!ctx.sigueVigente()) return;

  const lista = h("div", { class: "lista-citas", "aria-live": "polite" });
  const resumen = h("div", { class: "indicadores compactos" });
  const tituloDia = h("h2", { class: "titulo-dia" });
  const campoFecha = entrada({ type: "date", value: fecha, "aria-label": "Elegir fecha", onChange: (e) => { if (e.target.value) cambiarFecha(e.target.value); } });

  const filtro = gestiona
    ? selector([["", "Todos los fisioterapeutas"], ...fisioterapeutas.map((f) => [f.id, f.nombre])], {
        value: filtroFisio, "aria-label": "Filtrar por fisioterapeuta",
        onChange: (e) => { filtroFisio = e.target.value; recordar(); cargar(); },
      })
    : null;

  vista.replaceChildren(
    encabezado("Agenda", gestiona ? "Citas de la clínica por día." : "Tus citas por día.",
      gestiona ? boton("Nueva cita", () => abrirNuevaCita({ fecha, alGuardar: (c) => cambiarFecha(c.fecha) }), { tipo: "primario" }) : null),
    h("div", { class: "barra-herramientas" },
      h("div", { class: "navegacion-dias" },
        boton("‹", () => cambiarFecha(sumarDias(fecha, -1)), { titulo: "Día anterior" }),
        boton("Hoy", () => cambiarFecha(hoyISO())),
        boton("›", () => cambiarFecha(sumarDias(fecha, 1)), { titulo: "Día siguiente" }),
        campoFecha),
      filtro),
    tituloDia,
    resumen,
    lista);

  function cambiarFecha(nueva) {
    fecha = nueva;
    campoFecha.value = nueva;
    recordar();
    cargar();
  }

  function recordar() {
    const q = new URLSearchParams();
    if (fecha !== hoyISO()) q.set("fecha", fecha);
    if (filtroFisio) q.set("fisio", filtroFisio);
    history.replaceState(null, "", "#/agenda" + (q.toString() ? "?" + q : ""));
  }

  async function cargar() {
    const pedida = fecha;
    tituloDia.textContent = (fecha === hoyISO() ? "Hoy, " : "") + fechaLarga(fecha);
    lista.replaceChildren(h("p", { class: "tenue" }, "Cargando citas…"));
    let citas;
    try {
      citas = (await api.get("/citas?fecha=" + fecha)).map(normalizar.cita);
    } catch (e) {
      lista.replaceChildren(vacio("No se pudo cargar la agenda", e.message));
      return;
    }
    if (pedida !== fecha) return;
    if (filtroFisio) citas = citas.filter((c) => String(c.fisioterapeutaId) === filtroFisio);
    citas.sort((a, b) => a.hora.localeCompare(b.hora));

    const contar = (estado) => citas.filter((c) => c.estado === estado).length;
    resumen.replaceChildren(
      indicador(citas.length, "citas"),
      indicador(contar("CONFIRMADA"), "confirmadas"),
      indicador(contar("PENDIENTE"), "por confirmar", contar("PENDIENTE") ? "tono-pendiente" : ""),
      indicador(contar("ATENDIDA"), "atendidas"),
      indicador(contar("CANCELADA") + contar("NO_ASISTIO"), "canceladas o faltas"));

    if (!citas.length) {
      lista.replaceChildren(vacio("No hay citas este día",
        gestiona ? "Usa «Nueva cita» para agendar una." : "Cuando recepción te agende citas aparecerán aquí."));
      return;
    }
    lista.replaceChildren(...citas.map((c) => filaCita(c)));
  }

  function filaCita(c) {
    const hoy = hoyISO();
    const abierta = c.estado === "PENDIENTE" || c.estado === "CONFIRMADA";
    const acciones = [];
    if (abierta && gestiona) {
      if (c.estado === "PENDIENTE") acciones.push(boton("Confirmar", () => cambiarEstado(c, "CONFIRMADA", "Cita confirmada"), { chico: true }));
      acciones.push(boton("Reprogramar", () => reprogramar(c), { chico: true }));
      if (c.fecha >= hoy) acciones.push(boton("Cancelar", () => cancelar(c), { chico: true, tipo: "peligro" }));
    }
    const puedeMarcar = abierta && c.fecha <= hoy && (gestiona || c.fisioterapeutaId === ctx.usuario.id);
    if (puedeMarcar) {
      acciones.push(boton("Atendida", () => cambiarEstado(c, "ATENDIDA", "Cita marcada como atendida"), { chico: true }));
      acciones.push(boton("No asistió", () => marcarFalta(c), { chico: true }));
    }

    const nombrePaciente = c.pacienteId
      ? h("a", { href: "#/pacientes/" + c.pacienteId, class: "enlace fuerte" }, c.paciente)
      : h("strong", null, c.paciente, h("span", { class: "tenue" }, " · sin expediente"));

    return h("article", { class: "tarjeta cita" + (abierta ? "" : " cerrada") },
      h("div", { class: "cita-hora" }, h("strong", null, c.hora), h("span", { class: "tenue" }, horaFin(c.hora, c.duracionMinutos))),
      h("div", { class: "cita-datos" },
        nombrePaciente,
        h("span", { class: "tenue bloque" }, (gestiona ? c.fisioterapeuta + " · " : "") + c.motivo + " · " + c.duracionMinutos + " min"),
        c.nota ? h("span", { class: "nota-cita bloque" }, c.nota) : null),
      h("div", { class: "cita-estado" }, chipCita(c.estado)),
      acciones.length ? h("div", { class: "cita-acciones" }, acciones) : null);
  }

  async function cambiarEstado(c, estado, mensaje) {
    try {
      await api.post(`/citas/${c.id}/estado`, { estado });
      notificar(mensaje);
      cargar();
    } catch (e) {
      notificar(e.message, "error");
    }
  }

  async function marcarFalta(c) {
    const ok = await confirmar({
      titulo: "¿Marcar que no asistió?",
      texto: `${c.paciente} no llegó a su cita de las ${c.hora}. Esto queda en su historial.`,
      textoSi: "Marcar falta",
      alConfirmar: () => api.post(`/citas/${c.id}/estado`, { estado: "NO_ASISTIO" }),
    });
    if (ok) { notificar("Falta registrada"); cargar(); }
  }

  async function cancelar(c) {
    const nota = h("textarea", { class: "control", name: "nota", rows: "3", maxlength: "200", placeholder: "Opcional. Por ejemplo: el paciente pidió cambiar de día." });
    const ok = await modalFormulario({
      titulo: "Cancelar cita",
      contenido: [
        h("p", { class: "nota" }, `${c.paciente} · ${fechaLarga(c.fecha)} a las ${c.hora} con ${c.fisioterapeuta}. Se le avisa al paciente en la app.`),
        campo("Motivo de la cancelación", nota),
      ],
      textoEnviar: "Cancelar cita",
      peligro: true,
      alEnviar: (f) => api.post(`/citas/${c.id}/estado`, { estado: "CANCELADA", nota: valor(f, "nota") || null }),
    });
    if (ok) { notificar("Cita cancelada"); cargar(); }
  }

  async function reprogramar(c) {
    const guardada = await formularioCita({ cita: c, fisioterapeutas });
    if (guardada) { notificar("Cita reprogramada"); cambiarFecha(guardada.fecha); }
  }

  async function abrirNuevaCita({ fecha: fechaInicial, alGuardar }) {
    const guardada = await formularioCita({ fecha: fechaInicial >= hoyISO() ? fechaInicial : hoyISO(), fisioterapeutas });
    if (guardada) { notificar("Cita agendada"); alGuardar(guardada); }
  }

  if (gestiona && ctx.consulta.get("nueva") === "1") {
    const pacienteId = ctx.consulta.get("paciente");
    recordar();
    formularioCita({ fecha: hoyISO(), fisioterapeutas, pacienteId }).then((c) => {
      if (c) { notificar("Cita agendada"); cambiarFecha(c.fecha); }
    });
  }

  cargar();
}

/**
 * Formulario de nueva cita (sin `cita`) o de reprogramación (con `cita`).
 * Las horas se piden al servidor según fisioterapeuta, fecha y duración.
 */
async function formularioCita({ cita, fecha, fisioterapeutas, pacienteId }) {
  const nueva = !cita;
  const pacientes = nueva ? await api.get("/pacientes") : [];
  pacientes.sort((a, b) => a.nombre.localeCompare(b.nombre));

  const selPaciente = nueva
    ? selector([["", "Elige un paciente…"], ...pacientes.map((p) => [p.pacienteId, p.nombre]), ["otro", "Otra persona (primera consulta)"]],
        { name: "paciente", required: true, value: pacienteId || "" })
    : null;
  const nombreOtro = entrada({ name: "nombreOtro", type: "text", maxlength: "80", placeholder: "Nombre y apellidos", autocomplete: "off" });
  const campoOtro = campo("Nombre de la persona", nombreOtro, "Se le crea expediente cuando recepción le genere su invitación.");
  campoOtro.hidden = true;

  const selFisio = selector(fisioterapeutas.map((f) => [f.id, f.nombre]), { name: "fisio", value: cita ? cita.fisioterapeutaId : undefined });
  const campoFechaCita = entrada({ name: "fecha", type: "date", required: true, min: hoyISO(), max: sumarDias(hoyISO(), 120), value: cita ? (cita.fecha >= hoyISO() ? cita.fecha : hoyISO()) : fecha });
  const selDuracion = selector(DURACIONES.map((d) => [d, d + " minutos"]), { name: "duracion", value: cita ? cita.duracionMinutos : 50 });
  const selHora = h("select", { class: "control", name: "hora", required: true });
  const estadoHoras = h("p", { class: "ayuda" });
  const motivo = entrada({ name: "motivo", type: "text", maxlength: "120", required: true, value: cita ? cita.motivo : "", placeholder: "Por ejemplo: valoración de rodilla" });

  let pedido = 0;
  async function cargarHoras() {
    const mio = ++pedido;
    selHora.replaceChildren(h("option", { value: "" }, "Buscando horas libres…"));
    selHora.disabled = true;
    estadoHoras.textContent = "";
    const q = new URLSearchParams({ fisioterapeutaId: selFisio.value, fecha: campoFechaCita.value, duracion: selDuracion.value });
    if (cita) q.set("excepto", cita.id);
    try {
      const horas = await api.get("/citas/libres?" + q);
      if (mio !== pedido) return;
      if (!horas.length) {
        selHora.replaceChildren(h("option", { value: "" }, "Sin horas libres"));
        estadoHoras.textContent = "Ese día no tiene horas libres con este fisioterapeuta. Prueba otro día u otra persona.";
        return;
      }
      selHora.replaceChildren(...horas.map((hora) => h("option", { value: hora, selected: cita && cita.fecha === campoFechaCita.value && cita.hora === hora }, hora)));
      selHora.disabled = false;
      estadoHoras.textContent = "La clínica atiende de lunes a sábado, de 7:00 a 21:00.";
    } catch (e) {
      if (mio !== pedido) return;
      selHora.replaceChildren(h("option", { value: "" }, "Sin horas"));
      estadoHoras.textContent = e.message;
    }
  }

  selFisio.addEventListener("change", cargarHoras);
  campoFechaCita.addEventListener("change", cargarHoras);
  selDuracion.addEventListener("change", cargarHoras);
  if (selPaciente) {
    const alElegir = () => {
      campoOtro.hidden = selPaciente.value !== "otro";
      const p = pacientes.find((x) => String(x.pacienteId) === selPaciente.value);
      if (p && p.fisioterapeutaId && String(p.fisioterapeutaId) !== selFisio.value) {
        selFisio.value = String(p.fisioterapeutaId);
        cargarHoras();
      }
    };
    selPaciente.addEventListener("change", alElegir);
    alElegir();
  }
  cargarHoras();

  return modalFormulario({
    titulo: nueva ? "Nueva cita" : "Reprogramar cita",
    ancho: true,
    contenido: [
      nueva ? campo("Paciente", selPaciente) : h("p", { class: "nota" }, `${cita.paciente} · antes: ${fechaLarga(cita.fecha)} a las ${cita.hora}`),
      nueva ? campoOtro : null,
      h("div", { class: "rejilla-2" },
        campo("Fisioterapeuta", selFisio),
        campo("Duración", selDuracion),
        campo("Fecha", campoFechaCita),
        campo("Hora", selHora)),
      estadoHoras,
      campo("Motivo", motivo),
    ],
    textoEnviar: nueva ? "Agendar" : "Guardar cambios",
    alEnviar: async (f) => {
      if (!valor(f, "hora")) throw new Error("Elige una hora libre.");
      if (valor(f, "motivo").length < 3) throw new Error("Escribe el motivo de la cita.");
      const base = {
        fisioterapeutaId: Number(valor(f, "fisio")),
        fecha: valor(f, "fecha"),
        hora: valor(f, "hora"),
        duracionMinutos: Number(valor(f, "duracion")),
        motivo: valor(f, "motivo"),
      };
      if (!nueva) return api.patch("/citas/" + cita.id, base);
      const elegido = valor(f, "paciente");
      if (!elegido) throw new Error("Elige al paciente.");
      if (elegido === "otro") {
        const nombre = valor(f, "nombreOtro");
        if (nombre.length < 5) throw new Error("Escribe el nombre completo de la persona.");
        return api.post("/citas", { ...base, paciente: nombre });
      }
      const p = pacientes.find((x) => String(x.pacienteId) === elegido);
      return api.post("/citas", { ...base, paciente: p.nombre, pacienteId: p.pacienteId });
    },
  });
}

function esFecha(texto) {
  return Boolean(texto && /^\d{4}-\d{2}-\d{2}$/.test(texto));
}
