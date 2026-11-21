# Recuperación de la copia de migración de Fresh Canvas

Este procedimiento comprueba dos copias puntuales de la base de datos; no recupera el clúster completo ni sustituye el sistema de respaldos periódico. Una copia se obtuvo antes de retirar el origen y la otra después de la incidencia de prueba. Los datos posteriores requieren otro respaldo.

## Ubicación y conservación

- Proyecto AWS de destino: `970909363773`; región `us-east-2`.
- Bucket privado: `trazalab-recuperacion-970909363773-us-east-2`.
- Objeto: `fase1/fresh-canvas-20261120.dump`.
- Versión: `2cBfd.P5UCLnWDMdpR1YzaePK1SFITh9`.
- SHA-256: `ba3f5521881e1e50e8cbb11b68fc36a0a6b10ff441f6f544537eca95bd998037`.
- Tamaño: 64 573 bytes; cifrado SSE-S3. La recuperación requiere una identidad autorizada a leer esa versión. No depende del usuario o equipo Windows que generó la copia DPAPI.
- La configuración de ciclo de vida prevé la expiración del objeto actual el 21 de noviembre de 2027 UTC y limita las versiones anteriores. Antes de esa fecha se debe disponer de otra copia comprobada o revisar su retención; no confiar en este objeto indefinidamente.

La copia posterior, seleccionada con `-Copia incidencia`, está en el mismo bucket con objeto `fase1/actual-incidencia-20261120-1950.dump`, versión `emHANPxMjSeTe98dEMy3_Ng5HZ3Hrt.I`, SHA-256 `574f7f18cca733835868fd45e94dd4ab5ab464fb621880043066312082a01415` y tamaño de 64 658 bytes. Usa SSE-S3 y el prefijo sujeto a la regla de caducidad de 365 días; verificar su fecha efectiva en S3 y conservar una copia vigente antes de su vencimiento. Es una instantánea, no un respaldo continuo.

## Requisitos

Usar un equipo Windows autorizado con PowerShell 7, AWS CLI, Docker y la imagen `postgres:16` disponible. Iniciar sesión con un perfil que tenga `sts:GetCallerIdentity` y `s3:GetObjectVersion` para el objeto indicado. El script valida que la identidad pertenezca al proyecto de destino; no incluir credenciales en el repositorio, el comando ni la evidencia. La imagen puede descargarse previamente desde una fuente confiable. El contenedor no tiene red ni publica puertos; PostgreSQL se aloja en un `tmpfs` temporal.

La descarga crea un archivo temporal descifrado dentro de un directorio con permisos restringidos al usuario de Windows que ejecuta el comando y lo elimina al finalizar normalmente. Los administradores del equipo pueden modificar permisos y un fallo abrupto puede dejar el archivo. El borrado ordinario no garantiza el borrado físico en SSD: utilizar un equipo autorizado con disco cifrado, revisar la carpeta temporal tras una interrupción y restringir el acceso a copias o volcados del sistema.

## Ensayo

Desde PowerShell, en la raíz de `Proyecto/trazalab`:

```powershell
.\deploy\respaldo\restaurar-copia-fase1.ps1 -Perfil trazalab-proyecto
```

Para la copia posterior, seleccionar explícitamente la instantánea:

```powershell
.\deploy\respaldo\restaurar-copia-fase1.ps1 -Perfil trazalab-proyecto -Copia incidencia
```

Sustituir el nombre del perfil si el equipo autorizado utiliza otro. El script comprueba la identidad, descarga **la versión específica**, verifica el SHA-256, crea un PostgreSQL aislado, restaura con `pg_restore --no-owner --no-privileges --exit-on-error` y compara los recuentos y migraciones registrados en la instantánea. Después elimina el contenedor y el archivo temporal. Si aparece una advertencia de limpieza o el proceso se interrumpe, inspeccionar y retirar el contenedor o directorio temporal identificado, sin afectar otros recursos.

Resultado esperado para la copia predeterminada `fase1`: dos usuarios activos, una computadora, una incidencia, cero mantenimientos, cero componentes, siete migraciones exitosas, ninguna fallida y 16 tablas públicas. Para `incidencia` se esperan los mismos recuentos salvo **dos incidencias**. Cualquier diferencia o error invalida el ensayo y exige investigar antes de emplear la copia para reemplazar datos. Los recuentos no prueban por sí solos la igualdad de todos los datos ni del esquema.

El script también calcula la huella del esquema público mediante `pg_dump --schema-only --schema=public --no-owner --no-privileges`. Excluye las líneas variables `\restrict` y `\unrestrict` y los comentarios de versión de PostgreSQL y de `pg_dump`. La huella obtenida en el ensayo fue `e91130bfc6c9920782d376c2a4c7a1fca161d141f157b33a1143fc48f150aa8f`. Una comparación con el origen requiere el mismo método y versiones compatibles de `pg_dump`; una coincidencia del esquema tampoco comprueba el contenido completo de las tablas.

## Alcance de una recuperación real

El dump no incluye roles globales de PostgreSQL, contraseñas administrativas, secretos de Kubernetes, certificados, imágenes, manifiestos ni configuración del clúster. Se deben recuperar o recrear por separado con fuentes autorizadas. La prueba usa `--no-owner --no-privileges`, por lo que una recuperación operativa exige revisar y conceder los privilegios apropiados. Restaurar la base aislada no demuestra que la aplicación arranque ni que sus cuentas puedan iniciar sesión. Antes de pasar a producción, validar migraciones, permisos, acceso y flujos con el procedimiento operativo vigente y evitar sobrescribir una base con datos posteriores.

## Evidencia

El 20 de noviembre de 2026 se ejecutó este script desde el equipo Windows original con el perfil de destino: la descarga de la versión indicada, el hash, `pg_restore` y las comprobaciones de recuentos terminaron correctamente. El contenedor temporal se eliminó al finalizar. Esta ejecución demuestra que el procedimiento funciona con ese perfil y equipo; todavía no demuestra una ejecución desde otro equipo o por otra persona.

La copia posterior se descargó por versión y se restauró por separado en un contenedor PostgreSQL 16 local sin red. El hash coincidió con los metadatos de S3 y se obtuvieron los recuentos de `incidencia` indicados arriba; se retiraron el dump y el contenedor temporales. Esta prueba usó un procedimiento independiente y no ejecutó el selector nuevo del script; tampoco compara cada registro de la copia con la base de origen.

Para una recuperación real, verifica que la instantánea seleccionada incluya los cambios necesarios y que no se sobrescriban datos posteriores. El respaldo automático sigue pendiente de validación integral.
