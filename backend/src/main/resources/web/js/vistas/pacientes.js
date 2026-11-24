// Lista de pacientes con búsqueda y filtros. Recepción y administración asignan fisioterapeuta.
import { api } from "../api.js";
import { boton, campo, chipSemaforo, encabezado, entrada, fechaCorta, fechaHora, h, modalFormulario, notificar, selector, vacio, valor } from "../ui.js";
import { nuevaInvitacion } from "./invitaciones.js";

const FILTROS = [
  ["todos", "Todos"],
  ["sinFisio", "Sin fisioterapeuta"],
  ["sinCuenta", "Sin cuenta"],
  ["sinRutina", "Sin rutina"],
  ["rojo", "Dolor alto"],
];

export async function mostrar(vista, ctx) {
  const gestiona = ctx.usuario.rol !== "FISIOTERAPEUTA";
  const clinico = ctx.usuario.rol !== "RECEPCIONISTA";
  let filtro = ctx.consulta.get("filtro") || "todos";
  let texto = "";

  let pacientes = await api.get("/pacientes");
  if (!ctx.sigueVigente()) return;

  const contenido = h("div");
  const contador = h("p", { class: "tenue" });
  const buscador = entrada({ type: "search", placeholder: "Buscar por nombre o correo", "aria-label": "Buscar paciente",
    onInput: (e) => { texto = e.target.value.trim().toLowerCase(); pintar(); } });
  const chips = h("div", { class: "filtros", role: "group", "aria-label": "Filtrar pacientes" },
    FILTROS.filter(([clave]) => clinico || clave !== "rojo").filter(([clave]) => gestiona || clave !== "sinFisio").map(([clave, nombre]) =>
      h("button", { type: "button", class: "filtro", "aria-pressed": String(clave === filtro),
        onClick: (e) => {
          filtro = clave;
          chips.querySelectorAll("button").forEach((b) => b.setAttribute("aria-pressed", String(b === e.currentTarget)));
          pintar();
        } }, nombre)));

  vista.replaceChildren(
    encabezado("Pacientes",
      gestiona ? "Todos los pacientes de la clínica." : "Los pacientes que tienes en seguimiento.",
      gestiona ? boton("Nuevo paciente", async () => {
        const inv = await nuevaInvitacion();
        if (inv) recargar();
      }, { tipo: "primario" }) : null),
    h("div", { class: "barra-herramientas" }, buscador, chips),
    contador,
    contenido);

  async function recargar() {
    pacientes = await api.get("/pacientes");
    pintar();
  }

  function pintar() {
    const visibles = pacientes
      .filter((p) => {
        if (filtro === "sinFisio") return !p.fisioterapeutaId;
        if (filtro === "sinCuenta") return !p.tieneCuenta;
        if (filtro === "sinRutina") return !p.tieneRutina;
        if (filtro === "rojo") return p.semaforo === "ROJO";
        return true;
      })
      .filter((p) => !texto || p.nombre.toLowerCase().includes(texto) || (p.correo || "").toLowerCase().includes(texto))
      .sort((a, b) => a.nombre.localeCompare(b.nombre));

    contador.textContent = `${visibles.length} de ${pacientes.length} pacientes`;
    if (!visibles.length) {
      contenido.replaceChildren(vacio("Sin resultados", pacientes.length ? "Prueba con otro filtro o búsqueda." : "Todavía no hay pacientes registrados."));
      return;
    }

    const columnas = ["Paciente", "Fisioterapeuta", ...(clinico ? ["Rutina", "Última sesión", "Estado", "Cumplimiento"] : []), "Próxima cita"];
    contenido.replaceChildren(h("div", { class: "tabla-scroll" }, h("table", { class: "tabla" },
      h("thead", null, h("tr", null, columnas.map((c) => h("th", { scope: "col" }, c)))),
      h("tbody", null, visibles.map((p) => h("tr", null,
        h("td", { "data-etiqueta": "Paciente" },
          h("a", { class: "enlace fuerte", href: "#/pacientes/" + p.pacienteId }, p.nombre),
          h("span", { class: "tenue bloque" }, p.tieneCuenta ? (p.correo || "Con cuenta") : "Aún no crea su cuenta")),
        h("td", { "data-etiqueta": "Fisioterapeuta" },
          p.fisioterapeuta || h("span", { class: "chip chip-pendiente" }, "Sin asignar"),
          gestiona ? h("div", null, boton(p.fisioterapeutaId ? "Cambiar" : "Asignar", () => asignarFisio(p, recargar), { tipo: "texto", chico: true })) : null),
        clinico ? [
          h("td", { "data-etiqueta": "Rutina" }, p.tieneRutina ? (p.metaFuncional || "Con rutina") : h("span", { class: "tenue" }, "Sin rutina")),
          h("td", { "data-etiqueta": "Última sesión" }, p.ultimaSesion ? fechaHora(p.ultimaSesion) : h("span", { class: "tenue" }, "—")),
          h("td", { "data-etiqueta": "Estado" }, chipSemaforo(p.semaforo)),
          h("td", { "data-etiqueta": "Cumplimiento" }, p.cumplimientoPromedio != null ? barra(p.cumplimientoPromedio) : h("span", { class: "tenue" }, "—")),
        ] : null,
        h("td", { "data-etiqueta": "Próxima cita" }, p.proximaCita ? fechaCita(p.proximaCita) : h("span", { class: "tenue" }, "—"))))))));
  }

  pintar();
}

/** Modal para asignar o cambiar el fisioterapeuta de un paciente. */
export async function asignarFisio(paciente, alGuardar) {
  const fisioterapeutas = await api.get("/fisioterapeutas");
  const sel = selector(fisioterapeutas.map((f) => [f.id, f.nombre]), { name: "fisio", value: paciente.fisioterapeutaId ?? undefined });
  const guardado = await modalFormulario({
    titulo: paciente.fisioterapeutaId ? "Cambiar fisioterapeuta" : "Asignar fisioterapeuta",
    contenido: [
      h("p", { class: "nota" }, `${paciente.nombre}. El fisioterapeuta recibe un aviso y desde ese momento ve su ficha y su rutina.`),
      campo("Fisioterapeuta", sel),
    ],
    textoEnviar: "Asignar",
    alEnviar: (f) => api.patch(`/pacientes/${paciente.pacienteId}/fisioterapeuta`, { fisioterapeutaId: Number(valor(f, "fisio")) }),
  });
  if (guardado) {
    notificar("Fisioterapeuta asignado");
    await alGuardar(guardado);
  }
}

export function barra(porcentaje) {
  const p = Math.max(0, Math.min(100, porcentaje));
  return h("span", { class: "barra-cumplimiento", title: p + "%" },
    h("span", { class: "barra-fondo", "aria-hidden": "true" }, h("span", { class: "barra-relleno" + (p < 50 ? " bajo" : ""), style: `width:${p}%` })),
    h("span", null, p + "%"));
}

/** "2026-11-25T10:00" -> "25 nov 2026, 10:00". */
export function fechaCita(texto) {
  const [fecha, hora] = texto.split("T");
  return fechaCorta(fecha) + (hora ? ", " + hora.slice(0, 5) : "");
}
