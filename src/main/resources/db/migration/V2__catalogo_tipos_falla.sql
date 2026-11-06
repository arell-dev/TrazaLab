INSERT INTO tipo_falla (categoria_falla_id, nombre)
SELECT categoria_falla_id, tipo.nombre
FROM categoria_falla
CROSS JOIN (VALUES
    ('Hardware', 'Memoria RAM'),
    ('Hardware', 'Disco o unidad de almacenamiento'),
    ('Hardware', 'Sobrecalentamiento'),
    ('Software', 'Sistema operativo'),
    ('Software', 'Aplicación'),
    ('Red/Conectividad', 'Conectividad de red'),
    ('Energía', 'Fuente de alimentación'),
    ('Periféricos', 'Teclado o mouse')
) AS tipo(categoria, nombre)
WHERE categoria_falla.nombre = tipo.categoria
ON CONFLICT (categoria_falla_id, nombre) DO NOTHING;
