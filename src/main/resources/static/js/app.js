document.querySelectorAll("[data-caracteres-maximos]").forEach((campo) => {
    const contador = campo.closest(".mb-4")?.querySelector("[data-conteo-caracteres]");

    if (!contador) {
        return;
    }

    const actualizarConteo = () => {
        contador.textContent = String(campo.value.length);
    };

    campo.addEventListener("input", actualizarConteo);
    actualizarConteo();
});
