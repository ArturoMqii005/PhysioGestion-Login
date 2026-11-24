// Editor de rutina (solo fisioterapeuta): meta funcional, límite de dolor y de 1 a 10 ejercicios del catálogo.
import { api } from "../api.js";
import { boton, campo, encabezado, entrada, h, notificar, vacio } from "../ui.js";

const MAX_EJERCICIOS = 10;

export async function mostrar(vista, ctx) {
  const id = Number(ctx.params[0]);
  const [ficha, catalogo] = await Promise.all([api.get("/pacientes/" + id), api.get("/ejercicios")]);
  if (!ctx.sigueVigente()) return;

  const rutina = ficha.rutina;
  const porId = new Map(catalogo.map((e) => [e.id, e]));
  // Cada fila: { idEjercicio, series, repeticiones, descansoSegundos, anguloObjetivo }
  let filas = rutina
    ? rutina.ejercicios.map((e) => ({ idEjercicio: e.id, series: e.series, repeticiones: e.repeticiones, descansoSegundos: e.descansoSegundos, anguloObjetivo: e.anguloObjetivo }))
    : [];

  const meta = entrada({ name: "meta", type: "text", maxlength: "120", required: true, value: rutina?.metaFuncional || "",
    placeholder: "Por ejemplo: subir escaleras sin dolor" });
  const umbral = entrada({ name: "umbral", type: "range", min: "1", max: "10", step: "1", value: rutina?.umbralDolor ?? 6, class: "deslizador" });
  const umbralTexto = h("output", { class: "valor-deslizador" }, umbral.value);
  umbral.addEventListener("input", () => { umbralTexto.textContent = umbral.value; });

  const cuerpo = h("tbody");
  const error = h("p", { class: "error", role: "alert" });
  const agregarSel = h("select", { class: "control", "aria-label": "Ejercicio para agregar" });
  const botonAgregar = boton("Agregar ejercicio", agregar);
  const guardar = h("button", { type: "submit", class: "boton" }, "Guardar rutina");

  const formulario = h("form", { class: "editor-rutina", novalidate: true },
    h("section", { class: "tarjeta" },
      h("h2", null, "Objetivo"),
      campo("Meta funcional", meta, "Lo que el paciente quiere lograr. De 5 a 120 caracteres."),
      h("div", { class: "campo" },
        h("label", { for: "umbralDolor" }, "Límite de dolor"),
        h("div", { class: "fila-deslizador" }, Object.assign(umbral, { id: "umbralDolor" }), umbralTexto),
        h("p", { class: "ayuda" }, "Si el paciente termina una sesión con este dolor o más, recibes un aviso urgente."))),
    h("section", { class: "tarjeta" },
      h("div", { class: "seccion-titulo" }, h("h2", null, "Ejercicios"), h("span", { class: "tenue", id: "cuentaEjercicios" })),
      h("div", { class: "tabla-scroll" }, h("table", { class: "tabla editable" },
        h("thead", null, h("tr", null, ["Ejercicio", "Series", "Repeticiones", "Descanso (s)", "Ángulo (°)", ""].map((c) => h("th", { scope: "col" }, c)))),
        cuerpo)),
      h("div", { class: "agregar-ejercicio" }, agregarSel, botonAgregar)),
    error,
    h("div", { class: "acciones-formulario" },
      h("a", { class: "boton-borde enlace-boton", href: "#/pacientes/" + id }, "Cancelar"),
      guardar));

  formulario.addEventListener("submit", async (e) => {
    e.preventDefault();
    error.textContent = "";
    const solicitud = {
      metaFuncional: meta.value.trim(),
      umbralDolor: Number(umbral.value),
      ejercicios: filas.map((f) => ({
        idEjercicio: f.idEjercicio,
        series: Number(f.series),
        repeticiones: Number(f.repeticiones),
        descansoSegundos: Number(f.descansoSegundos),
        anguloObjetivo: f.anguloObjetivo === "" || f.anguloObjetivo == null ? null : Number(f.anguloObjetivo),
      })),
    };
    const problema = validar(solicitud);
    if (problema) { error.textContent = problema; return; }
    guardar.disabled = true;
    guardar.textContent = "Guardando…";
    try {
      await api.put(`/pacientes/${id}/rutina`, solicitud);
      notificar("Rutina guardada. El paciente recibe un aviso en la app.");
      ctx.ir("#/pacientes/" + id);
    } catch (err) {
      error.textContent = err.message;
    } finally {
      guardar.disabled = false;
      guardar.textContent = "Guardar rutina";
    }
  });

  function pintar() {
    document.getElementById("cuentaEjercicios")?.replaceChildren(`${filas.length} de ${MAX_EJERCICIOS}`);
    if (!filas.length) {
      cuerpo.replaceChildren(h("tr", null, h("td", { colspan: "6" }, vacio("Sin ejercicios", "Agrega al menos uno del catálogo."))));
    } else {
      cuerpo.replaceChildren(...filas.map((f, i) => filaEjercicio(f, i)));
    }
    const usados = new Set(filas.map((f) => f.idEjercicio));
    const disponibles = catalogo.filter((e) => !usados.has(e.id));
    agregarSel.replaceChildren(...disponibles.map((e) => h("option", { value: String(e.id) }, `${e.nombre} · ${e.zonaCorporal}`)));
    const lleno = filas.length >= MAX_EJERCICIOS || !disponibles.length;
    agregarSel.disabled = lleno;
    botonAgregar.disabled = lleno;
  }

  function filaEjercicio(f, i) {
    const base = porId.get(f.idEjercicio);
    const numero = (prop, min, max, etiqueta) => h("input", {
      class: "control numero", type: "number", min: String(min), max: String(max), value: f[prop] ?? "", "aria-label": `${etiqueta} de ${base?.nombre || "ejercicio"}`,
      placeholder: prop === "anguloObjetivo" ? "—" : null,
      onInput: (e) => { f[prop] = e.target.value; },
    });
    return h("tr", null,
      h("td", { "data-etiqueta": "Ejercicio" }, h("strong", null, base?.nombre || "Ejercicio " + f.idEjercicio),
        h("span", { class: "tenue bloque" }, base ? base.zonaCorporal + (base.medibleConSensores ? " · se mide con sensores" : "") : "")),
      h("td", { "data-etiqueta": "Series" }, numero("series", 1, 10, "Series")),
      h("td", { "data-etiqueta": "Repeticiones" }, numero("repeticiones", 1, 50, "Repeticiones")),
      h("td", { "data-etiqueta": "Descanso (s)" }, numero("descansoSegundos", 0, 600, "Descanso")),
      h("td", { "data-etiqueta": "Ángulo (°)" }, numero("anguloObjetivo", 5, 180, "Ángulo objetivo")),
      h("td", null, h("div", { class: "acciones-fila" },
        boton("↑", () => mover(i, -1), { chico: true, titulo: "Subir" }),
        boton("↓", () => mover(i, 1), { chico: true, titulo: "Bajar" }),
        boton("Quitar", () => { filas.splice(i, 1); pintar(); }, { chico: true, tipo: "peligro" }))));
  }

  function mover(i, delta) {
    const j = i + delta;
    if (j < 0 || j >= filas.length) return;
    [filas[i], filas[j]] = [filas[j], filas[i]];
    pintar();
  }

  function agregar() {
    const base = porId.get(Number(agregarSel.value));
    if (!base) return;
    filas.push({ idEjercicio: base.id, series: base.series, repeticiones: base.repeticiones, descansoSegundos: base.descansoSegundos, anguloObjetivo: base.anguloObjetivo });
    pintar();
  }

  vista.replaceChildren(
    encabezado(rutina ? "Editar rutina" : "Nueva rutina", ficha.resumen.nombre),
    h("p", null, h("a", { href: "#/pacientes/" + id, class: "enlace" }, "‹ Volver a la ficha")),
    formulario);
  pintar();
}

function validar(s) {
  if (s.metaFuncional.length < 5 || s.metaFuncional.length > 120) return "La meta funcional debe tener de 5 a 120 caracteres.";
  if (!s.ejercicios.length) return "Agrega al menos un ejercicio.";
  for (const e of s.ejercicios) {
    if (!(e.series >= 1 && e.series <= 10) || !(e.repeticiones >= 1 && e.repeticiones <= 50) || !(e.descansoSegundos >= 0 && e.descansoSegundos <= 600)) {
      return "Cada ejercicio usa de 1 a 10 series, de 1 a 50 repeticiones y hasta 600 s de descanso.";
    }
    if (e.anguloObjetivo != null && !(e.anguloObjetivo >= 5 && e.anguloObjetivo <= 180)) return "El ángulo objetivo va de 5° a 180° (o déjalo vacío).";
  }
  return null;
}
