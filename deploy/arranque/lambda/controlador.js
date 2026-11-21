export function crearControlador({ configuracion, consultarUsuario, listarGrupos, consultarInstancia, iniciarInstancia, consultarSalud }) {
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

    return await consultarSalud(configuracion.salud) ? 'disponible' : 'comprobando';
  }

  return async function controlador(evento) {
    try {
      if (!await autorizar(evento)) {
        return responder(403, 'no autorizado');
      }

      if (evento.routeKey === 'GET /estado') {
        return responder(200, await estadoActual());
      }

      if (evento.routeKey !== 'POST /iniciar') {
        return responder(404, 'no disponible');
      }

      const estado = await consultarInstancia(configuracion.instancia);
      if (estado === 'stopped') {
        try {
          await iniciarInstancia(configuracion.instancia);
        } catch (error) {
          if (error?.name !== 'IncorrectInstanceState') {
            throw error;
          }

          const actual = await consultarInstancia(configuracion.instancia);
          if (actual !== 'pending' && actual !== 'running') {
            throw error;
          }

        }
        return responder(202, 'iniciando');
      }

      if (estado === 'pending' || estado === 'running') {
        return responder(202, estado === 'pending' ? 'iniciando' : await estadoActual());
      }

      return responder(409, 'no disponible');
    } catch {
      return responder(503, 'no disponible');
    }
  };
}
