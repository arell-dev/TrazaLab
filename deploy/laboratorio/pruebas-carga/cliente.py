"""Cliente interno: solo se ejecuta en la red temporal creada por ejecutar.py."""

import concurrent.futures
import http.cookiejar
import json
import math
import os
import re
import statistics
import sys
import time
import urllib.parse
import urllib.request

APP = os.environ["ENSAYO_APP"]
CLAVE = os.environ["ENSAYO_CLAVE"]
ORIGEN = "http://" + APP + ":8080"
RUTAS = ("/disponibilidad", "/disponibilidad/resultados", "/equipos/QR-TEST-001", "/incidencias", "/reportes")


class NoRedirigirExterno(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, request, fp, code, msg, headers, newurl):
        destino = urllib.parse.urlsplit(newurl)
        if destino.scheme != "http" or destino.netloc != APP + ":8080":
            raise RuntimeError("Redirección fuera de la aplicación temporal.")
        return super().redirect_request(request, fp, code, msg, headers, newurl)


def usuario(numero):
    navegador = urllib.request.build_opener(urllib.request.ProxyHandler({}), NoRedirigirExterno(), urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
    try:
        with navegador.open(ORIGEN + "/usuarios/login", timeout=30) as respuesta:
            html = respuesta.read().decode("utf-8")
        token = re.search(r'name="_csrf"[^>]*value="([^"]+)"', html)
        if not token:
            return {"login": False, "motivo": "csrf_ausente", "lecturas": []}
        datos = urllib.parse.urlencode({"username": f"ensayo{numero}@prueba.local", "password": CLAVE, "_csrf": token.group(1)}).encode()
        with navegador.open(ORIGEN + "/usuarios/login", data=datos, timeout=60) as respuesta:
            if urllib.parse.urlsplit(respuesta.url).path != "/disponibilidad":
                return {"login": False, "motivo": "respuesta_inesperada", "lecturas": []}
    except Exception:
        return {"login": False, "motivo": "solicitud_fallida", "lecturas": []}
    resultados = []
    for ruta in RUTAS:
        inicio = time.perf_counter()
        try:
            with navegador.open(ORIGEN + ruta, timeout=60) as respuesta:
                if respuesta.status != 200 or urllib.parse.urlsplit(respuesta.url).path != ruta:
                    raise RuntimeError("Respuesta inesperada.")
                respuesta.read()
            resultados.append((ruta, (time.perf_counter() - inicio) * 1000, True))
        except Exception:
            resultados.append((ruta, (time.perf_counter() - inicio) * 1000, False))
    return {"login": True, "motivo": None, "lecturas": resultados}


def main():
    inicio = time.perf_counter()
    with concurrent.futures.ThreadPoolExecutor(max_workers=20) as ejecutor:
        grupos = list(ejecutor.map(usuario, range(1, 21)))
    duracion = time.perf_counter() - inicio
    fallos_login = sum(not grupo["login"] for grupo in grupos)
    motivos = {motivo: sum(grupo["motivo"] == motivo for grupo in grupos) for motivo in ("csrf_ausente", "respuesta_inesperada", "solicitud_fallida")}
    print(json.dumps({"logins_intentados": len(grupos), "logins_fallidos": fallos_login, "motivos": motivos}))
    errores_totales = 0
    for ruta in RUTAS:
        valores = [(ms, correcto) for grupo in grupos for direccion, ms, correcto in grupo["lecturas"] if direccion == ruta]
        tiempos = sorted(ms for ms, correcto in valores if correcto)
        errores_totales += len(valores) - len(tiempos)
        print(json.dumps({"ruta": ruta, "solicitudes": len(valores), "errores": len(valores) - len(tiempos), "mediana_ms": round(statistics.median(tiempos), 1) if tiempos else None, "p95_ms": round(tiempos[math.ceil(len(tiempos) * .95) - 1], 1) if tiempos else None}))
    print(json.dumps({"sesiones": len(grupos), "duracion_segundos": round(duracion, 2), "solicitudes_por_segundo": round(sum(len(grupo["lecturas"]) for grupo in grupos) / duracion, 2)}))
    if errores_totales or fallos_login:
        sys.exit(1)


if __name__ == "__main__":
    main()
