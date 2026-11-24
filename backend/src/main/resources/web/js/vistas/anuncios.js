// Anuncios de la clínica: llegan como notificación push a los teléfonos (tema de Firebase)
// y quedan en la bandeja de avisos de cada persona. Recepción y administración.
import { api } from "../api.js";
import { boton, campo, encabezado, entrada, h, haceCuanto, modalFormulario, notificar, selector, vacio, valor } from "../ui.js";

const DESTINOS = [["PACIENTES", "Pacientes"], ["PERSONAL", "Personal de la clínica"], ["TODOS", "Todos"]];
const NOMBRE_DESTINO = Object.fromEntries(DESTINOS);
const MAXIMO_POR_DIA = 3;

export async function mostrar(vista, ctx) {
  const contenido = h("div");
  const publicar = boton("Publicar anuncio", () => formulario(), { tipo: "primario" });
  vista.replaceChildren(
    encabezado("Anuncios", `Llegan como notificación a los teléfonos y quedan en Avisos. Máximo ${MAXIMO_POR_DIA} en 24 horas.`, publicar),
    contenido);

  let lista = [];
  async function cargar() {
    lista = await api.get("/anuncios");
    if (!ctx.sigueVigente()) return;
    const recientes = lista.filter((a) => a.creado > Date.now() - 86_400_000).length;
    publicar.disabled = recientes >= MAXIMO_POR_DIA;
    publicar.title = publicar.disabled ? "Ya se publicaron 3 anuncios en las últimas 24 horas" : "";
    if (!lista.length) {
      contenido.replaceChildren(vacio("Todavía no hay anuncios", "Por ejemplo: «Cerramos el lunes festivo» o «Junta del personal a las 8:00»."));
      return;
    }
    contenido.replaceChildren(h("ul", { class: "lista-avisos" }, lista.map((a) =>
      h("li", null, h("article", { class: "tarjeta aviso" },
        h("div", { class: "aviso-cabeza" },
          h("span", { class: "chip chip-confirmada" }, NOMBRE_DESTINO[a.destinatarios] || a.destinatarios),
          h("span", { class: "tenue" }, `${haceCuanto(a.creado)} · ${a.creadoPor}`)),
        h("h2", { class: "aviso-titulo" }, a.titulo),
        h("p", { class: "aviso-mensaje" }, a.texto),
        h("p", { class: "tenue" }, (a.personas || 0) === 1 ? "Le llegó a 1 persona" : `Les llegó a ${a.personas || 0} personas`))))));
  }

  async function formulario() {
    const titulo = entrada({ name: "titulo", type: "text", maxlength: "60", required: true, placeholder: "Cerramos el lunes festivo" });
    const texto = h("textarea", { class: "control", name: "texto", rows: "4", maxlength: "300", required: true,
      placeholder: "El lunes 16 de noviembre no hay consulta. Tus citas de ese día se moverán; recepción te llamará." });
    const cuenta = h("p", { class: "ayuda" }, "0 / 300");
    texto.addEventListener("input", () => { cuenta.textContent = `${texto.value.length} / 300`; });
    const destino = selector(DESTINOS, { name: "destino", value: "PACIENTES" });
    const ok = await modalFormulario({
      titulo: "Publicar anuncio",
      ancho: true,
      contenido: [
        campo("Para quién", destino),
        campo("Título", titulo),
        campo("Mensaje", texto),
        cuenta,
        h("p", { class: "nota" }, "Se manda en ese momento como notificación push. No incluyas datos de pacientes."),
      ],
      textoEnviar: "Publicar",
      alEnviar: (f) => {
        if (valor(f, "titulo").length < 3) throw new Error("El título debe tener al menos 3 caracteres.");
        if (valor(f, "texto").length < 5) throw new Error("Escribe el mensaje del anuncio.");
        return api.post("/anuncios", { titulo: valor(f, "titulo"), texto: valor(f, "texto"), destinatarios: valor(f, "destino") });
      },
    });
    if (ok) { notificar("Anuncio publicado"); ctx.actualizarAvisos(); cargar(); }
  }

  await cargar();
}
