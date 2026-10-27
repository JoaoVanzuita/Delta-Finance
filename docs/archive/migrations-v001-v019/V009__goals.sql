-- V009: metas financeiras, alocações explícitas e histórico de progresso.
--
-- Nome, descrição, valores e dívida inicial são cifrados. Tipo, estado,
-- progress_rule, percentual e datas ficam em claro para consulta e ordenação.
-- Metas nunca movimentam dinheiro nem criam ordens.

CREATE TABLE financial_goal (
  id                uuid        NOT NULL,
  user_id           uuid        NOT NULL,
  name_enc          bytea       NOT NULL,
  description_enc   bytea,
  type              text        NOT NULL,
  target_amount_enc bytea       NOT NULL,
  target_date       date,
  status            text        NOT NULL,
  progress_rule     text        NOT NULL,
  initial_value_enc bytea,
  changed_at        timestamptz,
  created_at        timestamptz NOT NULL,
  updated_at        timestamptz NOT NULL,
  version           bigint      NOT NULL DEFAULT 0,
  CONSTRAINT financial_goal_pk PRIMARY KEY (id),
  CONSTRAINT financial_goal_user_id_uk UNIQUE (user_id, id),
  CONSTRAINT financial_goal_user_fk FOREIGN KEY (user_id)
    REFERENCES app_user (id) ON DELETE CASCADE,
  CONSTRAINT financial_goal_type_ck CHECK (
    type IN ('EMERGENCY_FUND', 'TRAVEL', 'PLANNED_PURCHASE', 'DEBT_REDUCTION', 'NET_WORTH')
  ),
  CONSTRAINT financial_goal_status_ck CHECK (
    status IN ('ACTIVE', 'ACHIEVED', 'PAUSED', 'ARCHIVED', 'OVERDUE', 'CHANGED')
  ),
  CONSTRAINT financial_goal_rule_ck CHECK (
    progress_rule IN ('ALLOCATIONS', 'NET_WORTH', 'DEBT_REDUCTION')
  ),
  CONSTRAINT financial_goal_debt_initial_ck CHECK (
    progress_rule <> 'DEBT_REDUCTION' OR initial_value_enc IS NOT NULL
  )
);

CREATE INDEX financial_goal_user_created_ix ON financial_goal (user_id, created_at DESC, id);

CREATE TABLE goal_allocation (
  id                   uuid        NOT NULL,
  user_id              uuid        NOT NULL,
  goal_id              uuid        NOT NULL,
  ordinal              integer     NOT NULL,
  source_type          text        NOT NULL,
  source_id            uuid,
  allocation_mode      text        NOT NULL,
  allocated_amount_enc bytea,
  allocation_ratio     numeric(9,6),
  effective_from       date        NOT NULL,
  effective_to         date,
  created_at           timestamptz NOT NULL,
  updated_at           timestamptz NOT NULL,
  version              bigint      NOT NULL DEFAULT 0,
  CONSTRAINT goal_allocation_pk PRIMARY KEY (id),
  CONSTRAINT goal_allocation_user_id_uk UNIQUE (user_id, id),
  CONSTRAINT goal_allocation_goal_fk FOREIGN KEY (user_id, goal_id)
    REFERENCES financial_goal (user_id, id) ON DELETE CASCADE,
  CONSTRAINT goal_allocation_source_ck CHECK (
    source_type IN ('MANUAL', 'ACCOUNT', 'INVESTMENT', 'MANUAL_ASSET', 'MANUAL_LIABILITY')
  ),
  CONSTRAINT goal_allocation_mode_ck CHECK (
    allocation_mode IN ('FIXED_AMOUNT', 'PERCENTAGE')
  ),
  CONSTRAINT goal_allocation_exactly_one_ck CHECK (
    (allocation_mode = 'FIXED_AMOUNT' AND allocated_amount_enc IS NOT NULL
      AND allocation_ratio IS NULL)
    OR (allocation_mode = 'PERCENTAGE' AND allocation_ratio IS NOT NULL
      AND allocated_amount_enc IS NULL)
  ),
  CONSTRAINT goal_allocation_ratio_ck CHECK (
    allocation_ratio IS NULL OR (allocation_ratio >= 0 AND allocation_ratio <= 1)
  ),
  CONSTRAINT goal_allocation_ordinal_ck CHECK (ordinal >= 0),
  CONSTRAINT goal_allocation_manual_source_ck CHECK (
    (source_type = 'MANUAL' AND source_id IS NULL)
    OR (source_type <> 'MANUAL' AND source_id IS NOT NULL)
  )
);

CREATE INDEX goal_allocation_goal_ix ON goal_allocation (user_id, goal_id, ordinal, id);
CREATE INDEX goal_allocation_source_ix
  ON goal_allocation (user_id, source_type, source_id);

CREATE TABLE goal_progress_snapshot (
  id              uuid        NOT NULL,
  user_id         uuid        NOT NULL,
  goal_id         uuid        NOT NULL,
  reference_date  date        NOT NULL,
  current_value_enc bytea     NOT NULL,
  percentage      numeric(9,4) NOT NULL,
  remaining_enc   bytea       NOT NULL,
  rule_version    text        NOT NULL,
  created_at      timestamptz NOT NULL,
  updated_at      timestamptz NOT NULL,
  version         bigint      NOT NULL DEFAULT 0,
  CONSTRAINT goal_progress_snapshot_pk PRIMARY KEY (id),
  CONSTRAINT goal_progress_snapshot_user_id_uk UNIQUE (user_id, id),
  CONSTRAINT goal_progress_snapshot_goal_fk FOREIGN KEY (user_id, goal_id)
    REFERENCES financial_goal (user_id, id) ON DELETE CASCADE,
  CONSTRAINT goal_progress_snapshot_goal_date_uk UNIQUE (goal_id, reference_date),
  CONSTRAINT goal_progress_snapshot_percentage_ck CHECK (
    percentage >= 0 AND percentage <= 100
  )
);

CREATE INDEX goal_progress_snapshot_goal_date_ix
  ON goal_progress_snapshot (user_id, goal_id, reference_date DESC, id);

COMMENT ON TABLE financial_goal IS
  'Meta financeira; nunca movimenta dinheiro nem cria ordens.';
COMMENT ON TABLE goal_allocation IS
  'Alocação explícita por valor fixo ou percentual do alvo, com resíduo explícito.';
COMMENT ON TABLE goal_progress_snapshot IS
  'Histórico datado de progresso; um registro por meta e dia.';
