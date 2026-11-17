# OBS-08: avisos en pruebas Java

## Observaciones

- `GlobalExceptionHandlerTest.FormularioApi.registrar(String)` solo se consulta mediante reflexión para construir el `MethodParameter` de la prueba de validación. El analizador Java no reconoce ese uso reflectivo y lo reporta como método sin uso local.
- `RegistroIncidenciaJpaTest` importaba `Assertions.assertFalse` sin utilizarlo.

## Corrección

- Se añadió `@SuppressWarnings("unused")` únicamente al método de prueba que sirve como fixture de reflexión.
- Se eliminó el import estático no utilizado.

## Verificación

Se ejecutaron `GlobalExceptionHandlerTest` y `RegistroIncidenciaJpaTest` con Maven. Ambas clases finalizaron correctamente.

## Seguimiento (07/11/2026)

- El analizador seguía señalando `FormularioApi.registrar(String)` aunque el uso reflectivo estaba documentado con `@SuppressWarnings("unused")`. Se cambió el método de privado a público para eliminar la condición que origina el aviso de miembro privado sin uso local.
- El diagnóstico sobre `Assertions.assertFalse` no coincide con el archivo actual: ese import ya no existe en `RegistroIncidenciaJpaTest.java`, por lo que no requirió otra edición.
- `GlobalExceptionHandlerTest` pasó sus cinco pruebas con Maven. La compilación también incluyó las fuentes de prueba del proyecto.

## Seguimiento (16/11/2026)

- El editor volvió a marcar como no utilizado el método local `FormularioApi.registrar(String)`, que solo se consultaba por reflexión.
- La prueba ahora obtiene `MethodParameter` del método `registrar` real de `IncidenciaWebController` y usa el nombre real del objeto de formulario. Se eliminó la clase fixture que generaba el aviso.
- No se ejecutaron pruebas durante esta corrección.
