import test from 'node:test';
import assert from 'node:assert/strict';
import { crearHistorial } from '../lambda/historial.js';

test('consulta páginas en orden descendente y oculta contenido desconocido', async () => {
  const comandos = [];
  const paginas = [
    { events: [], nextToken: 'pagina-2' },
    { events: [
      { message: 'INFO TRAZALAB_EVENTO {"fecha":"2026-11-23T04:36:00.000Z","tipo":"salud_disponible"}' },
      { message: 'INFO TRAZALAB_EVENTO {"fecha":"2026-11-23T04:35:59.000Z","tipo":"salud_disponible"}' },
      { message: 'TRAZALAB_EVENTO {"fecha":"2026-11-23T04:35:00.000Z","tipo":"ec2_running","mensaje":"secreto"}' },
      { message: 'TRAZALAB_EVENTO {"fecha":"2026-11-23T04:34:00.000Z","tipo":"desconocido"}' }
    ] }
  ];
  const historial = crearHistorial({ cliente: { send: async comando => { comandos.push(comando.input); return paginas.shift(); } }, grupo: '/aws/lambda/panel', reloj: () => new Date('2026-11-23T12:00:00.000Z') });
  const eventos = await historial.consultar();
  assert.equal(eventos.length, 2);
  assert.equal(eventos[0].tipo, 'salud_disponible');
  assert.equal(eventos[1].tipo, 'ec2_running');
  assert.equal(JSON.stringify(eventos).includes('secreto'), false);
  assert.equal(comandos[0].startFromHead, false);
  assert.equal(comandos[1].nextToken, 'pagina-2');
});

test('conserva metadatos de despliegue solo con formato válido', async () => {
  const dato = { fecha: '2026-11-23T04:35:00.000Z', tipo: 'despliegue_verificado', version: 'v0.0.11', commit: 'a'.repeat(40), digest: `sha256:${'b'.repeat(64)}` };
  const historial = crearHistorial({ cliente: { send: async () => ({ events: [{ message: `TRAZALAB_EVENTO ${JSON.stringify(dato)}` }] }) }, grupo: '/aws/lambda/panel' });
  const [evento] = await historial.consultar();
  assert.equal(evento.version, dato.version);
  assert.equal(evento.commit, dato.commit);
  assert.equal(evento.digest, dato.digest);
});

test('omite reentregas de EventBridge sin agrupar sucesos diferentes', async () => {
  const fecha = '2026-11-23T04:35:00.000Z';
  const eventos = [
    { fecha, tipo: 'ec2_running', identificador: '01234567-89ab-cdef-0123-456789abcdef' },
    { fecha, tipo: 'ec2_running', identificador: '01234567-89ab-cdef-0123-456789abcdef' },
    { fecha, tipo: 'ec2_running', identificador: '11234567-89ab-cdef-0123-456789abcdef' }
  ];
  const historial = crearHistorial({ cliente: { send: async () => ({ events: eventos.map(evento => ({ message: `TRAZALAB_EVENTO ${JSON.stringify(evento)}` })) }) }, grupo: '/aws/lambda/panel' });
  assert.equal((await historial.consultar()).length, 2);
});
