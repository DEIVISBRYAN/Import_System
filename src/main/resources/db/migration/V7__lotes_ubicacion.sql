ALTER TABLE lotes ADD COLUMN ubicacion TEXT;

UPDATE lotes
SET ubicacion = CASE producto_id
    WHEN 1 THEN 'A-03-2'
    WHEN 2 THEN 'B-01-1'
    WHEN 3 THEN 'A-02-1'
    WHEN 4 THEN 'C-02-1'
    WHEN 5 THEN 'C-04-2'
    ELSE 'A-01-1'
END
WHERE ubicacion IS NULL OR trim(ubicacion) = '';
