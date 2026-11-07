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

const inicializarAvisos = () => {
    document.querySelectorAll("[data-toast]").forEach((toast) => {
        const cerrar = () => {
            toast.classList.remove("show");
            window.setTimeout(() => toast.remove(), 180);
        };
        const botonCerrar = toast.querySelector("[data-toast-close]");

        botonCerrar?.addEventListener("click", cerrar);
        window.setTimeout(cerrar, 5000);
    });
};

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

const mostrarEquipoVerificado = (formulario, equipo) => {
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
        resumenTipo.textContent = selectorTipo.selectedOptions[0]?.textContent.trim() || "Sin clasificar";
        resumenDescripcion.textContent = entradaDescripcion.value.trim() || "Sin descripción";
    };

    const mostrarPaso = (numero) => {
        pasoActual = numero;

        paneles.forEach((panel) => {
            panel.hidden = Number(panel.dataset.stepPanel) !== pasoActual;
        });

        indicadores.forEach((indicador) => {
            const paso = Number(indicador.dataset.stepIndicator);
            indicador.classList.toggle("is-active", paso === pasoActual);
            indicador.classList.toggle("is-complete", paso < pasoActual);

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

document.addEventListener("DOMContentLoaded", () => {
    inicializarContadores();
    inicializarAvisos();
    inicializarFormularioIncidencia();
});
