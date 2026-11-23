import test from 'node:test';
import assert from 'node:assert/strict';
import { crearCoordinador } from '../lambda/coordinador.js';

const instancia = 'i-0123456789abcdef0';

function preparar(responder = async () => ({})) {
  const llamadas = [];
  const cliente = {
    async send(comando) {
      llamadas.push({ nombre: comando.constructor.name, entrada: comando.input });
      return await responder(comando);
    }
  };
  return {
    llamadas,
    coordinador: crearCoordinador({
      cliente,
      tabla: 'trazalab-operacion',
      reloj: () => '2026-11-22T12:00:00Z',
      crearPropietario: () => 'propietario-1'
    })
  };
}

test('adquiere una sola operación con escritura condicional', async () => {
  const { coordinador, llamadas } = preparar();

  assert.equal(await coordinador.adquirir('apagado', instancia), 'propietario-1');
  assert.deepEqual(llamadas[0], {
    nombre: 'PutCommand',
    entrada: {
      TableName: 'trazalab-operacion',
      Item: {
        pk: 'trazalab-operacion',
        operacion: 'apagado',
        propietario: 'propietario-1',
        instancia,
        sesion: 'propietario-1',
        iniciado: '2026-11-22T12:00:00Z'
      },
      ConditionExpression: 'attribute_not_exists(pk)'
    }
  });
});

test('conflicto de escritura condicional devuelve ocupada y otros errores se propagan', async () => {
  const ocupada = preparar(async () => { throw Object.assign(new Error('ocupada'), { name: 'ConditionalCheckFailedException' }); });
  assert.equal(await ocupada.coordinador.adquirir('arranque', instancia), null);

  const error = Object.assign(new Error('sin acceso'), { name: 'AccessDeniedException' });
  const denegada = preparar(async () => { throw error; });
  await assert.rejects(denegada.coordinador.adquirir('arranque', instancia), error);
});

test('libera únicamente con operación, propietario e instancia coincidentes', async () => {
  const { coordinador, llamadas } = preparar();

  assert.equal(await coordinador.liberar('despliegue', 'propietario-1', instancia), true);
  assert.equal(llamadas[0].nombre, 'DeleteCommand');
  assert.equal(llamadas[0].entrada.ConditionExpression, 'operacion = :operacion AND propietario = :propietario AND instancia = :instancia');
  assert.deepEqual(llamadas[0].entrada.ExpressionAttributeValues, {
    ':operacion': 'despliegue',
    ':propietario': 'propietario-1',
    ':instancia': instancia
  });
});

test('transición externa lee de forma consistente y libera solo arranque o apagado', async () => {
  let operacion = 'apagado';
  const { coordinador, llamadas } = preparar(async comando => {
    if (comando.constructor.name === 'GetCommand') {
      return { Item: { pk: 'trazalab-operacion', operacion, propietario: 'propietario-1', instancia } };
    }
    return {};
  });

  assert.equal(await coordinador.liberarTrasTransicion('apagado', instancia), true);
  assert.equal(llamadas[0].entrada.ConsistentRead, true);
  assert.equal(llamadas[1].nombre, 'DeleteCommand');
  operacion = 'arranque';
  assert.equal(await coordinador.liberarTrasTransicion('arranque', instancia), true);
});

test('evento duplicado o propietario distinto no borra un bloqueo ajeno', async () => {
  const { coordinador, llamadas } = preparar(async comando => {
    if (comando.constructor.name === 'GetCommand') {
      return { Item: { pk: 'trazalab-operacion', operacion: 'despliegue', propietario: 'propietario-1', instancia } };
    }
    return {};
  });

  assert.equal(await coordinador.liberarTrasTransicion('apagado', instancia), false);
  assert.equal(llamadas.length, 1);
});
