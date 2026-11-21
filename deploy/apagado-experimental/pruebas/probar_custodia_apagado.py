import sys
from pathlib import Path
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from custodia_apagado import CustodiaApagado, Estado, Evento


class PruebasCustodia(unittest.TestCase):
    def estado_en(self, *eventos):
        custodia = CustodiaApagado()
        for evento in eventos:
            custodia = custodia.avanzar(evento)
        return custodia

    def test_exige_detencion_confirmada_antes_de_liberar(self):
        custodia = self.estado_en(Evento.PERMISO_ADQUIRIDO, Evento.PREPARACION_CONFIRMADA, Evento.SOLICITAR_PARADA, Evento.PARADA_ACEPTADA)
        with self.assertRaises(ValueError):
            custodia.avanzar(Evento.SOLICITAR_LIBERACION)
        custodia = custodia.avanzar(Evento.SIGUE_DETENIENDO).avanzar(Evento.INSTANCIA_DETENIDA)
        custodia = custodia.avanzar(Evento.SOLICITAR_LIBERACION).avanzar(Evento.LIBERACION_CONFIRMADA)
        self.assertEqual(Estado.FINALIZADO, custodia.estado)

    def test_resultado_incierto_cierra_toda_transicion_automatica(self):
        prefijos = [
            (),
            (Evento.PERMISO_ADQUIRIDO,),
            (Evento.PERMISO_ADQUIRIDO, Evento.PREPARACION_CONFIRMADA, Evento.SOLICITAR_PARADA),
            (Evento.PERMISO_ADQUIRIDO, Evento.PREPARACION_CONFIRMADA, Evento.SOLICITAR_PARADA, Evento.PARADA_ACEPTADA),
            (Evento.PERMISO_ADQUIRIDO, Evento.PREPARACION_CONFIRMADA, Evento.SOLICITAR_PARADA, Evento.PARADA_ACEPTADA, Evento.INSTANCIA_DETENIDA, Evento.SOLICITAR_LIBERACION),
        ]
        for prefijo in prefijos:
            with self.subTest(prefijo=prefijo):
                custodia = self.estado_en(*prefijo).avanzar(Evento.RESULTADO_INCIERTO)
                self.assertEqual(Estado.REVISION_MANUAL, custodia.estado)
                with self.assertRaises(ValueError):
                    custodia.avanzar(Evento.SOLICITAR_LIBERACION)

    def test_observacion_stopped_sin_parada_aceptada_no_libera(self):
        custodia = self.estado_en(Evento.PERMISO_ADQUIRIDO, Evento.PREPARACION_CONFIRMADA, Evento.SOLICITAR_PARADA)
        with self.assertRaises(ValueError):
            custodia.avanzar(Evento.INSTANCIA_DETENIDA)


if __name__ == "__main__":
    unittest.main()
