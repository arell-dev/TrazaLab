# Publicación y despliegue

Los workflows viven en `.github/workflows/ci.yml` y `release.yml`. La CI construye y prueba una sola imagen para esa ejecución. La publicación por etiqueta reutiliza el artefacto comprobado, lo publica en ECR por digest e invoca el documento SSM `TrazaLabDeploy` sobre una sola instancia.

## Requisitos de GitHub

- El entorno `produccion` permite la rama `main` y las etiquetas `v*`: `main` sirve para reintentar una etiqueta existente con `workflow_dispatch`; los despliegues automáticos solo se disparan al publicar una etiqueta. Las reglas del entorno y el ruleset de etiquetas `v*` están configurados; el rol administrador puede omitir el ruleset.
- Variables requeridas: `AWS_ACCOUNT_ID`, `AWS_DEPLOY_ROLE_ARN`, `AWS_INSTANCE_ID`, `AWS_DEPLOY_DOCUMENT` y `AWS_DEPLOY_DOCUMENT_VERSION`.
- Valores de destino verificados: cuenta `970909363773`, región `us-east-2`, instancia `i-08eb755e51c5be97b`, rol `trazalab-github-deploy`, documento `TrazaLabDeploy` y repositorio ECR `trazalab`.
- OIDC limita la confianza a `aud=sts.amazonaws.com` y al sujeto inmutable `repo:arell-dev@270571076/TrazaLab@1427248599:environment:produccion`. El rol publica imágenes solo en ECR `trazalab`, inicia la instancia prevista y ejecuta únicamente el documento SSM autorizado. No utiliza claves permanentes. La obtención real de credenciales OIDC todavía no se ha ejercitado en una publicación.

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

PR #25 integró `ci.yml` y `release.yml` en `main` mediante squash commit `216565e21e87a59a002368bab97457089c6d5b85` el 21 de noviembre de 2026. Las ejecuciones [44947404777](https://github.com/arell-dev/TrazaLab/actions/runs/44947404777) (PR) y [44946955832](https://github.com/arell-dev/TrazaLab/actions/runs/44946955832) (push) pasaron Maven/Testcontainers, construcción Docker y smoke test con PostgreSQL 16; el artefacto publicable se omitió en PR como estaba previsto. GitHub reconoce activos ambos workflows.

PR #26 corrigió el reinicio de componentes inferiores al incrementar la versión y añadió nueve casos a CI. Sus ejecuciones [44953111564](https://github.com/arell-dev/TrazaLab/actions/runs/44953111564) (PR) y [44953107224](https://github.com/arell-dev/TrazaLab/actions/runs/44953107224) (push) pasaron todos los jobs; también pasaron cuatro casos del helper. Las pruebas negativas [44951700474](https://github.com/arell-dev/TrazaLab/actions/runs/44951700474) y [44952020019](https://github.com/arell-dev/TrazaLab/actions/runs/44952020019) fallaron en `validar` como se esperaba y omitieron CI, OIDC y publicación. No se creó una etiqueta ni una release.

En EC2 se verificaron K3s, los despliegues de TrazaLab, PostgreSQL y Caddy, el PVC de PostgreSQL y el estado de salud por HTTPS. Después se instaló el helper de despliegue desde una revisión fijada, y se registró la huella de los manifiestos. Las variables del entorno `produccion`, sus reglas para `main` y `v*`, la confianza OIDC y el alcance del rol IAM están revisados. La responsable reportó que el panel y la aplicación son accesibles desde la red universitaria; aún falta confirmar el encendido real, el login y la consulta de datos desde esa red. La publicación real por ECR/SSM no se ha ejecutado y no existe la release `v0.0.1`.

## Comprobaciones locales

Desde la raíz del repositorio:

```powershell
docker run --rm --network none --user 0 --mount "type=bind,source=$((Resolve-Path -LiteralPath 'deploy\publicacion').Path),target=/prueba,readonly" --entrypoint bash postgres:16 /prueba/pruebas/probar-bloqueos-despliegue.sh
```

Esta prueba simula las barreras del helper. No invoca GitHub Actions, AWS, Systems Manager ni K3s.
