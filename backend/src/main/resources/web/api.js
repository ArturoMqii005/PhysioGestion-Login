// Comunicación con la API de PhysioGestion y manejo de la sesión del navegador.
// La página la sirve el mismo backend, así que la API está en el mismo dominio (/api/v1).

const API = "/api/v1";
const CLAVE_SESION = "physiogestion.sesion";

// La sesión vive en sessionStorage: se borra al cerrar la pestaña,
// lo más seguro para computadoras compartidas en recepción.
let sesionEnMemoria = null;

function guardarSesion(sesion) {
  sesionEnMemoria = sesion;
  try { sessionStorage.setItem(CLAVE_SESION, JSON.stringify(sesion)); } catch (_) { /* modo privado */ }
}

function leerSesion() {
  if (sesionEnMemoria) return sesionEnMemoria;
  try {
    const texto = sessionStorage.getItem(CLAVE_SESION);
    sesionEnMemoria = texto ? JSON.parse(texto) : null;
  } catch (_) {
    sesionEnMemoria = null;
  }
  return sesionEnMemoria;
}

function borrarSesion() {
  sesionEnMemoria = null;
  try { sessionStorage.removeItem(CLAVE_SESION); } catch (_) { /* nada que borrar */ }
}

/** Error con el código HTTP y el mensaje que manda el servidor ({"mensaje": ...}). */
class ErrorApi extends Error {
  constructor(estado, mensaje) {
    super(mensaje);
    this.estado = estado;
  }
}

/** Llama a la API. Lanza ErrorApi con un mensaje listo para mostrar. */
async function llamarApi(ruta, { metodo = "GET", cuerpo, token } = {}) {
  const encabezados = { Accept: "application/json" };
  if (cuerpo !== undefined) encabezados["Content-Type"] = "application/json";
  if (token) encabezados.Authorization = "Bearer " + token;

  let respuesta;
  try {
    respuesta = await fetch(API + ruta, {
      method: metodo,
      headers: encabezados,
      body: cuerpo === undefined ? undefined : JSON.stringify(cuerpo),
    });
  } catch (_) {
    throw new ErrorApi(0, "No se pudo conectar con el servidor. Revisa tu conexión a internet.");
  }

  if (respuesta.status === 204) return null;
  let datos = null;
  try { datos = await respuesta.json(); } catch (_) { /* respuesta sin JSON */ }

  if (!respuesta.ok) {
    const mensaje = (datos && datos.mensaje) || mensajePorEstado(respuesta.status);
    throw new ErrorApi(respuesta.status, mensaje);
  }
  return datos;
}

function mensajePorEstado(estado) {
  if (estado === 401) return "Usuario o contraseña incorrectos.";
  if (estado === 403) return "No tienes permiso para entrar.";
  if (estado === 429) return "Demasiados intentos. Espera un minuto y vuelve a intentarlo.";
  if (estado >= 500) return "El servidor tuvo un problema. Inténtalo más tarde.";
  return "Algo salió mal. Inténtalo de nuevo.";
}

const NOMBRES_ROL = {
  ADMINISTRADOR: "Administrador",
  FISIOTERAPEUTA: "Fisioterapeuta",
  RECEPCIONISTA: "Recepción",
  PACIENTE: "Paciente",
};
