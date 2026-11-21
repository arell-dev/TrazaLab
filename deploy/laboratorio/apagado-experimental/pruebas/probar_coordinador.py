import importlib.util
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
from threading import Barrier, Lock
from types import SimpleNamespace
import unittest


spec = importlib.util.spec_from_file_location("coordinador_operaciones", Path(__file__).resolve().parent.parent / "coordinador_operaciones.py")
modulo = importlib.util.module_from_spec(spec)
import sys
sys.modules[spec.name] = modulo
spec.loader.exec_module(modulo)


class Condicion:
    def __init__(self, funcion):
        self.funcion = funcion

    def __and__(self, otra):
        return Condicion(lambda item: self.funcion(item) and otra.funcion(item))


class Atributo:
    def __init__(self, nombre):
        self.nombre = nombre

    def not_exists(self):
        return Condicion(lambda item: self.nombre not in item)

    def eq(self, valor):
        return Condicion(lambda item: item.get(self.nombre) == valor)


class CondicionFallida(Exception):
    pass


class TablaSimulada:
    def __init__(self):
        self.cerrojo = Lock()
        self.item = {}
        self.fallar_tras_guardar = False
        excepciones = SimpleNamespace(ConditionalCheckFailedException=CondicionFallida)
        self.meta = SimpleNamespace(client=SimpleNamespace(exceptions=excepciones))

    def put_item(self, Item, ConditionExpression):
        with self.cerrojo:
            if not ConditionExpression.funcion(self.item):
                raise CondicionFallida()
            self.item = Item.copy()
            if self.fallar_tras_guardar:
                raise TimeoutError("Resultado incierto")

    def delete_item(self, Key, ConditionExpression):
        with self.cerrojo:
            if not ConditionExpression.funcion(self.item):
                raise CondicionFallida()
            self.item = {}


class PruebasCoordinador(unittest.TestCase):
    def setUp(self):
        self.tabla = TablaSimulada()
        self.coordinador = modulo.CoordinadorOperaciones(self.tabla, Atributo)

    def test_solo_un_concurrente_adquiere(self):
        barrera = Barrier(30)

        def intentar(indice):
            barrera.wait(timeout=5)
            return self.coordinador.adquirir(("arranque", "despliegue", "apagado")[indice % 3])

        with ThreadPoolExecutor(max_workers=30) as ejecutor:
            resultados = list(ejecutor.map(intentar, range(30)))
        self.assertEqual(1, sum(resultado is not None for resultado in resultados))

    def test_no_libera_un_permiso_ajeno_o_antiguo(self):
        anterior = self.coordinador.adquirir("apagado")
        self.coordinador.liberar(anterior)
        actual = self.coordinador.adquirir("arranque")
        with self.assertRaises(CondicionFallida):
            self.coordinador.liberar(anterior)
        self.assertIsNone(self.coordinador.adquirir("despliegue"))
        self.coordinador.liberar(actual)
        self.assertIsNotNone(self.coordinador.adquirir("despliegue"))

    def test_respuesta_perdida_no_concede_permiso_y_no_libera(self):
        self.tabla.fallar_tras_guardar = True
        with self.assertRaises(TimeoutError):
            self.coordinador.adquirir("apagado")
        self.tabla.fallar_tras_guardar = False
        self.assertIsNone(self.coordinador.adquirir("arranque"))

    def test_operacion_desconocida_no_se_adquiere(self):
        with self.assertRaises(ValueError):
            self.coordinador.adquirir("otra")
        self.assertEqual({}, self.tabla.item)


if __name__ == "__main__":
    unittest.main()
