-- V004: fila de sincronização, execuções e webhooks opcionais.
--
-- Jobs e execuções são metadados operacionais em claro (IDs, estados, códigos
-- sanitizados e contadores). Payloads de webhook não são persistidos: apenas o
-- HMAC de integridade e o identificador do evento.

CREATE TABLE sync_job (
  id               uuid        NOT NULL,
  user_id          uuid,
  integration_id   uuid,
  connection_id    uuid,
  job_type         text        NOT NULL,
  status           text        NOT NULL,
  run_after        timestamptz NOT NULL,
  attempts         integer     NOT NULL DEFAULT 0,
  max_attempts     integer     NOT NULL DEFAULT 5,
  locked_by        text,
  locked_at        timestamptz,
  payload_hash     bytea,
  last_error_code  text,
  created_at       timestamptz NOT NULL,
  updated_at       timestamptz NOT NULL,
  version          bigint      NOT NULL DEFAULT 0,
  CONSTRAINT sync_job_pk PRIMARY KEY (id),
  CONSTRAINT sync_job_user_fk FOREIGN KEY (user_id)
    REFERENCES app_user (id) ON DELETE CASCADE,
  CONSTRAINT sync_job_integration_fk FOREIGN KEY (user_id, integration_id)
    REFERENCES pluggy_integration (user_id, id) ON DELETE CASCADE,
  CONSTRAINT sync_job_connection_fk FOREIGN KEY (user_id, connection_id)
    REFERENCES pluggy_connection (user_id, id) ON DELETE CASCADE,
  CONSTRAINT sync_job_type_ck CHECK (
    job_type IN ('SYNC_INTEGRATION', 'SYNC_CONNECTION', 'PROCESS_WEBHOOK',
                 'RECALCULATE_INVOICE', 'RECONCILE')
  ),
  CONSTRAINT sync_job_status_ck CHECK (
    status IN ('QUEUED', 'RUNNING', 'SUCCEEDED', 'FAILED', 'RETRY')
  ),
  CONSTRAINT sync_job_attempts_ck CHECK (attempts >= 0 AND max_attempts BETWEEN 1 AND 20)
);

CREATE INDEX sync_job_due_ix ON sync_job (status, run_after);
CREATE INDEX sync_job_user_ix ON sync_job (user_id, created_at);

-- Um único job ativo por alvo/tipo. O enfileiramento repetido devolve o job
-- existente em vez de criar trabalho concorrente para a mesma conexão.
CREATE UNIQUE INDEX sync_job_active_uk
  ON sync_job (job_type, COALESCE(connection_id, integration_id, id))
  WHERE status IN ('QUEUED', 'RUNNING', 'RETRY');

COMMENT ON TABLE sync_job IS
  'Fila durável com claim FOR UPDATE SKIP LOCKED. Nenhum dado financeiro no payload.';

CREATE TABLE sync_run (
  id              uuid        NOT NULL,
  user_id         uuid        NOT NULL,
  integration_id  uuid        NOT NULL,
  connection_id   uuid,
  status          text        NOT NULL,
  started_at      timestamptz NOT NULL,
  finished_at     timestamptz,
  read_count      integer     NOT NULL DEFAULT 0,
  created_count   integer     NOT NULL DEFAULT 0,
  updated_count   integer     NOT NULL DEFAULT 0,
  skipped_count   integer     NOT NULL DEFAULT 0,
  failed_count    integer     NOT NULL DEFAULT 0,
  duration_ms     bigint,
  error_code      text,
  correlation_id  text,
  created_at      timestamptz NOT NULL,
  updated_at      timestamptz NOT NULL,
  version         bigint      NOT NULL DEFAULT 0,
  CONSTRAINT sync_run_pk PRIMARY KEY (id),
  CONSTRAINT sync_run_user_fk FOREIGN KEY (user_id)
    REFERENCES app_user (id) ON DELETE CASCADE,
  CONSTRAINT sync_run_integration_fk FOREIGN KEY (user_id, integration_id)
    REFERENCES pluggy_integration (user_id, id) ON DELETE CASCADE,
  CONSTRAINT sync_run_connection_fk FOREIGN KEY (user_id, connection_id)
    REFERENCES pluggy_connection (user_id, id) ON DELETE CASCADE,
  CONSTRAINT sync_run_status_ck CHECK (
    status IN ('SUCCESS', 'PARTIAL', 'EMPTY', 'AUTH_ERROR', 'RATE_LIMITED', 'TIMEOUT', 'FAILED')
  ),
  CONSTRAINT sync_run_counts_ck CHECK (
    read_count >= 0 AND created_count >= 0 AND updated_count >= 0
    AND skipped_count >= 0 AND failed_count >= 0
  )
);

CREATE INDEX sync_run_user_ix ON sync_run (user_id, started_at DESC, id);
CREATE INDEX sync_run_integration_ix ON sync_run (integration_id, started_at DESC);

COMMENT ON TABLE sync_run IS
  'Fonte de verdade operacional de cada tentativa de sincronização. Retenção: 90 dias.';

CREATE TABLE webhook_subscription (
  id                            uuid        NOT NULL,
  user_id                       uuid        NOT NULL,
  integration_id                uuid        NOT NULL,
  provider_subscription_id_hash bytea       NOT NULL,
  secret_enc                    bytea       NOT NULL,
  event_types                   text[]      NOT NULL DEFAULT '{}',
  status                        text        NOT NULL,
  created_at                    timestamptz NOT NULL,
  updated_at                    timestamptz NOT NULL,
  version                       bigint      NOT NULL DEFAULT 0,
  CONSTRAINT webhook_subscription_pk PRIMARY KEY (id),
  CONSTRAINT webhook_subscription_user_fk FOREIGN KEY (user_id)
    REFERENCES app_user (id) ON DELETE CASCADE,
  CONSTRAINT webhook_subscription_integration_fk FOREIGN KEY (user_id, integration_id)
    REFERENCES pluggy_integration (user_id, id) ON DELETE CASCADE,
  CONSTRAINT webhook_subscription_uk UNIQUE (integration_id, provider_subscription_id_hash),
  CONSTRAINT webhook_subscription_status_ck CHECK (status IN ('ACTIVE', 'DISABLED', 'ERROR'))
);

CREATE INDEX webhook_subscription_user_ix ON webhook_subscription (user_id, created_at);

CREATE TABLE webhook_event (
  id                 uuid        NOT NULL,
  user_id            uuid        NOT NULL,
  integration_id     uuid        NOT NULL,
  connection_id      uuid,
  provider_event_id  text        NOT NULL,
  event_type         text,
  item_id_hash       bytea,
  payload_hash       bytea       NOT NULL,
  status             text        NOT NULL,
  attempts           integer     NOT NULL DEFAULT 0,
  error_code         text,
  received_at        timestamptz NOT NULL,
  processed_at       timestamptz,
  created_at         timestamptz NOT NULL,
  updated_at         timestamptz NOT NULL,
  version            bigint      NOT NULL DEFAULT 0,
  CONSTRAINT webhook_event_pk PRIMARY KEY (id),
  CONSTRAINT webhook_event_user_fk FOREIGN KEY (user_id)
    REFERENCES app_user (id) ON DELETE CASCADE,
  CONSTRAINT webhook_event_integration_fk FOREIGN KEY (user_id, integration_id)
    REFERENCES pluggy_integration (user_id, id) ON DELETE CASCADE,
  CONSTRAINT webhook_event_connection_fk FOREIGN KEY (user_id, connection_id)
    REFERENCES pluggy_connection (user_id, id) ON DELETE CASCADE,
  CONSTRAINT webhook_event_uk UNIQUE (integration_id, provider_event_id),
  CONSTRAINT webhook_event_status_ck CHECK (
    status IN ('RECEIVED', 'PROCESSED', 'FAILED', 'IGNORED')
  )
);

CREATE INDEX webhook_event_status_ix ON webhook_event (status, received_at);
CREATE INDEX webhook_event_connection_ix ON webhook_event (connection_id, received_at DESC);

COMMENT ON TABLE webhook_event IS
  'eventId idempotente por integração. Payload completo não é persistido. Retenção: 90 dias.';
