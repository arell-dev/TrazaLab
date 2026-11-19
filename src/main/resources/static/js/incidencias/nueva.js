import { mostrarEquipoVerificado } from "./equipo-verificado.js";

const inicializarContadores = () => {
  document.querySelectorAll("[data-caracteres-maximos]").forEach((campo) => {
    const contador = campo.closest(".mb-4")?.querySelector("[data-conteo-caracteres]");

    if (!contador) {
      return;
    }

    const actualizar = () => {
      contador.textContent = String(campo.value.length);
    };

    campo.addEventListener("input", actualizar);
    actualizar();
  });
};

const inicializarFormularioIncidencia = () => {
  const formulario = document.querySelector("#formulario-incidencia");

  if (!formulario) {
    return;
  }

  const paneles = Array.from(formulario.querySelectorAll("[data-step-panel]"));
  const indicadores = Array.from(formulario.querySelectorAll("[data-step-indicator]"));
  const botonAnterior = formulario.querySelector("[data-step-back]");
  const botonSiguiente = formulario.querySelector("[data-step-next]");
  const botonGuardar = formulario.querySelector("[data-step-submit]");
  const enlaceCancelar = formulario.querySelector("[data-step-cancel]");
  const entradaEquipo = formulario.querySelector("#codigoQr");
  const selectorTipo = formulario.querySelector("#tipoFallaId");
  const entradaDescripcion = formulario.querySelector("#descripcion");
  const indicadorCargaEquipo = formulario.querySelector("[data-equipo-cargando]");
  const mensajeErrorEquipo = formulario.querySelector("[data-equipo-error]");
  let pasoActual = Number(formulario.dataset.initialStep) || 1;
  let solicitudEquipo = 0;
  let codigoQrVerificado = "";
  let equipoVerificado = null;

  const actualizarResumen = () => {
    const resumenEquipo = formulario.querySelector("[data-resumen-equipo]");
    const resumenUbicacion = formulario.querySelector("[data-resumen-ubicacion]");
    const resumenTipo = formulario.querySelector("[data-resumen-tipo]");
    const resumenDescripcion = formulario.querySelector("[data-resumen-descripcion]");

    if (!resumenEquipo || !resumenTipo || !resumenDescripcion) {
      return;
    }

    const fabricanteModelo = equipoVerificado
      ? [equipoVerificado.marca, equipoVerificado.modelo]
          .filter((valor) => typeof valor === "string" && valor.trim())
          .join(" ")
      : "";
    resumenEquipo.textContent = equipoVerificado
      ? [equipoVerificado.codigoQr, fabricanteModelo].filter(Boolean).join(" · ")
      : entradaEquipo.value.trim() || "Sin código";
    if (resumenUbicacion) {
      resumenUbicacion.textContent = equipoVerificado?.ubicacion || "No registrada";
    }
    resumenTipo.textContent = selectorTipo.selectedOptions[0]?.textContent.trim() || "Sin clasificar";
    resumenDescripcion.textContent = entradaDescripcion.value.trim() || "Sin descripción";
  };

  const mostrarPaso = (numero) => {
    const pasoAnterior = pasoActual;
    pasoActual = numero;

    paneles.forEach((panel) => {
      panel.hidden = Number(panel.dataset.stepPanel) !== pasoActual;
    });

    indicadores.forEach((indicador) => {
      const paso = Number(indicador.dataset.stepIndicator);
      indicador.classList.toggle("is-active", paso === pasoActual);
      indicador.classList.toggle("is-complete", paso < pasoActual);
      const numeroIndicador = indicador.querySelector(".incidencia-stepper__number");

      if (numeroIndicador) {
        numeroIndicador.textContent = paso < pasoActual ? "✓" : String(paso);
      }

      if (paso === pasoActual) {
        indicador.setAttribute("aria-current", "step");
      } else {
        indicador.removeAttribute("aria-current");
      }
    });

    botonAnterior.hidden = pasoActual === 1;
    botonSiguiente.hidden = pasoActual === paneles.length;
    botonGuardar.hidden = pasoActual !== paneles.length;
    enlaceCancelar.hidden = pasoActual !== 1;
    botonSiguiente.textContent = pasoActual === 1
      ? (equipoVerificado ? "Continuar a la incidencia" : "Verificar equipo")
      : "Siguiente";
    formulario.querySelector("[data-equipo-resumen]").hidden = pasoActual === paneles.length || !equipoVerificado;

    if (pasoActual === paneles.length) {
      actualizarResumen();
    }

    if (pasoAnterior !== pasoActual) {
      const encabezadoPaso = paneles
        .find((panel) => Number(panel.dataset.stepPanel) === pasoActual)
        ?.querySelector("h2");
      encabezadoPaso?.focus({ preventScroll: true });
      window.scrollTo(0, 0);
    }
  };

  const mostrarErrorEquipo = (mensaje) => {
    mensajeErrorEquipo.textContent = mensaje;
    mensajeErrorEquipo.hidden = false;
  };

  const limpiarEquipoVerificado = () => {
    equipoVerificado = null;
    codigoQrVerificado = "";
    formulario.querySelector("[data-equipo-resumen]").hidden = true;
    formulario.querySelector("[data-equipo-historial]").replaceChildren();
  };

  const verificarEquipo = async () => {
    const codigoQr = entradaEquipo.value.trim();

    if (!codigoQr) {
      entradaEquipo.reportValidity();
      return false;
    }

    const codigoNormalizado = codigoQr.toLocaleLowerCase("es-PE");
    const codigoVerificadoNormalizado = codigoQrVerificado.toLocaleLowerCase("es-PE");

    if (equipoVerificado && codigoNormalizado === codigoVerificadoNormalizado) {
      return true;
    }

    const solicitudActual = ++solicitudEquipo;
    const endpoint = new URL(formulario.dataset.equipoHistorialUrl, window.location.origin);
    endpoint.searchParams.set("codigoQr", codigoQr);
    indicadorCargaEquipo.hidden = false;
    mensajeErrorEquipo.hidden = true;
    botonSiguiente.disabled = true;

    try {
      const respuesta = await fetch(endpoint, {
        headers: { Accept: "application/json" }
      });
      const datos = await respuesta.json().catch(() => ({}));

      if (solicitudActual !== solicitudEquipo) {
        return false;
      }

      if (!respuesta.ok) {
        throw new Error(datos.detail || "No se encontró un equipo con ese código QR.");
      }

      equipoVerificado = datos;
      codigoQrVerificado = codigoQr;
      mostrarEquipoVerificado(formulario, datos);
      return true;
    } catch (error) {
      if (solicitudActual !== solicitudEquipo) {
        return false;
      }

      limpiarEquipoVerificado();
      mostrarErrorEquipo(error instanceof Error && error.message
        ? error.message
        : "No se pudo consultar el equipo. Intenta nuevamente.");
      return false;
    } finally {
      if (solicitudActual === solicitudEquipo) {
        indicadorCargaEquipo.hidden = true;
        botonSiguiente.disabled = false;
      }
    }
  };

  entradaEquipo.addEventListener("input", () => {
    const codigoActual = entradaEquipo.value.trim().toLocaleLowerCase("es-PE");
    const codigoVerificado = codigoQrVerificado.toLocaleLowerCase("es-PE");

    if (equipoVerificado && codigoActual === codigoVerificado) {
      return;
    }

    solicitudEquipo += 1;
    limpiarEquipoVerificado();
    mensajeErrorEquipo.hidden = true;
    indicadorCargaEquipo.hidden = true;
    botonSiguiente.disabled = false;

    mostrarPaso(1);
  });

  botonSiguiente.addEventListener("click", async () => {
    const panelActual = paneles.find((panel) => Number(panel.dataset.stepPanel) === pasoActual);
    const campoInvalido = panelActual.querySelector(":invalid");

    if (campoInvalido) {
      campoInvalido.reportValidity();
      return;
    }

    if (pasoActual === 1) {
      if (!equipoVerificado) {
        if (!await verificarEquipo()) {
          return;
        }

        mostrarPaso(1);
        return;
      }
    }

    if (pasoActual > 1 && !equipoVerificado) {
      mostrarPaso(1);
      mostrarErrorEquipo("Verifica el equipo antes de continuar con el reporte.");
      return;
    }

    mostrarPaso(Math.min(pasoActual + 1, paneles.length));
  });

  botonAnterior.addEventListener("click", () => {
    mostrarPaso(Math.max(pasoActual - 1, 1));
  });

  [selectorTipo, entradaDescripcion].forEach((campo) => {
    campo.addEventListener("input", actualizarResumen);
    campo.addEventListener("change", actualizarResumen);
  });

  formulario.addEventListener("submit", (evento) => {
    if (pasoActual !== paneles.length || !equipoVerificado) {
      evento.preventDefault();
    }
  });

  mostrarPaso(pasoActual);

  if (pasoActual > 1) {
    verificarEquipo().then((verificado) => {
      if (!verificado) {
        mostrarPaso(1);
      }
    });
  }
};

inicializarContadores();
inicializarFormularioIncidencia();
