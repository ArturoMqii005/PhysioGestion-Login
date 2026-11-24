// Aplicación web del personal: menú por rol, enrutador por hash y contador de avisos.
import { api, borrarSesion, guardarSesion, leerSesion, llamarApi, NOMBRES_ROL, PERSONAL } from "./api.js";
import { cargando, h, mostrarError } from "./ui.js";

const ADMIN = "ADMINISTRADOR";
const RECEPCION = "RECEPCIONISTA";
const FISIO = "FISIOTERAPEUTA";

/** Secciones del menú y qué roles las ven. */
const MENU = [
  { ruta: "inicio", titulo: "Inicio", roles: PERSONAL },
  { ruta: "agenda", titulo: "Agenda", roles: PERSONAL },
  { ruta: "pacientes", titulo: "Pacientes", roles: PERSONAL },
  { ruta: "solicitudes", titulo: "Solicitudes", roles: [ADMIN, RECEPCION] },
  { ruta: "invitaciones", titulo: "Invitaciones", roles: [ADMIN, RECEPCION] },
  { ruta: "anuncios", titulo: "Anuncios", roles: [ADMIN, RECEPCION] },
  { ruta: "usuarios", titulo: "Usuarios", roles: [ADMIN] },
  { ruta: "avisos", titulo: "Avisos", roles: PERSONAL },
  { ruta: "cuenta", titulo: "Mi cuenta", roles: PERSONAL },
];

/** Rutas: patrón del hash, módulo de la vista y roles permitidos. */
const RUTAS = [
  { patron: /^inicio$/, menu: "inicio", cargar: () => import("./vistas/inicio.js"), roles: PERSONAL },
  { patron: /^agenda$/, menu: "agenda", cargar: () => import("./vistas/agenda.js"), roles: PERSONAL },
  { patron: /^pacientes$/, menu: "pacientes", cargar: () => import("./vistas/pacientes.js"), roles: PERSONAL },
  { patron: /^pacientes\/(\d+)$/, menu: "pacientes", cargar: () => import("./vistas/ficha.js"), roles: PERSONAL },
  { patron: /^pacientes\/(\d+)\/rutina$/, menu: "pacientes", cargar: () => import("./vistas/rutina.js"), roles: [FISIO] },
  { patron: /^solicitudes$/, menu: "solicitudes", cargar: () => import("./vistas/solicitudes.js"), roles: [ADMIN, RECEPCION] },
  { patron: /^invitaciones$/, menu: "invitaciones", cargar: () => import("./vistas/invitaciones.js"), roles: [ADMIN, RECEPCION] },
  { patron: /^anuncios$/, menu: "anuncios", cargar: () => import("./vistas/anuncios.js"), roles: [ADMIN, RECEPCION] },
  { patron: /^usuarios$/, menu: "usuarios", cargar: () => import("./vistas/usuarios.js"), roles: [ADMIN] },
  { patron: /^avisos$/, menu: "avisos", cargar: () => import("./vistas/avisos.js"), roles: PERSONAL },
  { patron: /^cuenta$/, menu: "cuenta", cargar: () => import("./vistas/cuenta.js"), roles: PERSONAL },
];

const sesion = leerSesion();
if (!sesion || !PERSONAL.includes(sesion.usuario?.rol)) {
  borrarSesion();
  location.replace("index.html");
} else {
  iniciar();
}

function iniciar() {
  const usuario = sesion.usuario;
  const enlaces = new Map();
  const contadorAvisos = h("span", { class: "contador", hidden: true, "aria-label": "avisos sin leer" });
  const contadorSolicitudes = h("span", { class: "contador", hidden: true, "aria-label": "solicitudes pendientes" });

  const nav = h("nav", { class: "menu", "aria-label": "Secciones" },
    MENU.filter((m) => m.roles.includes(usuario.rol)).map((m) => {
      const contador = { avisos: contadorAvisos, solicitudes: contadorSolicitudes }[m.ruta] || null;
      const enlace = h("a", { href: "#/" + m.ruta }, h("span", null, m.titulo), contador);
      enlaces.set(m.ruta, enlace);
      return enlace;
    }));

  const nombre = h("span", { class: "lateral-nombre" }, usuario.nombre);
  const rol = h("span", { class: "lateral-rol" }, NOMBRES_ROL[usuario.rol]);
  const vista = h("main", { id: "vista", class: "vista", tabindex: "-1" });

  document.body.replaceChildren(
    h("a", { class: "saltar", href: "#vista", onClick: (e) => { e.preventDefault(); vista.focus(); } }, "Saltar al contenido"),
    h("div", { class: "aplicacion" },
      h("aside", { class: "lateral" },
        h("div", { class: "lateral-marca" }, h("img", { class: "logo chico", src: "logo.svg", alt: "", width: "36", height: "36" }), h("span", { class: "nombre-app" }, "PhysioGestion")),
        nav,
        h("div", { class: "lateral-pie" },
          h("div", { class: "lateral-usuario" }, nombre, rol),
          h("button", { type: "button", class: "boton-borde chico", onClick: cerrarSesion }, "Cerrar sesión"))),
      vista));

  let turno = 0;
  const contexto = {
    usuario,
    get debeCambiarContrasena() { return Boolean(sesion.debeCambiarContrasena); },
    contrasenaCambiada() {
      sesion.debeCambiarContrasena = false;
      guardarSesion(sesion);
    },
    actualizarAvisos,
    ir: (hash) => { location.hash = hash; },
  };

  async function mostrarRuta() {
    const [rutaTexto, consultaTexto = ""] = location.hash.replace(/^#\/?/, "").split("?");
    let ruta = rutaTexto || "inicio";
    if (sesion.debeCambiarContrasena && ruta !== "cuenta") {
      location.replace("#/cuenta");
      return;
    }
    const def = RUTAS.find((r) => r.patron.test(ruta) && r.roles.includes(usuario.rol));
    if (!def) {
      location.replace("#/inicio");
      return;
    }
    const params = ruta.match(def.patron).slice(1);
    const consulta = new URLSearchParams(consultaTexto);

    for (const [clave, enlace] of enlaces) {
      if (clave === def.menu) enlace.setAttribute("aria-current", "page");
      else enlace.removeAttribute("aria-current");
    }

    const miTurno = ++turno;
    vista.replaceChildren(cargando());
    try {
      const modulo = await def.cargar();
      if (miTurno !== turno) return;
      await modulo.mostrar(vista, { ...contexto, params, consulta, sigueVigente: () => miTurno === turno });
      if (miTurno !== turno) return;
      const titulo = vista.querySelector("h1");
      document.title = (titulo ? titulo.textContent + " · " : "") + "PhysioGestion";
      window.scrollTo(0, 0);
      (titulo || vista).focus({ preventScroll: true });
    } catch (e) {
      if (miTurno === turno) mostrarError(vista, e, mostrarRuta);
    }
  }

  async function actualizarAvisos() {
    if (usuario.rol !== FISIO) {
      api.get("/solicitudes-reagenda?estado=PENDIENTE").then((lista) => {
        contadorSolicitudes.hidden = lista.length === 0;
        contadorSolicitudes.textContent = String(lista.length);
      }).catch(() => {});
    }
    try {
      const bandeja = await api.get("/avisos");
      contadorAvisos.hidden = bandeja.sinLeer === 0;
      contadorAvisos.textContent = bandeja.sinLeer > 99 ? "99+" : String(bandeja.sinLeer);
      return bandeja;
    } catch (_) {
      return null;
    }
  }

  async function cerrarSesion() {
    const token = sesion.token;
    borrarSesion();
    try { await llamarApi("/auth/logout", { metodo: "POST", token }); } catch (_) { /* la sesión ya se borró aquí */ }
    location.replace("index.html");
  }

  // Confirma que el token sigue vigente y trae los datos actuales de la cuenta.
  api.get("/usuarios/yo").then((cuenta) => {
    if (!PERSONAL.includes(cuenta.usuario.rol)) return cerrarSesion();
    sesion.usuario = cuenta.usuario;
    sesion.debeCambiarContrasena = cuenta.debeCambiarContrasena;
    guardarSesion(sesion);
    nombre.textContent = cuenta.usuario.nombre;
    if (cuenta.debeCambiarContrasena && !location.hash.startsWith("#/cuenta")) location.replace("#/cuenta");
  }).catch(() => { /* sin red: se sigue con los datos guardados */ });

  window.addEventListener("hashchange", mostrarRuta);
  mostrarRuta();
  actualizarAvisos();
  // Revisa avisos nuevos cada minuto mientras la pestaña está visible.
  setInterval(() => { if (document.visibilityState === "visible") actualizarAvisos(); }, 60_000);
}
