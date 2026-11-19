export const inicializarAvisos = () => {
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
