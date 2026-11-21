import { EC2Client, DescribeInstancesCommand, StartInstancesCommand } from '@aws-sdk/client-ec2';
import { CognitoIdentityProviderClient, AdminGetUserCommand, AdminListGroupsForUserCommand } from '@aws-sdk/client-cognito-identity-provider';
import { crearControlador } from './controlador.js';

const instancia = process.env.INSTANCIA_ID;
const grupo = process.env.GRUPO_OPERADORES;
const pool = process.env.USER_POOL_ID;
const cliente = process.env.CLIENTE_ID;
const region = process.env.AWS_REGION;
const salud = process.env.SALUD_URL;

function validarConfiguracion() {
  if (!/^i-[a-f0-9]{8,17}$/.test(instancia || '') || !/^[\w-]+$/.test(grupo || '') || !/^[\w-]+$/.test(pool || '') || !/^[a-z0-9]+$/.test(cliente || '')) {
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

export const handler = crearControlador({
  configuracion: {
    instancia,
    grupo,
    cliente,
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
  async consultarSalud(url) {
    try {
      const respuesta = await fetch(url, { cache: 'no-store', redirect: 'error', signal: AbortSignal.timeout(5000) });

      if (!respuesta.ok || !(respuesta.headers.get('content-type') || '').includes('application/json')) {
        return false;
      }

      const lector = respuesta.body?.getReader();

      if (!lector) {
        return false;
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
          return false;
        }

        texto += decodificador.decode(bloque.value, { stream: true });
      }
      texto += decodificador.decode();
      return JSON.parse(texto).estado === 'disponible';
    } catch {
      return false;
    }
  }
});
