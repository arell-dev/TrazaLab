import { EC2Client, DescribeInstancesCommand, StartInstancesCommand } from '@aws-sdk/client-ec2';
import { CognitoIdentityProviderClient, AdminGetUserCommand, AdminListGroupsForUserCommand } from '@aws-sdk/client-cognito-identity-provider';
import { DynamoDBClient } from '@aws-sdk/client-dynamodb';
import { DynamoDBDocumentClient, GetCommand } from '@aws-sdk/lib-dynamodb';
import { CloudWatchLogsClient } from '@aws-sdk/client-cloudwatch-logs';
import { crearControlador } from './controlador.js';
import { crearCoordinador } from './coordinador.js';
import { crearHistorial } from './historial.js';

const instancia = process.env.INSTANCIA_ID;
const grupo = process.env.GRUPO_OPERADORES;
const pool = process.env.USER_POOL_ID;
const cliente = process.env.CLIENTE_ID;
const region = process.env.AWS_REGION;
const salud = process.env.SALUD_URL;
const cuenta = process.env.CUENTA_AWS;
const tablaCoordinador = process.env.COORDINADOR_TABLA;
const grupoHistorial = process.env.HISTORIAL_GRUPO;

function validarConfiguracion() {
  if (!/^i-[a-f0-9]{8,17}$/.test(instancia || '') || !/^[\w-]+$/.test(grupo || '') || !/^[\w-]+$/.test(pool || '') || !/^[a-z0-9]+$/.test(cliente || '') || !/^[0-9]{12}$/.test(cuenta || '') || !/^[A-Za-z0-9_.-]{3,255}$/.test(tablaCoordinador || '') || !/^\/aws\/lambda\/[A-Za-z0-9_-]+$/.test(grupoHistorial || '')) {
    throw new Error('Configuración incompleta');
  }

  const url = new URL(salud);
  if (url.protocol !== 'https:' || !url.hostname.endsWith('.cloudfront.net') || url.port || url.username || url.password || url.pathname !== '/api/health' || url.search || url.hash) {
    throw new Error('Dirección de salud inválida');
  }

  return url.href;
}

const urlSalud = validarConfiguracion();
const ec2 = new EC2Client({ region });
const cognito = new CognitoIdentityProviderClient({ region });
const dynamodb = DynamoDBDocumentClient.from(new DynamoDBClient({ region }));
const coordinador = crearCoordinador({ cliente: dynamodb, tabla: tablaCoordinador });
const historial = crearHistorial({ cliente: new CloudWatchLogsClient({ region }), grupo: grupoHistorial });

async function leerEstadoSalud(url) {
  try {
    const respuesta = await fetch(url, { cache: 'no-store', redirect: 'error', signal: AbortSignal.timeout(5000) });

    if (!respuesta.ok || !(respuesta.headers.get('content-type') || '').includes('application/json')) {
      return null;
    }

    const lector = respuesta.body?.getReader();

    if (!lector) {
      return null;
    }

    const decodificador = new TextDecoder();
    let texto = '';
    let total = 0;

    while (true) {
      const bloque = await lector.read();

      if (bloque.done) {
        break;
      }

      total += bloque.value.byteLength;

      if (total > 8192) {
        await lector.cancel();
        return null;
      }

      texto += decodificador.decode(bloque.value, { stream: true });
    }
    texto += decodificador.decode();

    const cuerpo = JSON.parse(texto);
    if (!cuerpo || typeof cuerpo !== 'object') {
      return null;
    }

    return {
      disponible: cuerpo.estado === 'disponible',
      relojValido: respuesta.headers.get('x-trazalab-clock-valid'),
      inactividadNanos: respuesta.headers.get('x-trazalab-idle-nanos'),
      solicitudActiva: respuesta.headers.get('x-trazalab-request-active'),
      drenando: respuesta.headers.get('x-trazalab-draining'),
      umbralSegundos: respuesta.headers.get('x-trazalab-timeout-seconds')
    };
  } catch {
    return null;
  }
}

function convertirEnteroNoNegativo(valor) {
  if (!/^\d+$/.test(valor || '')) {
    return null;
  }

  const numero = Number(valor);
  return Number.isSafeInteger(numero) ? numero : null;
}

export const handler = crearControlador({
  configuracion: {
    instancia,
    grupo,
    cliente,
    cuenta,
    region,
    emisor: `https://cognito-idp.${region}.amazonaws.com/${pool}`,
    scope: 'trazalab/iniciar',
    salud: urlSalud
  },
  async consultarUsuario(username) {
    const usuario = await cognito.send(new AdminGetUserCommand({ UserPoolId: pool, Username: username }));
    const sub = usuario.UserAttributes?.find(atributo => atributo.Name === 'sub')?.Value;
    return { enabled: usuario.Enabled === true, sub };
  },
  async listarGrupos(username) {
    const grupos = [];
    let token;
    let paginas = 0;
    do {
      paginas += 1;

      if (paginas > 20) {
        throw new Error('Límite de grupos excedido');
      }

      const resultado = await cognito.send(new AdminListGroupsForUserCommand({ UserPoolId: pool, Username: username, NextToken: token, Limit: 60 }));
      grupos.push(...(resultado.Groups || []).map(item => item.GroupName));
      token = resultado.NextToken;

    } while (token);
    return grupos;
  },
  async consultarInstancia(id) {
    const resultado = await ec2.send(new DescribeInstancesCommand({ InstanceIds: [id] }));
    const instancias = (resultado.Reservations || []).flatMap(reserva => reserva.Instances || []);

    if (instancias.length !== 1 || instancias[0].InstanceId !== id) {
      throw new Error('Instancia no encontrada');
    }

    return instancias[0].State?.Name;
  },
  async iniciarInstancia(id) {
    await ec2.send(new StartInstancesCommand({ InstanceIds: [id] }));
  },
  async consultarDespliegue() {
    const respuesta = await dynamodb.send(new GetCommand({
      TableName: tablaCoordinador,
      Key: { pk: 'trazalab-ultimo-despliegue' },
      ConsistentRead: true
    }));

    const item = respuesta.Item;

    if (!item || typeof item.fecha !== 'string' || Number.isNaN(Date.parse(item.fecha)) || !/^v(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$/.test(item.version || '') || !/^[a-f0-9]{40}$/.test(item.commit || '') || !/^sha256:[a-f0-9]{64}$/.test(item.digest || '') || !/^[a-f0-9-]{36}$/.test(item.comandoSsm || '')) {
      return null;
    }

    return { fecha: item.fecha, version: item.version, commit: item.commit, digest: item.digest, comandoSsm: item.comandoSsm };
  },
  async consultarSalud(url) {
    return (await leerEstadoSalud(url))?.disponible === true;
  },
  async consultarTemporizador(url) {
    const direccion = new URL(url);
    direccion.pathname = '/api/temporizador';
    const estado = await leerEstadoSalud(direccion);

    if (!estado?.disponible || estado.relojValido !== 'true' || !['true', 'false'].includes(estado.solicitudActiva) || !['true', 'false'].includes(estado.drenando)) {
      return null;
    }

    const inactividadNanos = convertirEnteroNoNegativo(estado.inactividadNanos);
    const umbralSegundos = convertirEnteroNoNegativo(estado.umbralSegundos);

    if (inactividadNanos === null || umbralSegundos !== 1800) {
      return null;
    }

    return {
      segundosRestantes: Math.max(0, Math.ceil(umbralSegundos - inactividadNanos / 1000000000)),
      segundosInactivo: Math.floor(inactividadNanos / 1000000000),
      inactividadNanos,
      solicitudActiva: estado.solicitudActiva === 'true',
      drenando: estado.drenando === 'true',
      umbralSegundos
    };
  },
  coordinador,
  historial
});
