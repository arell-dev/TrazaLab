"""Prototipo deshabilitado de exclusión mutua; no inicia ni detiene EC2."""

from dataclasses import dataclass
from secrets import token_urlsafe


@dataclass(frozen=True)
class PermisoOperacion:
    operacion: str
    propietario: str


class CoordinadorOperaciones:
    OPERACIONES = frozenset({"arranque", "despliegue", "apagado"})
    CLAVE = "trazalab-operacion"

    def __init__(self, tabla, atributo):
        self.tabla = tabla
        self.atributo = atributo

    def adquirir(self, operacion):
        if operacion not in self.OPERACIONES:
            raise ValueError("Operación no reconocida.")
        permiso = PermisoOperacion(operacion, token_urlsafe(32))
        try:
            self.tabla.put_item(
                Item={"pk": self.CLAVE, "operacion": operacion, "propietario": permiso.propietario},
                ConditionExpression=self.atributo("pk").not_exists(),
            )
        except self.tabla.meta.client.exceptions.ConditionalCheckFailedException:
            return None
        return permiso

    def liberar(self, permiso):
        if not isinstance(permiso, PermisoOperacion) or permiso.operacion not in self.OPERACIONES or not permiso.propietario:
            raise ValueError("Permiso inválido.")
        condicion = self.atributo("propietario").eq(permiso.propietario) & self.atributo("operacion").eq(permiso.operacion)
        self.tabla.delete_item(Key={"pk": self.CLAVE}, ConditionExpression=condicion)


def crear_coordinador(tabla, region):
    if not tabla or not region:
        raise ValueError("Se requieren tabla y región explícitas.")
    import boto3
    from boto3.dynamodb.conditions import Attr
    from botocore.config import Config

    sesion = boto3.Session(region_name=region)
    recurso = sesion.resource("dynamodb", config=Config(retries={"mode": "standard", "total_max_attempts": 1}, connect_timeout=5, read_timeout=10))
    return CoordinadorOperaciones(recurso.Table(tabla), Attr)
