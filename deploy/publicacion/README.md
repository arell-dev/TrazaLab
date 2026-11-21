# Publicación y despliegue

Los workflows viven en `.github/workflows/ci.yml` y `release.yml`. La CI construye y prueba una sola imagen para esa ejecución. La publicación por etiqueta reutiliza el artefacto comprobado, lo publica en ECR por digest e invoca el documento SSM `TrazaLabDeploy` sobre una sola instancia.

## Requisitos de GitHub

- El entorno `produccion` debe permitir la rama `main` y las etiquetas `v*`: `main` sirve para reintentar una etiqueta existente con `workflow_dispatch`; los despliegues automáticos solo se disparan al publicar una etiqueta. Configura reglas de etiqueta para impedir creación, actualización y eliminación por actores no autorizados.
- Variables requeridas: `AWS_ACCOUNT_ID`, `AWS_DEPLOY_ROLE_ARN`, `AWS_INSTANCE_ID`, `AWS_DEPLOY_DOCUMENT` y `AWS_DEPLOY_DOCUMENT_VERSION`.
- Valores de destino verificados: cuenta `970909363773`, región `us-east-2`, instancia `i-08eb755e51c5be97b`, rol `trazalab-github-deploy`, documento `TrazaLabDeploy` y repositorio ECR `trazalab`.
- OIDC limita la confianza al repositorio y al entorno `produccion`. El rol publica imágenes, inicia la instancia y ejecuta únicamente el documento SSM autorizado. No utiliza claves permanentes.

El workflow acepta `vX.Y.Z`, exige que el commit esté integrado en `main` y compara con la última GitHub Release publicada. La primera publicación es `v0.0.1`. En las siguientes versiones se incrementa un solo componente en uno y se reinician a cero los componentes inferiores. `workflow_dispatch` permite reintentar una etiqueta existente desde `main` sin moverla.

La GitHub Release se crea después de verificar el estado de SSM, el rollout, el digest y los tres endpoints de salud. Si el workflow termina después de iniciar EC2, comprobar el comando SSM y las operaciones activas antes de detenerla o reintentar.

## Preparación de EC2

Antes de publicar, revisar el SSM Document y el estado real de EC2. El documento activo es la versión 1. Requisitos del nodo:

- `/usr/local/sbin/trazalab-verificar-datos` comprueba el montaje y UUID del volumen persistente.
- `/usr/local/sbin/trazalab-desplegar` está instalado como `root:root`, modo `0750`, desde una revisión fijada y comprobada.
- `/usr/local/sbin/trazalab-respaldo-verificado` está instalado y se ejecutó correctamente el 21 de noviembre de 2026.
- `/etc/trazalab/manifiestos.sha256` contiene `0283e3c34b6982949afbb85a467e996e644b9d6291f2066732b1a0da00276e20`, calculada sobre los 13 archivos revisados de `deploy/k3s` que están aplicados al clúster.
- El secreto `trazalab-ecr-pull` y el servicio de renovación de acceso a ECR deben estar disponibles.

El helper de despliegue conserva un bloqueo compartido, registra un despliegue pendiente antes de cambiar la imagen y se detiene ante fallos o estado incierto. La huella cubre `deploy/k3s`; cualquier cambio de esos manifiestos requiere revisión y aplicación al clúster antes de publicar. Un rollback de imagen no revierte migraciones Flyway.

## Estado verificado

La CI anterior pasó en PR #24. El 21 de noviembre de 2026 se organizó localmente en jobs de Maven, imagen Docker, smoke test PostgreSQL y artefacto publicable. GitHub todavía no ha ejecutado estos cambios y no hay una versión `v0.0.1` registrada.

En EC2 se verificaron K3s, los despliegues de TrazaLab, PostgreSQL y Caddy, el PVC de PostgreSQL y el estado de salud por HTTPS. Después se instaló el helper de despliegue desde una revisión fijada, y se registró la huella de los manifiestos. Antes de publicar falta comprobar las variables del entorno GitHub y ejecutar CI con estos cambios. No hay todavía una publicación real de esta rama.

## Comprobaciones locales

Desde la raíz del repositorio:

```powershell
docker run --rm --network none --user 0 --mount "type=bind,source=$((Resolve-Path -LiteralPath 'deploy\publicacion').Path),target=/prueba,readonly" --entrypoint bash postgres:16 /prueba/pruebas/probar-bloqueos-despliegue.sh
```

Esta prueba simula las barreras del helper. No invoca GitHub Actions, AWS, Systems Manager ni K3s.
