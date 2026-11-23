import { FilterLogEventsCommand } from '@aws-sdk/client-cloudwatch-logs';

const MARCADOR = 'TRAZALAB_EVENTO ';
const diaLima = new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Lima', year: 'numeric', month: '2-digit', day: '2-digit' });
const MENSAJES = {
  solicitud_aceptada: 'AWS aceptó la solicitud de encendido de la instancia.',
  solicitud_incierta: 'No se pudo confirmar el resultado de la solicitud de encendido.',
  ec2_pending: 'AWS notificó que la instancia empezó a iniciarse.',
  ec2_running: 'AWS notificó que la instancia está encendida.',
  ec2_stopping: 'AWS notificó que la instancia empezó a detenerse.',
  ec2_stopped: 'AWS notificó que la instancia se detuvo.',
  salud_pendiente: 'La consulta HTTPS todavía no confirma la aplicación y la base de datos.',
  salud_disponible: 'La consulta HTTPS confirmó la aplicación y la base de datos disponibles.',
  despliegue_verificado: 'Último despliegue verificado mediante SSM.'
};

export function crearHistorial({ cliente, grupo, reloj = () => new Date(), escribir = console.log }) {
  function registrar(tipo, fecha, identificador) {
    if (!Object.hasOwn(MENSAJES, tipo)) {
      return;
    }

    const fechaEvento = typeof fecha === 'string' && !Number.isNaN(Date.parse(fecha)) ? new Date(fecha).toISOString() : reloj().toISOString();

    const evento = { tipo, fecha: fechaEvento };
    if (typeof identificador === 'string' && /^[a-f0-9-]{36}$/.test(identificador)) {
      evento.identificador = identificador;
    }

    escribir(`${MARCADOR}${JSON.stringify(evento)}`);
  }

  async function consultar() {
    const eventos = [];
    const identificadores = new Set();
    let siguiente;
    let paginas = 0;

    do {
      paginas += 1;

      if (paginas > 25) {
        throw new Error('El historial excede el límite de consulta.');
      }

      const respuesta = await cliente.send(new FilterLogEventsCommand({
        logGroupName: grupo,
        filterPattern: '"TRAZALAB_EVENTO"',
        startTime: reloj().getTime() - 30 * 24 * 60 * 60 * 1000,
        startFromHead: false,
        limit: 200,
        nextToken: siguiente
      }));

      for (const evento of respuesta.events || []) {
        const texto = evento.message || '';
        const inicio = texto.indexOf(MARCADOR);
        if (inicio < 0) {
          continue;
        }
        try {
          const dato = JSON.parse(texto.slice(inicio + MARCADOR.length).trim());
          if (Object.hasOwn(MENSAJES, dato.tipo) && typeof dato.fecha === 'string' && !Number.isNaN(Date.parse(dato.fecha))) {
            if (dato.identificador && identificadores.has(dato.identificador)) {
              continue;
            }
            if (typeof dato.identificador === 'string' && /^[a-f0-9-]{36}$/.test(dato.identificador)) {
              identificadores.add(dato.identificador);
            }
            const entrada = { fecha: dato.fecha, tipo: dato.tipo, mensaje: MENSAJES[dato.tipo] };
            if (dato.tipo === 'despliegue_verificado') {
              if (!/^v(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$/.test(dato.version || '') || !/^[a-f0-9]{40}$/.test(dato.commit || '') || !/^sha256:[a-f0-9]{64}$/.test(dato.digest || '')) {
                continue;
              }
              entrada.version = dato.version;
              entrada.commit = dato.commit;
              entrada.digest = dato.digest;
            }
            const anterior = eventos.at(-1);
            const mismaSaludDelDia = entrada.tipo.startsWith('salud_') && anterior?.tipo === entrada.tipo && diaLima.format(new Date(anterior.fecha)) === diaLima.format(new Date(entrada.fecha));
            if (!mismaSaludDelDia) {
              eventos.push(entrada);
            }
          }
        } catch {
          continue;
        }
      }
      siguiente = respuesta.nextToken;
    } while (siguiente && eventos.length < 1000);

    eventos.sort((primero, segundo) => Date.parse(segundo.fecha) - Date.parse(primero.fecha));
    return eventos.slice(0, 1000);
  }

  return { registrar, consultar };
}
