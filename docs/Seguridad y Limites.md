# Seguridad y límites de TrazaLab

## Controles implementados

- Al registrar una incidencia, el servicio exige que el usuario exista y esté activo.
- Solo se aceptan roles que pueden reportar incidencias según el modelo actual.
- Se verifica que el QR corresponda a una computadora existente.
- La descripción es obligatoria y tiene un máximo de 500 caracteres.
- El registro de la incidencia y sus asociaciones se guarda en PostgreSQL dentro de una transacción.
- Los eventos de aplicación se emiten a través de `LoggingAplicacion`; el adaptador actual escribe acciones y resultados en un archivo separado de los logs técnicos de Spring Boot.
- La bitácora no registra descripciones de incidencias, nombres, correos ni contraseñas; conserva acción, estado, identificadores operativos y cantidades.
- Las operaciones rechazadas quedan como `WARN` y los fallos técnicos como `ERROR`; los mensajes de error del usuario no se copian a la bitácora.
- Las credenciales de conexión se suministran desde `.env`, excluido de Git. El archivo incluido trae valores de ejemplo que deben reemplazarse por credenciales locales válidas.

## Límites de seguridad actuales

- La aplicación es de consola. No tiene login, contraseñas de usuarios, sesión ni autorización HTTP.
- El ID de usuario se ingresa en la consola; validar su rol no autentica a la persona que usa el equipo.
- `IncidenciaController` es un controlador de aplicación, no un controlador web.
- Spring Security está agregado como dependencia para una etapa futura, pero no se configura ni aplica en este flujo de consola.
- La comunicación segura entre la aplicación recolectora y TrazaLab aún no está definida.
- Los datos de arranque son ficticios y se insertan en PostgreSQL de forma idempotente; no se cargan en repositorios en memoria.

## Próximas medidas

- Implementar autenticación y autorización cuando exista la interfaz de usuarios definida.
- Definir identidad, transporte cifrado, validación y permisos del agente recolector antes de recibir datos de computadoras.
- Mantener privilegios mínimos para el usuario de base de datos y proteger los secretos locales.
- Añadir migraciones versionadas antes de evolucionar el esquema compartido.

Para los requisitos pendientes y sus evidencias, consulta la [matriz de trazabilidad](Matriz%20Trazabilidad.md).
