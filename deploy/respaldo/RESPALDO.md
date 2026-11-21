# Respaldo antes del despliegue

`trazalab-respaldo-verificado.sh` debe superar una restauración integral en K3s y S3 con datos sintéticos antes de instalarse. La sonda de restauración espera además al proceso PostgreSQL definitivo después de la inicialización temporal del contenedor.

El llamador debe mantener el bloqueo exclusivo `/run/trazalab/operacion.lock` en el descriptor 9, como hace `trazalab-desplegar.sh`. El script comprueba el descriptor y el bloqueo, pero no intenta adquirirlo. Comprueba el volumen de datos, la identidad AWS y el versionado del bucket; obtiene un `pg_dump` personalizado consistente, lo sube con cifrado SSE-S3 y SHA-256 y descarga exactamente la versión recibida. Compara el hash y restaura la copia descargada en un pod PostgreSQL aislado, sin servicio ni puerto TCP y con almacenamiento temporal. La restauración debe completar sin errores y contener usuarios activos y un historial Flyway sin fallos. El pod se elimina al terminar, también si falla la prueba.

Antes de instalarlo se necesita un bucket privado en `us-east-2`, con bloqueo de acceso público, versionado, SSE-S3, política que exija HTTPS y ciclo de vida acordado para el prefijo `respaldos/trazalab/`. El rol de EC2 necesita únicamente acceso al bucket y prefijo elegidos: consulta de versionado, escritura con cifrado y lectura de versiones; no requiere permisos de borrado. Verificar también la política del bucket, su propietario, CloudTrail o registro de acceso, y el costo de objetos y solicitudes. El script no crea ni configura recursos AWS.

Copiar `respaldo.conf.example` a `/etc/trazalab/respaldo.conf`, completar sus valores y configurar propiedad `root:root` y modo `0600`. Los valores no son credenciales. Instalar el script como `/usr/local/sbin/trazalab-respaldo-verificado`, propiedad root y modo `0700` o `0750`, solo después de revisar y completar las pruebas indicadas abajo. La identidad AWS se obtiene del rol de instancia; no se guardan contraseñas ni claves en el archivo de configuración.

Las copias temporales descifradas existen únicamente en `/run` con permisos de root y se eliminan al terminar normalmente o ante errores manejados. Su tamaño está acotado a 64 MiB por archivo, y la restauración usa hasta 1 GiB de almacenamiento efímero y 256 MiB de memoria. Estos valores son protecciones iniciales, no una capacidad garantizada: la prueba falla si los datos o recursos disponibles los superan. Si el proceso o el nodo se interrumpe de forma abrupta, revisar `/run` y los pods con etiqueta `app.kubernetes.io/name=trazalab-restauracion`; un pod residual alcanza su límite de ejecución, pero puede requerir retirada administrativa.

En el bucket vigente, el prefijo `respaldos/trazalab/` conserva objetos actuales durante 365 días y versiones anteriores durante 30 días. La regla no afecta a `fase1/`. Comprobar que una copia nueva de producción se restauró antes de que venza su respaldo vigente.

Una subida que falla durante la comprobación puede dejar una versión en S3 que **no ha sido verificada**. No se borran versiones automáticamente. Solo una ejecución que termine con código cero y confirme la eliminación del pod permite continuar el despliegue. Acordar retención de versiones y limpieza de copias no verificadas por separado.

El dump cubre la base de datos, no los roles globales de PostgreSQL, secretos de Kubernetes, certificados, imágenes ni los archivos necesarios para recuperar el clúster. La restauración usa `--no-owner --no-privileges`: documentar y ensayar la recuperación de roles, permisos y credenciales por separado. Las comprobaciones internas no comparan todos los registros con el origen ni ejecutan Flyway desde la aplicación; se requieren esas verificaciones para una recuperación completa.

## Prueba simulada

Desde PowerShell, en la raíz del repositorio:

```powershell
$carpetaPruebas = (Resolve-Path -LiteralPath 'deploy\respaldo').Path
docker run --rm --network none --user 0 --mount "type=bind,source=$carpetaPruebas,target=/prueba,readonly" --entrypoint bash postgres:16 /prueba/pruebas/probar-respaldo-simulado.sh
```

La prueba usa sustitutos de AWS, Kubernetes y Python; comprueba que una subida fallida, una alteración en la descarga o una restauración fallida interrumpan el proceso, y que el pod simulado se elimine. No conecta a S3, no restaura PostgreSQL real y no prueba permisos, cifrado efectivo, capacidad ni limpieza tras apagados inesperados.

Para comprobar por separado el arranque real y la restauración mediante el socket Unix de la imagen fijada, ejecutar desde la raíz del repositorio con Python y Docker disponibles:

```powershell
python deploy/respaldo/pruebas/probar-postgres-aislado.py
```

Esta prueba genera la configuración del pod a partir del ayudante y ejecuta la misma imagen en un contenedor temporal sin red, con usuario 999, capacidades retiradas y datos sintéticos. Comprueba el arranque, crea un dump, lo restaura en una base aislada y valida un registro. El 20 de noviembre de 2026 pasó localmente. Docker no reproduce todas las condiciones de K3s ni verifica S3 o la ejecución integral del ayudante. El servidor sigue sin escuchar TCP y utiliza el socket predeterminado `/var/run/postgresql`: el entrypoint de la imagen borra `PGHOST` durante la inicialización y necesita ese socket.

Antes de integrar, ensayar con un bucket de prueba y datos no sensibles: comprobar cifrado y versión del objeto, descarga con hash, restauración real, fallo de permisos, archivo alterado, fallo de restauración, falta de memoria o disco, corte de red, interrupción del proceso y limpieza. Comprobar recuperación con el procedimiento documentado y medir la carga de la instancia durante la prueba. Solo después conectar el script al ayudante de despliegue y verificar ambos bajo el mismo bloqueo.
