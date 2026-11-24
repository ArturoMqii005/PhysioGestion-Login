// Avisos del usuario: dolor alto, citas, rutinas y pacientes asignados.
import { api } from "../api.js";
import { boton, encabezado, h, haceCuanto, notificar, vacio } from "../ui.js";

const TIPOS = {
  DOLOR_ALTO: ["Dolor alto", "chip-alerta"],
  CITA_NUEVA: ["Cita nueva", "chip-confirmada"],
  CITA_REPROGRAMADA: ["Cita reprogramada", "chip-pendiente"],
  CITA_CANCELADA: ["Cita cancelada", "chip-apagado"],
  RUTINA_ACTUALIZADA: ["Rutina", "chip-confirmada"],
  PACIENTE_ASIGNADO: ["Paciente nuevo", "chip-exito"],
  SOLICITUD_REAGENDA: ["Pide cambio", "chip-pendiente"],
  SOLICITUD_RESUELTA: ["Solicitud", "chip-confirmada"],
  SEGURIDAD: ["Seguridad", "chip-alerta"],
  ANUNCIO: ["Anuncio", "chip-confirmada"],
  PACIENTES_INACTIVOS: ["Sin actividad", "chip-pendiente"],
};

export async function mostrar(vista, ctx) {
  let bandeja = await api.get("/avisos");
  if (!ctx.sigueVigente()) return;
  let soloSinLeer = false;
  const contenido = h("div");
  const marcarTodos = boton("Marcar todos como leídos", async () => {
    try {
      bandeja = await api.post("/avisos/leidos", { todos: true });
      notificar("Avisos marcados como leídos");
      ctx.actualizarAvisos();
      pintar();
    } catch (e) { notificar(e.message, "error"); }
  });

  vista.replaceChildren(
    encabezado("Avisos", "Se revisan solos cada minuto.", marcarTodos),
    h("div", { class: "barra-herramientas" },
      h("label", { class: "casilla" }, h("input", { type: "checkbox", onChange: (e) => { soloSinLeer = e.target.checked; pintar(); } }), "Solo sin leer")),
    contenido);

  function pintar() {
    marcarTodos.disabled = bandeja.sinLeer === 0;
    const lista = [...bandeja.avisos].filter((a) => !soloSinLeer || !a.leido).sort((a, b) => b.creado - a.creado);
    if (!lista.length) {
      contenido.replaceChildren(vacio(soloSinLeer ? "No tienes avisos sin leer" : "No tienes avisos", "Aquí llegan los avisos de dolor alto, citas, anuncios y pacientes."));
      return;
    }
    contenido.replaceChildren(h("ul", { class: "lista-avisos" }, lista.map((a) => {
      const [tipo, clase] = TIPOS[a.tipo] || [a.tipo, ""];
      const destino = a.tipo === "SOLICITUD_REAGENDA" && ctx.usuario.rol !== "FISIOTERAPEUTA" ? "#/solicitudes"
        : a.tipo === "SEGURIDAD" ? "#/cuenta"
        : a.tipo === "ANUNCIO" && ctx.usuario.rol !== "FISIOTERAPEUTA" ? "#/anuncios"
        : a.pacienteId ? "#/pacientes/" + a.pacienteId : a.citaId ? "#/agenda" : null;
      return h("li", null, h("article", { class: "tarjeta aviso" + (a.leido ? "" : " sin-leer") },
        h("div", { class: "aviso-cabeza" },
          h("span", { class: "chip " + clase }, tipo),
          h("span", { class: "tenue" }, haceCuanto(a.creado)),
          a.leido ? null : h("span", { class: "punto-nuevo", "aria-label": "sin leer" })),
        h("h2", { class: "aviso-titulo" }, a.titulo),
        h("p", { class: "aviso-mensaje" }, a.mensaje),
        h("div", { class: "aviso-acciones" },
          destino ? boton({ "#/solicitudes": "Ver solicitud", "#/cuenta": "Mi cuenta", "#/agenda": "Ver agenda", "#/anuncios": "Ver anuncios" }[destino] || "Ver paciente", () => abrir(a, destino), { chico: true }) : null,
          a.leido ? null : boton("Marcar como leído", () => marcar(a), { chico: true, tipo: "texto" }))));
    })));
  }

  async function marcar(aviso) {
    try {
      bandeja = await api.post("/avisos/leidos", { ids: [aviso.id] });
      ctx.actualizarAvisos();
      pintar();
    } catch (e) { notificar(e.message, "error"); }
  }

  async function abrir(aviso, destino) {
    if (!aviso.leido) {
      try { await api.post("/avisos/leidos", { ids: [aviso.id] }); ctx.actualizarAvisos(); } catch (_) { /* se abre de todos modos */ }
    }
    ctx.ir(destino);
  }

  pintar();
}
