import test from 'node:test';
import assert from 'node:assert/strict';
import { crearControlador } from '../lambda/controlador.js';

const configuracion = {
  instancia: 'i-0123456789abcdef0',
  grupo: 'operadores',
  cliente: 'cliente',
  emisor: 'https://cognito-idp.us-east-2.amazonaws.com/pool',
  scope: 'trazalab/iniciar',
  salud: 'https://ejemplo.cloudfront.net/api/health'
};

function preparar(ajustes = {}) {
  const llamadas = [];

  const servicios = {
    configuracion,
    consultarUsuario: async () => ({ enabled: true, sub: 'usuario-1' }),
    listarGrupos: async () => ['operadores'],
    consultarInstancia: async id => { llamadas.push(['consultar', id]); return 'stopped'; },
    iniciarInstancia: async id => { llamadas.push(['iniciar', id]); },
    consultarSalud: async () => true,
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

  return { controlador: crearControlador(servicios), evento, llamadas };
}

test('rechaza peticiones sin autorización sin consultar la instancia', async () => {
  const { controlador, llamadas } = preparar();
  const respuesta = await controlador({ routeKey: 'POST /iniciar' });
  assert.equal(respuesta.statusCode, 403);
  assert.deepEqual(llamadas, []);
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
  const { controlador, evento, llamadas } = preparar();
  evento.body = JSON.stringify({ instancia: 'i-atacante' });
  assert.equal((await controlador(evento)).statusCode, 202);
  assert.deepEqual(llamadas, [['consultar', configuracion.instancia], ['iniciar', configuracion.instancia]]);
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
