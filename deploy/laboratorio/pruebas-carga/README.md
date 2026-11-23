# Ensayo de carga aislado

El criterio RE-01 se refiere a **15–20 equipos**, no a sesiones concurrentes. Una prueba con 20 sesiones es un escenario de estrés supuesto; no demuestra el piloto ni la capacidad de EC2.

La prueba debe crear su propia aplicación y PostgreSQL con datos y cuentas sintéticos, red privada sin salida y almacenamiento efímero. No debe aceptar URL de destino, usar credenciales existentes ni conectarse a una base real. El entorno productivo no debe recibir solicitudes de prueba.

Desde la raíz de `Proyecto/trazalab`, con Docker local, Python y el JAR generado por Maven:

```powershell
python deploy/laboratorio/pruebas-carga/test_seguridad.py
python deploy/laboratorio/pruebas-carga/ejecutar.py
```

El script construye una imagen local desde el JAR actual y crea tres contenedores: PostgreSQL, aplicación y generador. Utiliza una red Docker interna, no publica puertos, solo admite daemon local y no acepta ninguna URL de destino. La base usa `tmpfs`; las cuentas, contraseñas y 20 computadoras son sintéticas. La aplicación y la base reciben credenciales aleatorias temporales; no se utiliza ninguna cuenta real. Un fallo o interrupción abrupta puede dejar recursos temporales: revisa los nombres `trazalab-ensayo-*` antes de eliminarlos; no borres recursos ajenos. Si la limpieza falla, el script informa los nombres y termina con error.

Veinte sesiones independientes realizan login con CSRF y una lectura de cinco rutas cada una. Se crean y comprueban 20 equipos, 60 incidencias, 60 fallas, 60 mantenimientos y 20 componentes con sus asignaciones, todos sintéticos. No se envían registros ni mantenimientos durante la carga. La salida incluye logins fallidos con motivos genéricos, latencias mediana y p95, errores, duración, tasa de lecturas, muestras puntuales de CPU y memoria durante el ensayo y uso de disco temporal. Si falla un login se conservan las métricas de las otras sesiones y el proceso termina con error. La tasa cuenta lecturas y el tiempo incluye los logins. No hay tiempo de espera entre peticiones y las 20 sesiones empiezan juntas: es una ráfaga sintética, no una simulación de comportamiento humano ni una prueba de duración.

Se registran el SHA-256 del JAR, los identificadores de las tres imágenes, el commit Git y si hay cambios locales. El commit y la marca de cambios son informativos: no prueban que ese JAR se haya construido con el estado actual del código. Compílalo y verifica su procedencia antes del ensayo.

Una medición local aislada no reproduce CloudFront, la red universitaria, el tamaño de instancia, los créditos de CPU ni la carga real del entorno académico. En el ensayo con historial sintético, 20 sesiones y 100 lecturas terminaron sin errores en 2,50 segundos. Las muestras de CPU y memoria son puntuales, no picos garantizados: la aplicación marcó 693 % de CPU en el equipo local, sin limitar los núcleos a los de EC2. La prueba no incluye escrituras durante la carga, no determina la suficiencia de `t3.small` y no valida el piloto.

El protocolo y la medición temporal ejecutada en AWS, junto con sus resultados y límites, se documentan en [Ensayo EC2](./Ensayo%20EC2.md). La medición no determina la suficiencia de `t3.small` para el piloto.
