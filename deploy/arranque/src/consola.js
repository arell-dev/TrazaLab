const mensaje = document.querySelector('#mensaje');
const eventos = document.querySelector('#eventos');
const indicador = document.querySelector('#indicador');
const nota = document.querySelector('#nota-historial');
const despliegue = document.querySelector('#despliegue');
const panelHistorial = document.querySelector('#panel-historial');
const diasHistorial = document.querySelector('#dias');
const tituloEventos = document.querySelector('#titulo-eventos');
let diaSeleccionado;

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
    boton.addEventListener('click', () => seleccionarDia(dia));
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

export function ocultarHistorial() {
  eventos.replaceChildren();
  diasHistorial.replaceChildren();
  panelHistorial.hidden = true;
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
