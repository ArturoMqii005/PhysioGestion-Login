// Comunicación con la API de PhysioGestion y sesión del navegador.
// La web la sirve el mismo backend, así que la API está en el mismo dominio (/api/v1).

const API = "/api/v1";
const CLAVE_SESION = "physiogestion.sesion";

// sessionStorage: la sesión se borra al cerrar la pestaña (computadoras compartidas en recepción).
let sesionEnMemoria = null;

export function guardarSesion(sesion) {
  sesionEnMemoria = sesion;
  try { sessionStorage.setItem(CLAVE_SESION, JSON.stringify(sesion)); } catch (_) { /* modo privado */ }
}

export function leerSesion() {
  if (sesionEnMemoria) return sesionEnMemoria;
  try {
    const texto = sessionStorage.getItem(CLAVE_SESION);
    sesionEnMemoria = texto ? JSON.parse(texto) : null;
  } catch (_) {
    sesionEnMemoria = null;
  }
  return sesionEnMemoria;
}

export function borrarSesion() {
  sesionEnMemoria = null;
  try { sessionStorage.removeItem(CLAVE_SESION); } catch (_) { /* nada que borrar */ }
}

/** Error con el código HTTP y el mensaje que manda el servidor ({"mensaje": ..., "codigo": ...}). */
export class ErrorApi extends Error {
  constructor(estado, mensaje, codigo) {
    super(mensaje);
    this.estado = estado;
    this.codigo = codigo;
  }
}

/**
 * Llama a la API. Por defecto usa el token de la sesión; si el servidor responde 401
 * con ese token, la sesión venció: se borra y se regresa al login.
 */
export async function llamarApi(ruta, { metodo = "GET", cuerpo, token, sinSesion = false } = {}) {
  const tokenUsado = token || (sinSesion ? null : leerSesion()?.token);
  const encabezados = { Accept: "application/json" };
  if (cuerpo !== undefined) encabezados["Content-Type"] = "application/json";
  if (tokenUsado) encabezados.Authorization = "Bearer " + tokenUsado;

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
    if (respuesta.status === 401 && tokenUsado && !token && !sinSesion) {
      borrarSesion();
      location.replace("index.html?vencida=1");
    }
    const mensaje = (datos && datos.mensaje) || mensajePorEstado(respuesta.status);
    throw new ErrorApi(respuesta.status, mensaje, datos && datos.codigo);
  }
  return datos;
}

export const api = {
  get: (ruta) => llamarApi(ruta),
  post: (ruta, cuerpo = {}) => llamarApi(ruta, { metodo: "POST", cuerpo }),
  put: (ruta, cuerpo) => llamarApi(ruta, { metodo: "PUT", cuerpo }),
  patch: (ruta, cuerpo) => llamarApi(ruta, { metodo: "PATCH", cuerpo }),
};

function mensajePorEstado(estado) {
  if (estado === 401) return "Usuario o contraseña incorrectos.";
  if (estado === 403) return "No tienes permiso para hacer esto.";
  if (estado === 404) return "No se encontró la información.";
  if (estado === 409) return "Eso choca con información que ya existe.";
  if (estado === 422) return "Revisa los datos: hay algo que no es válido.";
  if (estado === 429) return "Demasiados intentos. Espera un minuto y vuelve a intentarlo.";
  if (estado >= 500) return "El servidor tuvo un problema. Inténtalo más tarde.";
  return "Algo salió mal. Inténtalo de nuevo.";
}

/**
 * El servidor no manda los campos que tienen su valor por defecto (por ejemplo, activo = true
 * o duracionMinutos = 50). Estas funciones los completan antes de usarlos.
 */
export const normalizar = {
  cita: (c) => ({ duracionMinutos: 50, nota: null, pacienteId: null, ...c }),
  usuario: (u) => ({ ...u, activo: u.activo !== false, correoVerificado: u.correoVerificado !== false }),
  ficha: (f) => ({
    ...f,
    rutina: f.rutina || null,
    citas: (f.citas || []).map(normalizar.cita),
    sesionesRecientes: f.sesionesRecientes || [],
    pruebasEscaleras: f.pruebasEscaleras || [],
  }),
};

export const NOMBRES_ROL = {
  ADMINISTRADOR: "Administrador",
  FISIOTERAPEUTA: "Fisioterapeuta",
  RECEPCIONISTA: "Recepción",
  PACIENTE: "Paciente",
};

export const PERSONAL = ["ADMINISTRADOR", "RECEPCIONISTA", "FISIOTERAPEUTA"];
