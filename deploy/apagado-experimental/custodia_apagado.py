"""Modelo local de custodia. No realiza llamadas AWS ni libera bloqueos."""

from dataclasses import dataclass
from enum import Enum


class Estado(Enum):
    PENDIENTE = "pendiente"
    PERMISO_CONFIRMADO = "permiso_confirmado"
    PREPARADO = "preparado"
    SOLICITUD_EN_CURSO = "solicitud_en_curso"
    ESPERANDO_DETENCION = "esperando_detencion"
    DETENCION_CONFIRMADA = "detencion_confirmada"
    LIBERACION_EN_CURSO = "liberacion_en_curso"
    FINALIZADO = "finalizado"
    REVISION_MANUAL = "revision_manual"


class Evento(Enum):
    PERMISO_ADQUIRIDO = "permiso_adquirido"
    PREPARACION_CONFIRMADA = "preparacion_confirmada"
    SOLICITAR_PARADA = "solicitar_parada"
    PARADA_ACEPTADA = "parada_aceptada"
    SIGUE_DETENIENDO = "sigue_deteniendo"
    INSTANCIA_DETENIDA = "instancia_detenida"
    SOLICITAR_LIBERACION = "solicitar_liberacion"
    LIBERACION_CONFIRMADA = "liberacion_confirmada"
    RESULTADO_INCIERTO = "resultado_incierto"


@dataclass(frozen=True)
class CustodiaApagado:
    estado: Estado = Estado.PENDIENTE

    def avanzar(self, evento):
        if evento == Evento.RESULTADO_INCIERTO and self.estado not in {Estado.FINALIZADO, Estado.REVISION_MANUAL}:
            return CustodiaApagado(Estado.REVISION_MANUAL)
        transiciones = {
            (Estado.PENDIENTE, Evento.PERMISO_ADQUIRIDO): Estado.PERMISO_CONFIRMADO,
            (Estado.PERMISO_CONFIRMADO, Evento.PREPARACION_CONFIRMADA): Estado.PREPARADO,
            (Estado.PREPARADO, Evento.SOLICITAR_PARADA): Estado.SOLICITUD_EN_CURSO,
            (Estado.SOLICITUD_EN_CURSO, Evento.PARADA_ACEPTADA): Estado.ESPERANDO_DETENCION,
            (Estado.ESPERANDO_DETENCION, Evento.SIGUE_DETENIENDO): Estado.ESPERANDO_DETENCION,
            (Estado.ESPERANDO_DETENCION, Evento.INSTANCIA_DETENIDA): Estado.DETENCION_CONFIRMADA,
            (Estado.DETENCION_CONFIRMADA, Evento.SOLICITAR_LIBERACION): Estado.LIBERACION_EN_CURSO,
            (Estado.LIBERACION_EN_CURSO, Evento.LIBERACION_CONFIRMADA): Estado.FINALIZADO,
        }
        siguiente = transiciones.get((self.estado, evento))
        if siguiente is None:
            raise ValueError("Transición no permitida; conservar el bloqueo y revisar.")
        return CustodiaApagado(siguiente)
