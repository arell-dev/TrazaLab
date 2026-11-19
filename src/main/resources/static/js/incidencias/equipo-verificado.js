const crearElementoTexto = (nombre, clases, texto) => {
  const elemento = document.createElement(nombre);
  elemento.className = clases;
  elemento.textContent = texto;

  return elemento;
};

const formatearFechaIncidencia = (fechaRegistro) => {
  const fecha = new Date(fechaRegistro);

  if (Number.isNaN(fecha.getTime())) {
    return "Fecha no disponible";
  }

  return new Intl.DateTimeFormat("es-PE", {
    dateStyle: "medium",
    timeStyle: "short"
  }).format(fecha);
};

const renderizarHistorialEquipo = (lista, mensajeVacio, incidencias) => {
  lista.replaceChildren();
  const registros = Array.isArray(incidencias) ? incidencias : [];
  mensajeVacio.hidden = registros.length > 0;

  registros.forEach((incidencia) => {
    const elemento = document.createElement("li");
    elemento.className = "list-group-item px-3 py-3";

    const encabezado = document.createElement("div");
    encabezado.className = "d-flex justify-content-between align-items-start gap-2 mb-2";

    const estado = crearElementoTexto(
      "span",
      "badge text-bg-light border text-body-secondary",
      incidencia.estado || "Estado no disponible"
    );
    const fecha = crearElementoTexto(
      "time",
      "small text-body-secondary text-end",
      formatearFechaIncidencia(incidencia.fechaRegistro)
    );

    encabezado.append(estado, fecha);
    elemento.append(encabezado);
    elemento.append(crearElementoTexto(
      "p",
      "small text-break mb-1",
      incidencia.descripcion || "Sin descripción"
    ));
    lista.append(elemento);
  });
};

export const mostrarEquipoVerificado = (formulario, equipo) => {
  const panel = formulario.querySelector("[data-equipo-resumen]");
  const estado = formulario.querySelector("[data-equipo-estado]");
  const nombreEstado = equipo.estado || "Estado no disponible";
  const fabricanteModelo = [equipo.marca, equipo.modelo]
    .filter((valor) => typeof valor === "string" && valor.trim())
    .join(" ");

  formulario.querySelector("[data-equipo-titulo]").textContent = `Equipo ${equipo.codigoQr}`;
  estado.textContent = nombreEstado;
  estado.className = nombreEstado === "operativa"
    ? "badge rounded-pill text-bg-success"
    : "badge rounded-pill text-bg-warning";
  formulario.querySelector("[data-equipo-serie]").textContent = equipo.numeroSerie || "No registrado";
  formulario.querySelector("[data-equipo-modelo]").textContent = fabricanteModelo || "No registrado";
  formulario.querySelector("[data-equipo-ubicacion]").textContent = equipo.ubicacion || "No registrada";
  renderizarHistorialEquipo(
    formulario.querySelector("[data-equipo-historial]"),
    formulario.querySelector("[data-historial-vacio]"),
    equipo.incidencias
  );
  panel.hidden = false;
};
