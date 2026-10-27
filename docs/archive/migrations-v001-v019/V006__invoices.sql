-- V006: faturas de cartão, itens, pagamentos e correções de itens.
--
-- Totais e valores são cifrados (AES-256-GCM). Datas, estados e tipos ficam em
-- claro para consulta e ordenação. Itens espelho de transação compõem a fatura
-- uma única vez.

ALTER TABLE transaction ADD COLUMN invoice_id uuid;

CREATE TABLE invoice (
  id                       uuid        NOT NULL,
  user_id                  uuid        NOT NULL,
  card_account_id          uuid        NOT NULL,
  cycle_start              date        NOT NULL,
  cycle_end                date        NOT NULL,
  closing_date             date        NOT NULL,
  due_date                 date        NOT NULL,
  status                   text        NOT NULL,
  has_adjustments          boolean     NOT NULL DEFAULT false,
  adjustment_count         integer     NOT NULL DEFAULT 0,
  provider_bill_id_enc     bytea,
  provider_bill_id_hash    bytea,
  total_enc                bytea       NOT NULL,
  payments_total_enc       bytea       NOT NULL,
  outstanding_enc          bytea       NOT NULL,
  applied_credit_enc       bytea       NOT NULL,
  minimum_payment_enc      bytea,
  source                   text        NOT NULL,
  created_at               timestamptz NOT NULL,
  updated_at               timestamptz NOT NULL,
  version                  bigint      NOT NULL DEFAULT 0,
  CONSTRAINT invoice_pk PRIMARY KEY (id),
  CONSTRAINT invoice_user_id_uk UNIQUE (user_id, id),
  CONSTRAINT invoice_user_fk FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE,
  CONSTRAINT invoice_account_fk FOREIGN KEY (user_id, card_account_id)
    REFERENCES financial_account (user_id, id) ON DELETE CASCADE,
  CONSTRAINT invoice_cycle_uk UNIQUE (card_account_id, cycle_start),
  CONSTRAINT invoice_status_ck CHECK (
    status IN ('OPEN', 'CLOSED', 'PARTIALLY_PAID', 'PAID', 'OVERDUE')
  ),
  CONSTRAINT invoice_source_ck CHECK (source IN ('LOCAL', 'PROVIDER')),
  CONSTRAINT invoice_adjustment_ck CHECK (adjustment_count >= 0),
  CONSTRAINT invoice_cycle_dates_ck CHECK (cycle_start <= cycle_end)
);

CREATE UNIQUE INDEX invoice_provider_bill_uk
  ON invoice (card_account_id, provider_bill_id_hash)
  WHERE provider_bill_id_hash IS NOT NULL;

CREATE INDEX invoice_user_due_ix ON invoice (user_id, due_date, id);

CREATE TABLE invoice_item (
  id                    uuid        NOT NULL,
  user_id               uuid        NOT NULL,
  invoice_id            uuid        NOT NULL,
  transaction_id        uuid,
  kind                  text        NOT NULL,
  reporting_effect      text        NOT NULL,
  source                text        NOT NULL,
  description_enc       bytea       NOT NULL,
  amount_enc            bytea       NOT NULL,
  occurred_on           date        NOT NULL,
  installment_number    integer,
  total_installments    integer,
  installment_plan_key  bytea,
  provider_item_id_enc  bytea,
  provider_item_id_hash bytea,
  removed_at            timestamptz,
  created_at            timestamptz NOT NULL,
  updated_at            timestamptz NOT NULL,
  version               bigint      NOT NULL DEFAULT 0,
  CONSTRAINT invoice_item_pk PRIMARY KEY (id),
  CONSTRAINT invoice_item_user_id_uk UNIQUE (user_id, id),
  CONSTRAINT invoice_item_invoice_fk FOREIGN KEY (user_id, invoice_id)
    REFERENCES invoice (user_id, id) ON DELETE CASCADE,
  CONSTRAINT invoice_item_transaction_fk FOREIGN KEY (user_id, transaction_id)
    REFERENCES transaction (user_id, id),
  CONSTRAINT invoice_item_kind_ck CHECK (
    kind IN ('PURCHASE', 'INSTALLMENT', 'INTEREST', 'FEE', 'TAX', 'CREDIT', 'REFUND',
             'MANUAL_ADJUSTMENT')
  ),
  CONSTRAINT invoice_item_effect_ck CHECK (
    reporting_effect IN ('EXPENSE_INCREASE', 'EXPENSE_DECREASE', 'NONE')
  ),
  CONSTRAINT invoice_item_source_ck CHECK (source IN ('IMPORTED', 'MANUAL', 'PROVIDER_BILL')),
  CONSTRAINT invoice_item_installment_ck CHECK (
    (installment_number IS NULL AND total_installments IS NULL)
    OR (installment_number >= 1 AND total_installments >= 1
        AND installment_number <= total_installments)
  )
);

CREATE UNIQUE INDEX invoice_item_transaction_uk
  ON invoice_item (user_id, transaction_id)
  WHERE transaction_id IS NOT NULL AND removed_at IS NULL;

CREATE INDEX invoice_item_invoice_ix ON invoice_item (invoice_id, removed_at);
CREATE INDEX invoice_item_user_transaction_ix ON invoice_item (user_id, transaction_id);

CREATE TABLE invoice_payment (
  id                    uuid        NOT NULL,
  user_id               uuid        NOT NULL,
  invoice_id            uuid        NOT NULL,
  payment_account_id    uuid        NOT NULL,
  amount_enc            bytea       NOT NULL,
  paid_on               date        NOT NULL,
  status                text        NOT NULL,
  source                text        NOT NULL,
  source_transaction_id uuid,
  provider_payment_id_enc  bytea,
  provider_payment_id_hash bytea,
  created_at            timestamptz NOT NULL,
  updated_at            timestamptz NOT NULL,
  version               bigint      NOT NULL DEFAULT 0,
  CONSTRAINT invoice_payment_pk PRIMARY KEY (id),
  CONSTRAINT invoice_payment_user_id_uk UNIQUE (user_id, id),
  CONSTRAINT invoice_payment_invoice_fk FOREIGN KEY (user_id, invoice_id)
    REFERENCES invoice (user_id, id) ON DELETE CASCADE,
  CONSTRAINT invoice_payment_account_fk FOREIGN KEY (user_id, payment_account_id)
    REFERENCES financial_account (user_id, id),
  CONSTRAINT invoice_payment_transaction_fk FOREIGN KEY (user_id, source_transaction_id)
    REFERENCES transaction (user_id, id),
  CONSTRAINT invoice_payment_status_ck CHECK (status IN ('PENDING', 'CONFIRMED', 'REVERSED')),
  CONSTRAINT invoice_payment_source_ck CHECK (source IN ('MANUAL', 'IMPORTED', 'PROVIDER_BILL'))
);

CREATE INDEX invoice_payment_invoice_ix ON invoice_payment (invoice_id, paid_on);

CREATE TABLE invoice_item_correction (
  id              uuid        NOT NULL,
  user_id         uuid        NOT NULL,
  invoice_item_id uuid        NOT NULL,
  field_name      text        NOT NULL,
  old_value_enc   bytea,
  new_value_enc   bytea       NOT NULL,
  reason_enc      bytea,
  changed_at      timestamptz NOT NULL,
  created_at      timestamptz NOT NULL,
  updated_at      timestamptz NOT NULL,
  version         bigint      NOT NULL DEFAULT 0,
  CONSTRAINT invoice_item_correction_pk PRIMARY KEY (id),
  CONSTRAINT invoice_item_correction_item_fk FOREIGN KEY (user_id, invoice_item_id)
    REFERENCES invoice_item (user_id, id) ON DELETE CASCADE,
  CONSTRAINT invoice_item_correction_user_fk FOREIGN KEY (user_id)
    REFERENCES app_user (id) ON DELETE CASCADE
);

CREATE INDEX invoice_item_correction_ix
  ON invoice_item_correction (invoice_item_id, changed_at);

COMMENT ON TABLE invoice IS 'Fatura local por ciclo; pagamentos e crédito não duplicam despesas.';
COMMENT ON TABLE invoice_item IS 'Item de fatura; espelho de transação compõe o total uma única vez.';
COMMENT ON TABLE invoice_payment IS 'Pagamento de fatura; reduz saldo devedor, não é despesa.';
