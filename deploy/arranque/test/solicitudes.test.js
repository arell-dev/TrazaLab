import test from 'node:test';
import assert from 'node:assert/strict';
import { consultar, crearGestorSolicitudes } from '../src/solicitudes.js';

function promesaControlada() {
  let resolver;
  let rechazar;
  const promesa = new Promise((resolve, reject) => {
    resolver = resolve;
    rechazar = reject;
  });
  return { promesa, resolver, rechazar };
}

test('descarta una respuesta antigua que llega después de la más reciente', async () => {
  const gestor = crearGestorSolicitudes();
  const anterior = promesaControlada();
  const reciente = promesaControlada();
  const primera = gestor.ejecutar(() => anterior.promesa);
  const segunda = gestor.ejecutar(() => reciente.promesa);

  reciente.resolver({ estado: 'iniciando' });
  assert.deepEqual(await segunda, { vigente: true, valor: { estado: 'iniciando' } });
  anterior.resolver({ estado: 'detenida' });
  assert.deepEqual(await primera, { vigente: false, valor: { estado: 'detenida' } });
});

test('descarta errores anteriores y respuestas pendientes al cerrar sesión', async () => {
  const gestor = crearGestorSolicitudes();
  const anterior = promesaControlada();
  const pendiente = promesaControlada();
  const primera = gestor.ejecutar(() => anterior.promesa);
  const segunda = gestor.ejecutar(() => pendiente.promesa);

  anterior.rechazar(new Error('Acceso no autorizado'));
  assert.equal((await primera).vigente, false);
  gestor.invalidar();
  pendiente.resolver({ estado: 'disponible' });
  assert.equal((await segunda).vigente, false);
});

test('distingue el rechazo de autorización de errores temporales', async () => {
  const api = new URL('https://ejemplo.execute-api.us-east-2.amazonaws.com/');
  const usuario = { access_token: 'token-de-prueba', expired: false };

  for (const status of [401, 403]) {
    await assert.rejects(
      consultar(api, '/estado', 'GET', usuario, async () => ({ ok: false, status })),
      { message: 'Acceso no autorizado' }
    );
  }

  await assert.rejects(
    consultar(api, '/estado', 'GET', usuario, async () => ({ ok: false, status: 503 })),
    { message: 'Solicitud no disponible' }
  );

  await assert.rejects(
    consultar(api, '/iniciar', 'POST', usuario, async () => ({ ok: false, status: 409 })),
    { message: 'Otra operación está en curso' }
  );

  await assert.rejects(consultar(api, '/estado', 'GET', { expired: true }), { message: 'Sesión vencida' });
});

test('conserva el método y la autorización en solicitudes aceptadas', async () => {
  const api = new URL('https://ejemplo.execute-api.us-east-2.amazonaws.com/');
  const usuario = { access_token: 'token-de-prueba', expired: false };

  let peticion;

  const resultado = await consultar(api, '/iniciar', 'POST', usuario, async (url, opciones) => {
    peticion = { url: url.href, opciones };
    return { ok: true, status: 202, json: async () => ({ estado: 'iniciando' }) };
  });

  assert.equal(peticion.url, 'https://ejemplo.execute-api.us-east-2.amazonaws.com/iniciar');
  assert.equal(peticion.opciones.method, 'POST');
  assert.equal(peticion.opciones.headers.Authorization, 'Bearer token-de-prueba');
  assert.deepEqual(resultado, { estado: 'iniciando' });
});
