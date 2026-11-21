import importlib.util
import contextlib
import io
import json
import os
import unittest
from pathlib import Path
from unittest.mock import patch


RUTA = Path(__file__).resolve().parent / "ejecutar.py"
ESPECIFICACION = importlib.util.spec_from_file_location("ensayo_aislado", RUTA)
ENSAYO = importlib.util.module_from_spec(ESPECIFICACION)
ESPECIFICACION.loader.exec_module(ENSAYO)
CLIENTE_SPEC = importlib.util.spec_from_file_location("cliente_aislado", RUTA.parent / "cliente.py")
CLIENTE = importlib.util.module_from_spec(CLIENTE_SPEC)
with patch.dict(os.environ, {"ENSAYO_APP": "app-prueba", "ENSAYO_CLAVE": "clave-sintetica"}):
    CLIENTE_SPEC.loader.exec_module(CLIENTE)


class SeguridadEnsayoTest(unittest.TestCase):
    def test_limpia_solo_recursos_registrados_en_orden_inverso(self):
        acciones = []
        with patch.object(ENSAYO, "RECURSOS", [("image", "imagen-prueba"), ("network", "red-prueba"), ("container", "contenedor-prueba")]), patch.object(ENSAYO, "docker", side_effect=lambda *args: acciones.append(args)):
            ENSAYO.limpiar()
        self.assertEqual(acciones, [("rm", "-f", "contenedor-prueba"), ("network", "rm", "red-prueba"), ("image", "rm", "imagen-prueba")])

    def test_error_de_limpieza_no_se_muestra_como_exito(self):
        with patch.object(ENSAYO, "RECURSOS", [("container", "contenedor-prueba")]), patch.object(ENSAYO, "docker", side_effect=RuntimeError("fallo")):
            with self.assertRaisesRegex(RuntimeError, "contenedor-prueba"):
                ENSAYO.limpiar()

    def test_rechaza_daemon_remoto_antes_de_llamar_docker(self):
        with patch.object(ENSAYO.Path, "is_file", return_value=True), patch.dict(ENSAYO.os.environ, {"DOCKER_HOST": "tcp://ejemplo:2375"}), patch.object(ENSAYO, "docker") as llamada:
            with self.assertRaisesRegex(RuntimeError, "DOCKER_HOST"):
                ENSAYO.principal()
            llamada.assert_not_called()

    def test_login_fallido_no_descarta_metricas_de_los_demas(self):
        def simular(numero):
            if numero == 1:
                return {"login": False, "motivo": "solicitud_fallida", "lecturas": []}
            return {"login": True, "motivo": None, "lecturas": [(ruta, 10.0, True) for ruta in CLIENTE.RUTAS]}
        salida = io.StringIO()
        with patch.object(CLIENTE, "usuario", side_effect=simular), contextlib.redirect_stdout(salida):
            with self.assertRaises(SystemExit) as error:
                CLIENTE.main()
        self.assertEqual(error.exception.code, 1)
        lineas = [json.loads(linea) for linea in salida.getvalue().splitlines()]
        self.assertEqual(lineas[0]["logins_fallidos"], 1)
        self.assertEqual(lineas[1]["solicitudes"], 19)
        self.assertNotIn("clave-sintetica", salida.getvalue())


if __name__ == "__main__":
    unittest.main()
