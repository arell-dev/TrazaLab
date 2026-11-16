INSERT INTO ubicacion (nombre, detalle, sede_id, edificio, piso, tipo)
SELECT ubicacion_nueva.nombre,
       ubicacion_nueva.detalle,
       sede.sede_id,
       ubicacion_nueva.edificio,
       ubicacion_nueva.piso,
       ubicacion_nueva.tipo
FROM (VALUES
    ('Torre A', NULL, 'Torre A', NULL, 'TORRE'),
    ('Torre B', NULL, 'Torre B', NULL, 'TORRE'),
    ('Piso 1', NULL, 'Torre A', 1, 'PISO'),
    ('Piso 2', NULL, 'Torre A', 2, 'PISO'),
    ('Piso 3', NULL, 'Torre A', 3, 'PISO'),
    ('Piso 4', NULL, 'Torre A', 4, 'PISO'),
    ('Piso 5', NULL, 'Torre A', 5, 'PISO'),
    ('Piso 6', NULL, 'Torre A', 6, 'PISO'),
    ('Piso 7', NULL, 'Torre A', 7, 'PISO'),
    ('Piso 8', NULL, 'Torre A', 8, 'PISO'),
    ('Piso 9', NULL, 'Torre A', 9, 'PISO'),
    ('Piso 10', NULL, 'Torre A', 10, 'PISO'),
    ('Piso 1', NULL, 'Torre B', 1, 'PISO'),
    ('Piso 2', NULL, 'Torre B', 2, 'PISO'),
    ('Piso 3', NULL, 'Torre B', 3, 'PISO'),
    ('Piso 4', NULL, 'Torre B', 4, 'PISO'),
    ('Piso 5', NULL, 'Torre B', 5, 'PISO'),
    ('Piso 6', NULL, 'Torre B', 6, 'PISO'),
    ('Piso 7', NULL, 'Torre B', 7, 'PISO')
) AS ubicacion_nueva(nombre, detalle, edificio, piso, tipo)
CROSS JOIN sede
WHERE sede.nombre = 'UTP Sede Piura'
    AND NOT EXISTS (
        SELECT 1
        FROM ubicacion existente
        WHERE existente.sede_id = sede.sede_id
            AND existente.tipo = ubicacion_nueva.tipo
            AND existente.edificio IS NOT DISTINCT FROM ubicacion_nueva.edificio
            AND existente.piso IS NOT DISTINCT FROM ubicacion_nueva.piso
            AND UPPER(existente.nombre) = UPPER(ubicacion_nueva.nombre)
    );
