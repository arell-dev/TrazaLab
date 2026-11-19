# Seguridad y límites de TrazaLab

## Controles implementados

- El formulario web valida que el QR y la descripción estén presentes y dentro de sus longitudes máximas.
- El servicio exige que el usuario reportante exista, esté activo y tenga un rol permitido.
- El controlador obtiene la identidad del principal autenticado y busca esa cuenta en PostgreSQL; no confía en un ID recibido del formulario.
- Los mantenimientos también usan la cuenta de la sesión; el servicio y la regla de base de datos requieren un usuario activo con rol técnico.
- Spring Security autentica el formulario de acceso consultando `usuario` mediante `UsuarioDetailsService`; solo acepta cuentas activas con `password_hash` BCrypt.
- La autorización web usa el rol relacionado de la cuenta almacenada en PostgreSQL. Las rutas de administración de usuarios requieren `ROLE_Administrador`.
- Las cuentas de acceso, incluida la cuenta administradora inicial, deben estar registradas en la base de datos recuperada.
- Un mantenimiento correctivo referencia una falla de la misma computadora; el registro no cambia el estado del equipo ni de la incidencia.
- El registro de la incidencia y sus asociaciones se guarda en PostgreSQL dentro de una transacción.
- Spring Security conserva protección CSRF para formularios; el endpoint de salud de la API permanece público y las rutas web protegidas requieren sesión y rol.
- Los eventos de aplicación se emiten por `LoggingAplicacion`; el adaptador actual escribe acción y resultado en un archivo separado de los logs técnicos.
- La bitácora no registra descripciones, nombres, correos ni contraseñas.
- Las credenciales de conexión a PostgreSQL se suministran desde `.env`, excluido de Git. No se usan variables de entorno ni propiedades para las credenciales de las cuentas de TrazaLab.
- La inserción opcional de equipos de ejemplo está desactivada por defecto con `trazalab.seed.enabled=false`.

## Límites de seguridad actuales

- No existe un flujo de recuperación o cambio de contraseña para usuarios. La creación posterior de cuentas se hace desde la administración de usuarios, disponible solo para administradores.
- Las incidencias y mantenimientos quedan asociados a la cuenta autenticada; esto acredita la cuenta que inició sesión, no necesariamente quién usó físicamente el equipo.
- El formulario de incidencia recibe el QR como texto y no admite adjuntar imágenes. La pantalla de escaneo permite usar la cámara del navegador o introducir el código manualmente; la lectura con cámara requiere permisos y contexto seguro. La validación en dispositivos físicos está pendiente.
- `GET /api/computadoras/historial` devuelve los datos básicos del equipo y hasta cinco incidencias recientes con descripción, fecha y estado; no incluye el nombre del reportante y requiere una cuenta autorizada.
- Mantenimiento y reportes están implementados en código. La ficha del equipo presenta una cronología de incidencias, fallas, mantenimientos y componentes; su validación integral y la gestión del inventario en un piloto permanecen pendientes.
- El intercambio seguro entre la aplicación recolectora y TrazaLab todavía no está definido.
- La configuración local no incluye HTTPS ni está preparada para exponer la aplicación como servicio público o productivo.

## Próximas medidas

- Validar autenticación, permisos, atribución de las acciones, gestión de sesión y recuperación de cuentas antes de un piloto.
- Definir identidad, transporte cifrado, validación y permisos del agente recolector antes de recibir datos de computadoras.
- Mantener privilegios mínimos para el usuario de base de datos y proteger los secretos locales.
- Mantener migraciones versionadas antes de evolucionar el esquema compartido.

Para los requisitos pendientes y sus evidencias, consulta la [matriz de trazabilidad](Matriz%20Trazabilidad.md).
