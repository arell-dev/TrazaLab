export function crearControlador({ configuracion, consultarUsuario, listarGrupos, consultarInstancia, iniciarInstancia, consultarSalud, consultarDespliegue, coordinador, historial }) {
  function registrar(tipo, fecha, identificador) {
    try {
      historial?.registrar(tipo, fecha, identificador);
    } catch {
      // El resultado de EC2 no depende del registro de eventos.
    }
  }

  function responder(codigo, estado) {
    return {
      statusCode: codigo,
      headers: {
        'content-type': 'application/json; charset=utf-8',
        'cache-control': 'no-store',
        'x-content-type-options': 'nosniff'
      },
      body: JSON.stringify({ estado })
    };
  }

  async function autorizar(evento) {
    const claims = evento.requestContext?.authorizer?.jwt?.claims;
    if (!claims || claims.token_use !== 'access' || claims.iss !== configuracion.emisor || claims.client_id !== configuracion.cliente) {
      return false;
    }

    if (typeof claims.sub !== 'string' || typeof claims.username !== 'string' || typeof claims.scope !== 'string') {
      return false;
    }

    if (!claims.scope.split(' ').includes(configuracion.scope)) {
      return false;
    }
    const usuario = await consultarUsuario(claims.username);
    if (!usuario.enabled || usuario.sub !== claims.sub) {
      return false;
    }

    const grupos = await listarGrupos(claims.username);
    return grupos.includes(configuracion.grupo);
  }

  async function estadoActual() {
    const estado = await consultarInstancia(configuracion.instancia);
    if (estado === 'stopped') {
      return 'detenida';
    }

    if (estado === 'pending') {
      return 'iniciando';
    }

    if (estado === 'stopping') {
      return 'deteniendo';
    }

    if (estado !== 'running') {
      return 'no disponible';
    }

    if (await consultarSalud(configuracion.salud)) {
      registrar('salud_disponible');
      return 'disponible';
    }
    registrar('salud_pendiente');
    return 'comprobando';
  }

  async function gestionarCambioEstado(evento) {
    const estado = evento.detail?.state;
    const instancia = evento.detail?.['instance-id'];

    if (evento.region !== configuracion.region || evento.account !== configuracion.cuenta || instancia !== configuracion.instancia) {
      return { procesado: false };
    }

    if (['pending', 'running', 'stopping', 'stopped'].includes(estado)) {
      registrar(`ec2_${estado}`, evento.time, evento.id);
    }

    const estadoReal = await consultarInstancia(instancia);
    if (estadoReal !== estado) {
      return { procesado: false };
    }

    if (estado === 'running') {
      await coordinador.liberarTrasTransicion('arranque', instancia);
    } else if (estado === 'stopped') {
      await coordinador.liberarTrasTransicion('apagado', instancia);
    }

    return { procesado: true };
  }

  return async function controlador(evento) {
    if (evento.source === 'aws.ec2' && evento['detail-type'] === 'EC2 Instance State-change Notification') {
      return await gestionarCambioEstado(evento);
    }

    try {
      if (!await autorizar(evento)) {
        return responder(403, 'no autorizado');
      }

      if (evento.routeKey === 'GET /estado') {
        return responder(200, await estadoActual());
      }

      if (evento.routeKey === 'GET /historial') {
        const respuesta = responder(200, 'disponible');
        const [eventos, despliegue] = await Promise.all([
          historial.consultar(),
          consultarDespliegue().catch(() => null)
        ]);
        respuesta.body = JSON.stringify({ eventos, despliegue });
        return respuesta;
      }

      if (evento.routeKey !== 'POST /iniciar') {
        return responder(404, 'no disponible');
      }

      const propietario = await coordinador.adquirir('arranque', configuracion.instancia);
      if (!propietario) {
        return responder(409, 'operación en curso');
      }

      let liberar = false;
      try {
        const estado = await consultarInstancia(configuracion.instancia);
        if (estado !== 'stopped') {
          if (estado === 'pending' || estado === 'running') {
            liberar = estado === 'running';
            return responder(202, estado === 'pending' ? 'iniciando' : await estadoActual());
          }

          return responder(409, 'no disponible');
        }

        try {
          await iniciarInstancia(configuracion.instancia);
          registrar('solicitud_aceptada');
        } catch (error) {
          registrar('solicitud_incierta');
          const actual = await consultarInstancia(configuracion.instancia);
          if (actual === 'pending' || actual === 'running') {
            liberar = actual === 'running';
            return responder(202, 'iniciando');
          }

          if (actual === 'stopped') {
            liberar = true;
          }

          throw error;
        }

        return responder(202, 'iniciando');
      } finally {
        if (liberar) {
          await coordinador.liberar('arranque', propietario, configuracion.instancia);
        }
      }
    } catch {
      return responder(503, 'no disponible');
    }
  };
}
