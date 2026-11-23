#!/usr/bin/env bash
set -euo pipefail

raiz=${TRAZALAB_APAGADO_RAIZ:-$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd -P)}
source "$raiz/ec2/coordinador.sh"

python3() {
  [[ "${1:-}" == -c ]] || return 2
  local codigo=$2
  local operacion=$3
  local respuesta

  respuesta=$(cat)
  if [[ "$codigo" == *re.fullmatch* ]]; then
    local instancia_esperada=$4
    local propietario_respuesta sesion_respuesta
    grep -Fq "\"operacion\":{\"S\":\"$operacion\"}" <<< "$respuesta" || return 1
    grep -Fq "\"instancia\":{\"S\":\"$instancia_esperada\"}" <<< "$respuesta" || return 1
    propietario_respuesta=$(sed -n 's/.*"propietario":{"S":"\([^"]*\)"}.*/\1/p' <<< "$respuesta")
    sesion_respuesta=$(sed -n 's/.*"sesion":{"S":"\([^"]*\)"}.*/\1/p' <<< "$respuesta")
    [[ "$propietario_respuesta" =~ ^[a-f0-9-]{36}$ && "$sesion_respuesta" =~ ^[a-f0-9-]{36}$ ]]
    return
  fi

  [[ "$#" -eq 6 ]] || return 2
  local propietario_esperado=$4
  local instancia_esperada=$5
  local sesion_esperada=$6
  grep -Fq "\"operacion\":{\"S\":\"$operacion\"}" <<< "$respuesta" || return 1
  grep -Fq "\"propietario\":{\"S\":\"$propietario_esperado\"}" <<< "$respuesta" || return 1
  grep -Fq "\"instancia\":{\"S\":\"$instancia_esperada\"}" <<< "$respuesta" || return 1
  grep -Fq "\"sesion\":{\"S\":\"$sesion_esperada\"}" <<< "$respuesta"
}

propietario=11111111-1111-4111-8111-111111111111
sesion=22222222-2222-4222-8222-222222222222
instancia=i-0123456789abcdef0
operacion=apagado
mock_ocupado=false
mock_operacion=apagado
mock_fallo_borrado=false
llamadas=()

aws() {
  [[ "$1" == dynamodb ]]
  llamadas+=("$2")

  case "$2" in
    put-item)
      [[ "$*" == *"attribute_not_exists(pk)"* ]]
      if [[ "$mock_ocupado" == true ]]; then
        echo 'ConditionalCheckFailedException' >&2
        return 254
      fi
      ;;
    get-item)
      if [[ "$mock_operacion" == error ]]; then
        return 254
      fi
      printf '{"Item":{"pk":{"S":"trazalab-operacion"},"operacion":{"S":"%s"},"propietario":{"S":"%s"},"instancia":{"S":"%s"},"sesion":{"S":"%s"}}}\n' \
        "$mock_operacion" "$propietario" "$instancia" "$sesion"
      ;;
    delete-item)
      [[ "$*" == *"propietario = :propietario"* && "$*" == *"sesion = :sesion"* ]]
      if [[ "$mock_fallo_borrado" == true ]]; then
        echo 'ConditionalCheckFailedException' >&2
        return 254
      fi
      ;;
    *)
      return 2
      ;;
  esac
}

trazalab_coord_adquirir "$operacion" "$propietario" "$instancia" "$sesion"
trazalab_coord_verificar "$operacion" "$propietario" "$instancia" "$sesion"
trazalab_coord_verificar_operacion "$operacion" "$instancia"
trazalab_coord_liberar "$operacion" "$propietario" "$instancia" "$sesion"

mock_ocupado=true
if trazalab_coord_adquirir despliegue "$propietario" "$instancia" "$sesion"; then
  echo 'Una adquisición ocupada no debe tener éxito.' >&2
  exit 1
else
  test "$?" -eq 2
fi

mock_operacion=despliegue
if trazalab_coord_verificar apagado "$propietario" "$instancia" "$sesion"; then
  echo 'Una operación distinta no debe verificar.' >&2
  exit 1
fi

mock_operacion=error
if trazalab_coord_verificar_operacion despliegue "$instancia"; then
  echo 'Un fallo de lectura no debe verificar.' >&2
  exit 1
fi

mock_operacion=apagado
mock_fallo_borrado=true
if trazalab_coord_liberar apagado "$propietario" "$instancia" "$sesion"; then
  echo 'Un fallo de liberación no debe aparecer como exitoso.' >&2
  exit 1
fi

echo 'Coordinador: adquisición condicional, lectura y liberación verificadas; cuatro fallos rechazados.'
