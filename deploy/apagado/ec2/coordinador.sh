#!/usr/bin/env bash

TRAZALAB_COORDINADOR_TABLA=${TRAZALAB_COORDINADOR_TABLA:-trazalab-operacion}
TRAZALAB_COORDINADOR_REGION=${TRAZALAB_COORDINADOR_REGION:-us-east-2}
TRAZALAB_COORDINADOR_CLAVE=trazalab-operacion

trazalab_coord_validar() {
  [[ "$TRAZALAB_COORDINADOR_TABLA" =~ ^[A-Za-z0-9_.-]{3,255}$ ]]
  [[ "$TRAZALAB_COORDINADOR_REGION" =~ ^[a-z0-9-]+$ ]]
}

trazalab_coord_adquirir() {
  local operacion=$1
  local propietario=$2
  local instancia=$3
  local sesion=$4
  local iniciado item salida

  [[ "$operacion" =~ ^(arranque|despliegue|respaldo|apagado)$ ]]
  [[ "$propietario" =~ ^[a-f0-9-]{36}$ ]]
  [[ "$instancia" =~ ^i-[a-f0-9]{8,17}$ ]]
  [[ "$sesion" =~ ^[a-f0-9-]{36}$ ]]
  trazalab_coord_validar

  iniciado=$(date -u +%Y-%m-%dT%H:%M:%SZ)
  item=$(printf '{"pk":{"S":"%s"},"operacion":{"S":"%s"},"propietario":{"S":"%s"},"instancia":{"S":"%s"},"sesion":{"S":"%s"},"iniciado":{"S":"%s"}}' \
    "$TRAZALAB_COORDINADOR_CLAVE" "$operacion" "$propietario" "$instancia" "$sesion" "$iniciado")

  if salida=$(aws dynamodb put-item --table-name "$TRAZALAB_COORDINADOR_TABLA" --region "$TRAZALAB_COORDINADOR_REGION" --item "$item" --condition-expression 'attribute_not_exists(pk)' --no-cli-pager 2>&1); then
    return 0
  fi

  if [[ "$salida" == *ConditionalCheckFailedException* ]]; then
    return 2
  fi

  echo 'No se pudo confirmar la adquisición del coordinador.' >&2
  return 1
}

trazalab_coord_verificar() {
  local operacion=$1
  local propietario=$2
  local instancia=$3
  local sesion=$4
  local respuesta

  [[ "$operacion" =~ ^(arranque|despliegue|respaldo|apagado)$ ]]
  [[ "$propietario" =~ ^[a-f0-9-]{36}$ ]]
  [[ "$instancia" =~ ^i-[a-f0-9]{8,17}$ ]]
  [[ "$sesion" =~ ^[a-f0-9-]{36}$ ]]
  trazalab_coord_validar

  if ! respuesta=$(aws dynamodb get-item --table-name "$TRAZALAB_COORDINADOR_TABLA" --region "$TRAZALAB_COORDINADOR_REGION" --key '{"pk":{"S":"trazalab-operacion"}}' --consistent-read --no-cli-pager --output json 2>/dev/null); then
    echo 'No se pudo consultar el coordinador de forma consistente.' >&2
    return 1
  fi

  python3 -c 'import json,sys; i=json.load(sys.stdin).get("Item",{}); sys.exit(0 if i.get("operacion",{}).get("S")==sys.argv[1] and i.get("propietario",{}).get("S")==sys.argv[2] and i.get("instancia",{}).get("S")==sys.argv[3] and i.get("sesion",{}).get("S")==sys.argv[4] else 1)' \
    "$operacion" "$propietario" "$instancia" "$sesion" <<< "$respuesta"
}

trazalab_coord_verificar_operacion() {
  local operacion=$1
  local instancia=$2
  local respuesta

  [[ "$operacion" =~ ^(arranque|despliegue|respaldo|apagado)$ ]]
  [[ "$instancia" =~ ^i-[a-f0-9]{8,17}$ ]]
  trazalab_coord_validar

  if ! respuesta=$(aws dynamodb get-item --table-name "$TRAZALAB_COORDINADOR_TABLA" --region "$TRAZALAB_COORDINADOR_REGION" --key '{"pk":{"S":"trazalab-operacion"}}' --consistent-read --no-cli-pager --output json 2>/dev/null); then
    echo 'No se pudo consultar el coordinador de forma consistente.' >&2
    return 1
  fi

  python3 -c 'import json,re,sys; i=json.load(sys.stdin).get("Item",{}); o=i.get("propietario",{}).get("S",""); s=i.get("sesion",{}).get("S",""); sys.exit(0 if i.get("operacion",{}).get("S")==sys.argv[1] and i.get("instancia",{}).get("S")==sys.argv[2] and re.fullmatch(r"[a-f0-9-]{36}",o) and re.fullmatch(r"[a-f0-9-]{36}",s) else 1)' \
    "$operacion" "$instancia" <<< "$respuesta"
}

trazalab_coord_liberar() {
  local operacion=$1
  local propietario=$2
  local instancia=$3
  local sesion=$4
  local salida

  [[ "$operacion" =~ ^(arranque|despliegue|respaldo|apagado)$ ]]
  [[ "$propietario" =~ ^[a-f0-9-]{36}$ ]]
  [[ "$instancia" =~ ^i-[a-f0-9]{8,17}$ ]]
  [[ "$sesion" =~ ^[a-f0-9-]{36}$ ]]
  trazalab_coord_validar

  if salida=$(aws dynamodb delete-item --table-name "$TRAZALAB_COORDINADOR_TABLA" --region "$TRAZALAB_COORDINADOR_REGION" \
    --key '{"pk":{"S":"trazalab-operacion"}}' \
    --condition-expression 'operacion = :operacion AND propietario = :propietario AND instancia = :instancia AND sesion = :sesion' \
    --expression-attribute-values "{\":operacion\":{\"S\":\"$operacion\"},\":propietario\":{\"S\":\"$propietario\"},\":instancia\":{\"S\":\"$instancia\"},\":sesion\":{\"S\":\"$sesion\"}}" \
    --no-cli-pager 2>&1); then
    return 0
  fi

  echo 'No se pudo confirmar la liberación condicional del coordinador.' >&2
  return 1
}
