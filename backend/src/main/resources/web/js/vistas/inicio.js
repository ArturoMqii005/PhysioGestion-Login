// Inicio: resumen del día según el rol.
import { api, normalizar } from "../api.js";
import { chipCita, chipSemaforo, encabezado, fechaLarga, h, hoyISO, horaFin, indicador, saludo, vacio } from "../ui.js";

export async function mostrar(vista, ctx) {
  const { usuario } = ctx;
  const hoy = hoyISO();
  const esAdmin = usuario.rol === "ADMINISTRADOR";
  const esRecepcion = usuario.rol === "RECEPCIONISTA";
  const esFisio = usuario.rol === "FISIOTERAPEUTA";

  const [citas, pacientes, bandeja, invitaciones, usuarios] = await Promise.all([
    api.get("/citas?fecha=" + hoy),
    api.get("/pacientes"),
    ctx.actualizarAvisos(),
    esFisio ? null : api.get("/invitaciones"),
    esAdmin ? api.get("/usuarios") : null,
  ]);
  if (!ctx.sigueVigente()) return;
  citas.forEach((c, i) => { citas[i] = normalizar.cita(c); });

  const abiertas = citas.filter((c) => c.estado === "PENDIENTE" || c.estado === "CONFIRMADA");
  const porConfirmar = citas.filter((c) => c.estado === "PENDIENTE");
  const sinFisio = pacientes.filter((p) => !p.fisioterapeutaId);
  const vigentes = (invitaciones || []).filter((i) => !i.usada && new Date(i.venceEn) > new Date());
  const enRojo = pacientes.filter((p) => p.semaforo === "ROJO");
  const sinLeer = bandeja ? bandeja.sinLeer : 0;

  let indicadores;
  if (esFisio) {
    indicadores = [
      indicador(abiertas.length, "citas por atender hoy"),
      indicador(pacientes.length, "pacientes en seguimiento"),
      indicador(enRojo.length, "con dolor alto en su última sesión", enRojo.length ? "tono-alerta" : ""),
      indicador(sinLeer, "avisos sin leer", sinLeer ? "tono-violeta" : ""),
    ];
  } else if (esRecepcion) {
    indicadores = [
      indicador(abiertas.length, "citas por atender hoy"),
      indicador(porConfirmar.length, "citas por confirmar hoy", porConfirmar.length ? "tono-pendiente" : ""),
      indicador(sinFisio.length, "pacientes sin fisioterapeuta", sinFisio.length ? "tono-pendiente" : ""),
      indicador(vigentes.length, "invitaciones vigentes"),
    ];
  } else {
    const personal = (usuarios || []).map(normalizar.usuario).filter((u) => u.rol !== "PACIENTE" && u.activo);
    indicadores = [
      indicador(personal.length, "cuentas de personal activas"),
      indicador(pacientes.length, "pacientes registrados"),
      indicador(abiertas.length, "citas por atender hoy"),
      indicador(vigentes.length, "invitaciones vigentes"),
    ];
  }

  const agendaHoy = h("section", { class: "tarjeta" },
    h("div", { class: "seccion-titulo" }, h("h2", null, "Agenda de hoy"), h("a", { href: "#/agenda", class: "enlace" }, "Ver agenda")),
    abiertas.length
      ? h("ul", { class: "lista" }, abiertas.slice(0, 6).map((c) => h("li", { class: "fila" },
          h("span", { class: "hora" }, c.hora, h("span", { class: "tenue" }, " – " + horaFin(c.hora, c.duracionMinutos))),
          h("span", { class: "crece" },
            h("strong", null, c.paciente),
            h("span", { class: "tenue bloque" }, esFisio ? c.motivo : c.fisioterapeuta + " · " + c.motivo)),
          chipCita(c.estado))))
      : vacio("Sin citas pendientes hoy", "Las citas por atender aparecen aquí."));

  let lateral;
  if (esFisio) {
    const atencion = pacientes
      .filter((p) => p.semaforo === "ROJO" || p.semaforo === "AMARILLO" || !p.tieneRutina)
      .sort((a, b) => peso(b) - peso(a));
    lateral = h("section", { class: "tarjeta" },
      h("div", { class: "seccion-titulo" }, h("h2", null, "Pacientes que necesitan atención"), h("a", { href: "#/pacientes", class: "enlace" }, "Ver todos")),
      atencion.length
        ? h("ul", { class: "lista" }, atencion.slice(0, 6).map((p) => h("li", null,
            h("a", { class: "fila enlace-fila", href: "#/pacientes/" + p.pacienteId },
              h("span", { class: "crece" }, h("strong", null, p.nombre),
                h("span", { class: "tenue bloque" }, p.tieneRutina ? (p.metaFuncional || "Con rutina") : "Todavía no tiene rutina")),
              p.tieneRutina ? chipSemaforo(p.semaforo) : h("span", { class: "chip chip-pendiente" }, "Sin rutina")))))
        : vacio("Todo en orden", "Ningún paciente tiene dolor alto ni le falta rutina."));
  } else {
    lateral = h("section", { class: "tarjeta" },
      h("div", { class: "seccion-titulo" }, h("h2", null, "Pacientes sin fisioterapeuta"), h("a", { href: "#/pacientes?filtro=sinFisio", class: "enlace" }, "Asignar")),
      sinFisio.length
        ? h("ul", { class: "lista" }, sinFisio.slice(0, 6).map((p) => h("li", null,
            h("a", { class: "fila enlace-fila", href: "#/pacientes/" + p.pacienteId },
              h("span", { class: "crece" }, h("strong", null, p.nombre),
                h("span", { class: "tenue bloque" }, p.tieneCuenta ? "Con cuenta en la app" : "Aún no crea su cuenta"))))))
        : vacio("Todos tienen fisioterapeuta", "Los pacientes nuevos aparecen aquí para asignarlos."));
  }

  vista.replaceChildren(
    encabezado(`${saludo()}, ${primerNombre(usuario.nombre)}`, capital(fechaLarga(hoy))),
    h("div", { class: "indicadores" }, indicadores),
    h("div", { class: "dos-columnas" }, agendaHoy, lateral));
}

function peso(p) {
  if (p.semaforo === "ROJO") return 3;
  if (p.semaforo === "AMARILLO") return 2;
  return 1;
}

/** "Ft. Carlos Ortega Luna" -> "Carlos". */
function primerNombre(nombre) {
  return nombre.split(" ").find((parte) => parte && !parte.endsWith(".")) || nombre;
}

function capital(texto) {
  return texto.charAt(0).toUpperCase() + texto.slice(1);
}
