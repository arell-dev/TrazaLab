# Apagado automático seguro

## Estado

El módulo funcional se integró en `main` mediante el PR #27 el 23/11/2026, en el commit `ccf703ef6964f560aa6567ab25844937783a94ae`. La CI del PR y la CI de `main` pasaron; también se ejecutaron pruebas automatizadas locales y simulaciones aisladas. En AWS están desplegadas la tabla DynamoDB y la regla EventBridge. El worker está instalado en EC2 y `trazalab-apagado.timer` se habilitó el 23/11/2026. Una prueba real confirmó apagado automático y recuperación de PostgreSQL y TrazaLab. Los temporizadores siguen instalándose deshabilitados para permitir la validación antes de activarlos en un entorno nuevo.

## Construcción e historial

El prototipo experimental se añadió en [`959d42d` (PR #24)](https://github.com/arell-dev/TrazaLab/commit/959d42d9083a56380eabd71ff6f44e0f0b11ead0) y se trasladó al directorio de laboratorio en [`216565e` (PR #25)](https://github.com/arell-dev/TrazaLab/commit/216565e21e87a59a002368bab97457089c6d5b85). Ese antecedente incluía una máquina de estados experimental; su helper de preparación se protegía fallando siempre y no implementaba un apagado operativo. El control de actividad tampoco podía confirmar el fin de solicitudes asíncronas. La documentación advertía que no debía desplegarse la máquina por el riesgo de carrera entre drenaje y parada, y que el coordinador aún no estaba integrado en los actores. Se retiró el duplicado del árbol de trabajo y se conservan ambos commits como historial del proyecto.

El reemplazo funcional se construyó en este orden: (1) el backend registra actividad autenticada y espera la finalización de solicitudes asíncronas; (2) panel, publicación y apagado coordinan un solo permiso condicional en DynamoDB; (3) el worker local confirma inactividad, drenaje, PVC/PV y montaje, detiene TrazaLab antes de PostgreSQL y programa el apagado del sistema operativo; (4) el retorno de EC2 recupera PostgreSQL antes de TrazaLab, mientras SSM realiza esa recuperación bajo el permiso de despliegue antes del respaldo; (5) las pruebas aisladas cubren conflictos, errores y estados inciertos. El historial registra esta implementación como **1 commit squash**, integrado por el PR #27 en `ccf703ef6964f560aa6567ab25844937783a94ae`. La secuencia funcional de construcción se refleja en ese cambio y en las pruebas; no equivale a cinco commits independientes.

## Componentes

| Ruta | Responsabilidad |
|---|---|
| `ec2/trazalab-apagar-inactivo.sh` | Valida inactividad, adquiere el permiso compartido, drena la aplicación y detiene los servicios en orden. |
| `ec2/trazalab-recuperar-arranque.sh` | Tras un inicio de EC2, restaura PostgreSQL antes de TrazaLab y libera el permiso solo al confirmar el estado. |
| `ec2/coordinador.sh` | Adquiere, consulta y libera condicionalmente el permiso DynamoDB. |
| `ec2/actividad_socket.py` | Cliente root del socket local usado para observar, iniciar y cancelar el drenaje. |
| `ec2/apagado.conf.example` | Ejemplo de configuración root para la instancia y la tabla. |
| `iam/politica-instancia.json` | Permisos de tabla previstos para el rol de EC2, limitados a cuenta, región, recurso y clave de partición. |
| `systemd/` | Servicios, temporizadores de recuperación y apagado, y permisos de los directorios en `/run`. |
| `instalar-apagado.sh` | Instala los archivos locales; no habilita el temporizador. |
| `pruebas/` | Comprobaciones aisladas del coordinador y del worker de apagado. |

## Actividad y solicitudes

El reloj es monotónico y parte de la inicialización de la aplicación. El worker revisa cada cinco minutos, con una primera espera de diez minutos tras el arranque. El umbral es 30 minutos de inactividad; por la frecuencia de revisión, el apagado normal puede ocurrir hasta unos cinco minutos después del umbral.

Un inicio de sesión exitoso y las solicitudes autenticadas de usuarios no anónimos cuentan como actividad. Las rutas de salud, archivos estáticos y solicitudes anónimas no reinician el reloj. Las solicitudes autenticadas permanecen activas hasta completar la operación asíncrona; si el contenedor no confirma su finalización, se conserva el contador y se impide apagar.

No se clasifica el tráfico automatizado con una sesión válida. Por tanto, una integración que use una cuenta autenticada puede mantener la instancia activa. No se infiere inactividad a partir de pérdida de red ni se distingue una persona por el agente de usuario.

## Coordinación y parada

La tabla `trazalab-operacion` conserva un único elemento, adquirido mediante escritura condicional. No usa vencimiento automático: un resultado incierto debe bloquear las demás operaciones hasta que una persona reconcilie el estado. La definición local de CloudFormation está en `../arranque/cloudformation/infraestructura.yaml`; allí se declaran la tabla y la regla de EventBridge. No se han creado esos recursos en AWS. Revisa el [precio de DynamoDB](https://aws.amazon.com/dynamodb/pricing/) antes de desplegar la tabla.

| Actor | Permiso y liberación |
|---|---|
| Panel | Mantiene `arranque` mientras EC2 está `pending`; EventBridge libera tras confirmar `running`. |
| GitHub Actions | Mantiene `despliegue` desde antes de iniciar EC2 hasta que SSM confirma el despliegue. Si se inicia EC2 o se envía SSM con resultado incierto, conserva el permiso. |
| Respaldo | Se ejecuta dentro de `despliegue` y del bloqueo local existente. No hay un actor de respaldo independiente. |
| Worker | Mantiene `apagado` desde el inicio del drenaje hasta que EventBridge confirma EC2 en `stopped`. No ejecuta `StopInstances` ni `TerminateInstances`; programa `systemctl poweroff`. |

El worker también exige el bloqueo local `/run/trazalab/operacion.lock`, el PVC `Bound`, una política de retención `Retain`, el `hostPath` PostgreSQL esperado y el montaje de datos. Detiene primero TrazaLab, espera que sus pods terminen, verifica otra vez el permiso y detiene PostgreSQL. Solo entonces programa el apagado del sistema operativo. Ante un error recuperable intenta iniciar PostgreSQL y luego TrazaLab, cancela el drenaje y libera el permiso únicamente si confirma la recuperación. Si no puede demostrar ese estado, conserva el permiso para conciliación manual.

Al volver a iniciar EC2, las réplicas de Kubernetes continúan en cero si se detuvieron antes del `poweroff`. `trazalab-recuperar-arranque.sh` toma el mismo permiso, valida el volumen, inicia PostgreSQL, espera su disponibilidad y luego inicia TrazaLab. El temporizador vuelve a intentarlo cada minuto, se detiene al completar la recuperación o cuando ambos servicios ya están activos y limita la espera a 30 intentos. Si se agotan los intentos o una recuperación parcial queda incierta, detiene el temporizador y deja el permiso para conciliación manual. Si GitHub Actions mantiene `despliegue`, el helper SSM activa ambos servicios antes del respaldo; así no compite con la recuperación de arranque.

## Instalación y activación

El archivo `/etc/trazalab/apagado.conf` debe ser propiedad de root y tener modo `0600`. El administrador debe configurar la instancia, la región y la tabla, y preparar el rol de EC2 con los permisos del módulo después de revisar la política. La plantilla de CloudFormation y ambas políticas IAM requieren revisión antes de desplegar.

`sudo ./instalar-apagado.sh` instala el worker y las unidades de systemd. No ejecuta `systemctl enable` ni inicia los temporizadores. Para una futura activación, primero se deben revisar y desplegar la tabla, la regla y los permisos efectivos; después habilitar solo `trazalab-recuperar-arranque.timer` y verificar un ciclo de inicio y restauración de PostgreSQL. Habilitar `trazalab-apagado.timer` únicamente tras confirmar esa recuperación, el montaje y retención del EBS, el respaldo/restauración y la coordinación con publicación. Esos pasos permanecen pendientes; no se ejecutaron en esta implementación local.

## Comprobaciones locales

- `npm.cmd test` en `deploy/arranque`: 23 pruebas pasaron.
- Pruebas Maven dirigidas a actividad, filtros, seguridad y seguimiento asíncrono: 9 pruebas pasaron.
- En `postgres:16`, con red deshabilitada y el repositorio de solo lectura, pasaron las pruebas del coordinador, del apagado (29:59, 30:00, permiso ocupado, error de red, montaje inesperado y pods que no terminan), de recuperación de arranque (orden PostgreSQL/TrazaLab, permiso ocupado, volumen incorrecto y límite de reintentos), de las barreras de publicación y del respaldo simulado.
- La simulación confirma decisiones del código local. No valida el comportamiento real de EventBridge, IAM, SSM, EBS, el arranque desde el panel ni la integridad de PostgreSQL después de detener EC2.

Para repetir la prueba aislada del apagado y la recuperación desde PowerShell, desde la raíz de `Proyecto/trazalab`:

```powershell
$repo = (Resolve-Path -LiteralPath '.').Path
docker run --rm --network none --user 0:0 `
  --mount "type=bind,source=$repo,target=/prueba,readonly" `
  --entrypoint bash postgres:16 `
  /prueba/deploy/apagado/pruebas/probar-apagado-seguro.sh
```

Para las reglas vigentes del producto, consulta `../../../../AGENTS.md` y el plan único de la raíz del proyecto.
