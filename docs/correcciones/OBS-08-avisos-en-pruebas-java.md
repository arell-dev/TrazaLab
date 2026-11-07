# OBS-08: avisos en pruebas Java

## Observaciones

- `GlobalExceptionHandlerTest.FormularioApi.registrar(String)` solo se consulta mediante reflexión para construir el `MethodParameter` de la prueba de validación. El analizador Java no reconoce ese uso reflectivo y lo reporta como método sin uso local.
- `RegistroIncidenciaJpaTest` importaba `Assertions.assertFalse` sin utilizarlo.

## Corrección

- Se añadió `@SuppressWarnings("unused")` únicamente al método de prueba que sirve como fixture de reflexión.
- Se eliminó el import estático no utilizado.

## Verificación

Se ejecutaron `GlobalExceptionHandlerTest` y `RegistroIncidenciaJpaTest` con Maven. Ambas clases finalizaron correctamente.
