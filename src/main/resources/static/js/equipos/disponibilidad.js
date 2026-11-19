import { inicializarUbicacion } from "./ubicacion.js";

const panel = document.querySelector("[data-disponibilidad]");
const destino = panel.querySelector("[data-resultados-destino]");
const error = panel.querySelector("[data-error-carga]");
const estadoCarga = document.querySelector("[data-estado-carga]");
const avisoUbicacion = document.querySelector("[data-aviso-ubicacion]");
const formulario = document.querySelector("[data-busqueda-equipos]");
const busqueda = formulario.querySelector("[name=buscar]");
const campoUbicacion = formulario.querySelector("[name=ubicacionId]");
const casillas = Array.from(formulario.querySelectorAll("[name=estado]"));
const botonFiltros = formulario.querySelector("[data-abrir-filtros]");
const panelFiltros = formulario.querySelector("[data-panel-filtros]");
const conteo = formulario.querySelector("[data-conteo-filtros]");
let controlador;
let secuencia = 0;
let temporizador;
let ultimaConsulta;

function actualizarConteo() {
  conteo.textContent = casillas.filter((casilla) => casilla.checked).length;
}

function sincronizarControles(consulta) {
  busqueda.value = consulta.get("buscar") || "";
  const estados = consulta.getAll("estado");
  casillas.forEach((casilla) => {
    casilla.checked = estados.includes(casilla.value);
  });
  actualizarConteo();
}

const ubicacion = inicializarUbicacion(() => {
  clearTimeout(temporizador);
  controlador?.abort();
  secuencia += 1;
  busqueda.value = "";
  casillas.forEach((casilla) => {
    casilla.checked = false;
  });
  actualizarConteo();
  campoUbicacion.value = ubicacion.valores().ubicacionId;
  avisoUbicacion?.setAttribute("hidden", "");
  panel.setAttribute("aria-busy", "true");
  error.hidden = true;
  temporizador = setTimeout(() => cargar("0", true), 500);
});

function parametros(pagina) {
  const consulta = new URLSearchParams();
  Object.entries(ubicacion.valores()).forEach(([nombre, valor]) => {
    if (valor !== "") {
      consulta.set(nombre, valor);
    }
  });
  if (busqueda.value.trim()) {
    consulta.set("buscar", busqueda.value.trim());
  }
  casillas.filter((casilla) => casilla.checked).forEach((casilla) => {
    consulta.append("estado", casilla.value);
  });
  consulta.set("pagina", pagina);
  return consulta;
}

async function cargar(pagina = "0", guardarHistorial = false, consultaAnterior = null, enfocarPagina = false) {
  clearTimeout(temporizador);
  const consulta = consultaAnterior || parametros(pagina);
  ultimaConsulta = new URLSearchParams(consulta);
  controlador?.abort();
  const solicitud = ++secuencia;
  if (guardarHistorial) {
    const url = new URL(window.location.href);
    url.search = consulta.toString();
    window.history.pushState(null, "", url);
  }
  panel.setAttribute("aria-busy", "true");
  error.hidden = true;
  if (!consulta.get("ubicacionId")) {
    const mensaje = document.createElement("div");
    mensaje.className = "equipment-empty-state";
    mensaje.textContent = "Selecciona un aula para consultar sus equipos.";
    destino.querySelector("[data-indicadores]").hidden = true;
    const listado = destino.querySelector("[data-listado-equipos]");
    listado.hidden = false;
    listado.replaceChildren(mensaje);
    destino.hidden = false;
    panel.removeAttribute("data-carga-inicial");
    estadoCarga.textContent = mensaje.textContent;
    panel.setAttribute("aria-busy", "false");
    return;
  }
  controlador = new AbortController();
  try {
    const url = new URL(panel.dataset.urlResultados, window.location.origin);
    url.search = consulta.toString();
    const respuesta = await fetch(url, { signal: controlador.signal });
    if (!respuesta.ok || respuesta.redirected) {
      throw new Error("No se pudo consultar la disponibilidad");
    }
    const documento = new DOMParser().parseFromString(await respuesta.text(), "text/html");
    const contenido = documento.querySelector("[data-resultados-contenido]");
    const indicadores = contenido?.querySelector("[data-indicadores]");
    const listado = contenido?.querySelector("[data-listado-equipos]");
    if (!indicadores || !listado) {
      throw new Error("La respuesta no contiene resultados");
    }
    if (solicitud !== secuencia) {
      return;
    }
    destino.querySelector("[data-indicadores]").replaceWith(indicadores);
    destino.querySelector("[data-listado-equipos]").replaceWith(listado);
    destino.hidden = false;
    panel.removeAttribute("data-carga-inicial");
    if (enfocarPagina) {
      destino.querySelector("[aria-current=page]")?.focus();
    }
    estadoCarga.textContent = "Resultados de disponibilidad actualizados.";
  } catch (fallo) {
    if (fallo.name === "AbortError" || solicitud !== secuencia) {
      return;
    }
    destino.hidden = true;
    panel.removeAttribute("data-carga-inicial");
    error.hidden = false;
    estadoCarga.textContent = "No se pudieron cargar los equipos.";
  } finally {
    if (solicitud === secuencia) {
      panel.setAttribute("aria-busy", "false");
    }
  }
}

formulario.addEventListener("submit", (evento) => {
  evento.preventDefault();
  cargar("0", true);
});

busqueda.addEventListener("input", () => {
  clearTimeout(temporizador);
  controlador?.abort();
  secuencia += 1;
  panel.setAttribute("aria-busy", "true");
  error.hidden = true;
  temporizador = setTimeout(() => cargar("0", true), 500);
});

casillas.forEach((casilla) => {
  casilla.addEventListener("change", () => {
    actualizarConteo();
    cargar("0", true);
  });
});

botonFiltros.addEventListener("click", () => {
  panelFiltros.hidden = !panelFiltros.hidden;
  botonFiltros.setAttribute("aria-expanded", String(!panelFiltros.hidden));
});

formulario.querySelector("[data-limpiar-filtros]").addEventListener("click", () => {
  casillas.forEach((casilla) => {
    casilla.checked = false;
  });
  actualizarConteo();
  cargar("0", true);
});

document.addEventListener("click", (evento) => {
  if (!evento.target.closest(".filtro-equipos")) {
    panelFiltros.hidden = true;
    botonFiltros.setAttribute("aria-expanded", "false");
  }
});

document.addEventListener("keydown", (evento) => {
  if (evento.key === "Escape" && !panelFiltros.hidden) {
    panelFiltros.hidden = true;
    botonFiltros.setAttribute("aria-expanded", "false");
    botonFiltros.focus();
  }
});

panel.addEventListener("click", (evento) => {
  if (evento.target.closest("[data-reintentar]")) {
    cargar("0", false, ultimaConsulta);
    return;
  }
  const enlace = evento.target.closest("nav[aria-label='Paginación de equipos'] a");
  if (!enlace) {
    return;
  }
  evento.preventDefault();
  const consulta = new URL(enlace.href).searchParams;
  cargar(consulta.get("pagina") || "0", true, null, true);
});

window.addEventListener("popstate", () => {
  clearTimeout(temporizador);
  const consulta = new URL(window.location.href).searchParams;
  if (consulta.get("ubicacionId")) {
    ubicacion.seleccionar(consulta.get("ubicacionId"));
  } else if (consulta.has("edificio") || consulta.has("piso")) {
    ubicacion.seleccionar("", consulta.get("edificio"), consulta.get("piso"));
  } else {
    ubicacion.seleccionar(aulaInicial);
  }
  campoUbicacion.value = ubicacion.valores().ubicacionId;
  sincronizarControles(consulta);
  cargar(consulta.get("pagina") || "0");
});

const consultaInicial = new URL(window.location.href).searchParams;
const aulaInicial = ubicacion.valores().ubicacionId;
sincronizarControles(consultaInicial);
if (consultaInicial.get("completo") !== "true") {
  cargar(consultaInicial.get("pagina") || "0");
}
