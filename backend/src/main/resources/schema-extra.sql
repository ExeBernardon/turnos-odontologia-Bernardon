CREATE EXTENSION IF NOT EXISTS btree_gist
^^^
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint
    WHERE conname = 'turno_sin_solapamiento' AND conrelid = 'turno'::regclass
  ) THEN
    ALTER TABLE turno ADD CONSTRAINT turno_sin_solapamiento
      EXCLUDE USING gist (tstzrange(inicio, fin, '[)') WITH &&)
      WHERE (estado IN ('PENDIENTE', 'CONFIRMADO'));
  END IF;
END
$$
^^^
