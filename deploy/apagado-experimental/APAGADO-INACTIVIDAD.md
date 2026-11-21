# Diseño pendiente: apagado por inactividad

Estado: **apagado no implementado ni habilitado**. Existen prototipos locales de actividad, drenaje y canal Unix en Spring, deshabilitados por defecto mediante propiedades independientes. No hay temporizador, coordinador operativo, servicio instalado, permisos de apagado ni prueba de extremo a extremo. Hasta contar con una implementación revisada, se detiene la instancia manualmente cuando corresponda.

El prototipo cuenta solicitudes autenticadas posteriores a la autorización y registra el evento de inicio de sesión correcto. Reinicia el plazo al finalizar una solicitud y rechaza las nuevas solicitudes durante el drenaje sin cancelarlo; la cancelación debe ser explícita. Conservadoramente, si observa una solicitud asíncrona, impide el drenaje durante el resto de la vida del proceso: todavía no verifica su finalización. Las pruebas locales comprueban reloj, solicitudes simultáneas, sesión anónima, endpoint de salud y este bloqueo conservador. El canal Unix opcional puede solicitar el drenaje, pero no está integrado con un mecanismo de parada ni probado integralmente. No activar las propiedades en producción antes de completar el protocolo y sus pruebas.

Un filtro adicional admite el POST de login antes de procesar credenciales: mientras está en curso impide iniciar el drenaje; durante el drenaje lo rechaza. Un login fallido no reinicia el plazo y el evento de autenticación correcta sí lo hace. Existe una prueba parcial de la cadena Spring Security con MockMvc, además de las pruebas de filtros aislados; ninguna valida el canal, el helper ni el apagado de extremo a extremo. La preparación todavía no puede mantener la exclusión hasta el apagado ni comunicar una confirmación segura al coordinador externo, por lo que no debe conectarse a la parada.

## Comportamiento previsto

El plazo es de 30 minutos sin solicitudes de personas autenticadas. Una solicitud solo cuenta cuando Spring Security ha reconocido una identidad no anónima; `isAuthenticated()` por sí solo no basta. El acceso correcto cuenta también al iniciar sesión. Las sondas, recursos estáticos y solicitudes anónimas no reinician el plazo. La autenticación no permite distinguir por sí sola una persona de un bot con una sesión válida; esa limitación necesita una decisión de producto y una prueba antes de declarar cumplido el requisito de excluir bots.

Mientras la aplicación arranca o no se conoce con certeza el estado de actividad, no se apaga. Tras un reinicio se inicia un nuevo período de gracia de 30 minutos medido con reloj monotónico. Una sesión abierta sin solicitudes no equivale a actividad: el requisito se basa en solicitudes, no en presencia.

## Coordinación necesaria

El bloqueo local `/run/trazalab/operacion.lock` protege el despliegue y el respaldo cuando ya se ejecutan en el nodo. No protege el intervalo entre encender EC2 y comenzar el comando SSM, ni coordina con el panel de encendido o una solicitud de despliegue desde GitHub. Por sí solo, no permite garantizar que una parada no interrumpa un inicio o despliegue.

Hay un prototipo local del coordinador y de sus contratos en `COORDINADOR.md`. Sus pruebas usan sustitutos en memoria; no está integrado ni validado en AWS y no resuelve aún la custodia al apagarse el nodo.

Hace falta un coordinador externo disponible con EC2 detenida, compartido por todos los mecanismos autorizados de encendido, despliegue y parada. Debe admitir adquisición condicional exclusiva y proteger los cambios de estado frente a procesos concurrentes. Un bloqueo sin caducidad automática evita que la expiración permita a otro actor iniciar mientras una parada tardía aún puede completarse; si el dueño falla, se requiere recuperación administrativa verificada, con costo de disponibilidad. Un lease con caducidad necesita además fencing efectivo en todas las operaciones, incluida la llamada a EC2; no se debe asumir que un simple token de fencing impide una llamada tardía a `StopInstances`.

La selección, permisos, costo y retención del coordinador aún no se han decidido. Deben bloquearse o incluirse en el mismo protocolo las rutas alternativas de encendido y parada; de lo contrario no existe garantía global. El coordinador no reemplaza al bloqueo local de despliegue y respaldo.

## Secuencia candidata

1. El observador obtiene actividad, salud y estado de arranque de una fuente local autenticada y confiable. Si no puede leerlos, son incoherentes o el proceso ha reiniciado, cancela la parada.
2. Al cumplir 30 minutos, adquiere el bloqueo externo de parada y el bloqueo local existente. Si cualquiera falla, cancela. Conserva ambos hasta confirmar el resultado y liberar de forma segura.
3. Solicita a la aplicación entrar en drenaje mediante un canal administrativo local autenticado y sin exposición pública. La transición a drenaje y el registro de solicitudes activas deben ser atómicos entre sí: durante el drenaje no se admiten nuevas operaciones de usuario y se informa una respuesta temporal clara. El canal Unix es experimental: faltan pruebas de identidad, permisos y espacios de nombres, limpieza de sockets residuales y recuperación después de una caída.
4. Espera a que terminen todas las solicitudes admitidas, incluidas las asíncronas, y confirma otra vez que han transcurrido los 30 minutos desde la última actividad. Las solicitudes nuevas se rechazan mientras dure el drenaje. Si hay timeout, fallo o incertidumbre, reanuda la atención y cancela la parada.
5. Comprueba de nuevo despliegue, respaldo y estado del coordinador; inicia el cierre ordenado de la aplicación y PostgreSQL. No llamar a `StopInstances` si no se confirma el cierre. El timeout nunca debe convertirse en una parada forzada silenciosa.
6. Solicita `StopInstances` para la instancia concreta con permisos mínimos y comprueba finalmente el estado `stopped` desde un componente externo. Una respuesta aceptada o una desconexión de red no demuestran que EC2 se detuvo. Si el resultado es incierto, conserva el bloqueo externo y requiere conciliación administrativa antes de otro inicio o parada.

El protocolo debe definir cómo el panel y GitHub adquieren el bloqueo externo antes de iniciar EC2 y cómo lo liberan al terminar, con recuperación segura ante cancelación, caída del runner y pérdida de red. El panel debe distinguir parada en curso de instancia detenida. Los comandos SSM deben seguir adquiriendo el bloqueo local; no deben adquirirlo dos veces en el mismo proceso.

## Pruebas y criterios de habilitación

- Pruebas unitarias con reloj controlado para 29:59 y 30:00, inicio de sesión, sesión anónima, salud, estáticos, reinicio y actividad autenticada concurrente.
- Pruebas de integración de Spring Security y operaciones asíncronas: la transición a drenaje no pierde una solicitud y espera a las ya admitidas; errores y timeout reanudan el servicio sin apagar.
- Pruebas de concurrencia entre parada, encendido desde el panel, despliegue GitHub/SSM y respaldo; simular bloqueo ocupado, interrupción del runner, partición de red y fallo del coordinador. Ningún caso incierto debe autorizar una parada.
- Verificar cierre ordenado y recuperación de PostgreSQL, montaje EBS, arranque posterior y ausencia de pérdida de datos con una base aislada restaurada.
- Medir en AWS el tiempo real desde la última solicitud hasta `stopped`, demostrar que tráfico de salud no lo prolonga y registrar los límites de detección de bots. Comprobar también que el encendido y el despliegue sobreviven a las carreras probadas.
- Revisar cambios de seguridad, costes y configuración IAM; solo después, documentar cómo habilitar, vigilar y deshabilitar el mecanismo. No hay resultados de estas pruebas todavía.
