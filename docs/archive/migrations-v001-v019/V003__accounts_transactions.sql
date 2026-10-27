-- V003: contas, cartões, categorias, transações e histórico de identidade.
--
-- Valores monetários, descrições e nomes são cifrados (AES-256-GCM). Metadados
-- de consulta (user_id, datas, tipo, status, moeda, origem) ficam em claro.

CREATE TABLE category (
  id                    uuid        NOT NULL,
  user_id               uuid        NOT NULL,
  name_enc              bytea       NOT NULL,
  normalized_name_hash  bytea       NOT NULL,
  kind                  text        NOT NULL,
  predefined_key        text,
  active                boolean     NOT NULL DEFAULT true,
  created_at            timestamptz NOT NULL,
  updated_at            timestamptz NOT NULL,
  version               bigint      NOT NULL DEFAULT 0,
  CONSTRAINT category_pk PRIMARY KEY (id),
  CONSTRAINT category_user_fk FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE,
  CONSTRAINT category_user_id_uk UNIQUE (user_id, id),
  CONSTRAINT category_name_uk UNIQUE (user_id, normalized_name_hash),
  CONSTRAINT category_kind_ck CHECK (kind IN ('INCOME', 'EXPENSE', 'TRANSFER', 'OTHER'))
);

CREATE TABLE financial_account (
  id                        uuid        NOT NULL,
  user_id                   uuid        NOT NULL,
  connection_id             uuid,
  origin                    text        NOT NULL,
  type                      text        NOT NULL,
  subtype                   text,
  external_account_id_enc   bytea,
  external_account_id_hash  bytea,
  institution_name_enc      bytea,
  alias_enc                 bytea,
  currency                  varchar(3)  NOT NULL DEFAULT 'BRL',
  balance_enc               bytea,
  balance_reference_at      timestamptz,
  status                    text        NOT NULL,
  last_sync_at              timestamptz,
  created_at                timestamptz NOT NULL,
  updated_at                timestamptz NOT NULL,
  version                   bigint      NOT NULL DEFAULT 0,
  CONSTRAINT financial_account_pk PRIMARY KEY (id),
  CONSTRAINT financial_account_user_fk FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE,
  CONSTRAINT financial_account_user_id_uk UNIQUE (user_id, id),
  CONSTRAINT financial_account_connection_fk FOREIGN KEY (user_id, connection_id)
    REFERENCES pluggy_connection (user_id, id) ON DELETE SET NULL,
  CONSTRAINT financial_account_origin_ck CHECK (origin IN ('IMPORTED', 'MANUAL')),
  CONSTRAINT financial_account_type_ck CHECK (type IN ('BANK', 'CREDIT', 'CASH', 'WALLET')),
  CONSTRAINT financial_account_status_ck CHECK (status IN ('ACTIVE', 'INACTIVE', 'UNAVAILABLE', 'CLOSED')),
  CONSTRAINT financial_account_origin_connection_ck CHECK (
    (origin = 'IMPORTED' AND connection_id IS NOT NULL) OR
    (origin = 'MANUAL' AND connection_id IS NULL)
  )
);

CREATE UNIQUE INDEX financial_account_external_uk
  ON financial_account (connection_id, external_account_id_hash)
  WHERE origin = 'IMPORTED' AND external_account_id_hash IS NOT NULL;

CREATE INDEX financial_account_user_ix ON financial_account (user_id, type, status);

CREATE TABLE card_profile (
  id                  uuid        NOT NULL,
  user_id             uuid        NOT NULL,
  account_id          uuid        NOT NULL,
  closing_day         integer     NOT NULL,
  due_day             integer     NOT NULL,
  credit_limit_enc    bytea,
  credit_balance_enc  bytea,
  source              text        NOT NULL,
  created_at          timestamptz NOT NULL,
  updated_at          timestamptz NOT NULL,
  version             bigint      NOT NULL DEFAULT 0,
  CONSTRAINT card_profile_pk PRIMARY KEY (id),
  CONSTRAINT card_profile_user_id_uk UNIQUE (user_id, id),
  CONSTRAINT card_profile_account_uk UNIQUE (user_id, account_id),
  CONSTRAINT card_profile_account_fk FOREIGN KEY (user_id, account_id)
    REFERENCES financial_account (user_id, id) ON DELETE CASCADE,
  CONSTRAINT card_profile_day_ck CHECK (closing_day BETWEEN 1 AND 31 AND due_day BETWEEN 1 AND 31),
  CONSTRAINT card_profile_source_ck CHECK (source IN ('PROVIDER', 'USER'))
);

CREATE TABLE transaction (
  id                            uuid        NOT NULL,
  user_id                       uuid        NOT NULL,
  account_id                    uuid        NOT NULL,
  category_id                   uuid,
  source                        text        NOT NULL,
  type                          text        NOT NULL,
  status                        text        NOT NULL,
  posted_on                     date        NOT NULL,
  source_posted_on              date,
  currency                      varchar(3)  NOT NULL DEFAULT 'BRL',
  amount_source_enc             bytea       NOT NULL,
  amount_effective_enc          bytea       NOT NULL,
  description_source_enc        bytea       NOT NULL,
  description_effective_enc     bytea       NOT NULL,
  external_transaction_id_enc   bytea,
  external_transaction_id_hash  bytea,
  dedup_key                     bytea,
  fingerprint                   bytea,
  related_transaction_id        uuid,
  adjustment_reason_enc         bytea,
  edited_fields                 text[]      NOT NULL DEFAULT '{}',
  reconciliation_state          text        NOT NULL DEFAULT 'NONE',
  created_at                    timestamptz NOT NULL,
  updated_at                    timestamptz NOT NULL,
  version                       bigint      NOT NULL DEFAULT 0,
  CONSTRAINT transaction_pk PRIMARY KEY (id),
  CONSTRAINT transaction_user_id_uk UNIQUE (user_id, id),
  CONSTRAINT transaction_account_fk FOREIGN KEY (user_id, account_id)
    REFERENCES financial_account (user_id, id) ON DELETE CASCADE,
  CONSTRAINT transaction_category_fk FOREIGN KEY (user_id, category_id)
    REFERENCES category (user_id, id) ON DELETE SET NULL,
  CONSTRAINT transaction_related_fk FOREIGN KEY (user_id, related_transaction_id)
    REFERENCES transaction (user_id, id),
  CONSTRAINT transaction_source_ck CHECK (source IN ('IMPORTED', 'MANUAL')),
  CONSTRAINT transaction_type_ck CHECK (
    type IN ('DEBIT', 'CREDIT', 'TRANSFER', 'REFUND', 'CHARGEBACK', 'ADJUSTMENT')
  ),
  CONSTRAINT transaction_status_ck CHECK (
    status IN ('PENDING', 'POSTED', 'SOURCE_REMOVED', 'RECONCILED')
  ),
  CONSTRAINT transaction_reconciliation_ck CHECK (
    reconciliation_state IN ('NONE', 'PENDING_MATCH', 'MATCHED', 'SOURCE_REMOVED')
  )
);

CREATE UNIQUE INDEX transaction_dedup_uk
  ON transaction (user_id, dedup_key)
  WHERE source = 'IMPORTED' AND dedup_key IS NOT NULL;

CREATE INDEX transaction_user_posted_ix ON transaction (user_id, posted_on DESC, id);
CREATE INDEX transaction_account_posted_ix ON transaction (user_id, account_id, posted_on DESC);
CREATE INDEX transaction_category_posted_ix ON transaction (user_id, category_id, posted_on DESC);
CREATE INDEX transaction_type_posted_ix ON transaction (user_id, type, posted_on DESC);

CREATE TABLE external_identity_history (
  id                uuid        NOT NULL,
  user_id           uuid        NOT NULL,
  transaction_id    uuid        NOT NULL,
  account_id        uuid        NOT NULL,
  source            text        NOT NULL,
  external_id_enc   bytea       NOT NULL,
  external_id_hash  bytea       NOT NULL,
  first_seen_at     timestamptz NOT NULL,
  last_seen_at      timestamptz NOT NULL,
  created_at        timestamptz NOT NULL,
  updated_at        timestamptz NOT NULL,
  version           bigint      NOT NULL DEFAULT 0,
  CONSTRAINT external_identity_history_pk PRIMARY KEY (id),
  CONSTRAINT external_identity_history_transaction_fk FOREIGN KEY (user_id, transaction_id)
    REFERENCES transaction (user_id, id) ON DELETE CASCADE,
  CONSTRAINT external_identity_history_account_fk FOREIGN KEY (user_id, account_id)
    REFERENCES financial_account (user_id, id) ON DELETE CASCADE,
  CONSTRAINT external_identity_history_uk
    UNIQUE (user_id, source, account_id, external_id_hash),
  CONSTRAINT external_identity_history_source_ck CHECK (source IN ('PLUGGY'))
);
