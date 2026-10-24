-- V008: patrimônio consolidado, ativos/passivos manuais e snapshots datados.
--
-- Descrições, valores e observações são cifrados (AES-256-GCM). Tipos, estados,
-- kind e datas ficam em claro para consulta e ordenação. Snapshots guardam a
-- composição do dia; dados antigos nunca são reconstruídos a partir de saldos
-- atuais incompletos.

CREATE TABLE manual_asset_liability (
  id                  uuid        NOT NULL,
  user_id             uuid        NOT NULL,
  kind                text        NOT NULL,
  type                text        NOT NULL,
  description_enc     bytea       NOT NULL,
  reference_value_enc bytea       NOT NULL,
  reference_date      date        NOT NULL,
  notes_enc           bytea,
  created_at          timestamptz NOT NULL,
  updated_at          timestamptz NOT NULL,
  version             bigint      NOT NULL DEFAULT 0,
  CONSTRAINT manual_asset_liability_pk PRIMARY KEY (id),
  CONSTRAINT manual_asset_liability_user_id_uk UNIQUE (user_id, id),
  CONSTRAINT manual_asset_liability_user_fk FOREIGN KEY (user_id)
    REFERENCES app_user (id) ON DELETE CASCADE,
  CONSTRAINT manual_asset_liability_kind_ck CHECK (kind IN ('ASSET', 'LIABILITY')),
  CONSTRAINT manual_asset_liability_type_ck CHECK (char_length(type) BETWEEN 1 AND 60)
);

CREATE INDEX manual_asset_liability_user_kind_date_ix
  ON manual_asset_liability (user_id, kind, reference_date DESC, id DESC);

CREATE TABLE wealth_snapshot (
  id                uuid        NOT NULL,
  user_id           uuid        NOT NULL,
  reference_date    date        NOT NULL,
  gross_assets_enc  bytea       NOT NULL,
  liabilities_enc   bytea       NOT NULL,
  net_worth_enc     bytea       NOT NULL,
  completeness      text        NOT NULL,
  notes_enc         bytea,
  created_at        timestamptz NOT NULL,
  updated_at        timestamptz NOT NULL,
  version           bigint      NOT NULL DEFAULT 0,
  CONSTRAINT wealth_snapshot_pk PRIMARY KEY (id),
  CONSTRAINT wealth_snapshot_user_id_uk UNIQUE (user_id, id),
  CONSTRAINT wealth_snapshot_user_fk FOREIGN KEY (user_id)
    REFERENCES app_user (id) ON DELETE CASCADE,
  CONSTRAINT wealth_snapshot_user_date_uk UNIQUE (user_id, reference_date),
  CONSTRAINT wealth_snapshot_completeness_ck CHECK (
    completeness IN ('COMPLETE', 'PARTIAL', 'ESTIMATED')
  )
);

CREATE INDEX wealth_snapshot_user_date_ix
  ON wealth_snapshot (user_id, reference_date DESC, id DESC);

CREATE TABLE wealth_snapshot_item (
  id               uuid        NOT NULL,
  user_id          uuid        NOT NULL,
  snapshot_id      uuid        NOT NULL,
  source_type      text        NOT NULL,
  source_id        uuid,
  description_enc  bytea       NOT NULL,
  value_enc        bytea       NOT NULL,
  included         boolean     NOT NULL,
  exclusion_reason text,
  created_at       timestamptz NOT NULL,
  updated_at       timestamptz NOT NULL,
  version          bigint      NOT NULL DEFAULT 0,
  CONSTRAINT wealth_snapshot_item_pk PRIMARY KEY (id),
  CONSTRAINT wealth_snapshot_item_user_id_uk UNIQUE (user_id, id),
  CONSTRAINT wealth_snapshot_item_snapshot_fk FOREIGN KEY (user_id, snapshot_id)
    REFERENCES wealth_snapshot (user_id, id) ON DELETE CASCADE,
  CONSTRAINT wealth_snapshot_item_source_ck CHECK (
    source_type IN ('ACCOUNT', 'CARD', 'INVESTMENT', 'MANUAL_ASSET', 'MANUAL_LIABILITY')
  ),
  CONSTRAINT wealth_snapshot_item_reason_ck CHECK (char_length(exclusion_reason) <= 200)
);

CREATE INDEX wealth_snapshot_item_snapshot_ix
  ON wealth_snapshot_item (user_id, snapshot_id, source_type, id);

COMMENT ON TABLE manual_asset_liability IS
  'Ativo ou passivo manual com valor e data de referência informados pelo usuário.';
COMMENT ON TABLE wealth_snapshot IS
  'Fotografia datada do patrimônio; nunca reconstrói saldos passados a partir dos atuais.';
COMMENT ON TABLE wealth_snapshot_item IS
  'Composição cifrada de um snapshot, incluindo itens excluídos e o motivo.';
