# VS Code para infraestructura AWS

El clúster K3s de EC2 se administra mediante Systems Manager. El editor no necesita acceso directo ni un kubeconfig local para editar los manifiestos. Si la extensión Kubernetes solicita crear `C:\Users\arel\.kube\config`, no es necesario crear un archivo vacío ni exponer el servidor de API. La opción `vs-kubernetes.suppress-kubeconfig-not-found-alerts` oculta solo ese aviso; no configura una conexión ni desactiva K3s. Se puede revertir o desactivar la extensión Kubernetes para este espacio de trabajo desde la vista Extensiones.

Las plantillas de CloudFormation del panel utilizan etiquetas intrínsecas que YAML genérico no reconoce automáticamente. La extensión Red Hat YAML admite `yaml.customTags`: se han configurado las formas usadas actualmente, `!Ref`, `!Sub`, `!GetAtt`, `!Equals` y `!If`. La forma de `!If` se declara booleana porque ese es su único uso actual; si se añade un uso de otro tipo debe revisarse esta configuración. El reconocimiento de etiquetas se aplica al espacio de trabajo YAML, por lo que no sustituye la validación de CloudFormation ni elimina otras comprobaciones del editor.

La configuración se aplicó localmente en `.vscode/settings.json` tanto al abrir la raíz `ProyectoFinal` como al abrir `Proyecto/trazalab`. La segunda carpeta `.vscode` está ignorada por Git: cada integrante puede incorporar estas opciones a su configuración local, conservando sus ajustes existentes:

```json
{
  "vs-kubernetes.suppress-kubeconfig-not-found-alerts": true,
  "yaml.customTags": [
    "!Ref scalar",
    "!Sub scalar",
    "!GetAtt scalar",
    "!Equals sequence:boolean",
    "!If sequence:boolean"
  ]
}
```

Con `cfn-lint` instalado y accesible, validar ambas plantillas desde `Proyecto/trazalab`:

```powershell
cfn-lint -r us-east-2 -t deploy/arranque/infraestructura.yaml deploy/arranque/alojamiento.yaml
```

El 20 de noviembre de 2026, `cfn-lint` 1.61.0 terminó sin diagnósticos para ambas plantillas y `ValidateTemplate` de CloudFormation las aceptó en `us-east-2` tras verificar la identidad del proyecto. Estas comprobaciones no demuestran que todos los recursos se puedan desplegar ni reemplazan las verificaciones de seguridad. Se confirmó que los avisos de YAML y kubeconfig desaparecieron tras abrir `Proyecto/trazalab` y recargar la ventana.

En una comprobación adicional por protocolo LSP con el servidor instalado de Red Hat YAML 2.0.0, las dos plantillas produjeron 42 y 19 diagnósticos sin `customTags` y ninguno con la configuración anterior. Un YAML malformado y una etiqueta desconocida siguieron generando errores. El ensayo no verifica que la ventana de VS Code haya cargado efectivamente la configuración ni que CloudFormation acepte todas las propiedades.

## Aviso del workflow reutilizable

En `release.yml`, `uses: ./.github/workflows/ci.yml` es una ruta relativa a la raíz del repositorio Git, `Proyecto/trazalab`. El archivo existe y declara `workflow_call`. No se debe cambiar la ruta a `Proyecto/trazalab/...`: GitHub ejecuta el workflow desde la raíz de ese repositorio.

La extensión GitHub Actions 0.32.3 puede emitir `Unable to find reusable workflow` si busca la ruta desde `ProyectoFinal`, que contiene el repositorio anidado. Para editar y validar los workflows, abre `Proyecto/trazalab` como carpeta de VS Code con **Archivo > Abrir carpeta**, y recarga la ventana con **Developer: Reload Window** si el diagnóstico permanece. Comprueba también que VS Code haya detectado ese repositorio en Control de código fuente. Si sigue apareciendo en esa carpeta, revisa la salida de la extensión antes de modificar el YAML.

La prueba con el servidor real de la extensión reprodujo el aviso usando `ProyectoFinal` como raíz y no lo produjo usando `Proyecto/trazalab`. Se confirmó que el aviso desapareció tras abrir el repositorio y recargar. Ambos archivos de workflow siguen sin seguimiento en Git: deben incluirse juntos en un commit antes de que GitHub pueda ejecutarlos; la ausencia del aviso no demuestra una ejecución real de Actions.
