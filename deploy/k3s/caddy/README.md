# Caddy para TrazaLab

Estos manifiestos son una base pendiente de configurar y se entregan con cero réplicas. Antes de iniciarlos:

- Sustituir `origen.invalid` por el dominio real del origen que apunta a la IP elástica. Verificar la disponibilidad del digest fijado de Caddy antes de iniciar y al actualizarlo.
- Comprobar que el volumen EBS previsto esté montado por UUID antes de iniciar K3s. Crear `/var/lib/trazalab-data/caddy` en ese volumen con propietario 10001:10001 y permisos restrictivos; verificar la política SELinux aplicable. La etiqueta del nodo y `hostPath: Directory` no demuestran que el EBS esté montado.
- Confirmar que ningún servicio use los puertos 80 y 443. Restringir el puerto 443 a los rangos de origen de CloudFront mediante el grupo de seguridad y permitir el puerto 80 para el desafío HTTP-01.
- El origen HTTPS aún no comprueba una cabecera secreta que identifique la distribución. La lista de CloudFront limita las conexiones a su infraestructura, pero no identifica una distribución concreta; resolver esta protección antes de exponer el servicio.
- Configurar CloudFront con HTTPS hacia el origen, certificado válido para el dominio y políticas compatibles con sesiones y CSRF. Probar redirecciones y cookies con el dominio público.
- Revisar los límites de CPU y memoria con mediciones. Proteger y respaldar el directorio persistente porque contiene certificados y claves privadas. El servidor debe estar encendido para renovar los certificados.

La configuración permite HTTP-01 y desactiva TLS-ALPN-01. El almacenamiento y el Secret de base de datos son independientes. Los manifiestos no crean la red, el volumen ni reglas de seguridad de AWS.
