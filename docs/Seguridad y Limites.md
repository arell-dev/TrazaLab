# Seguridad y límites de TrazaLab

## Controles implementados

- El formulario web valida que el QR y la descripción estén presentes y dentro de sus longitudes máximas.
- El servicio exige que el usuario reportante exista, esté activo y tenga un rol permitido.
- La identidad demo se lee desde `trazalab.demo.usuario-correo`; el controlador construye la solicitud en el servidor y no confía en un ID recibido del formulario.
- El responsable técnico demo se lee desde `trazalab.demo.mantenimiento-correo`; el servicio y la regla de base de datos requieren un usuario activo con rol técnico.
- Un mantenimiento correctivo referencia una falla de la misma computadora; el registro no cambia el estado del equipo ni de la incidencia.
- El registro de la incidencia y sus asociaciones se guarda en PostgreSQL dentro de una transacción.
- Spring Security conserva protección CSRF para formularios y desactiva los formularios de login y HTTP Basic en esta etapa.
- Los eventos de aplicación se emiten por `LoggingAplicacion`; el adaptador actual escribe acción y resultado en un archivo separado de los logs técnicos.
- La bitácora no registra descripciones, nombres, correos ni contraseñas.
- Las credenciales de conexión se suministran desde `.env`, excluido de Git. La plantilla incluye valores de ejemplo que deben reemplazarse por credenciales locales.
- La inserción de datos demo está desactivada por defecto con `trazalab.seed.enabled=false`.

## Límites de seguridad actuales

- Esta entrega es una aplicación web de demostración sin autenticación. La configuración permite las rutas y no implementa login, sesión ni autorización HTTP por rol.
- Las incidencias quedan asociadas al usuario demo configurado; esto identifica el registro de demostración y no acredita quién usó el equipo.
- El formulario no admite escanear la cámara ni adjuntar imágenes; el QR se escribe como texto.
- `GET /api/computadoras/historial` devuelve los datos básicos del equipo y hasta cinco incidencias recientes con descripción, fecha y estado; no incluye el nombre del reportante. La ruta sigue siendo pública mientras no exista autenticación.
- Los módulos de mantenimiento y reportes funcionan en modo demo; el historial completo por equipo y la gestión integral del inventario siguen pendientes.
- El intercambio seguro entre la aplicación recolectora y TrazaLab todavía no está definido.
- La configuración demo no está preparada para exponer la aplicación como servicio público o productivo.

## Próximas medidas

- Definir autenticación y autorización cuando se implemente la gestión de usuarios.
- Definir identidad, transporte cifrado, validación y permisos del agente recolector antes de recibir datos de computadoras.
- Mantener privilegios mínimos para el usuario de base de datos y proteger los secretos locales.
- Mantener migraciones versionadas antes de evolucionar el esquema compartido.

Para los requisitos pendientes y sus evidencias, consulta la [matriz de trazabilidad](Matriz%20Trazabilidad.md).
