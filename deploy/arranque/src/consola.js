const mensaje = document.querySelector('#mensaje');
const eventos = document.querySelector('#eventos');
const indicador = document.querySelector('#indicador');
const nota = document.querySelector('#nota-historial');
const despliegue = document.querySelector('#despliegue');
const panelHistorial = document.querySelector('#panel-historial');
const diasHistorial = document.querySelector('#dias');
const tituloEventos = document.querySelector('#titulo-eventos');
const abrirHistorial = document.querySelector('#abrir-historial');
const temporizador = document.querySelector('#temporizador');
const cuentaRegresiva = document.querySelector('#cuenta-regresiva');
const cuentaRegresivaPendiente = document.querySelector('#cuenta-regresiva-pendiente');
const notaTemporizador = document.querySelector('#nota-temporizador');
const formatoDuracion = typeof Intl.DurationFormat === 'function'
  ? new Intl.DurationFormat('es-PE', { style: 'digital', minutes: '2-digit', seconds: '2-digit' })
  : undefined;
let diaSeleccionado;

function cerrarMenuMovil() {
  panelHistorial.classList.remove('abierto');
  abrirHistorial.setAttribute('aria-expanded', 'false');
}

abrirHistorial.onclick = () => {
  const abierto = panelHistorial.classList.toggle('abierto');
  abrirHistorial.setAttribute('aria-expanded', String(abierto));
};

document.addEventListener('keydown', evento => {
  if (evento.key === 'Escape' && panelHistorial.classList.contains('abierto')) {
    cerrarMenuMovil();
    abrirHistorial.focus();
  }
});

export function mostrarHistorial(registros) {
  const dias = new Map();
  const formato = new Intl.DateTimeFormat('es-PE', { timeStyle: 'medium' });
  const formatoDia = new Intl.DateTimeFormat('es-PE', { dateStyle: 'full' });

  for (const registro of registros) {
    if (!registro || typeof registro.fecha !== 'string' || typeof registro.mensaje !== 'string') {
      continue;
    }
    const fecha = new Date(registro.fecha);
    if (Number.isNaN(fecha.getTime())) {
      continue;
    }
    const dia = formatoDia.format(fecha);
    if (!dias.has(dia)) {
      dias.set(dia, []);
    }
    dias.get(dia).push({ fecha, texto: registro.mensaje, fechaOriginal: registro.fecha });
  }
  if (!dias.has(diaSeleccionado)) {
    diaSeleccionado = dias.keys().next().value;
  }
  panelHistorial.hidden = false;
  abrirHistorial.hidden = false;
  const botones = [];
  function seleccionarDia(dia) {
    diaSeleccionado = dia;
    tituloEventos.textContent = dia;
    for (const boton of botones) {
      boton.setAttribute('aria-current', boton.dataset.dia === dia ? 'date' : 'false');
    }
    const items = dias.get(dia).map(registro => {
      const item = document.createElement('li');
      const hora = document.createElement('time');
      hora.className = 'evento-hora';
      hora.dateTime = registro.fechaOriginal;
      hora.textContent = formato.format(registro.fecha);
      item.append(hora, document.createTextNode(registro.texto));
      return item;
    });
    eventos.replaceChildren(...items);
  }
  for (const dia of dias.keys()) {
    const boton = document.createElement('button');
    boton.type = 'button';
    boton.dataset.dia = dia;
    boton.textContent = dia;
    boton.addEventListener('click', () => {
      seleccionarDia(dia);
      cerrarMenuMovil();
    });
    botones.push(boton);
  }
  diasHistorial.replaceChildren(...botones);
  if (diaSeleccionado) {
    seleccionarDia(diaSeleccionado);
  } else {
    eventos.replaceChildren();
    tituloEventos.textContent = 'Historial de operaciones';
  }
  nota.textContent = dias.size ? 'Se muestran los eventos del día seleccionado.' : 'Aún no hay eventos registrados.';
}

export function mostrarDespliegue(datos) {
  despliegue.hidden = false;
  if (!datos || !/^v\d+\.\d+\.\d+$/.test(datos.version || '') || !/^[a-f0-9]{40}$/.test(datos.commit || '') || !/^sha256:[a-f0-9]{64}$/.test(datos.digest || '') || Number.isNaN(Date.parse(datos.fecha))) {
    despliegue.textContent = 'Último despliegue verificado: información no disponible.';
    return;
  }
  const fecha = new Intl.DateTimeFormat('es-PE', { dateStyle: 'short', timeStyle: 'medium' }).format(new Date(datos.fecha));
  const titulo = document.createElement('h2');
  const lista = document.createElement('dl');
  titulo.textContent = 'Último despliegue verificado';
  for (const [nombre, valor] of [['Versión', datos.version], ['Fecha', fecha], ['Commit', datos.commit], ['Digest de imagen', datos.digest]]) {
    const campo = document.createElement('div');
    const etiqueta = document.createElement('dt');
    const contenido = document.createElement('dd');
    etiqueta.textContent = nombre;
    contenido.textContent = valor;
    campo.append(etiqueta, contenido);
    lista.append(campo);
  }
  despliegue.replaceChildren(titulo, lista);
}

export function mostrarErrorHistorial() {
  nota.textContent = 'No se pudo cargar el historial de operaciones. Puedes actualizar el estado.';
}

export function mostrarTemporizador(segundosRestantes, mensaje, estado = 'contando') {
  temporizador.hidden = false;
  temporizador.dataset.estado = estado;
  if (notaTemporizador.textContent !== mensaje) {
    notaTemporizador.textContent = mensaje;
  }

  const tieneLectura = Number.isSafeInteger(segundosRestantes) && segundosRestantes >= 0;
  cuentaRegresiva.hidden = !tieneLectura;
  cuentaRegresivaPendiente.hidden = tieneLectura;
  if (!tieneLectura) {
    return;
  }

  const minutos = Math.floor(segundosRestantes / 60);
  const segundos = segundosRestantes % 60;
  cuentaRegresiva.dateTime = `PT${minutos}M${segundos}S`;
  if (formatoDuracion) {
    const partes = formatoDuracion.formatToParts({ minutes: minutos, seconds: segundos });
    const minutosFormateados = partes.find((parte) => parte.unit === 'minute')?.value;
    const segundosFormateados = partes.find((parte) => parte.unit === 'second')?.value;
    cuentaRegresiva.textContent = `${minutosFormateados}:${segundosFormateados}`;
    return;
  }

  cuentaRegresiva.textContent = `${String(minutos).padStart(2, '0')}:${String(segundos).padStart(2, '0')}`;
}

export function ocultarTemporizador() {
  temporizador.hidden = true;
  temporizador.removeAttribute('data-estado');
  cuentaRegresiva.hidden = true;
  cuentaRegresivaPendiente.hidden = false;
  notaTemporizador.textContent = 'Se reinicia con actividad autenticada en TrazaLab.';
}

export function ocultarHistorial() {
  eventos.replaceChildren();
  diasHistorial.replaceChildren();
  panelHistorial.hidden = true;
  abrirHistorial.hidden = true;
  cerrarMenuMovil();
  diaSeleccionado = undefined;
  tituloEventos.textContent = 'Historial de operaciones';
  despliegue.hidden = true;
  despliegue.textContent = '';
  nota.textContent = 'Inicia sesión para consultar el historial de operaciones.';
}

export function mostrar(texto, estado = 'espera') {
  if (mensaje.textContent === texto) {
    return;
  }
  mensaje.textContent = texto;
  indicador.dataset.estado = estado;
  indicador.textContent = estado === 'disponible' ? 'Disponible' : estado === 'error' ? 'Atención' : estado === 'conectado' ? 'Conectado' : 'En espera';
}
