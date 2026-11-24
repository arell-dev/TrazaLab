# Alojamiento del panel de encendido

`cloudformation/alojamiento.yaml` prepara una distribución CloudFront y un bucket S3 privado para publicar el panel sin depender de la instancia EC2. El bucket usa cifrado SSE-S3, versionado, bloqueo de acceso público y control de acceso de origen (OAC). La distribución parte **deshabilitada**: crear la infraestructura no demuestra que el panel o su autenticación funcionen.

La plantilla no implementa Cognito, la API ni la aplicación TrazaLab; se coordinan por separado con `cloudformation/infraestructura.yaml`. No guarda contraseñas, tokens ni archivos de respaldo. El archivo `config.json` contiene únicamente identificadores y URL públicas, nunca credenciales.

## Secuencia de puesta en marcha

1. Comprobar la identidad de `trazalab-proyecto`, la región seleccionada `us-east-2`, los créditos y las cuotas. Revisar la plantilla y sus recursos con un conjunto de cambios. S3, CloudFront, registros y eventuales claves KMS pueden consumir créditos; detener EC2 no los detiene.
2. Crear la pila con `HabilitarDistribucion=false`. Consultar las salidas `BucketPanel`, `DistribucionId` y `OrigenPanel`. La distribución queda deshabilitada hasta el paso 6. El bucket tiene `DeletionPolicy: Retain`: borrar la pila no elimina sus objetos ni elimina el costo de almacenamiento.
3. Configurar registros estándar de CloudFront antes de habilitar la distribución. Las operaciones de entrega se realizan en `us-east-1` por tratarse de un recurso global; permitir solo los campos necesarios y excluir cookies y parámetros de consulta. En la configuración aplicada se eligió un bucket privado en `us-east-2`, con cifrado SSE-S3 y caducidad de 30 días. Seguir la [guía de registros estándar de CloudFront](https://docs.aws.amazon.com/AmazonCloudFront/latest/DeveloperGuide/standard-logging.html). La plantilla no crea esta entrega: configurarla por separado y verificar que lleguen registros.
4. Construir y probar el panel con `npm ci`, `npm test` y `npm run build` en esta carpeta. Empaquetar la Lambda con sus dependencias por separado. Crear la pila `cloudformation/infraestructura.yaml` con el `OrigenPanel` de este alojamiento, la URL HTTPS real de salud de TrazaLab y el paquete Lambda en S3 de la región seleccionada. La configuración actual de la otra plantilla requiere esos valores y un dominio Cognito disponible.
5. Generar `config.json` en el directorio `dist` con las salidas reales `UserPoolId`, `ClientId`, `CognitoUrl` y `ApiUrl` de la pila de control, más `aplicacionUrl` apuntando al login HTTPS de TrazaLab. Utilizar `public/config.example.json` solo como referencia de estructura. Publicar `dist` en `BucketPanel`; asignar `Cache-Control: no-store` a `index.html` y `config.json`. No incluir `node_modules`, código Lambda, dumps ni archivos privados.
6. Habilitar la distribución mediante un cambio de pila con `HabilitarDistribucion=true`. Esperar su propagación y comprobar que la política OAC impide descargar directamente de S3, mientras CloudFront sirve el panel y `config.json`. La política CSP permite las URL Cognito y API Gateway de `us-east-2`; comprobar la consola del navegador y ajustar solo con evidencia si bloquea una ruta legítima.
7. Configurar las cuentas autorizadas en Cognito y asignarlas al grupo `operadores`; el autorregistro permanece deshabilitado. Probar, sin exponer tokens en capturas, la denegación de acceso no autorizado y el acceso autorizado. Verificar que el panel carga con EC2 detenida y que el estado refleja los resultados reales del controlador. El contenido estático es accesible por URL; la autenticación protege las operaciones y los estados de la API, no oculta el HTML.
8. Tras publicar cambios de frontend, verificar el HTML, los assets y `config.json` servidos por CloudFront; la política de caché tiene TTL cero. Verificar el login, el callback OAuth, CORS y logout, y ensayar en móvil y escritorio. Registrar errores y resultados sin incluir datos personales ni credenciales.

## Límites de la evidencia

El panel puede mostrar los estados que reciba de la API. No demuestra por sí solo la ejecución de pruebas de GitHub Actions ni el digest de una imagen. Registrar build, pruebas, imagen y despliegue con sus respectivas evidencias reales. La URL de CloudFront de esta pila corresponde al **panel** y es distinta de la URL de TrazaLab.

El cifrado y versionado del bucket no sustituyen una política de limpieza de versiones antiguas. Antes de eliminar una pila o bucket retenido, revisar sus objetos, los registros, las dependencias de Cognito/CORS y los costos.

## Apagado seguro

La consola muestra el tiempo restante hasta el umbral de 30 minutos de inactividad. Sincroniza mediante `GET /temporizador`, protegido por Cognito y el grupo de operadores, que lee `GET /api/temporizador` en el backend. La ruta de backend expone solo el reloj y el estado agregado de actividad, sin consultar PostgreSQL ni renovar el plazo. La consulta del panel tampoco agrega eventos al historial. La consola sincroniza cada minuto y cada diez segundos mientras hay solicitudes activas. Si el reloj deja de responder, muestra `--:--` hasta obtener una lectura válida. El worker revisa el apagado cada cinco minutos. Llegar a `00:00` indica que se alcanzó el umbral.

Para publicar el contador, primero despliega el backend con `GET /api/temporizador`. Comprueba que la respuesta HTTPS del backend incluya las cabeceras de reloj y `Cache-Control: no-store`. Después actualiza el paquete de Lambda y la ruta JWT de la pila de control, y verifica `GET /temporizador` con una sesión de operador. Publica los recursos estáticos del panel después de verificar ese contrato.

El módulo de `deploy/apagado/` está instalado en EC2. La tabla DynamoDB y la regla EventBridge están desplegadas. El temporizador `trazalab-apagado.timer` quedó habilitado el 23/11/2026. La instalación inicial mantiene los temporizadores deshabilitados hasta validar la recuperación de PostgreSQL, TrazaLab y el volumen persistente.
## Estado comprobado el 20 de noviembre de 2026

La pila de alojamiento y la de control se crearon en `us-east-2`. El panel se publica en `https://d2ltkzmo83k169.cloudfront.net/`, independientemente de la instancia. Se comprobó el inicio de sesión de un operador, la solicitud de encendido y las respuestas de la API; sin credenciales, la consulta y el encendido devolvieron HTTP 401. La aplicación respondió a la comprobación de salud por HTTPS después del arranque.

La instancia se detuvo al terminar la prueba y AWS confirmó el estado `stopped`. El apagado por inactividad y el despliegue automático por etiquetas siguen pendientes. El panel muestra estados recibidos de su API, no registros internos completos ni evidencia de compilación de GitHub Actions. Los buckets, la IP elástica y los registros pueden seguir generando cargos.

Ese mismo día se publicó el ajuste del panel para descartar respuestas antiguas, tratar rechazos de autorización y mantener el cierre de sesión al vencer el token. Tras recargar se comprobaron el inicio de sesión, la consulta del estado detenido y el cierre de sesión. Los casos de expiración y errores se probaron con Cognito y API simulados; falta la validación de esos flujos desde la universidad. Se conservaron la versión anterior del índice y sus recursos para recuperación.

## Corrección del encendido desde EC2 (23 de noviembre de 2026)

Durante la prueba de parada y recuperación, el panel mostró un error aunque CloudTrail registró que AWS aceptó `StartInstances`; después, EC2 pasó a `running` y el panel confirmó `Disponible`. La lectura inmediata del estado después de solicitar el encendido podía adelantarse a la transición de EC2.

El commit firmado `768cc50` elimina esa lectura inmediata: la Lambda responde `202 iniciando` al aceptar AWS y mantiene el bloqueo hasta que EventBridge confirma `running`. La CI `45106488368` terminó correctamente; las 22 pruebas del panel y `npm.cmd run build` pasaron. El paquete `panel/control-pr27-ccf703e-start-race-768cc50.zip` se desplegó mediante el cambio CloudFormation `panel-start-race-768cc50`, que terminó `UPDATE_COMPLETE`; la función reportó `LastUpdateStatus=Successful`. La consulta posterior del panel mostró TrazaLab disponible.

La respuesta tardía también está cubierta por la prueba automatizada que mantiene `stopped` en la lectura simulada tras aceptar el inicio. Este ajuste no publicó una nueva versión de la aplicación TrazaLab.

En una prueba posterior el 23/11/2026, el temporizador de inactividad con el umbral original de 30 minutos inició el apagado a las 04:34 UTC. EC2 confirmó `stopped` y el panel autenticado mostró que podía solicitarse el encendido. La solicitud desde el panel a las 04:35 mostró `iniciando`; EC2 pasó a `running` y el panel mostró `Disponible` después de recuperar la aplicación y la base. El coordinador quedó libre. La verificación posterior se hizo desde el navegador automatizado; el acceso desde la red universitaria se había confirmado previamente.

El comando controlado SSM `87f55a9d-5d69-4ecc-b2a2-7da542ed2bdc` mantuvo un permiso de despliegue durante una espera y lo dejó retenido al terminar. Tras confirmar el estado final del comando, la ausencia de marcador pendiente, los deployments listos y el montaje del volumen mediante SSM `d1f8dd14-3ccc-4f18-a140-3550bf545738`, se liberó el permiso con condición de propietario y sesión. El coordinador quedó vacío. La prueba no reprodujo una pérdida real de conectividad SSM.

## Corrección del apagado automático (23 de noviembre de 2026)

La instancia seguía en ejecución porque `trazalab-apagado.timer` estaba deshabilitado. El comando SSM `383fe971-1b6c-4d29-bc01-be339376a332` registró la eliminación del enlace de activación del temporizador durante la recuperación anterior. Tras habilitarlo con SSM `8ec5a87c-bfe5-4229-a3a8-d7960aab7caf`, AWS confirmó que EC2 pasó automáticamente a `stopped` y el coordinador quedó vacío.

La recuperación siguiente se inició por API de AWS porque el panel estaba sin sesión autenticada. El temporizador de arranque restauró PostgreSQL y TrazaLab, ambos `1/1`, con PVC `Bound`, política `Retain` y volumen montado. El temporizador de apagado continúa habilitado y activo con el umbral de 30 minutos. Las comprobaciones posteriores al reinicio confirmaron que se ejecuta cada cinco minutos y espera a que venza el umbral.
