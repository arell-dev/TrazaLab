export function inicializarUbicacion(alCambiar) {
  const formulario = document.querySelector("[data-ubicacion-selectores]");
  const torre = formulario.querySelector("#edificio");
  const piso = formulario.querySelector("#piso");
  const aula = formulario.querySelector("#ubicacion-id");
  const opcionesPiso = Array.from(piso.querySelectorAll("option[data-torre]"));
  const opcionesAula = Array.from(aula.querySelectorAll("option[data-torre]"));

  function actualizar() {
    opcionesPiso.forEach((opcion) => {
      opcion.hidden = opcion.dataset.torre !== torre.value;
      opcion.disabled = opcion.hidden;
    });
    opcionesAula.forEach((opcion) => {
      opcion.hidden = opcion.dataset.torre !== torre.value || opcion.dataset.piso !== piso.value;
      opcion.disabled = opcion.hidden;
    });
    piso.disabled = !torre.value;
    aula.disabled = !piso.value;
  }

  torre.addEventListener("change", () => {
    actualizar();
    piso.value = opcionesPiso.find((opcion) => !opcion.hidden)?.value || "";
    actualizar();
    aula.value = opcionesAula.find((opcion) => !opcion.hidden)?.value || "";
    alCambiar();
  });

  piso.addEventListener("change", () => {
    actualizar();
    aula.value = opcionesAula.find((opcion) => !opcion.hidden)?.value || "";
    alCambiar();
  });

  aula.addEventListener("change", alCambiar);
  formulario.addEventListener("submit", (evento) => evento.preventDefault());
  actualizar();

  return {
    valores() {
      return { edificio: torre.value, piso: piso.value, ubicacionId: aula.value };
    },
    seleccionar(ubicacionId, edificio = "", numeroPiso = "") {
      const opcion = opcionesAula.find((elemento) => elemento.value === ubicacionId);
      if (opcion) {
        torre.value = opcion.dataset.torre;
        piso.value = opcion.dataset.piso;
        actualizar();
        aula.value = opcion.value;
        return;
      }

      torre.value = edificio;
      actualizar();
      piso.value = numeroPiso;
      actualizar();
      aula.value = "";
    }
  };
}
