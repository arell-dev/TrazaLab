const mensaje = document.querySelector('#mensaje');
const eventos = document.querySelector('#eventos');
const indicador = document.querySelector('#indicador');
let horaMensaje = new Date();

export function mostrar(texto, estado = 'espera') {
  if (mensaje.textContent === texto) {
    return;
  }
  if (mensaje.textContent && mensaje.textContent !== 'Comprobando configuración…') {
    const item = document.createElement('li');
    const hora = document.createElement('span');

    hora.className = 'evento-hora';
    hora.textContent = new Intl.DateTimeFormat('es-PE', { hour: '2-digit', minute: '2-digit', second: '2-digit' }).format(horaMensaje);
    item.append(hora, document.createTextNode(` ${mensaje.textContent}`));
    eventos.append(item);

    if (eventos.children.length > 8) {
      eventos.firstElementChild.remove();
    }
  }
  mensaje.textContent = texto;
  horaMensaje = new Date();
  indicador.dataset.estado = estado;
  indicador.textContent = estado === 'disponible' ? 'Disponible' : estado === 'error' ? 'Atención' : estado === 'conectado' ? 'Conectado' : 'En espera';
}
