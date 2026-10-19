ALTER TABLE fase ADD COLUMN quantidade_classificados INTEGER;

ALTER TABLE fase ADD CONSTRAINT ck_fase_quantidade_classificados
    CHECK (quantidade_classificados IS NULL OR quantidade_classificados >= 4);
