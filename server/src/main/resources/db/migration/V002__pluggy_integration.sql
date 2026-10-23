-- V002: integração pessoal Meu Pluggy e conexões (itens).
--
-- Credenciais e itemIds são cifrados; hashes HMAC permitem igualdade/unicidade
-- sem texto claro. Nenhuma API Key é persistida.

CREATE TABLE pluggy_integration (
  id                     uuid        NOT NULL,
  user_id                uuid        NOT NULL,
  provider               text        NOT NULL,
  client_id_enc          bytea,
  client_secret_enc      bytea,
  encryption_key_version integer,
  status                 text        NOT NULL,
  last_sync_attempt_at   timestamptz,
  last_successful_sync_at timestamptz,
  last_error_code        text,
  created_at             timestamptz NOT NULL,
  updated_at             timestamptz NOT NULL,
  version                bigint      NOT NULL DEFAULT 0,
  CONSTRAINT pluggy_integration_pk PRIMARY KEY (id),
  CONSTRAINT pluggy_integration_user_fk FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE,
  CONSTRAINT pluggy_integration_user_provider_uk UNIQUE (user_id, provider),
  CONSTRAINT pluggy_integration_user_id_uk UNIQUE (user_id, id),
  CONSTRAINT pluggy_integration_provider_ck CHECK (provider IN ('MEU_PLUGGY')),
  CONSTRAINT pluggy_integration_status_ck CHECK (
    status IN ('INCOMPLETE', 'ACTIVE', 'SYNCING', 'SYNCED', 'CREDENTIAL_FAILURE',
               'ACTION_REQUIRED', 'AUTHORIZATION_EXPIRED', 'ITEM_UNAVAILABLE')
  )
);

COMMENT ON TABLE pluggy_integration IS
  'Uma integração Meu Pluggy por usuário. Client ID/Secret cifrados; API Key só em memória.';

CREATE TABLE pluggy_connection (
  id                    uuid        NOT NULL,
  user_id               uuid        NOT NULL,
  integration_id        uuid        NOT NULL,
  external_item_id_enc  bytea,
  external_item_id_hash bytea,
  institution_name_enc  bytea,
  status                text        NOT NULL,
  consent_expires_at    timestamptz,
  next_auto_sync_at     timestamptz,
  last_sync_at          timestamptz,
  last_error_code       text,
  consecutive_failures  integer     NOT NULL DEFAULT 0,
  created_at            timestamptz NOT NULL,
  updated_at            timestamptz NOT NULL,
  version               bigint      NOT NULL DEFAULT 0,
  CONSTRAINT pluggy_connection_pk PRIMARY KEY (id),
  CONSTRAINT pluggy_connection_user_id_uk UNIQUE (user_id, id),
  CONSTRAINT pluggy_connection_integration_fk FOREIGN KEY (user_id, integration_id)
    REFERENCES pluggy_integration (user_id, id) ON DELETE CASCADE,
  CONSTRAINT pluggy_connection_item_uk UNIQUE (integration_id, external_item_id_hash),
  CONSTRAINT pluggy_connection_status_ck CHECK (
    status IN ('INCOMPLETE', 'ACTIVE', 'SYNCING', 'SYNCED', 'CREDENTIAL_FAILURE',
               'ACTION_REQUIRED', 'AUTHORIZATION_EXPIRED', 'ITEM_UNAVAILABLE')
  )
);

CREATE INDEX pluggy_connection_user_ix ON pluggy_connection (user_id, created_at);

COMMENT ON TABLE pluggy_connection IS
  'Item externo com ID local estável; o itemId pode mudar sem trocar o id local.';
