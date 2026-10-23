-- V005: correções de transações, observações e movimentos internos.
--
-- Valores anteriores/novos de correções são cifrados. `internal_match` registra
-- sugestões determinísticas e a decisão explícita do usuário; apenas CONFIRMED
-- exclui os dois lados dos totais de receitas/despesas.

ALTER TABLE transaction ADD COLUMN notes_enc bytea;
ALTER TABLE financial_account ADD COLUMN notes_enc bytea;

CREATE TABLE transaction_correction (
  id             uuid        NOT NULL,
  user_id        uuid        NOT NULL,
  transaction_id uuid        NOT NULL,
  field_name     text        NOT NULL,
  old_value_enc  bytea,
  new_value_enc  bytea       NOT NULL,
  changed_at     timestamptz NOT NULL,
  created_at     timestamptz NOT NULL,
  updated_at     timestamptz NOT NULL,
  version        bigint      NOT NULL DEFAULT 0,
  CONSTRAINT transaction_correction_pk PRIMARY KEY (id),
  CONSTRAINT transaction_correction_user_fk FOREIGN KEY (user_id)
    REFERENCES app_user (id) ON DELETE CASCADE,
  CONSTRAINT transaction_correction_transaction_fk FOREIGN KEY (user_id, transaction_id)
    REFERENCES transaction (user_id, id) ON DELETE CASCADE,
  CONSTRAINT transaction_correction_field_ck CHECK (
    field_name IN ('description', 'postedOn', 'amount', 'category', 'notes')
  )
);

CREATE INDEX transaction_correction_ix
  ON transaction_correction (transaction_id, changed_at);

COMMENT ON TABLE transaction_correction IS
  'Auditoria de correções do usuário; somente nomes de campo e valores cifrados.';

CREATE TABLE internal_match (
  id                    uuid        NOT NULL,
  user_id               uuid        NOT NULL,
  kind                  text        NOT NULL,
  debit_transaction_id  uuid        NOT NULL,
  credit_transaction_id uuid        NOT NULL,
  score                 numeric(5,2) NOT NULL,
  criteria_version      text        NOT NULL,
  status                text        NOT NULL,
  reason                text,
  decided_at            timestamptz,
  created_at            timestamptz NOT NULL,
  updated_at            timestamptz NOT NULL,
  version               bigint      NOT NULL DEFAULT 0,
  CONSTRAINT internal_match_pk PRIMARY KEY (id),
  CONSTRAINT internal_match_user_fk FOREIGN KEY (user_id)
    REFERENCES app_user (id) ON DELETE CASCADE,
  CONSTRAINT internal_match_debit_fk FOREIGN KEY (user_id, debit_transaction_id)
    REFERENCES transaction (user_id, id) ON DELETE CASCADE,
  CONSTRAINT internal_match_credit_fk FOREIGN KEY (user_id, credit_transaction_id)
    REFERENCES transaction (user_id, id) ON DELETE CASCADE,
  CONSTRAINT internal_match_uk UNIQUE (
    user_id, kind, debit_transaction_id, credit_transaction_id
  ),
  CONSTRAINT internal_match_kind_ck CHECK (kind IN ('TRANSFER', 'CARD_PAYMENT')),
  CONSTRAINT internal_match_status_ck CHECK (status IN ('PENDING', 'CONFIRMED', 'REJECTED')),
  CONSTRAINT internal_match_distinct_ck CHECK (debit_transaction_id <> credit_transaction_id),
  CONSTRAINT internal_match_score_ck CHECK (score >= 0 AND score <= 100)
);

CREATE INDEX internal_match_pending_ix
  ON internal_match (user_id, status, score DESC, created_at);
CREATE INDEX internal_match_debit_ix ON internal_match (debit_transaction_id);
CREATE INDEX internal_match_credit_ix ON internal_match (credit_transaction_id);

COMMENT ON TABLE internal_match IS
  'Sugestões determinísticas de transferência/pagamento; confirmação explícita do usuário.';
