-- V001: identidade, auditoria, idempotência e sessões.
--
-- Expand/contract: este é o primeiro schema, portanto apenas cria estruturas.
-- Nenhum dado sensível financeiro vive aqui: apenas identidade, metadados de
-- autenticação/auditoria e a sessão opaca.

CREATE TABLE app_user (
  id                    uuid        NOT NULL,
  username_normalized   text        NOT NULL,
  username_display      text        NOT NULL,
  password_hash         text        NOT NULL,
  status                text        NOT NULL,
  deletion_requested_at timestamptz,
  deleted_at            timestamptz,
  created_at            timestamptz NOT NULL,
  updated_at            timestamptz NOT NULL,
  version               bigint      NOT NULL DEFAULT 0,
  CONSTRAINT app_user_pk PRIMARY KEY (id),
  CONSTRAINT app_user_username_normalized_uk UNIQUE (username_normalized),
  CONSTRAINT app_user_username_length_ck
    CHECK (char_length(username_normalized) BETWEEN 3 AND 40),
  CONSTRAINT app_user_status_ck
    CHECK (status IN ('ACTIVE', 'DELETION_PENDING', 'DELETED')),
  CONSTRAINT app_user_deleted_consistency_ck
    CHECK (deleted_at IS NULL OR status = 'DELETED')
);

COMMENT ON TABLE app_user IS 'Conta de acesso; nunca contém dados financeiros.';
COMMENT ON COLUMN app_user.username_normalized IS 'NFKC + trim + minúsculas, único globalmente.';
COMMENT ON COLUMN app_user.password_hash IS 'Argon2id; nunca reversível.';

CREATE TABLE login_attempt (
  id             uuid        NOT NULL,
  username_hash  bytea       NOT NULL,
  ip_hash        bytea       NOT NULL,
  attempted_at   timestamptz NOT NULL,
  success        boolean     NOT NULL,
  failure_reason text,
  CONSTRAINT login_attempt_pk PRIMARY KEY (id),
  CONSTRAINT login_attempt_reason_ck
    CHECK (failure_reason IS NULL OR failure_reason IN ('BAD_CREDENTIALS', 'RATE_LIMITED')),
  CONSTRAINT login_attempt_success_consistency_ck
    CHECK ((success AND failure_reason IS NULL) OR (NOT success AND failure_reason IS NOT NULL))
);

CREATE INDEX login_attempt_username_time_ix ON login_attempt (username_hash, attempted_at);
CREATE INDEX login_attempt_ip_time_ix ON login_attempt (ip_hash, attempted_at);
CREATE INDEX login_attempt_attempted_at_ix ON login_attempt (attempted_at);

COMMENT ON TABLE login_attempt IS 'Tentativas de login; IP e usuário apenas como HMAC. Retenção: 30 dias.';

CREATE TABLE audit_event (
  id          uuid        NOT NULL,
  user_id     uuid,
  event_type  text        NOT NULL,
  target_type text,
  target_id   uuid,
  metadata    jsonb       NOT NULL DEFAULT '{}'::jsonb,
  created_at  timestamptz NOT NULL,
  CONSTRAINT audit_event_pk PRIMARY KEY (id),
  CONSTRAINT audit_event_user_fk FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE SET NULL
);

CREATE INDEX audit_event_user_time_ix ON audit_event (user_id, created_at);
CREATE INDEX audit_event_type_time_ix ON audit_event (event_type, created_at);

COMMENT ON TABLE audit_event IS
  'Auditoria sanitizada: apenas IDs, códigos e contadores. Retenção: 1 ano.';

CREATE TABLE idempotency_key (
  id                     uuid        NOT NULL,
  user_id                uuid        NOT NULL,
  key                    text        NOT NULL,
  method                 text        NOT NULL,
  path                   text        NOT NULL,
  request_hash           bytea       NOT NULL,
  response_status        integer,
  response_resource_type text,
  response_resource_id   uuid,
  response_body_enc      bytea,
  response_body_hash     bytea,
  created_at             timestamptz NOT NULL,
  expires_at             timestamptz NOT NULL,
  CONSTRAINT idempotency_key_pk PRIMARY KEY (id),
  CONSTRAINT idempotency_key_user_fk FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE,
  CONSTRAINT idempotency_key_user_key_uk UNIQUE (user_id, key),
  CONSTRAINT idempotency_key_key_length_ck CHECK (char_length(key) BETWEEN 8 AND 128)
);

CREATE INDEX idempotency_key_expires_at_ix ON idempotency_key (expires_at);

COMMENT ON TABLE idempotency_key IS 'Replay de mutações; TTL de 24 h.';

-- ---------------------------------------------------------------------------
-- Spring Session JDBC (schema oficial 4.1.x para PostgreSQL).
-- O schema é gerenciado pelo Flyway (initialize-schema=never na aplicação).
-- ---------------------------------------------------------------------------
CREATE TABLE SPRING_SESSION (
  PRIMARY_ID            CHAR(36) NOT NULL,
  SESSION_ID            CHAR(36) NOT NULL,
  CREATION_TIME         BIGINT   NOT NULL,
  LAST_ACCESS_TIME      BIGINT   NOT NULL,
  MAX_INACTIVE_INTERVAL INT      NOT NULL,
  EXPIRY_TIME           BIGINT   NOT NULL,
  PRINCIPAL_NAME        VARCHAR(100),
  CONSTRAINT SPRING_SESSION_PK PRIMARY KEY (PRIMARY_ID)
);

CREATE UNIQUE INDEX SPRING_SESSION_IX1 ON SPRING_SESSION (SESSION_ID);
CREATE INDEX SPRING_SESSION_IX2 ON SPRING_SESSION (EXPIRY_TIME);
CREATE INDEX SPRING_SESSION_IX3 ON SPRING_SESSION (PRINCIPAL_NAME);

CREATE TABLE SPRING_SESSION_ATTRIBUTES (
  SESSION_PRIMARY_ID CHAR(36)     NOT NULL,
  ATTRIBUTE_NAME     VARCHAR(200) NOT NULL,
  ATTRIBUTE_BYTES    BYTEA        NOT NULL,
  CONSTRAINT SPRING_SESSION_ATTRIBUTES_PK PRIMARY KEY (SESSION_PRIMARY_ID, ATTRIBUTE_NAME),
  CONSTRAINT SPRING_SESSION_ATTRIBUTES_FK FOREIGN KEY (SESSION_PRIMARY_ID)
    REFERENCES SPRING_SESSION (PRIMARY_ID) ON DELETE CASCADE
);
