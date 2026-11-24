import test from 'node:test';
import assert from 'node:assert/strict';
import { crearControlador } from '../lambda/controlador.js';

const configuracion = {
  instancia: 'i-0123456789abcdef0',
  grupo: 'operadores',
  cliente: 'cliente',
  emisor: 'https://cognito-idp.us-east-2.amazonaws.com/pool',
  scope: 'trazalab/iniciar',
  salud: 'https://ejemplo.cloudfront.net/api/health',
  cuenta: '970909363773',
  region: 'us-east-2'
};

function preparar(ajustes = {}) {
  const llamadas = [];
  const operaciones = [];

  const servicios = {
    configuracion,
    consultarUsuario: async () => ({ enabled: true, sub: 'usuario-1' }),
    listarGrupos: async () => ['operadores'],
    consultarInstancia: async id => { llamadas.push(['consultar', id]); return llamadas.filter(llamada => llamada[0] === 'consultar').length === 1 ? 'stopped' : 'pending'; },
    iniciarInstancia: async id => { llamadas.push(['iniciar', id]); },
    consultarSalud: async () => true,
    consultarDespliegue: async () => null,
    coordinador: {
      adquirir: async (operacion, instancia) => { operaciones.push(['adquirir', operacion, instancia]); return 'propietario-1'; },
      liberar: async (operacion, propietario, instancia) => { operaciones.push(['liberar', operacion, propietario, instancia]); return true; },
      liberarTrasTransicion: async (operacion, instancia) => { operaciones.push(['transicion', operacion, instancia]); return true; }
    },
    ...ajustes
  };

  const evento = {
    routeKey: 'POST /iniciar',
    requestContext: {
      authorizer: {
        jwt: {
          claims: {
            token_use: 'access',
            iss: configuracion.emisor,
            client_id: configuracion.cliente,
            scope: configuracion.scope,
            sub: 'usuario-1',
            username: 'persona'
          }
        }
      }
    }
  };

  return { controlador: crearControlador(servicios), evento, llamadas, operaciones };
}

test('rechaza peticiones sin autorización sin consultar la instancia', async () => {
  const { controlador, llamadas } = preparar();
  const respuesta = await controlador({ routeKey: 'POST /iniciar' });
  assert.equal(respuesta.statusCode, 403);
  assert.deepEqual(llamadas, []);
});

test('entrega el reloj a operadores sin consultar EC2 ni escribir historial', async () => {
  const temporizador = {
    segundosRestantes: 1770,
    segundosInactivo: 30,
    inactividadNanos: 30000000000,
    solicitudActiva: false,
    drenando: false,
    umbralSegundos: 1800
  };
  const efectos = [];
  const { controlador, evento, llamadas } = preparar({
    consultarTemporizador: async (url) => {
      efectos.push(url);
      return temporizador;
    },
    historial: { registrar: () => efectos.push('registrar') }
  });
  evento.routeKey = 'GET /temporizador';

  assert.equal((await controlador({ routeKey: evento.routeKey })).statusCode, 403);
  assert.deepEqual(efectos, []);

  const respuesta = await controlador(evento);
  assert.equal(respuesta.statusCode, 200);
  assert.equal(respuesta.headers['cache-control'], 'no-store');
  assert.deepEqual(JSON.parse(respuesta.body), { estado: 'disponible', temporizador });
  assert.deepEqual(efectos, [configuracion.salud]);
  assert.deepEqual(llamadas, []);
});

test('devuelve un estado explícito cuando el reloj no está disponible', async () => {
  const { controlador, evento } = preparar({ consultarTemporizador: async () => null });
  evento.routeKey = 'GET /temporizador';

  const respuesta = await controlador(evento);
  assert.equal(respuesta.statusCode, 503);
  assert.deepEqual(JSON.parse(respuesta.body), { estado: 'temporizador no disponible' });
});

test('rechaza token ID y emisor o cliente incorrectos', async () => {
  for (const [propiedad, valor] of [['token_use', 'id'], ['iss', 'otro'], ['client_id', 'otro'], ['scope', 'openid']]) {

    const { controlador, evento, llamadas } = preparar();
    evento.requestContext.authorizer.jwt.claims[propiedad] = valor;
    assert.equal((await controlador(evento)).statusCode, 403);
    assert.deepEqual(llamadas, []);
  }

});

test('deniega usuarios deshabilitados, ajenos o sin grupo', async () => {

  for (const opciones of [
    { consultarUsuario: async () => ({ enabled: false, sub: 'usuario-1' }) },
    { consultarUsuario: async () => ({ enabled: true, sub: 'otro' }) },
    { listarGrupos: async () => ['otros'] }
  ]) {
    const { controlador, evento, llamadas } = preparar(opciones);
    assert.equal((await controlador(evento)).statusCode, 403);
    assert.deepEqual(llamadas, []);
  }

});

test('enciende solo la instancia configurada y no acepta una elegida por el cliente', async () => {
  const { controlador, evento, llamadas, operaciones } = preparar();
  evento.body = JSON.stringify({ instancia: 'i-atacante' });
  assert.equal((await controlador(evento)).statusCode, 202);
  assert.deepEqual(llamadas, [
    ['consultar', configuracion.instancia],
    ['iniciar', configuracion.instancia]
  ]);
  assert.deepEqual(operaciones, [
    ['adquirir', 'arranque', configuracion.instancia]
  ]);
});

test('acepta el encendido mientras AWS termina de reflejar el estado y conserva el bloqueo', async () => {
  let consultas = 0;
  const { controlador, evento, llamadas, operaciones } = preparar({
    consultarInstancia: async () => {
      consultas += 1;
      return 'stopped';
    }
  });

  const respuesta = await controlador(evento);
  assert.equal(respuesta.statusCode, 202);
  assert.equal(JSON.parse(respuesta.body).estado, 'iniciando');
  assert.equal(consultas, 1);
  assert.deepEqual(llamadas, [['iniciar', configuracion.instancia]]);
  assert.deepEqual(operaciones, [['adquirir', 'arranque', configuracion.instancia]]);
});

test('no inicia EC2 cuando otro actor mantiene el coordinador', async () => {
  const { controlador, evento, llamadas } = preparar({
    coordinador: {
      adquirir: async () => null,
      liberar: async () => { throw new Error('No debía liberar un permiso ajeno.'); },
      liberarTrasTransicion: async () => false
    }
  });

  const respuesta = await controlador(evento);
  assert.equal(respuesta.statusCode, 409);
  assert.equal(JSON.parse(respuesta.body).estado, 'operación en curso');
  assert.deepEqual(llamadas, []);
});

test('evento detenido libera solo el permiso de apagado tras verificar EC2', async () => {
  const { controlador, llamadas, operaciones } = preparar({
    consultarInstancia: async id => { llamadas.push(['consultar', id]); return 'stopped'; }
  });
  const evento = {
    source: 'aws.ec2',
    'detail-type': 'EC2 Instance State-change Notification',
    account: configuracion.cuenta,
    region: configuracion.region,
    detail: { state: 'stopped', 'instance-id': configuracion.instancia }
  };

  assert.deepEqual(await controlador(evento), { procesado: true });
  assert.deepEqual(llamadas, [['consultar', configuracion.instancia]]);
  assert.deepEqual(operaciones, [['transicion', 'apagado', configuracion.instancia]]);
});

test('evento running libera solo el permiso de arranque tras verificar EC2', async () => {
  const { controlador, llamadas, operaciones } = preparar({
    consultarInstancia: async id => { llamadas.push(['consultar', id]); return 'running'; }
  });
  const evento = {
    source: 'aws.ec2',
    'detail-type': 'EC2 Instance State-change Notification',
    account: configuracion.cuenta,
    region: configuracion.region,
    detail: { state: 'running', 'instance-id': configuracion.instancia }
  };

  assert.deepEqual(await controlador(evento), { procesado: true });
  assert.deepEqual(llamadas, [['consultar', configuracion.instancia]]);
  assert.deepEqual(operaciones, [['transicion', 'arranque', configuracion.instancia]]);
});

test('ignora eventos de otra cuenta o instancia', async () => {
  const { controlador, llamadas, operaciones } = preparar();
  const evento = {
    source: 'aws.ec2',
    'detail-type': 'EC2 Instance State-change Notification',
    account: '000000000000',
    region: configuracion.region,
    detail: { state: 'stopped', 'instance-id': configuracion.instancia }
  };

  assert.deepEqual(await controlador(evento), { procesado: false });
  assert.deepEqual(llamadas, []);
  assert.deepEqual(operaciones, []);
});

test('no considera disponible una instancia ejecutándose si falla la salud', async () => {
  const { controlador, evento } = preparar({ consultarInstancia: async () => 'running', consultarSalud: async () => false });

  evento.routeKey = 'GET /estado';
  const respuesta = await controlador(evento);
  assert.equal(respuesta.statusCode, 200);
  assert.equal(JSON.parse(respuesta.body).estado, 'comprobando');

});

test('responde disponible solo cuando EC2 y la aplicación están listas', async () => {
  const { controlador, evento } = preparar({ consultarInstancia: async () => 'running', consultarSalud: async () => true });

  evento.routeKey = 'GET /estado';
  const respuesta = await controlador(evento);
  assert.equal(JSON.parse(respuesta.body).estado, 'disponible');
  assert.equal(respuesta.headers['cache-control'], 'no-store');

});

test('permite una segunda solicitud concurrente si la instancia ya está iniciando', async () => {
  let consultas = 0;

  const { controlador, evento } = preparar({
    consultarInstancia: async () => ++consultas === 1 ? 'stopped' : 'pending',
    iniciarInstancia: async () => { throw Object.assign(new Error(), { name: 'IncorrectInstanceState' }); }
  });

  assert.equal((await controlador(evento)).statusCode, 202);
});

test('errores de AWS no exponen contenido sensible', async () => {
  const { controlador, evento } = preparar({ consultarUsuario: async () => { throw new Error('secreto'); } });
  const respuesta = await controlador(evento);
  assert.equal(respuesta.statusCode, 503);
  assert.equal(respuesta.body.includes('secreto'), false);
});

test('el historial requiere autorización y conserva el formato acordado', async () => {
  let consultas = 0;
  const eventos = [{ fecha: '2026-11-23T04:35:00.000Z', tipo: 'ec2_running', mensaje: 'AWS confirmó que la instancia está encendida.' }];
  const { controlador, evento } = preparar({ historial: { consultar: async () => { consultas += 1; return eventos; } } });
  assert.equal((await controlador({ routeKey: 'GET /historial' })).statusCode, 403);
  assert.equal(consultas, 0);
  evento.routeKey = 'GET /historial';
  const respuesta = await controlador(evento);
  assert.equal(respuesta.statusCode, 200);
  assert.deepEqual(JSON.parse(respuesta.body), { eventos, despliegue: null });
});

test('un fallo al registrar eventos no altera el resultado del encendido', async () => {
  const { controlador, evento, operaciones } = preparar({ historial: { registrar: () => { throw new Error('fallo de registro'); } } });
  const respuesta = await controlador(evento);
  assert.equal(respuesta.statusCode, 202);
  assert.equal(JSON.parse(respuesta.body).estado, 'iniciando');
  assert.deepEqual(operaciones, [['adquirir', 'arranque', configuracion.instancia]]);
});

test('conserva la notificación EC2 tardía sin liberar un permiso con estado distinto', async () => {
  const registrados = [];
  const { controlador, operaciones } = preparar({
    consultarInstancia: async () => 'running',
    historial: { registrar: (...datos) => registrados.push(datos) }
  });
  const evento = {
    source: 'aws.ec2',
    'detail-type': 'EC2 Instance State-change Notification',
    account: configuracion.cuenta,
    region: configuracion.region,
    time: '2026-11-23T04:35:00Z',
    id: '01234567-89ab-cdef-0123-456789abcdef',
    detail: { state: 'pending', 'instance-id': configuracion.instancia }
  };
  assert.deepEqual(await controlador(evento), { procesado: false });
  assert.deepEqual(registrados, [['ec2_pending', evento.time, evento.id]]);
  assert.deepEqual(operaciones, []);
});
