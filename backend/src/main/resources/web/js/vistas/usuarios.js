// Usuarios (solo administración): alta del personal, edición de datos y rol, activar o desactivar cuentas.
import { api, NOMBRES_ROL, normalizar } from "../api.js";
import { boton, campo, confirmar, copiar, encabezado, entrada, h, modalFormulario, modalInfo, notificar, selector, vacio, valor } from "../ui.js";

const ROLES_PERSONAL = [["FISIOTERAPEUTA", "Fisioterapeuta"], ["RECEPCIONISTA", "Recepción"], ["ADMINISTRADOR", "Administrador"]];

export async function mostrar(vista, ctx) {
  const traer = async () => (await api.get("/usuarios")).map(normalizar.usuario);
  let usuarios = await traer();
  if (!ctx.sigueVigente()) return;
  let filtroRol = "";
  let texto = "";
  let verInactivos = false;
  const contenido = h("div");

  const recargar = async () => { usuarios = await traer(); pintar(); };

  vista.replaceChildren(
    encabezado("Usuarios", "Cuentas del personal y de los pacientes.",
      boton("Dar de alta personal", async () => { if (await altaPersonal()) recargar(); }, { tipo: "primario" })),
    h("div", { class: "barra-herramientas" },
      entrada({ type: "search", placeholder: "Buscar por nombre, usuario o correo", "aria-label": "Buscar usuario",
        onInput: (e) => { texto = e.target.value.trim().toLowerCase(); pintar(); } }),
      selector([["", "Todos los roles"], ...ROLES_PERSONAL, ["PACIENTE", "Pacientes"]], { "aria-label": "Filtrar por rol",
        onChange: (e) => { filtroRol = e.target.value; pintar(); } }),
      h("label", { class: "casilla" }, h("input", { type: "checkbox", onChange: (e) => { verInactivos = e.target.checked; pintar(); } }), "Mostrar desactivadas")),
    contenido);

  function pintar() {
    const lista = usuarios
      .filter((u) => !filtroRol || u.rol === filtroRol)
      .filter((u) => verInactivos || u.activo)
      .filter((u) => !texto || [u.nombre, u.usuario, u.correo || ""].some((t) => t.toLowerCase().includes(texto)))
      .sort((a, b) => a.nombre.localeCompare(b.nombre));
    if (!lista.length) { contenido.replaceChildren(vacio("Sin resultados", "Prueba con otro filtro.")); return; }

    contenido.replaceChildren(h("div", { class: "tabla-scroll" }, h("table", { class: "tabla" },
      h("thead", null, h("tr", null, ["Nombre", "Usuario", "Correo", "Rol", "Estado", ""].map((c) => h("th", { scope: "col" }, c)))),
      h("tbody", null, lista.map((u) => {
        const propio = u.id === ctx.usuario.id;
        return h("tr", { class: u.activo ? "" : "inactiva" },
          h("td", { "data-etiqueta": "Nombre" }, h("strong", null, u.nombre), propio ? h("span", { class: "tenue" }, " (tú)") : null),
          h("td", { "data-etiqueta": "Usuario" }, u.usuario),
          h("td", { "data-etiqueta": "Correo" }, u.correo || "—",
            u.rol === "PACIENTE" && !u.correoVerificado ? h("span", { class: "tenue bloque" }, "Correo sin verificar") : null),
          h("td", { "data-etiqueta": "Rol" }, h("span", { class: "chip chip-confirmada" }, NOMBRES_ROL[u.rol])),
          h("td", { "data-etiqueta": "Estado" }, u.activo ? h("span", { class: "chip chip-exito" }, "Activa") : h("span", { class: "chip chip-apagado" }, "Desactivada")),
          h("td", null, h("div", { class: "acciones-fila" },
            boton("Editar", async () => { if (await editar(u)) recargar(); }, { chico: true }),
            propio ? null : boton(u.activo ? "Desactivar" : "Activar", () => cambiarActivo(u), { chico: true, tipo: u.activo ? "peligro" : "borde" }))));
      })))));
  }

  async function cambiarActivo(u) {
    const ok = await confirmar({
      titulo: u.activo ? "¿Desactivar la cuenta?" : "¿Activar la cuenta?",
      texto: u.activo
        ? `${u.nombre} ya no podrá iniciar sesión. Su historial se conserva y puedes volver a activarla.`
        : `${u.nombre} podrá volver a iniciar sesión con su contraseña.`,
      textoSi: u.activo ? "Desactivar" : "Activar",
      peligro: u.activo,
      alConfirmar: () => api.patch("/usuarios/" + u.id, { activo: !u.activo }),
    });
    if (ok) { notificar(u.activo ? "Cuenta desactivada" : "Cuenta activada"); recargar(); }
  }

  pintar();
}

async function altaPersonal() {
  const creado = await modalFormulario({
    titulo: "Dar de alta personal",
    contenido: [
      h("p", { class: "nota" }, "Se genera una contraseña temporal que la persona cambia al iniciar sesión por primera vez. Los pacientes no se dan de alta aquí: usan un código de invitación."),
      campo("Nombre completo", entrada({ name: "nombre", type: "text", maxlength: "80", required: true, autocomplete: "off" })),
      h("div", { class: "rejilla-2" },
        campo("Usuario", entrada({ name: "usuario", type: "text", maxlength: "30", required: true, autocomplete: "off", autocapitalize: "none", spellcheck: "false" })),
        campo("Rol", selector(ROLES_PERSONAL, { name: "rol" }))),
      campo("Correo", entrada({ name: "correo", type: "email", maxlength: "120", required: true, autocomplete: "off" })),
    ],
    textoEnviar: "Dar de alta",
    alEnviar: (f) => {
      if (valor(f, "nombre").length < 5) throw new Error("Escribe el nombre completo.");
      if (!valor(f, "usuario")) throw new Error("Escribe el usuario.");
      if (!valor(f, "correo").includes("@")) throw new Error("Escribe un correo válido.");
      return api.post("/usuarios", { nombre: valor(f, "nombre"), usuario: valor(f, "usuario"), rol: valor(f, "rol"), correo: valor(f, "correo") });
    },
  });
  if (!creado) return null;
  await modalInfo("Cuenta creada", [
    h("p", { class: "nota" }, `Entrégale a ${creado.usuario.nombre} su usuario y esta contraseña temporal. Se le pedirá cambiarla al entrar.`),
    h("dl", { class: "datos" },
      h("div", null, h("dt", null, "Usuario"), h("dd", null, creado.usuario.usuario)),
      h("div", null, h("dt", null, "Rol"), h("dd", null, NOMBRES_ROL[creado.usuario.rol]))),
    h("div", { class: "codigo-grande" }, h("code", null, creado.contrasenaTemporal), boton("Copiar", () => copiar(creado.contrasenaTemporal))),
    h("p", { class: "ayuda" }, "Esta contraseña no se vuelve a mostrar."),
  ]);
  return creado;
}

async function editar(u) {
  const esPaciente = u.rol === "PACIENTE";
  const guardado = await modalFormulario({
    titulo: "Editar cuenta",
    contenido: [
      h("p", { class: "nota" }, `Usuario: ${u.usuario}`),
      campo("Nombre completo", entrada({ name: "nombre", type: "text", maxlength: "80", required: true, value: u.nombre })),
      campo("Correo", entrada({ name: "correo", type: "email", maxlength: "120", value: u.correo || "" })),
      esPaciente ? null : campo("Rol", selector(ROLES_PERSONAL, { name: "rol", value: u.rol }),
        u.rol === "FISIOTERAPEUTA" ? "Si tiene pacientes o citas pendientes, primero hay que reasignarlos." : null),
    ],
    alEnviar: (f) => {
      const cambios = {};
      if (valor(f, "nombre") !== u.nombre) cambios.nombre = valor(f, "nombre");
      if (valor(f, "correo") && valor(f, "correo") !== (u.correo || "")) cambios.correo = valor(f, "correo");
      if (!esPaciente && valor(f, "rol") !== u.rol) cambios.rol = valor(f, "rol");
      if (!Object.keys(cambios).length) return true;
      return api.patch("/usuarios/" + u.id, cambios);
    },
  });
  if (guardado) notificar("Cuenta actualizada");
  return guardado;
}
