CREATE UNIQUE INDEX uq_ubicacion_aula_sede_nombre_lower
    ON ubicacion (sede_id, LOWER(nombre))
    WHERE tipo = 'AULA';

ALTER TABLE ubicacion
    ADD CONSTRAINT ck_ubicacion_aula_codigo
        CHECK (
            tipo <> 'AULA'
            OR (
                nombre ~ '^[A-Z][0-9]{4}$'
                AND UPPER(SUBSTRING(nombre FROM 1 FOR 1)) = UPPER(RIGHT(edificio, 1))
                AND SUBSTRING(nombre FROM 2 FOR 2)::INTEGER = piso
                AND SUBSTRING(nombre FROM 4 FOR 2)::INTEGER > 0
            )
        );

INSERT INTO ubicacion (nombre, detalle, sede_id, edificio, piso, tipo)
SELECT aula.nombre,
       NULL,
       sede.sede_id,
       aula.edificio,
       aula.piso,
       'AULA'
FROM (VALUES
    ('A0301', 'Torre A', 3),
    ('A0302', 'Torre A', 3),
    ('A0303', 'Torre A', 3),
    ('A0305', 'Torre A', 3),
    ('A0307', 'Torre A', 3),
    ('A0308', 'Torre A', 3),
    ('A0310', 'Torre A', 3),
    ('A0311', 'Torre A', 3),
    ('A0312', 'Torre A', 3),
    ('B0605', 'Torre B', 6),
    ('B0606', 'Torre B', 6),
    ('B0607', 'Torre B', 6),
    ('B0608', 'Torre B', 6)
) AS aula(nombre, edificio, piso)
CROSS JOIN sede
WHERE sede.nombre = 'UTP Sede Piura'
    AND NOT EXISTS (
        SELECT 1
        FROM ubicacion existente
        WHERE existente.sede_id = sede.sede_id
            AND existente.tipo = 'AULA'
            AND LOWER(existente.nombre) = LOWER(aula.nombre)
    );
