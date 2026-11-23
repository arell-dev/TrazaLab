export function crearGestorSolicitudes() {
  let ultimaSolicitud = 0;

  return {
    invalidar() {
      ultimaSolicitud += 1;
    },
    async ejecutar(operacion) {
      const solicitud = ++ultimaSolicitud;
      try {
        const valor = await operacion();
        return { vigente: solicitud === ultimaSolicitud, valor };
      } catch (error) {
        return { vigente: solicitud === ultimaSolicitud, error };
      }
    }
  };
}

export async function consultar(api, ruta, metodo, usuario, solicitar = fetch) {
  if (!usuario || usuario.expired) {
    throw new Error('Sesión vencida');
  }

  const respuesta = await solicitar(new URL(ruta, api), {
    method: metodo,
    headers: { Authorization: `Bearer ${usuario.access_token}` },
    cache: 'no-store',
    redirect: 'error',
    signal: AbortSignal.timeout(10000)
  });

  if (respuesta.status === 401 || respuesta.status === 403) {
    throw new Error('Acceso no autorizado');
  }

  if (respuesta.status === 409) {
    throw new Error('Otra operación está en curso');
  }

  if (!respuesta.ok) {
    throw new Error('Solicitud no disponible');
  }

  return respuesta.json();
}
