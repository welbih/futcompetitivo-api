ALTER TABLE fase ADD COLUMN formato_eliminatorio VARCHAR(255);

ALTER TABLE fase ADD CONSTRAINT ck_fase_formato_eliminatorio CHECK (
    formato_eliminatorio IS NULL OR formato_eliminatorio IN (
        'JOGO_UNICO', 'IDA_E_VOLTA', 'IDA_E_VOLTA_FINAL_UNICA'
    )
);
