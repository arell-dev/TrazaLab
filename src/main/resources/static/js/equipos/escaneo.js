const inicializarLectorQr = () => {
  const formulario = document.querySelector("[data-lector-form]");

  if (!formulario) {
    return;
  }

  const video = formulario.closest("main")?.querySelector("[data-lector-video]");
  const mensaje = formulario.closest("main")?.querySelector("[data-lector-mensaje]");
  const entrada = formulario.querySelector("[data-lector-entrada]");
  const iniciar = formulario.closest("main")?.querySelector("[data-lector-iniciar]");
  const detener = formulario.closest("main")?.querySelector("[data-lector-detener]");
  const placeholder = formulario.closest("main")?.querySelector("[data-lector-placeholder]");

  if (!video || !mensaje || !entrada || !iniciar || !detener || !placeholder) {
    return;
  }

  let controlesLector = null;

  const detenerCamara = () => {
    if (controlesLector) {
      controlesLector.stop();
      controlesLector = null;
    }

    video.srcObject?.getTracks().forEach((pista) => pista.stop());
    video.srcObject = null;
    video.hidden = true;
    placeholder.hidden = false;
    iniciar.hidden = false;
    detener.hidden = true;
  };

  iniciar.addEventListener("click", async () => {
    if (!window.isSecureContext || !navigator.mediaDevices?.getUserMedia) {
      mensaje.textContent = "La cámara requiere HTTPS o localhost. Puedes ingresar el código manualmente.";
      return;
    }

    if (!window.ZXingBrowser?.BrowserQRCodeReader) {
      mensaje.textContent = "No se pudo cargar el lector QR. Puedes ingresar el código manualmente.";
      return;
    }

    iniciar.disabled = true;
    mensaje.textContent = "Solicitando permiso para usar la cámara…";

    try {
      const lector = new window.ZXingBrowser.BrowserQRCodeReader();
      video.hidden = false;
      placeholder.hidden = true;
      iniciar.hidden = true;
      detener.hidden = false;
      mensaje.textContent = "Centra el código QR dentro del recuadro.";
      controlesLector = await lector.decodeFromVideoDevice(undefined, video, (resultado) => {
        if (!resultado) {
          return;
        }

        entrada.value = resultado.getText().trim();
        mensaje.textContent = "Código detectado. Abriendo la ficha del equipo…";
        detenerCamara();
        formulario.requestSubmit();
      });
    } catch (error) {
      detenerCamara();
      mensaje.textContent = error.name === "NotAllowedError"
        ? "No se concedió permiso para usar la cámara. Puedes ingresar el código manualmente."
        : "No se pudo iniciar la cámara. Puedes ingresar el código manualmente.";
    } finally {
      iniciar.disabled = false;
    }
  });

  detener.addEventListener("click", () => {
    detenerCamara();
    mensaje.textContent = "Cámara detenida. Puedes volver a iniciarla o escribir el código.";
  });
  formulario.addEventListener("submit", detenerCamara);
  window.addEventListener("pagehide", detenerCamara);
};

inicializarLectorQr();
