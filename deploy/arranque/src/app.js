import { InMemoryWebStorage, UserManager, WebStorageStateStore } from 'oidc-client-ts';
import { mostrar, mostrarDespliegue, mostrarErrorHistorial, mostrarHistorial, mostrarTemporizador, ocultarHistorial, ocultarTemporizador } from './consola.js';
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
let estadoServidor;
let temporizadorSincronizacion;
let intervaloCuenta;
let lecturaTemporizador;
let sincronizadoEn;
let ultimoTiempoInactivo;
let mensajeActividadHasta = 0;
const solicitudes = crearGestorSolicitudes();
const solicitudesHistorial = crearGestorSolicitudes();
const solicitudesTemporizador = crearGestorSolicitudes();

function detenerTemporizador() {
  clearTimeout(temporizadorSincronizacion);
  temporizadorSincronizacion = undefined;
  clearInterval(intervaloCuenta);
  intervaloCuenta = undefined;
  solicitudesTemporizador.invalidar();
  lecturaTemporizador = undefined;
  sincronizadoEn = undefined;
  ultimoTiempoInactivo = undefined;
  mensajeActividadHasta = 0;
  ocultarTemporizador();
}

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
  estadoServidor = estado;
  const textos = {
    detenida: 'AWS confirma que el servidor está detenido. Puedes solicitar el encendido.',
    iniciando: 'AWS informa que el servidor se está iniciando.',
    comprobando: 'El servidor está encendido. Esperando la respuesta HTTPS de TrazaLab y su base de datos.',
    disponible: 'La aplicación y la base de datos respondieron correctamente. TrazaLab está disponible.',
    deteniendo: 'AWS indica que el servidor se está deteniendo. Inténtalo de nuevo en unos momentos.',
    'no disponible': 'No se pudo confirmar el estado del servidor. Inténtalo de nuevo.'
  };
  if (estado !== 'disponible') {
    detenerTemporizador();
  }
  const estadoReconocido = Object.hasOwn(textos, estado);
  mostrar(estadoReconocido ? textos[estado] : textos['no disponible'], estado === 'disponible' ? 'disponible' : !estadoReconocido || estado === 'no disponible' ? 'error' : 'conectado');
  encender.hidden = estado !== 'detenida';
  abrir.hidden = estado !== 'disponible';
}

function mostrarError(texto, error) {
  abrir.hidden = true;
  encender.hidden = true;
  if (error?.message === 'Sesión vencida') {
    detenerTemporizador();
    usuario = undefined;
    solicitudes.invalidar();
    solicitudesHistorial.invalidar();
    ocultarHistorial();
    ingresar.hidden = false;
    verificar.hidden = true;
    salir.hidden = false;
    mostrar('La sesión venció. Inicia sesión nuevamente.', 'error');
    return;
  }
  if (error?.message === 'Acceso no autorizado') {
    detenerTemporizador();
    usuario = undefined;
    solicitudes.invalidar();
    solicitudesHistorial.invalidar();
    ocultarHistorial();
    ingresar.hidden = false;
    verificar.hidden = true;
    mostrar('No se pudo autorizar el acceso. Vuelve a iniciar sesión o consulta al responsable de las cuentas.', 'error');
    return;
  }
  mostrar(texto, 'error');
}

async function iniciar() {
  const config = await cargarConfiguracion();
  let renovacionSesion;
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
    usuario = undefined;
    detenerTemporizador();
    solicitudes.invalidar();
    solicitudesHistorial.invalidar();
    ocultarHistorial();
    await manager.removeUser();
    const url = new URL('/logout', config.cognito);
    url.searchParams.set('client_id', config.clientId);
    url.searchParams.set('logout_uri', `${location.origin}${location.pathname}`);
    location.assign(url.href);
  });

  async function actualizarHistorial() {
    const respuesta = await solicitudesHistorial.ejecutar(() => consultar(config.api, '/historial', 'GET', usuario));
    if (!respuesta.vigente) {
      return;
    }
    if (['Sesión vencida', 'Acceso no autorizado'].includes(respuesta.error?.message)) {
      mostrarError('No se pudo cargar el historial.', respuesta.error);
      return;
    }
    if (respuesta.error || !Array.isArray(respuesta.valor?.eventos)) {
      mostrarErrorHistorial();
      return;
    }
    mostrarHistorial(respuesta.valor.eventos);
    mostrarDespliegue(respuesta.valor.despliegue);
  }

  function pintarTemporizador() {
    if (!lecturaTemporizador) {
      return;
    }

    let segundosRestantes = null;
    let mensaje = 'La actividad autenticada en TrazaLab reinicia el contador.';
    let estado = 'contando';

    if (lecturaTemporizador.drenando) {
      segundosRestantes = 0;
      mensaje = 'Se alcanzó el umbral y el servidor está preparando el apagado seguro.';
      estado = 'preparando';
    } else if (lecturaTemporizador.solicitudActiva) {
      mensaje = 'Hay actividad en curso; el apagado espera a que termine.';
      estado = 'actividad';
    } else {
      const transcurrido = Math.floor((performance.now() - sincronizadoEn) / 1000);
      segundosRestantes = Math.max(0, lecturaTemporizador.segundosRestantes - transcurrido);

      if (segundosRestantes === 0) {
        mensaje = 'Se alcanzó el umbral. El servidor revisa el apagado cada cinco minutos.';
        estado = 'vencido';
      } else if (performance.now() < mensajeActividadHasta) {
        mensaje = 'TrazaLab reinició el contador de inactividad.';
      }
    }

    mostrarTemporizador(segundosRestantes, mensaje, estado);
  }

  function programarSincronizacionTemporizador(demora = 60000) {
    clearTimeout(temporizadorSincronizacion);

    if (!usuario || estadoServidor !== 'disponible' || document.visibilityState === 'hidden') {
      return;
    }

    temporizadorSincronizacion = setTimeout(() => sincronizarTemporizador().catch((error) => mostrarError('No se pudo sincronizar el temporizador.', error)), demora);
  }

  async function sincronizarTemporizador() {
    clearTimeout(temporizadorSincronizacion);

    if (!usuario || estadoServidor !== 'disponible' || document.visibilityState === 'hidden') {
      return;
    }

    const respuesta = await solicitudesTemporizador.ejecutar(async () => {
      if (usuario.expired && usuario.refresh_token) {
        const sesion = usuario;
        renovacionSesion ??= manager.signinSilent({ silentRequestTimeoutInSeconds: 10 })
          .catch(() => {
            throw new Error('Sesión vencida');
          })
          .finally(() => {
            renovacionSesion = undefined;
          });
        const renovada = await renovacionSesion;
        if (usuario === sesion) {
          usuario = renovada;
        }
        if (!usuario || estadoServidor !== 'disponible' || document.visibilityState === 'hidden') {
          return;
        }
      }
      return consultar(config.api, '/temporizador', 'GET', usuario);
    });

    if (!respuesta.vigente) {
      return;
    }

    if (['Sesión vencida', 'Acceso no autorizado'].includes(respuesta.error?.message)) {
      mostrarError('No se pudo sincronizar el temporizador.', respuesta.error);
      return;
    }

    const datos = respuesta.valor?.temporizador;
    const lecturaValida = datos && datos.umbralSegundos === 1800
      && Number.isSafeInteger(datos.segundosRestantes) && datos.segundosRestantes >= 0 && datos.segundosRestantes <= datos.umbralSegundos
      && Number.isSafeInteger(datos.inactividadNanos) && datos.inactividadNanos >= 0
      && typeof datos.solicitudActiva === 'boolean' && typeof datos.drenando === 'boolean';

    if (!lecturaValida) {
      clearInterval(intervaloCuenta);
      intervaloCuenta = undefined;
      mostrarTemporizador(null, 'No se pudo sincronizar el reloj de actividad del servidor.', 'error');
      programarSincronizacionTemporizador();
      return;
    }

    const ahora = performance.now();
    if (ultimoTiempoInactivo !== undefined && !datos.solicitudActiva && datos.inactividadNanos < ultimoTiempoInactivo) {
      mensajeActividadHasta = ahora + 5000;
    }

    lecturaTemporizador = datos;
    sincronizadoEn = ahora;
    ultimoTiempoInactivo = datos.inactividadNanos;
    intervaloCuenta ??= setInterval(pintarTemporizador, 1000);
    pintarTemporizador();
    programarSincronizacionTemporizador(datos.solicitudActiva ? 10000 : 60000);
  }

  document.addEventListener('visibilitychange', () => {
    if (document.visibilityState === 'hidden') {
      clearTimeout(temporizadorSincronizacion);
      temporizadorSincronizacion = undefined;
      clearInterval(intervaloCuenta);
      intervaloCuenta = undefined;
      solicitudesTemporizador.invalidar();
    } else {
      sincronizarTemporizador().catch((error) => mostrarError('No se pudo sincronizar el temporizador.', error));
    }
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
    await actualizarHistorial();
    if (!usuario) {
      return;
    }
    if (resultado.estado === 'disponible') {
      await sincronizarTemporizador();
    }
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
