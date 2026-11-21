ALTER TABLE fase ADD COLUMN forma_geracao_mata_mata VARCHAR(255);
UPDATE fase SET forma_geracao_mata_mata = 'AUTOMATICA'
WHERE tipo = 'ELIMINATORIA' OR quantidade_classificados IS NOT NULL;
