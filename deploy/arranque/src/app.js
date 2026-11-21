import { InMemoryWebStorage, UserManager, WebStorageStateStore } from 'oidc-client-ts';
import { mostrar } from './consola.js';
import { consultar, crearGestorSolicitudes } from './solicitudes.js';
import './estilos.css';

const ingresar = document.querySelector('#ingresar');
const encender = document.querySelector('#encender');
const verificar = document.querySelector('#verificar');
const abrir = document.querySelector('#abrir');
const salir = document.querySelector('#salir');
let usuario;
let temporizador;
let inicioEspera;
const solicitudes = crearGestorSolicitudes();

function direccionSegura(valor, dominio) {
  const url = new URL(valor);
  if (url.protocol !== 'https:' || !url.hostname.endsWith(dominio) || url.username || url.password || url.port || url.search || url.hash) {
    throw new Error('Dirección inválida');
  }
  return url;
}

async function cargarConfiguracion() {
  const respuesta = await fetch('/config.json', { cache: 'no-store' });
  if (!respuesta.ok || !respuesta.headers.get('content-type')?.includes('application/json')) {
    throw new Error('Configuración pendiente');
  }
  const config = await respuesta.json();
  if (!/^us-east-2_[A-Za-z0-9]+$/.test(config.userPoolId || '') || !/^[a-z0-9]+$/.test(config.clientId || '')) {
    throw new Error('Configuración inválida');
  }
  const api = direccionSegura(config.apiUrl, '.execute-api.us-east-2.amazonaws.com');
  const aplicacion = direccionSegura(config.aplicacionUrl, '.cloudfront.net');
  const cognito = direccionSegura(config.cognitoUrl, '.auth.us-east-2.amazoncognito.com');
  if (api.pathname !== '/' || cognito.pathname !== '/' || aplicacion.pathname !== '/usuarios/login') {
    throw new Error('Ruta de configuración inválida');
  }
  if (location.protocol !== 'https:') {
    throw new Error('Se requiere HTTPS');
  }
  return { ...config, api, aplicacion, cognito };
}

function estadoVisual(estado) {
  const textos = {
    detenida: 'AWS confirma que el servidor está detenido. Puedes solicitar el encendido.',
    iniciando: 'AWS informa que el servidor se está iniciando.',
    comprobando: 'El servidor está encendido. Esperando la respuesta HTTPS de TrazaLab y su base de datos.',
    disponible: 'La aplicación y la base de datos respondieron correctamente. TrazaLab está disponible.',
    deteniendo: 'AWS indica que el servidor se está deteniendo. Inténtalo de nuevo en unos momentos.',
    'no disponible': 'No se pudo confirmar el estado del servidor. Inténtalo de nuevo.'
  };
  const estadoReconocido = Object.hasOwn(textos, estado);
  mostrar(estadoReconocido ? textos[estado] : textos['no disponible'], estado === 'disponible' ? 'disponible' : !estadoReconocido || estado === 'no disponible' ? 'error' : 'conectado');
  encender.hidden = estado !== 'detenida';
  abrir.hidden = estado !== 'disponible';
}

function mostrarError(texto, error) {
  abrir.hidden = true;
  encender.hidden = true;
  if (error?.message === 'Sesión vencida') {
    usuario = undefined;
    ingresar.hidden = false;
    verificar.hidden = true;
    salir.hidden = false;
    mostrar('La sesión venció. Inicia sesión nuevamente.', 'error');
    return;
  }
  if (error?.message === 'Acceso no autorizado') {
    usuario = undefined;
    ingresar.hidden = false;
    verificar.hidden = true;
    mostrar('No se pudo autorizar el acceso. Vuelve a iniciar sesión o consulta al responsable de las cuentas.', 'error');
    return;
  }
  mostrar(texto, 'error');
}

async function iniciar() {
  const config = await cargarConfiguracion();
  const manager = new UserManager({
    authority: `https://cognito-idp.us-east-2.amazonaws.com/${config.userPoolId}`,
    client_id: config.clientId,
    redirect_uri: `${location.origin}${location.pathname}`,
    response_type: 'code',
    scope: 'openid trazalab/iniciar',
    automaticSilentRenew: false,
    userStore: new WebStorageStateStore({ store: new InMemoryWebStorage() }),
    stateStore: new WebStorageStateStore({ store: window.sessionStorage })
  });
  abrir.href = config.aplicacion.href;
  if (location.search) {
    const callback = location.href;
    history.replaceState(null, '', location.pathname);
    usuario = await manager.signinRedirectCallback(callback);
  }
  if (!usuario) {
    mostrar('Inicia sesión para continuar.');
    ingresar.hidden = false;
  } else {
    salir.hidden = false;
    verificar.hidden = false;
  }
  ingresar.addEventListener('click', async () => {
    ingresar.disabled = true;
    try {
      await manager.removeUser();
      await manager.signinRedirect();
    } catch {
      ingresar.disabled = false;
      mostrar('No se pudo abrir el inicio de sesión.');
    }
  });
  encender.addEventListener('click', async () => {
    encender.disabled = true;
    verificar.disabled = true;
    clearTimeout(temporizador);
    try {
      const respuesta = await solicitudes.ejecutar(() => consultar(config.api, '/iniciar', 'POST', usuario));
      if (!respuesta.vigente) {
        return;
      }
      if (respuesta.error) {
        mostrarError('No se pudo solicitar el encendido.', respuesta.error);
        return;
      }
      estadoVisual(respuesta.valor.estado);
      inicioEspera = Date.now();
      await actualizar();
    } finally {
      encender.disabled = false;
      verificar.disabled = false;
    }
  });
  verificar.addEventListener('click', async () => {
    verificar.disabled = true;
    try {
      inicioEspera = Date.now();
      await actualizar();
    } catch (error) {
      mostrarError('No se pudo comprobar la disponibilidad.', error);
    } finally {
      verificar.disabled = false;
    }
  });
  salir.addEventListener('click', async () => {
    clearTimeout(temporizador);
    solicitudes.invalidar();
    await manager.removeUser();
    usuario = undefined;
    const url = new URL('/logout', config.cognito);
    url.searchParams.set('client_id', config.clientId);
    url.searchParams.set('logout_uri', `${location.origin}${location.pathname}`);
    location.assign(url.href);
  });

  async function actualizar() {
    clearTimeout(temporizador);
    const respuesta = await solicitudes.ejecutar(() => consultar(config.api, '/estado', 'GET', usuario));
    if (!respuesta.vigente) {
      return;
    }
    if (respuesta.error) {
      mostrarError('No se pudo comprobar la disponibilidad.', respuesta.error);
      return;
    }
    const resultado = respuesta.valor;
    estadoVisual(resultado.estado);
    if (resultado.estado === 'iniciando' || resultado.estado === 'comprobando') {
      inicioEspera ||= Date.now();
      if (Date.now() - inicioEspera >= 300000) {
        mostrar('La comprobación tardó demasiado. Puedes actualizar el estado.');
      } else {
        temporizador = setTimeout(() => actualizar().catch((error) => mostrarError('No se pudo comprobar la disponibilidad.', error)), 5000);
      }
    } else {
      inicioEspera = undefined;
    }
  }

  if (usuario) {
    try {
      await actualizar();
    } catch (error) {
      mostrarError('La sesión inició, pero no se pudo consultar el estado. Puedes volver a intentarlo.', error);
    }
  }
}

iniciar().catch((error) => {
  if (['Configuración pendiente', 'Configuración inválida', 'Ruta de configuración inválida', 'Dirección inválida'].includes(error.message)) {
    mostrar('Configuración pendiente. El panel todavía no está conectado al servicio de inicio.', 'error');
  } else if (error.message === 'Se requiere HTTPS') {
    mostrar('Se requiere una conexión HTTPS para iniciar sesión.', 'error');
  } else {
    mostrar('No se pudo iniciar el panel. Comprueba la conexión o vuelve a intentarlo.', 'error');
  }
});
