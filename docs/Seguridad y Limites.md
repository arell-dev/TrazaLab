# Seguridad y límites de TrazaLab

## Controles implementados

- El formulario web valida que el QR y la descripción estén presentes y dentro de sus longitudes máximas.
- El servicio exige que el usuario reportante exista, esté activo y tenga un rol permitido.
- El controlador obtiene la identidad del principal autenticado y busca esa cuenta en PostgreSQL; no confía en un ID recibido del formulario.
- Los mantenimientos también usan la cuenta de la sesión; el servicio y la regla de base de datos requieren un usuario activo con rol técnico.
- Spring Security autentica el formulario de acceso consultando `usuario` mediante `UsuarioAutenticacionServicio`; solo acepta cuentas activas con `password_hash` BCrypt.
- La autorización web usa el rol relacionado de la cuenta almacenada en PostgreSQL. Las rutas de administración de usuarios requieren `ROLE_Administrador`.
- El primer administrador se crea bajo el perfil explícito `crear-administrador`, desde una consola interactiva que no muestra la contraseña. El proceso guarda solo el hash BCrypt en PostgreSQL y no vuelve a crear cuentas en arranques normales; puede completar una única fila administradora si esta quedó sin hash.
- Un mantenimiento correctivo referencia una falla de la misma computadora; el registro no cambia el estado del equipo ni de la incidencia.
- El registro de la incidencia y sus asociaciones se guarda en PostgreSQL dentro de una transacción.
- Spring Security conserva protección CSRF para formularios; el endpoint de salud de la API permanece público y las rutas web protegidas requieren sesión y rol.
- Los eventos de aplicación se emiten por `LoggingAplicacion`; el adaptador actual escribe acción y resultado en un archivo separado de los logs técnicos.
- La bitácora no registra descripciones, nombres, correos ni contraseñas.
- Las credenciales de conexión a PostgreSQL se suministran desde `.env`, excluido de Git. No se usan variables de entorno ni propiedades para las credenciales de las cuentas de TrazaLab.
- La inserción de datos demo está desactivada por defecto con `trazalab.seed.enabled=false`. Los correos configurados bajo `trazalab.seed.*` solo se usan para crear cuentas de muestra cuando el sembrado se activa.

## Límites de seguridad actuales

- No existe un flujo de recuperación o cambio de contraseña para usuarios. La creación posterior de cuentas se hace desde la administración de usuarios, disponible solo para administradores.
- Las incidencias y mantenimientos quedan asociados a la cuenta autenticada; esto acredita la cuenta que inició sesión, no necesariamente quién usó físicamente el equipo.
- El formulario no admite escanear la cámara ni adjuntar imágenes; el QR se escribe como texto.
- `GET /api/computadoras/historial` devuelve los datos básicos del equipo y hasta cinco incidencias recientes con descripción, fecha y estado; no incluye el nombre del reportante y requiere una cuenta autorizada.
- Los módulos de mantenimiento y reportes funcionan en modo demo; el historial completo por equipo y la gestión integral del inventario siguen pendientes.
- El intercambio seguro entre la aplicación recolectora y TrazaLab todavía no está definido.
- La configuración local no incluye HTTPS ni está preparada para exponer la aplicación como servicio público o productivo.

## Próximas medidas

- Validar autenticación, permisos, atribución de las acciones, gestión de sesión y recuperación de cuentas antes de un piloto.
- Definir identidad, transporte cifrado, validación y permisos del agente recolector antes de recibir datos de computadoras.
- Mantener privilegios mínimos para el usuario de base de datos y proteger los secretos locales.
- Mantener migraciones versionadas antes de evolucionar el esquema compartido.

Para los requisitos pendientes y sus evidencias, consulta la [matriz de trazabilidad](Matriz%20Trazabilidad.md).
