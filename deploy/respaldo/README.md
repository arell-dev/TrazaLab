# Respaldo y recuperación de PostgreSQL

Esta guía cubre el respaldo periódico de la base activa y las copias puntuales de la migración. PostgreSQL 16 continúa en K3s y escribe en el volumen EBS de datos, separado del volumen raíz.

## Respaldo vigente

El respaldo se ejecuta como parte del despliegue: el flujo SSM conserva el permiso externo despliegue y el bloqueo local durante todo el proceso. El helper de respaldo verifica ambos antes de leer la base. No existe un actor de respaldo independiente que adquiera el coordinador; no ejecutes el helper por separado.



El ejecutable `ec2/trazalab-respaldo-verificado.sh` obtiene un `pg_dump` consistente, sube la copia a S3 con SSE-S3 y SHA-256, descarga la misma versión y la restaura en un pod PostgreSQL aislado. Comprueba el estado de la base restaurada y elimina el pod temporal. Solo termina correctamente si se verifica la restauración.

EC2 usa el rol `trazalab-ec2-ssm`, limitado al bucket `trazalab-recuperacion-970909363773-us-east-2` y al prefijo `respaldos/trazalab/`. El bucket tiene versionado, bloqueo de acceso público, cifrado SSE-S3 y una política que exige HTTPS. Las versiones actuales se conservan 365 días y las anteriores 30 días. No se necesitan credenciales de AWS dentro del helper.

La configuración instalada en `/etc/trazalab/respaldo.conf` contiene región, cuenta, bucket y prefijo. La plantilla `ec2/respaldo.conf.example` muestra su estructura. La configuración instalada es propiedad de root y modo `0600`. El ejecutable está en `/usr/local/sbin/trazalab-respaldo-verificado`, propiedad de root y sin permisos de escritura para grupo ni otros.

El 21 de noviembre de 2026 se ejecutó el respaldo real bajo el bloqueo compartido de operación. La versión exacta se descargó y restauró en EC2 antes de dar éxito.

| Dato | Valor |
|---|---|
| Objeto S3 | `respaldos/trazalab/2026/11/21T192943Z-f3a47c7f124d49238fa4744823f52d61.dump` |
| Versión S3 | `lA41leKHV6RJZQpDJWErIapwpu31lKZK` |
| SHA-256 | `e803d7c9b8074c85b440219a3cfca3605bc6d6a823bad937b9bd579cb3d440ff` |
| Resultado | Subida cifrada, descarga de la misma versión y restauración aislada correctas |

El ejecutable de despliegue ya exige que este procedimiento termine correctamente antes de reemplazar la imagen. Si falla, no se debe retirar el bloqueo ni continuar el despliegue hasta revisar el estado real.

## Copias históricas de la migración

El bucket también conserva copias puntuales de Fresh Canvas. No contienen roles globales de PostgreSQL, secretos de Kubernetes ni configuración del clúster.

| Selección | Clave | Versión | SHA-256 |
|---|---|---|---|
| `fase1` | `fase1/fresh-canvas-20261120.dump` | `2cBfd.P5UCLnWDMdpR1YzaePK1SFITh9` | `ba3f5521881e1e50e8cbb11b68fc36a0a6b10ff441f6f544537eca95bd998037` |
| `incidencia` | `fase1/actual-incidencia-20261120-1950.dump` | `emHANPxMjSeTe98dEMy3_Ng5HZ3Hrt.I` | `574f7f18cca733835868fd45e94dd4ab5ab464fb621880043066312082a01415` |

Desde PowerShell 7, en la raíz del repositorio, `historico/restaurar-copia-fase1.ps1` valida la cuenta AWS, descarga una versión exacta, verifica SHA-256 y restaura en PostgreSQL 16 temporal sin red. La base temporal se elimina al terminar.

```powershell
.\deploy\respaldo\historico\restaurar-copia-fase1.ps1 -Perfil trazalab-proyecto
.\deploy\respaldo\historico\restaurar-copia-fase1.ps1 -Perfil trazalab-proyecto -Copia incidencia
```

La copia `fase1` debe producir 2 cuentas activas, 1 computadora y 1 incidencia. La copia `incidencia` debe producir 2 incidencias. Ambas registran 7 migraciones exitosas, ninguna fallida y 16 tablas públicas. Los recuentos y las migraciones prueban el resultado acotado de esas copias, no la disponibilidad completa del clúster ni la igualdad de todos los registros.

## Pruebas locales

Desde la raíz de `Proyecto/trazalab`, la prueba de restauración aislada comprueba que la imagen PostgreSQL fijada arranca, genera y restaura un dump sintético sin red.

```powershell
python deploy/respaldo/pruebas/probar-postgres-aislado.py
```

La prueba simulada valida fallos de subida, descarga y restauración. No contacta S3 ni demuestra permisos efectivos de AWS.

```powershell
$carpetaPruebas = (Resolve-Path -LiteralPath 'deploy').Path
docker run --rm --network none --user 0 --mount "type=bind,source=$carpetaPruebas,target=/prueba,readonly" --entrypoint bash postgres:16 -lc "tr -d '\r' < /prueba/respaldo/pruebas/probar-respaldo-simulado.sh > /tmp/probar-respaldo-simulado.sh && bash /tmp/probar-respaldo-simulado.sh"
```

El dump protege la base. La recuperación completa de la aplicación requiere además los manifiestos, secretos, certificados y configuración autorizada de K3s. Antes de reemplazar PostgreSQL, validar la versión y el estado del volumen persistente.
