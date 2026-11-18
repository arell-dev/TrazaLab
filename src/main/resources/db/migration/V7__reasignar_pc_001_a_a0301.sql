UPDATE computadora equipo
SET ubicacion_id = aula.ubicacion_id
FROM ubicacion ubicacion_actual,
     sede,
     ubicacion aula
WHERE equipo.ubicacion_id = ubicacion_actual.ubicacion_id
    AND ubicacion_actual.sede_id = sede.sede_id
    AND aula.sede_id = sede.sede_id
    AND sede.nombre = 'UTP Sede Piura'
    AND equipo.codigo_qr = 'PC-001'
    AND UPPER(ubicacion_actual.nombre) = 'LAB-01'
    AND aula.tipo = 'AULA'
    AND aula.nombre = 'A0301';
