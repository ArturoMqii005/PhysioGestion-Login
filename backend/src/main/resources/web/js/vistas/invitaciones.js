// Invitaciones: dar de alta a un paciente nuevo y entregarle su código para crear la cuenta en la app.
import { api } from "../api.js";
import { boton, campo, copiar, encabezado, entrada, fechaCorta, h, modalFormulario, modalInfo, vacio, valor } from "../ui.js";

export async function mostrar(vista, ctx) {
  let invitaciones = await api.get("/invitaciones");
  if (!ctx.sigueVigente()) return;
  let soloVigentes = true;
  const contenido = h("div");

  const casilla = h("input", { type: "checkbox", id: "soloVigentes", checked: true,
    onChange: (e) => { soloVigentes = e.target.checked; pintar(); } });

  vista.replaceChildren(
    encabezado("Invitaciones",
      "Cada paciente nuevo recibe un código para crear su cuenta en la app móvil.",
      boton("Nuevo paciente", async () => {
        const inv = await nuevaInvitacion();
        if (inv) { invitaciones = await api.get("/invitaciones"); pintar(); }
      }, { tipo: "primario" })),
    h("div", { class: "barra-herramientas" },
      h("label", { class: "casilla", for: "soloVigentes" }, casilla, "Solo vigentes (sin usar y sin vencer)")),
    contenido);

  function pintar() {
    const ahora = new Date();
    const lista = invitaciones
      .map((i) => ({ ...i, vencida: new Date(i.venceEn) < ahora }))
      .filter((i) => !soloVigentes || (!i.usada && !i.vencida))
      .sort((a, b) => b.creadaEn.localeCompare(a.creadaEn));

    if (!lista.length) {
      contenido.replaceChildren(vacio(soloVigentes ? "No hay invitaciones vigentes" : "Todavía no hay invitaciones",
        "Usa «Nuevo paciente» para generar un código."));
      return;
    }
    contenido.replaceChildren(h("div", { class: "tabla-scroll" }, h("table", { class: "tabla" },
      h("thead", null, h("tr", null, ["Código", "Paciente", "Creada por", "Creada", "Vence", "Estado"].map((c) => h("th", { scope: "col" }, c)))),
      h("tbody", null, lista.map((i) => h("tr", null,
        h("td", { "data-etiqueta": "Código" },
          h("code", { class: "codigo" }, i.codigo),
          !i.usada && !i.vencida ? boton("Copiar", () => copiar(i.codigo), { tipo: "texto", chico: true }) : null),
        h("td", { "data-etiqueta": "Paciente" }, h("a", { class: "enlace", href: "#/pacientes/" + i.pacienteId }, i.nombrePaciente)),
        h("td", { "data-etiqueta": "Creada por" }, i.creadaPor),
        h("td", { "data-etiqueta": "Creada" }, fechaCorta(i.creadaEn)),
        h("td", { "data-etiqueta": "Vence" }, fechaCorta(i.venceEn)),
        h("td", { "data-etiqueta": "Estado" }, i.usada
          ? h("span", { class: "chip chip-exito" }, "Usada")
          : i.vencida ? h("span", { class: "chip chip-apagado" }, "Vencida") : h("span", { class: "chip chip-confirmada" }, "Vigente"))))))));
  }

  pintar();
}

/** Pide el nombre del paciente, crea la invitación y muestra el código para entregárselo. */
export async function nuevaInvitacion() {
  const nombre = entrada({ name: "nombre", type: "text", maxlength: "80", required: true, autocomplete: "off", placeholder: "Nombre y apellidos" });
  const invitacion = await modalFormulario({
    titulo: "Nuevo paciente",
    contenido: [
      h("p", { class: "nota" }, "Se crea su expediente y un código de invitación. Con ese código el paciente crea su cuenta en la app móvil."),
      campo("Nombre completo del paciente", nombre),
    ],
    textoEnviar: "Generar código",
    alEnviar: (f) => {
      const texto = valor(f, "nombre");
      if (texto.length < 5 || !texto.includes(" ")) throw new Error("Escribe el nombre completo (nombre y apellidos).");
      return api.post("/invitaciones", { nombrePaciente: texto });
    },
  });
  if (!invitacion) return null;

  await modalInfo("Código de invitación", [
    h("p", { class: "nota" }, `Entrégale este código a ${invitacion.nombrePaciente}. Vence el ${fechaCorta(invitacion.venceEn)}.`),
    h("div", { class: "codigo-grande" },
      h("code", null, invitacion.codigo),
      boton("Copiar", () => copiar(invitacion.codigo))),
    h("ol", { class: "pasos" },
      h("li", null, "El paciente instala la app de PhysioGestion."),
      h("li", null, "En el inicio elige «Crear mi cuenta»."),
      h("li", null, "Escribe este código, su correo y una contraseña.")),
  ]);
  return invitacion;
}
