-- V007: posições manuais de investimento (fase secundária, US8).
--
-- Ativo, ticker, quantidade, preço unitário e observações são cifrados
-- (AES-256-GCM). Corretora, data de referência e origem ficam em claro para
-- consulta e ordenação. O total é quantity × reference_price e é calculado na
-- aplicação; não é persistido para evitar divergência entre campos.

CREATE TABLE investment_position (
  id                  uuid        NOT NULL,
  user_id             uuid        NOT NULL,
  broker              text        NOT NULL,
  asset_name_enc      bytea       NOT NULL,
  ticker_enc          bytea,
  quantity_enc        bytea       NOT NULL,
  reference_price_enc bytea       NOT NULL,
  reference_date      date        NOT NULL,
  notes_enc           bytea,
  origin              text        NOT NULL,
  created_at          timestamptz NOT NULL,
  updated_at          timestamptz NOT NULL,
  version             bigint      NOT NULL DEFAULT 0,
  CONSTRAINT investment_position_pk PRIMARY KEY (id),
  CONSTRAINT investment_position_user_id_uk UNIQUE (user_id, id),
  CONSTRAINT investment_position_user_fk FOREIGN KEY (user_id)
    REFERENCES app_user (id) ON DELETE CASCADE,
  CONSTRAINT investment_position_broker_ck CHECK (broker IN ('XP', 'SANTANDER', 'OTHER')),
  CONSTRAINT investment_position_origin_ck CHECK (origin IN ('MANUAL'))
);

CREATE INDEX investment_position_user_date_ix
  ON investment_position (user_id, reference_date DESC, id DESC);

COMMENT ON TABLE investment_position IS
  'Posição manual de investimento; valores cifrados e total derivado na aplicação.';
