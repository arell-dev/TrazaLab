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
    let pasoActual = Number(formulario.dataset.initialStep) || 1;

    const actualizarResumen = () => {
        const resumenEquipo = formulario.querySelector("[data-resumen-equipo]");
        const resumenTipo = formulario.querySelector("[data-resumen-tipo]");
        const resumenDescripcion = formulario.querySelector("[data-resumen-descripcion]");

        if (!resumenEquipo || !resumenTipo || !resumenDescripcion) {
            return;
        }

        resumenEquipo.textContent = entradaEquipo.value.trim() || "Sin código";
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

        if (pasoActual === paneles.length) {
            actualizarResumen();
        }
    };

    botonSiguiente.addEventListener("click", () => {
        const panelActual = paneles.find((panel) => Number(panel.dataset.stepPanel) === pasoActual);
        const campoInvalido = panelActual.querySelector(":invalid");

        if (campoInvalido) {
            campoInvalido.reportValidity();
            return;
        }

        mostrarPaso(Math.min(pasoActual + 1, paneles.length));
    });

    botonAnterior.addEventListener("click", () => {
        mostrarPaso(Math.max(pasoActual - 1, 1));
    });

    [entradaEquipo, selectorTipo, entradaDescripcion].forEach((campo) => {
        campo.addEventListener("input", actualizarResumen);
        campo.addEventListener("change", actualizarResumen);
    });

    formulario.addEventListener("submit", (evento) => {
        if (pasoActual !== paneles.length) {
            evento.preventDefault();
        }
    });

    mostrarPaso(pasoActual);
};

document.addEventListener("DOMContentLoaded", () => {
    inicializarContadores();
    inicializarAvisos();
    inicializarFormularioIncidencia();
});
