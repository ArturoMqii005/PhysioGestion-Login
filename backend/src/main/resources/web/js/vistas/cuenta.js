// Mi cuenta: datos del usuario y cambio de contraseña (obligatorio si la cuenta tiene contraseña temporal).
import { api, NOMBRES_ROL } from "../api.js";
import { campo, encabezado, entrada, h, notificar } from "../ui.js";

export async function mostrar(vista, ctx) {
  const cuenta = await api.get("/usuarios/yo");
  if (!ctx.sigueVigente()) return;
  const u = cuenta.usuario;

  const actual = entrada({ name: "actual", type: "password", autocomplete: "current-password", required: true });
  const nueva = entrada({ name: "nueva", type: "password", autocomplete: "new-password", required: true, minlength: "8", maxlength: "64" });
  const repetir = entrada({ name: "repetir", type: "password", autocomplete: "new-password", required: true });
  const error = h("p", { class: "error", role: "alert" });
  const guardar = h("button", { type: "submit", class: "boton" }, "Cambiar contraseña");

  const formulario = h("form", { class: "tarjeta formulario-angosto", novalidate: true },
    h("h2", null, "Cambiar contraseña"),
    campo(cuenta.debeCambiarContrasena ? "Contraseña temporal" : "Contraseña actual", actual),
    campo("Contraseña nueva", nueva, "De 8 a 64 caracteres, con letras y números, y sin tu usuario."),
    campo("Repite la contraseña nueva", repetir),
    error,
    guardar);

  formulario.addEventListener("submit", async (e) => {
    e.preventDefault();
    error.textContent = "";
    const problema = revisar(nueva.value, repetir.value, u.usuario);
    if (!actual.value) { error.textContent = "Escribe tu contraseña actual."; return; }
    if (problema) { error.textContent = problema; return; }
    guardar.disabled = true;
    guardar.textContent = "Guardando…";
    try {
      const r = await api.patch("/usuarios/yo/contrasena", { contrasenaActual: actual.value, contrasenaNueva: nueva.value });
      formulario.reset();
      notificar(r?.mensaje || "Contraseña actualizada");
      if (ctx.debeCambiarContrasena) {
        ctx.contrasenaCambiada();
        ctx.ir("#/inicio");
      }
    } catch (err) {
      error.textContent = err.message;
    } finally {
      guardar.disabled = false;
      guardar.textContent = "Cambiar contraseña";
    }
  });

  vista.replaceChildren(
    encabezado("Mi cuenta", NOMBRES_ROL[u.rol]),
    cuenta.debeCambiarContrasena
      ? h("p", { class: "aviso-temporal", role: "alert" }, "Tu cuenta tiene una contraseña temporal. Cámbiala para poder usar el resto de la aplicación.")
      : null,
    h("div", { class: "dos-columnas" },
      h("section", { class: "tarjeta" },
        h("h2", null, "Tus datos"),
        h("dl", { class: "datos" },
          h("div", null, h("dt", null, "Nombre"), h("dd", null, u.nombre)),
          h("div", null, h("dt", null, "Usuario"), h("dd", null, u.usuario)),
          h("div", null, h("dt", null, "Correo"), h("dd", null, u.correo || "—")),
          h("div", null, h("dt", null, "Rol"), h("dd", null, NOMBRES_ROL[u.rol]))),
        h("p", { class: "ayuda" }, "Para corregir tu nombre o correo, pídeselo a la administración.")),
      formulario));
}

/** Las mismas reglas que el servidor (Contrasenas.problema), para avisar antes de enviar. */
function revisar(nueva, repetir, usuario) {
  if (nueva.length < 8) return "La contraseña debe tener al menos 8 caracteres.";
  if (nueva.length > 64) return "La contraseña puede tener máximo 64 caracteres.";
  if (!/\p{L}/u.test(nueva) || !/\d/.test(nueva)) return "La contraseña debe combinar letras y números.";
  if (nueva.trim() !== nueva) return "La contraseña no puede empezar ni terminar con espacios.";
  if (usuario.length >= 4 && nueva.toLowerCase().includes(usuario.toLowerCase())) return "La contraseña no debe contener tu usuario.";
  if (nueva !== repetir) return "Las contraseñas nuevas no coinciden.";
  return null;
}
