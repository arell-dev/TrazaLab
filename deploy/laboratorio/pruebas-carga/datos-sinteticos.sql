BEGIN;

INSERT INTO usuario (rol_id, nombres, apellidos, correo, estado)
SELECT rol_id, 'Tecnico', 'Sintetico', 'tecnico@prueba.local', 'activo'
FROM rol
WHERE nombre LIKE 'T%cnico de Soporte';

INSERT INTO incidencia (computadora_id, usuario_id, descripcion, fecha_registro)
SELECT computadora.computadora_id, usuario.usuario_id,
    'Incidencia sintetica ' || serie.numero,
    CURRENT_TIMESTAMP - (serie.numero || ' days')::INTERVAL
FROM computadora
CROSS JOIN generate_series(1, 3) AS serie(numero)
CROSS JOIN usuario
WHERE computadora.codigo_qr LIKE 'QR-TEST-%'
    AND usuario.correo = 'tecnico@prueba.local';

INSERT INTO falla (incidencia_id, tipo_falla_id, descripcion_tecnica)
SELECT incidencia.incidencia_id, tipo_falla.tipo_falla_id, 'Falla sintetica'
FROM incidencia
CROSS JOIN tipo_falla
WHERE incidencia.descripcion LIKE 'Incidencia sintetica %'
    AND tipo_falla.nombre = 'Memoria RAM';

INSERT INTO mantenimiento (computadora_id, falla_id, usuario_id, tipo, descripcion)
SELECT incidencia.computadora_id, falla.falla_id, usuario.usuario_id,
    'correctivo', 'Mantenimiento sintetico'
FROM falla
JOIN incidencia ON incidencia.incidencia_id = falla.incidencia_id
CROSS JOIN usuario
WHERE falla.descripcion_tecnica = 'Falla sintetica'
    AND usuario.correo = 'tecnico@prueba.local';

INSERT INTO componente (nombre, tipo, numero_serie)
SELECT 'Componente sintetico', 'RAM', 'COMP-TEST-' || numero
FROM generate_series(1, 20) AS serie(numero);

INSERT INTO computadora_componente (computadora_id, componente_id)
SELECT computadora.computadora_id, componente.componente_id
FROM computadora
JOIN componente ON componente.numero_serie = 'COMP-TEST-' ||
    CAST(SUBSTRING(computadora.codigo_qr FROM '[0-9]+$') AS INTEGER)
WHERE computadora.codigo_qr LIKE 'QR-TEST-%';

COMMIT;
