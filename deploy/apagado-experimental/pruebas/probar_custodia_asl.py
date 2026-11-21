import json
from pathlib import Path
import unittest


MAQUINA = json.loads((Path(__file__).resolve().parent.parent / "custodia-apagado.asl.json").read_text(encoding="utf-8"))
ESTADOS = MAQUINA["States"]


class PruebasDefinicion(unittest.TestCase):
    def test_referencias_y_errores_apuntan_a_estados_validos(self):
        self.assertIn(MAQUINA["StartAt"], ESTADOS)
        for nombre, estado in ESTADOS.items():
            with self.subTest(estado=nombre):
                destinos = [estado[campo] for campo in ("Next", "Default") if campo in estado]
                destinos += [eleccion["Next"] for eleccion in estado.get("Choices", [])]
                destinos += [error["Next"] for error in estado.get("Catch", [])]
                self.assertTrue(all(destino in ESTADOS for destino in destinos))
                if estado["Type"] == "Task":
                    self.assertEqual([{"ErrorEquals": ["States.ALL"], "Next": "RevisionManual"}], estado["Catch"])
                    self.assertNotIn("Retry", estado)

    def test_liberacion_solo_es_alcanzable_por_stopped(self):
        entradas = []
        for nombre, estado in ESTADOS.items():
            for eleccion in estado.get("Choices", []):
                if eleccion["Next"] == "LiberarPermiso":
                    entradas.append((nombre, eleccion["Condition"]))
            self.assertNotEqual("LiberarPermiso", estado.get("Next"))
            self.assertNotEqual("LiberarPermiso", estado.get("Default"))
        self.assertEqual(1, len(entradas))
        self.assertEqual("ComprobarEstado", entradas[0][0])
        self.assertIn("'stopped'", entradas[0][1])
        self.assertIn("REEMPLAZAR_INSTANCIA", entradas[0][1])

    def test_condiciones_de_bloqueo_y_rutas_de_espera(self):
        adquirir = ESTADOS["AdquirirPermiso"]["Arguments"]
        liberar = ESTADOS["LiberarPermiso"]["Arguments"]
        self.assertEqual("attribute_not_exists(pk)", adquirir["ConditionExpression"])
        self.assertIn(":propietario", liberar["ConditionExpression"])
        self.assertIn(":operacion", liberar["ConditionExpression"])
        self.assertEqual(adquirir["Item"]["propietario"], liberar["ExpressionAttributeValues"][":propietario"])
        self.assertEqual(adquirir["Item"]["operacion"], liberar["ExpressionAttributeValues"][":operacion"])
        self.assertEqual("Esperar", ESTADOS["ComprobarEstado"]["Choices"][1]["Next"])
        self.assertEqual("RevisionManual", ESTADOS["ComprobarEstado"]["Default"])
        self.assertEqual("RevisionManual", ESTADOS["ComprobarSolicitud"]["Default"])
        self.assertEqual("RevisionManual", ESTADOS["ComprobarPreparacion"]["Default"])
        self.assertEqual("Fail", ESTADOS["RevisionManual"]["Type"])
        self.assertEqual(1800, MAQUINA["TimeoutSeconds"])


if __name__ == "__main__":
    unittest.main()
