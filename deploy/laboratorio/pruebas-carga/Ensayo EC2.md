# Protocolo propuesto para medir capacidad en EC2

**Estado: se ejecutó una medición temporal en EC2 el 20 de noviembre de 2026.** La medición caracteriza una ráfaga breve con datos sintéticos; no determina por sí sola la capacidad de un piloto. RE-01 habla de 15–20 equipos, pero no fija usuarios concurrentes, frecuencia, volumen de historial ni umbrales de latencia. Esos valores siguen siendo supuestos, no criterios institucionales aprobados.

## Resultado de la medición temporal

- Entorno separado en `us-east-2`: `t3.small`, Amazon Linux 2023 y K3s `v1.37.2+k3s1`, con modo de créditos `unlimited`. Se usaron raíz gp3 cifrada de 20 GiB y volumen de datos gp3 cifrado de 10 GiB. El grupo de seguridad no tenía reglas de entrada. El servicio se alcanzó mediante Systems Manager a través del IP privado y un listener local en `127.0.0.1`; no se abrió acceso público.
- Se desplegaron la imagen de aplicación `sha256:9d7dcf70b9c245494de0cfb7d1d64f0a46720dd82e238019a46e29f69bdf22ae` y PostgreSQL `sha256:ca0bd484cb98bf4b24eb1010e73fb3fcbd6714d240fbc1a10eea5b7dbecb641d`. El cifrado de Secrets de Kubernetes estaba habilitado y sus hashes coincidían. La PVC quedó enlazada al volumen temporal; las siete migraciones terminaron correctamente y `/api/health` devolvió `disponible`.
- La base contenía 20 cuentas sintéticas de administrador, una cuenta sintética de técnico, 20 equipos, 60 incidencias, 60 fallas, 60 mantenimientos, 20 componentes y 20 asignaciones de componentes. No se copiaron datos de la base productiva.
- El cliente local completó cinco rondas de 20 sesiones concurrentes. Cada sesión inició sesión con CSRF y consultó cinco rutas: `/disponibilidad`, `/disponibilidad/resultados`, `/equipos/QR-TEST-001`, `/incidencias` y `/reportes`. Resultado total: 100 inicios de sesión y 500 lecturas, sin errores de login ni de ruta. La duración por ronda fue de 6,42 a 13,73 segundos; el primer grupo fue el más lento. No se hicieron escrituras durante la carga.

El siguiente cuadro muestra el **rango del p95 entre las cinco rondas**; cada p95 se calculó con 20 respuestas por ruta. No representa un p95 agregado ni una garantía de latencia.

| Ruta | Rango del p95 por ronda |
|---|---:|
| `/disponibilidad` | 605,5–2496,4 ms |
| `/disponibilidad/resultados` | 912,5–1301,7 ms |
| `/equipos/QR-TEST-001` | 601,4–1465,8 ms |
| `/incidencias` | 1087,4–2011,7 ms |
| `/reportes` | 718,2–1029,4 ms |

Las muestras de `kubectl top` se tomaron aproximadamente cada cinco segundos entre 23:40:14 y 23:45:47 UTC, incluyendo reposo posterior a las rondas. El máximo observado fue 954m de CPU en el nodo (47 %) y 1408 MiB de memoria (73 %). La aplicación llegó a 713m de CPU y 362 MiB; PostgreSQL, a 41m y 89 MiB. No hubo reinicios ni eventos de OOM; se registró un fallo transitorio de la sonda de inicio mientras la aplicación aún arrancaba, tras lo cual ambos pods quedaron listos. Al cierre, la raíz ocupaba 4,2 de 20 GiB y el volumen de datos 48 MiB de 9,8 GiB. En las métricas de CloudWatch consultadas a intervalos de cinco minutos no aparecieron créditos excedentes ni cargos por ellos; esa resolución no muestra el pico de una ráfaga corta.

El conteo de lecturas fue de 7,29 a 15,59 por segundo por ronda. El intervalo incluyó un túnel SSM y tráfico de administración y monitoreo; no equivale a la latencia desde la universidad, CloudFront ni HTTPS.

## Aislamiento y comparabilidad

1. Antes de crear recursos, verificar la identidad del proyecto `970909363773`, la región `us-east-2`, el saldo y los límites vigentes. Consultar el estado de la instancia de producción `i-08eb755e51c5be97b` y no modificarla ni sus volúmenes, secretos o cuentas. Si alguien la está usando, el ensayo aislado no debe interrumpirla.
2. Preparar una instancia **temporal y distinta** de tipo `t3.small`, con AMI y arquitectura equivalentes, K3s y configuración de CPU, memoria, sondas y almacenamiento comparables al entorno real. Registrar AMI, versión K3s, tipo y tamaño EBS, límites de los pods y modo de créditos. Los manifiestos locales son plantillas: por ejemplo, el despliegue de la aplicación incluye una imagen de marcador y Caddy tiene cero réplicas; no asumir que coinciden con el estado aplicado. Comparar contra la configuración real autorizada antes del ensayo.
3. Usar la imagen de aplicación **por el digest realmente desplegado**, la misma versión de PostgreSQL y la misma configuración de recursos; verificar los digest desde el clúster y ECR, sin mostrar secretos. No usar el JAR local ni una reconstrucción como equivalente. Si se omiten Caddy, TLS, CloudFront o componentes del nodo, registrar esas diferencias: los resultados medirán solo el subconjunto probado.
4. Crear una base y cuentas **exclusivamente sintéticas** en almacenamiento temporal dedicado. Poblar 20 equipos y cantidades explícitas de incidencias, diagnósticos, componentes y mantenimientos para las rutas probadas; registrar los recuentos, distribución por equipo y semillas. No copiar el dump productivo, hashes de contraseñas, identidades ni datos personales. Comprobar las migraciones y el contenido esperado antes de cargar.
5. Utilizar credenciales temporales con permisos mínimos, IMDSv2, acceso administrativo por Systems Manager y un grupo de seguridad sin puertos de entrada públicos. El perfil de ensayo debe permitir solo SSM y las lecturas ECR estrictamente necesarias; no reutilizar permisos de respaldos o secretos productivos. Verificar conectividad **saliente** real a SSM, `ssmmessages`, ECR API y registro y las capas de imagen en S3, DNS y repositorios necesarios. Una instancia sin IP pública requiere NAT o endpoints adecuados; no crear componentes permanentes o costosos sin revisar antes su necesidad y limpieza.

## Generación y control de tráfico

El cliente debe ejecutarse fuera de la `t3.small` para no consumir su CPU. Para evitar exposición pública, se puede investigar un túnel Systems Manager hacia un puerto ligado solo a `127.0.0.1` de la instancia de ensayo, conectado al servicio de aplicación de su propio K3s. El túnel y el cliente añaden latencia y pueden limitar el caudal: medir un control de red, registrar esa limitación y no extrapolar a la universidad ni a CloudFront. El túnel requiere Session Manager Plugin y un agente SSM compatible; comprobar ambos. No abrir PostgreSQL ni el API de K3s al exterior.

La herramienta local actual **no es un cliente remoto seguro**: crea sus propios contenedores, construye una imagen local y no acepta un destino. Antes de utilizar una adaptación remota deben implementarse y probarse la verificación inequívoca del ID de la instancia temporal y del destino del túnel, rechazo del ID productivo, autenticación y cuentas sintéticas, exclusión de redirecciones externas, límites, cancelación y limpieza. Una URL de loopback por sí sola no demuestra aislamiento, ya que puede apuntar a un túnel hacia producción. No modificar la herramienta para aceptar una URL arbitraria sin esas comprobaciones. Las contraseñas y tokens deben viajar por un canal local protegido, sin argumentos visibles, archivos versionados, salida ni registros.

Preparar un escenario de lectura y escritura sobre la base sintética: login real con CSRF, consultas de disponibilidad y ficha, registro y consulta de incidencia, mantenimiento y reportes según los flujos aprobados. No dar por buenas respuestas HTTP 200 sin comprobar identidad, contenido y resultado esperado. Como punto de partida, una ráfaga de 20 sesiones simultáneas y un ensayo sostenido más largo pueden caracterizar estrés, pero sus duraciones, ritmo y mezcla son supuestos que deben anotarse. Registrar operaciones esperadas, logins fallidos, errores por ruta, tiempos p50/p95 y distribución de muestras; no interpretar un p95 de solo 20 muestras como garantía estadística.

## Mediciones y cierre

- Recoger antes, durante y después: timestamps UTC, tipo de instancia, digest, recuentos sintéticos, CPU y memoria del nodo y pods, uso y operaciones de disco, reinicios, OOM, errores de aplicación y capacidad de red. Registrar las muestras y sus intervalos, no únicamente el máximo de `kubectl top`.
- Consultar CloudWatch para `CPUCreditBalance`, `CPUCreditUsage`, `CPUSurplusCreditBalance` y `CPUSurplusCreditsCharged`; esas métricas de créditos tienen resolución de cinco minutos. Comparar saldo antes/después, considerar la ventana de facturación de `unlimited` y no deducir consumo sostenido de una ráfaga de dos segundos.
- Identificar diferencias con la instancia de destino y condiciones que invalidan la comparación: distinta imagen o K3s, presión de memoria previa, dataset insuficiente, cliente saturado, túnel limitado, falta de métricas o prueba interrumpida. Informar observaciones y límites; sin umbrales aprobados y un escenario representativo, no declarar que `t3.small` es suficiente.
- Etiquetar todos los recursos temporales y guardar sus IDs antes de iniciar. En caso de error o interrupción, conservar el inventario para limpieza controlada. Retirar solo recursos creados para el ensayo —incluidos túneles, instancia, EBS con `DeleteOnTermination` comprobado, endpoints, objetos y rol si se crearon— y verificar su ausencia. Nunca eliminar el volumen persistente o el bucket de recuperación de producción.
- Costos: la consulta AWS Pricing del 20 de noviembre de 2026 devolvió para Ohio USD 0,0208/h por Linux `t3.small` compartida bajo demanda y USD 0,08/GB-mes de capacidad gp3. A modo de referencia, dos horas de instancia y 20 GB gp3 por dos horas son aproximadamente USD 0,046 usando 730 h/mes. No incluye otra máquina generadora, IPv4, NAT/endpoints, red, solicitudes, monitoreo, rendimiento EBS adicional ni eventuales créditos excedentes; el importe y los créditos aplicables deben comprobarse en AWS Settings. Detener una instancia no elimina el costo de sus volúmenes.

## Límites y comprobaciones pendientes

La ráfaga fue breve y de solo lectura. No se incluyeron Caddy, TLS, CloudFront ni una escritura de incidencia durante la carga. El túnel agrega latencia, la muestra no representa un uso sostenido y no existe un umbral aprobado contra el cual evaluar la suficiencia de `t3.small`; por ello, el resultado no declara capacidad suficiente para el piloto.

## Renovación controlada de credencial ECR

El 21 de noviembre de 2026 se probó la renovación en otra instancia temporal `t3.small`, aislada de producción, con K3s `v1.37.2+k3s1`, cifrado de Secrets habilitado, grupo de seguridad sin reglas de entrada y administración por Systems Manager. Se sustituyó temporalmente el Secret de pull por una credencial deliberadamente inválida y se intentó descargar la imagen de aplicación por el digest `sha256:9d7dcf70b9c245494de0cfb7d1d64f0a46720dd82e238019a46e29f69bdf22ae`. Kubelet devolvió `ErrImagePull` y el evento `ImagePullBackOff`; ECR respondió `400 Bad Request`. No se registró el valor de la credencial.

Después se ejecutó `deploy/k3s/trazalab-renovar-ecr-pull.sh` y se volvió a crear un pod con el mismo digest y `imagePullPolicy: Always`. El pod terminó `Succeeded`, con código de salida `0` y la salida `ECR_PULL_OK`. Esto comprueba que el script renueva el Secret y recupera el pull frente a una credencial inválida. **No se esperó el vencimiento real de 12 horas**, por lo que no se afirma que el vencimiento cronológico se haya reproducido. La instancia, el Secret, el grupo de seguridad, el perfil y el rol temporales se eliminaron; producción permaneció detenida.

El acceso al panel desde la red universitaria fue confirmado; los flujos de la aplicación desde esa red no formaron parte de esta medición. La capacidad de producción tampoco se midió con el tráfico de esta prueba.
