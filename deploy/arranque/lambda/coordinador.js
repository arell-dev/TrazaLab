import { DeleteCommand, GetCommand, PutCommand } from '@aws-sdk/lib-dynamodb';
import { randomUUID } from 'node:crypto';

const CLAVE = 'trazalab-operacion';
const OPERACIONES = new Set(['arranque', 'despliegue', 'respaldo', 'apagado']);

function esConflictoCondicional(error) {
  return error?.name === 'ConditionalCheckFailedException';
}

export function crearCoordinador({ cliente, tabla, reloj = () => new Date().toISOString(), crearPropietario = randomUUID }) {
  if (!cliente || !/^[A-Za-z0-9_.-]{3,255}$/.test(tabla || '')) {
    throw new Error('Configuración del coordinador incompleta.');
  }

  async function adquirir(operacion, instancia) {
    if (!OPERACIONES.has(operacion) || !/^i-[a-f0-9]{8,17}$/.test(instancia || '')) {
      throw new Error('Operación o instancia inválida.');
    }

    const propietario = crearPropietario();
    const sesion = crearPropietario();
    try {
      await cliente.send(new PutCommand({
        TableName: tabla,
        Item: { pk: CLAVE, operacion, propietario, instancia, sesion, iniciado: reloj() },
        ConditionExpression: 'attribute_not_exists(pk)'
      }));
      return propietario;
    } catch (error) {
      if (esConflictoCondicional(error)) {
        return null;
      }

      throw error;
    }
  }

  async function liberar(operacion, propietario, instancia) {
    if (!OPERACIONES.has(operacion) || !propietario || !/^i-[a-f0-9]{8,17}$/.test(instancia || '')) {
      throw new Error('Permiso de operación inválido.');
    }

    try {
      await cliente.send(new DeleteCommand({
        TableName: tabla,
        Key: { pk: CLAVE },
        ConditionExpression: 'operacion = :operacion AND propietario = :propietario AND instancia = :instancia',
        ExpressionAttributeValues: { ':operacion': operacion, ':propietario': propietario, ':instancia': instancia }
      }));
      return true;
    } catch (error) {
      if (esConflictoCondicional(error)) {
        return false;
      }

      throw error;
    }
  }

  async function liberarTrasTransicion(operacion, instancia) {
    if (!['arranque', 'apagado'].includes(operacion) || !/^i-[a-f0-9]{8,17}$/.test(instancia || '')) {
      throw new Error('Transición de operación inválida.');
    }

    const { Item } = await cliente.send(new GetCommand({ TableName: tabla, Key: { pk: CLAVE }, ConsistentRead: true }));
    if (!Item || Item.operacion !== operacion || Item.instancia !== instancia || typeof Item.propietario !== 'string') {
      return false;
    }

    return await liberar(operacion, Item.propietario, instancia);
  }

  return { adquirir, liberar, liberarTrasTransicion };
}

export { CLAVE as CLAVE_COORDINADOR };
