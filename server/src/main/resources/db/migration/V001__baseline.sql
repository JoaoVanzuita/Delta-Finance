-- V001: baseline do Delta Finance (consolidada em 2026-10-26).
--
-- Gerada a partir das migrações V001–V019 após os cortes de simplificação.
-- Contém apenas tabelas, colunas, índices e checks ativos.
-- Webhooks, snapshots patrimoniais, Bills e dívida inicial não fazem parte
-- deste schema. Próximas migrações começam em V002.
--
-- Não edite esta migração depois de aplicada; correções exigem nova versão.

--
-- PostgreSQL database dump
--


-- Dumped from database version 18.6
-- Dumped by pg_dump version 18.6

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET transaction_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: app_user; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.app_user (
    id uuid NOT NULL,
    username_normalized text NOT NULL,
    username_display text NOT NULL,
    password_hash text NOT NULL,
    status text NOT NULL,
    deletion_requested_at timestamp with time zone,
    deleted_at timestamp with time zone,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    CONSTRAINT app_user_deleted_consistency_ck CHECK (((deleted_at IS NULL) OR (status = 'DELETED'::text))),
    CONSTRAINT app_user_status_ck CHECK ((status = ANY (ARRAY['ACTIVE'::text, 'DELETION_PENDING'::text, 'DELETED'::text]))),
    CONSTRAINT app_user_username_length_ck CHECK (((char_length(username_normalized) >= 3) AND (char_length(username_normalized) <= 40)))
);


--
-- Name: TABLE app_user; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.app_user IS 'Conta de acesso; nunca contém dados financeiros.';


--
-- Name: COLUMN app_user.username_normalized; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.app_user.username_normalized IS 'NFKC + trim + minúsculas, único globalmente.';


--
-- Name: COLUMN app_user.password_hash; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.app_user.password_hash IS 'Argon2id; nunca reversível.';


--
-- Name: audit_event; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.audit_event (
    id uuid NOT NULL,
    user_id uuid,
    event_type text NOT NULL,
    target_type text,
    target_id uuid,
    metadata jsonb DEFAULT '{}'::jsonb NOT NULL,
    created_at timestamp with time zone NOT NULL
);


--
-- Name: TABLE audit_event; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.audit_event IS 'Auditoria sanitizada: apenas IDs, códigos e contadores. Retenção: 1 ano.';


--
-- Name: card_profile; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.card_profile (
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    account_id uuid NOT NULL,
    closing_day integer NOT NULL,
    due_day integer NOT NULL,
    credit_limit_enc bytea,
    credit_balance_enc bytea,
    source text NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    CONSTRAINT card_profile_day_ck CHECK ((((closing_day >= 1) AND (closing_day <= 31)) AND ((due_day >= 1) AND (due_day <= 31)))),
    CONSTRAINT card_profile_source_ck CHECK ((source = ANY (ARRAY['PROVIDER'::text, 'USER'::text])))
);


--
-- Name: category; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.category (
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    name_enc bytea NOT NULL,
    normalized_name_hash bytea NOT NULL,
    kind text NOT NULL,
    predefined_key text,
    active boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    CONSTRAINT category_kind_ck CHECK ((kind = ANY (ARRAY['INCOME'::text, 'EXPENSE'::text, 'TRANSFER'::text, 'OTHER'::text])))
);


--
-- Name: external_identity_history; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.external_identity_history (
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    transaction_id uuid NOT NULL,
    account_id uuid NOT NULL,
    source text NOT NULL,
    external_id_enc bytea NOT NULL,
    external_id_hash bytea NOT NULL,
    first_seen_at timestamp with time zone NOT NULL,
    last_seen_at timestamp with time zone NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    CONSTRAINT external_identity_history_source_ck CHECK ((source = 'PLUGGY'::text))
);


--
-- Name: financial_account; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.financial_account (
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    connection_id uuid,
    origin text NOT NULL,
    type text NOT NULL,
    subtype text,
    external_account_id_enc bytea,
    external_account_id_hash bytea,
    institution_name_enc bytea,
    alias_enc bytea,
    currency character varying(3) DEFAULT 'BRL'::character varying NOT NULL,
    balance_enc bytea,
    balance_reference_at timestamp with time zone,
    status text NOT NULL,
    last_sync_at timestamp with time zone,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    notes_enc bytea,
    CONSTRAINT financial_account_origin_ck CHECK ((origin = ANY (ARRAY['IMPORTED'::text, 'MANUAL'::text]))),
    CONSTRAINT financial_account_origin_connection_ck CHECK ((((origin = 'IMPORTED'::text) AND (connection_id IS NOT NULL)) OR ((origin = 'MANUAL'::text) AND (connection_id IS NULL)))),
    CONSTRAINT financial_account_status_ck CHECK ((status = ANY (ARRAY['ACTIVE'::text, 'INACTIVE'::text, 'UNAVAILABLE'::text, 'CLOSED'::text]))),
    CONSTRAINT financial_account_type_ck CHECK ((type = ANY (ARRAY['BANK'::text, 'CREDIT'::text, 'CASH'::text, 'WALLET'::text, 'UNKNOWN'::text])))
);


--
-- Name: financial_goal; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.financial_goal (
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    name_enc bytea NOT NULL,
    description_enc bytea,
    type text NOT NULL,
    target_amount_enc bytea NOT NULL,
    target_date date,
    status text NOT NULL,
    progress_rule text NOT NULL,
    changed_at timestamp with time zone,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    notes_enc bytea,
    CONSTRAINT financial_goal_rule_ck CHECK ((progress_rule = ANY (ARRAY['ALLOCATIONS'::text, 'NET_WORTH'::text]))),
    CONSTRAINT financial_goal_status_ck CHECK ((status = ANY (ARRAY['ACTIVE'::text, 'ACHIEVED'::text, 'PAUSED'::text, 'ARCHIVED'::text, 'OVERDUE'::text, 'CHANGED'::text]))),
    CONSTRAINT financial_goal_type_ck CHECK ((type = ANY (ARRAY['EMERGENCY_FUND'::text, 'TRAVEL'::text, 'PLANNED_PURCHASE'::text, 'NET_WORTH'::text])))
);


--
-- Name: TABLE financial_goal; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.financial_goal IS 'Meta financeira; nunca movimenta dinheiro nem cria ordens.';


--
-- Name: COLUMN financial_goal.notes_enc; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.financial_goal.notes_enc IS 'Observações livres da meta, cifradas com AES-256-GCM.';


--
-- Name: goal_allocation; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.goal_allocation (
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    goal_id uuid NOT NULL,
    ordinal integer NOT NULL,
    source_type text NOT NULL,
    source_id uuid,
    allocation_mode text NOT NULL,
    allocated_amount_enc bytea,
    allocation_ratio numeric(9,6),
    effective_from date NOT NULL,
    effective_to date,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    CONSTRAINT goal_allocation_exactly_one_ck CHECK ((((allocation_mode = 'FIXED_AMOUNT'::text) AND (allocated_amount_enc IS NOT NULL) AND (allocation_ratio IS NULL)) OR ((allocation_mode = 'PERCENTAGE'::text) AND (allocation_ratio IS NOT NULL) AND (allocated_amount_enc IS NULL)))),
    CONSTRAINT goal_allocation_manual_source_ck CHECK ((((source_type = 'MANUAL'::text) AND (source_id IS NULL)) OR ((source_type <> 'MANUAL'::text) AND (source_id IS NOT NULL)))),
    CONSTRAINT goal_allocation_mode_ck CHECK ((allocation_mode = ANY (ARRAY['FIXED_AMOUNT'::text, 'PERCENTAGE'::text]))),
    CONSTRAINT goal_allocation_ordinal_ck CHECK ((ordinal >= 0)),
    CONSTRAINT goal_allocation_ratio_ck CHECK (((allocation_ratio IS NULL) OR ((allocation_ratio >= (0)::numeric) AND (allocation_ratio <= (1)::numeric)))),
    CONSTRAINT goal_allocation_source_ck CHECK ((source_type = ANY (ARRAY['MANUAL'::text, 'ACCOUNT'::text, 'INVESTMENT'::text, 'MANUAL_ASSET'::text])))
);


--
-- Name: TABLE goal_allocation; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.goal_allocation IS 'Alocação explícita por valor fixo ou percentual do alvo, com resíduo explícito.';


--
-- Name: goal_progress_snapshot; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.goal_progress_snapshot (
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    goal_id uuid NOT NULL,
    reference_date date NOT NULL,
    current_value_enc bytea NOT NULL,
    percentage numeric(9,4) NOT NULL,
    remaining_enc bytea NOT NULL,
    rule_version text NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    CONSTRAINT goal_progress_snapshot_percentage_ck CHECK (((percentage >= (0)::numeric) AND (percentage <= (100)::numeric)))
);


--
-- Name: TABLE goal_progress_snapshot; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.goal_progress_snapshot IS 'Histórico datado de progresso; um registro por meta e dia.';


--
-- Name: idempotency_key; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.idempotency_key (
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    key text NOT NULL,
    method text NOT NULL,
    path text NOT NULL,
    request_hash bytea NOT NULL,
    response_status integer,
    response_body_enc bytea,
    created_at timestamp with time zone NOT NULL,
    expires_at timestamp with time zone NOT NULL,
    CONSTRAINT idempotency_key_key_length_ck CHECK (((char_length(key) >= 8) AND (char_length(key) <= 128)))
);


--
-- Name: TABLE idempotency_key; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.idempotency_key IS 'Replay de mutações; TTL de 24 h.';


--
-- Name: internal_match; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.internal_match (
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    kind text NOT NULL,
    debit_transaction_id uuid NOT NULL,
    credit_transaction_id uuid NOT NULL,
    score numeric(5,2) NOT NULL,
    criteria_version text NOT NULL,
    status text NOT NULL,
    reason text,
    decided_at timestamp with time zone,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    CONSTRAINT internal_match_distinct_ck CHECK ((debit_transaction_id <> credit_transaction_id)),
    CONSTRAINT internal_match_kind_ck CHECK ((kind = ANY (ARRAY['TRANSFER'::text, 'CARD_PAYMENT'::text]))),
    CONSTRAINT internal_match_score_ck CHECK (((score >= (0)::numeric) AND (score <= (100)::numeric))),
    CONSTRAINT internal_match_status_ck CHECK ((status = ANY (ARRAY['PENDING'::text, 'CONFIRMED'::text, 'REJECTED'::text])))
);


--
-- Name: TABLE internal_match; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.internal_match IS 'Sugestões determinísticas de transferência/pagamento; confirmação explícita do usuário.';


--
-- Name: investment_position; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.investment_position (
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    broker text NOT NULL,
    asset_name_enc bytea NOT NULL,
    ticker_enc bytea,
    quantity_enc bytea NOT NULL,
    reference_price_enc bytea NOT NULL,
    reference_date date NOT NULL,
    notes_enc bytea,
    origin text NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    CONSTRAINT investment_position_broker_ck CHECK ((broker = ANY (ARRAY['XP'::text, 'SANTANDER'::text, 'OTHER'::text]))),
    CONSTRAINT investment_position_origin_ck CHECK ((origin = 'MANUAL'::text))
);


--
-- Name: TABLE investment_position; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.investment_position IS 'Posição manual de investimento; valores cifrados e total derivado na aplicação.';


--
-- Name: invoice; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.invoice (
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    card_account_id uuid NOT NULL,
    cycle_start date NOT NULL,
    cycle_end date NOT NULL,
    closing_date date NOT NULL,
    due_date date NOT NULL,
    status text NOT NULL,
    has_adjustments boolean DEFAULT false NOT NULL,
    adjustment_count integer DEFAULT 0 NOT NULL,
    total_enc bytea NOT NULL,
    payments_total_enc bytea NOT NULL,
    outstanding_enc bytea NOT NULL,
    applied_credit_enc bytea NOT NULL,
    minimum_payment_enc bytea,
    source text NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    CONSTRAINT invoice_adjustment_ck CHECK ((adjustment_count >= 0)),
    CONSTRAINT invoice_cycle_dates_ck CHECK ((cycle_start <= cycle_end)),
    CONSTRAINT invoice_source_ck CHECK ((source = 'LOCAL'::text)),
    CONSTRAINT invoice_status_ck CHECK ((status = ANY (ARRAY['OPEN'::text, 'CLOSED'::text, 'PARTIALLY_PAID'::text, 'PAID'::text, 'OVERDUE'::text])))
);


--
-- Name: TABLE invoice; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.invoice IS 'Fatura local por ciclo; pagamentos e crédito não duplicam despesas.';


--
-- Name: invoice_item; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.invoice_item (
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    invoice_id uuid NOT NULL,
    transaction_id uuid,
    kind text NOT NULL,
    reporting_effect text NOT NULL,
    source text NOT NULL,
    description_enc bytea NOT NULL,
    amount_enc bytea NOT NULL,
    occurred_on date NOT NULL,
    installment_number integer,
    total_installments integer,
    installment_plan_key bytea,
    removed_at timestamp with time zone,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    mirror_suspended boolean DEFAULT false NOT NULL,
    CONSTRAINT invoice_item_effect_ck CHECK ((reporting_effect = ANY (ARRAY['EXPENSE_INCREASE'::text, 'EXPENSE_DECREASE'::text, 'NONE'::text]))),
    CONSTRAINT invoice_item_installment_ck CHECK ((((installment_number IS NULL) AND (total_installments IS NULL)) OR ((installment_number >= 1) AND (total_installments >= 1) AND (installment_number <= total_installments)))),
    CONSTRAINT invoice_item_kind_ck CHECK ((kind = ANY (ARRAY['PURCHASE'::text, 'INSTALLMENT'::text, 'INTEREST'::text, 'FEE'::text, 'TAX'::text, 'CREDIT'::text, 'REFUND'::text, 'MANUAL_ADJUSTMENT'::text]))),
    CONSTRAINT invoice_item_source_ck CHECK ((source = ANY (ARRAY['IMPORTED'::text, 'MANUAL'::text])))
);


--
-- Name: TABLE invoice_item; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.invoice_item IS 'Item de fatura; espelho de transação compõe o total uma única vez.';


--
-- Name: COLUMN invoice_item.mirror_suspended; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.invoice_item.mirror_suspended IS 'Item suspenso por perda de efetividade da transação; pode ser reativado.';


--
-- Name: invoice_item_correction; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.invoice_item_correction (
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    invoice_item_id uuid NOT NULL,
    field_name text NOT NULL,
    old_value_enc bytea,
    new_value_enc bytea NOT NULL,
    reason_enc bytea,
    changed_at timestamp with time zone NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);


--
-- Name: invoice_payment; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.invoice_payment (
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    invoice_id uuid NOT NULL,
    payment_account_id uuid NOT NULL,
    amount_enc bytea NOT NULL,
    paid_on date NOT NULL,
    status text NOT NULL,
    source text NOT NULL,
    source_transaction_id uuid,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    CONSTRAINT invoice_payment_source_ck CHECK ((source = ANY (ARRAY['MANUAL'::text, 'IMPORTED'::text]))),
    CONSTRAINT invoice_payment_status_ck CHECK ((status = ANY (ARRAY['PENDING'::text, 'CONFIRMED'::text, 'REVERSED'::text])))
);


--
-- Name: TABLE invoice_payment; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.invoice_payment IS 'Pagamento de fatura; reduz saldo devedor, não é despesa.';


--
-- Name: login_attempt; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.login_attempt (
    id uuid NOT NULL,
    username_hash bytea NOT NULL,
    ip_hash bytea NOT NULL,
    attempted_at timestamp with time zone NOT NULL,
    success boolean NOT NULL,
    failure_reason text,
    blocked_until timestamp with time zone,
    CONSTRAINT login_attempt_reason_ck CHECK (((failure_reason IS NULL) OR (failure_reason = ANY (ARRAY['BAD_CREDENTIALS'::text, 'RATE_LIMITED'::text])))),
    CONSTRAINT login_attempt_success_consistency_ck CHECK (((success AND (failure_reason IS NULL)) OR ((NOT success) AND (failure_reason IS NOT NULL))))
);


--
-- Name: TABLE login_attempt; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.login_attempt IS 'Tentativas de login; IP e usuário apenas como HMAC. Retenção: 30 dias.';


--
-- Name: COLUMN login_attempt.blocked_until; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.login_attempt.blocked_until IS 'Fim do bloqueio de 15 minutos gravado na falha que atingiu o limite.';


--
-- Name: manual_asset_liability; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.manual_asset_liability (
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    kind text NOT NULL,
    type text NOT NULL,
    description_enc bytea NOT NULL,
    reference_value_enc bytea NOT NULL,
    reference_date date NOT NULL,
    notes_enc bytea,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    CONSTRAINT manual_asset_liability_kind_ck CHECK ((kind = ANY (ARRAY['ASSET'::text, 'LIABILITY'::text]))),
    CONSTRAINT manual_asset_liability_type_ck CHECK (((char_length(type) >= 1) AND (char_length(type) <= 60)))
);


--
-- Name: TABLE manual_asset_liability; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.manual_asset_liability IS 'Ativo ou passivo manual com valor e data de referência informados pelo usuário.';


--
-- Name: pluggy_connection; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.pluggy_connection (
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    integration_id uuid NOT NULL,
    external_item_id_enc bytea,
    external_item_id_hash bytea,
    institution_name_enc bytea,
    status text NOT NULL,
    consent_expires_at timestamp with time zone,
    next_auto_sync_at timestamp with time zone,
    last_sync_at timestamp with time zone,
    last_error_code text,
    consecutive_failures integer DEFAULT 0 NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    CONSTRAINT pluggy_connection_status_ck CHECK ((status = ANY (ARRAY['INCOMPLETE'::text, 'ACTIVE'::text, 'SYNCING'::text, 'SYNCED'::text, 'CREDENTIAL_FAILURE'::text, 'ACTION_REQUIRED'::text, 'AUTHORIZATION_EXPIRED'::text, 'ITEM_UNAVAILABLE'::text])))
);


--
-- Name: TABLE pluggy_connection; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.pluggy_connection IS 'Item externo com ID local estável; o itemId pode mudar sem trocar o id local.';


--
-- Name: pluggy_integration; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.pluggy_integration (
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    provider text NOT NULL,
    client_id_enc bytea,
    client_secret_enc bytea,
    encryption_key_version integer,
    status text NOT NULL,
    last_sync_attempt_at timestamp with time zone,
    last_successful_sync_at timestamp with time zone,
    last_error_code text,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    CONSTRAINT pluggy_integration_provider_ck CHECK ((provider = 'MEU_PLUGGY'::text)),
    CONSTRAINT pluggy_integration_status_ck CHECK ((status = ANY (ARRAY['INCOMPLETE'::text, 'ACTIVE'::text, 'SYNCING'::text, 'SYNCED'::text, 'CREDENTIAL_FAILURE'::text, 'ACTION_REQUIRED'::text, 'AUTHORIZATION_EXPIRED'::text, 'ITEM_UNAVAILABLE'::text])))
);


--
-- Name: TABLE pluggy_integration; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.pluggy_integration IS 'Uma integração Meu Pluggy por usuário. Client ID/Secret cifrados; API Key só em memória.';


--
-- Name: spring_session; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.spring_session (
    primary_id character(36) NOT NULL,
    session_id character(36) NOT NULL,
    creation_time bigint NOT NULL,
    last_access_time bigint NOT NULL,
    max_inactive_interval integer NOT NULL,
    expiry_time bigint NOT NULL,
    principal_name character varying(100)
);


--
-- Name: spring_session_attributes; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.spring_session_attributes (
    session_primary_id character(36) NOT NULL,
    attribute_name character varying(200) NOT NULL,
    attribute_bytes bytea NOT NULL
);


--
-- Name: sync_job; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.sync_job (
    id uuid NOT NULL,
    user_id uuid,
    integration_id uuid,
    connection_id uuid,
    job_type text NOT NULL,
    status text NOT NULL,
    run_after timestamp with time zone NOT NULL,
    attempts integer DEFAULT 0 NOT NULL,
    max_attempts integer DEFAULT 5 NOT NULL,
    locked_by text,
    locked_at timestamp with time zone,
    payload_hash bytea,
    last_error_code text,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    CONSTRAINT sync_job_attempts_ck CHECK (((attempts >= 0) AND ((max_attempts >= 1) AND (max_attempts <= 20)))),
    CONSTRAINT sync_job_status_ck CHECK ((status = ANY (ARRAY['QUEUED'::text, 'RUNNING'::text, 'SUCCEEDED'::text, 'FAILED'::text, 'RETRY'::text]))),
    CONSTRAINT sync_job_type_ck CHECK ((job_type = ANY (ARRAY['SYNC_INTEGRATION'::text, 'SYNC_CONNECTION'::text, 'RECALCULATE_INVOICE'::text, 'RECONCILE'::text])))
);


--
-- Name: TABLE sync_job; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.sync_job IS 'Fila durável com claim FOR UPDATE SKIP LOCKED. Nenhum dado financeiro no payload.';


--
-- Name: sync_run; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.sync_run (
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    integration_id uuid NOT NULL,
    connection_id uuid,
    status text NOT NULL,
    started_at timestamp with time zone NOT NULL,
    finished_at timestamp with time zone,
    read_count integer DEFAULT 0 NOT NULL,
    created_count integer DEFAULT 0 NOT NULL,
    updated_count integer DEFAULT 0 NOT NULL,
    skipped_count integer DEFAULT 0 NOT NULL,
    failed_count integer DEFAULT 0 NOT NULL,
    duration_ms bigint,
    error_code text,
    correlation_id text,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    CONSTRAINT sync_run_counts_ck CHECK (((read_count >= 0) AND (created_count >= 0) AND (updated_count >= 0) AND (skipped_count >= 0) AND (failed_count >= 0))),
    CONSTRAINT sync_run_status_ck CHECK ((status = ANY (ARRAY['SUCCESS'::text, 'PARTIAL'::text, 'EMPTY'::text, 'AUTH_ERROR'::text, 'RATE_LIMITED'::text, 'TIMEOUT'::text, 'FAILED'::text])))
);


--
-- Name: TABLE sync_run; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.sync_run IS 'Fonte de verdade operacional de cada tentativa de sincronização. Retenção: 90 dias.';


--
-- Name: transaction; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.transaction (
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    account_id uuid NOT NULL,
    category_id uuid,
    source text NOT NULL,
    type text NOT NULL,
    status text NOT NULL,
    posted_on date NOT NULL,
    source_posted_on date,
    currency character varying(3) DEFAULT 'BRL'::character varying NOT NULL,
    amount_source_enc bytea NOT NULL,
    amount_effective_enc bytea NOT NULL,
    description_source_enc bytea NOT NULL,
    description_effective_enc bytea NOT NULL,
    external_transaction_id_enc bytea,
    external_transaction_id_hash bytea,
    dedup_key bytea,
    fingerprint bytea,
    related_transaction_id uuid,
    adjustment_reason_enc bytea,
    edited_fields text[] DEFAULT '{}'::text[] NOT NULL,
    reconciliation_state text DEFAULT 'NONE'::text NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    notes_enc bytea,
    invoice_id uuid,
    installment_number integer,
    total_installments integer,
    CONSTRAINT transaction_installment_ck CHECK ((((installment_number IS NULL) AND (total_installments IS NULL)) OR ((installment_number >= 1) AND (total_installments >= 1) AND (installment_number <= total_installments)))),
    CONSTRAINT transaction_reconciliation_ck CHECK ((reconciliation_state = ANY (ARRAY['NONE'::text, 'PENDING_MATCH'::text, 'MATCHED'::text, 'SOURCE_REMOVED'::text]))),
    CONSTRAINT transaction_source_ck CHECK ((source = ANY (ARRAY['IMPORTED'::text, 'MANUAL'::text]))),
    CONSTRAINT transaction_status_ck CHECK ((status = ANY (ARRAY['PENDING'::text, 'POSTED'::text, 'SOURCE_REMOVED'::text, 'RECONCILED'::text]))),
    CONSTRAINT transaction_type_ck CHECK ((type = ANY (ARRAY['DEBIT'::text, 'CREDIT'::text, 'TRANSFER'::text, 'REFUND'::text, 'CHARGEBACK'::text, 'ADJUSTMENT'::text])))
);


--
-- Name: COLUMN transaction.installment_number; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.transaction.installment_number IS 'Número da parcela informado pela origem; nulo quando não parcelada.';


--
-- Name: COLUMN transaction.total_installments; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.transaction.total_installments IS 'Total de parcelas informado pela origem; nulo quando não parcelada.';


--
-- Name: transaction_correction; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.transaction_correction (
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    transaction_id uuid NOT NULL,
    field_name text NOT NULL,
    old_value_enc bytea,
    new_value_enc bytea NOT NULL,
    changed_at timestamp with time zone NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    CONSTRAINT transaction_correction_field_ck CHECK ((field_name = ANY (ARRAY['description'::text, 'postedOn'::text, 'amount'::text, 'category'::text, 'notes'::text, 'relatedTransactionId'::text])))
);


--
-- Name: TABLE transaction_correction; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.transaction_correction IS 'Auditoria de correções do usuário; somente nomes de campo e valores cifrados.';


--
-- Name: app_user app_user_pk; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.app_user
    ADD CONSTRAINT app_user_pk PRIMARY KEY (id);


--
-- Name: app_user app_user_username_normalized_uk; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.app_user
    ADD CONSTRAINT app_user_username_normalized_uk UNIQUE (username_normalized);


--
-- Name: audit_event audit_event_pk; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.audit_event
    ADD CONSTRAINT audit_event_pk PRIMARY KEY (id);


--
-- Name: card_profile card_profile_account_uk; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.card_profile
    ADD CONSTRAINT card_profile_account_uk UNIQUE (user_id, account_id);


--
-- Name: card_profile card_profile_pk; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.card_profile
    ADD CONSTRAINT card_profile_pk PRIMARY KEY (id);


--
-- Name: card_profile card_profile_user_id_uk; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.card_profile
    ADD CONSTRAINT card_profile_user_id_uk UNIQUE (user_id, id);


--
-- Name: category category_name_uk; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.category
    ADD CONSTRAINT category_name_uk UNIQUE (user_id, normalized_name_hash);


--
-- Name: category category_pk; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.category
    ADD CONSTRAINT category_pk PRIMARY KEY (id);


--
-- Name: category category_user_id_uk; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.category
    ADD CONSTRAINT category_user_id_uk UNIQUE (user_id, id);


--
-- Name: external_identity_history external_identity_history_pk; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.external_identity_history
    ADD CONSTRAINT external_identity_history_pk PRIMARY KEY (id);


--
-- Name: external_identity_history external_identity_history_uk; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.external_identity_history
    ADD CONSTRAINT external_identity_history_uk UNIQUE (user_id, source, account_id, external_id_hash);


--
-- Name: financial_account financial_account_pk; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.financial_account
    ADD CONSTRAINT financial_account_pk PRIMARY KEY (id);


--
-- Name: financial_account financial_account_user_id_uk; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.financial_account
    ADD CONSTRAINT financial_account_user_id_uk UNIQUE (user_id, id);


--
-- Name: financial_goal financial_goal_pk; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.financial_goal
    ADD CONSTRAINT financial_goal_pk PRIMARY KEY (id);


--
-- Name: financial_goal financial_goal_user_id_uk; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.financial_goal
    ADD CONSTRAINT financial_goal_user_id_uk UNIQUE (user_id, id);


--
-- Name: goal_allocation goal_allocation_pk; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.goal_allocation
    ADD CONSTRAINT goal_allocation_pk PRIMARY KEY (id);


--
-- Name: goal_allocation goal_allocation_user_id_uk; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.goal_allocation
    ADD CONSTRAINT goal_allocation_user_id_uk UNIQUE (user_id, id);


--
-- Name: goal_progress_snapshot goal_progress_snapshot_goal_date_uk; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.goal_progress_snapshot
    ADD CONSTRAINT goal_progress_snapshot_goal_date_uk UNIQUE (goal_id, reference_date);


--
-- Name: goal_progress_snapshot goal_progress_snapshot_pk; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.goal_progress_snapshot
    ADD CONSTRAINT goal_progress_snapshot_pk PRIMARY KEY (id);


--
-- Name: goal_progress_snapshot goal_progress_snapshot_user_id_uk; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.goal_progress_snapshot
    ADD CONSTRAINT goal_progress_snapshot_user_id_uk UNIQUE (user_id, id);


--
-- Name: idempotency_key idempotency_key_pk; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.idempotency_key
    ADD CONSTRAINT idempotency_key_pk PRIMARY KEY (id);


--
-- Name: idempotency_key idempotency_key_user_key_uk; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.idempotency_key
    ADD CONSTRAINT idempotency_key_user_key_uk UNIQUE (user_id, key);


--
-- Name: internal_match internal_match_pk; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.internal_match
    ADD CONSTRAINT internal_match_pk PRIMARY KEY (id);


--
-- Name: internal_match internal_match_uk; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.internal_match
    ADD CONSTRAINT internal_match_uk UNIQUE (user_id, kind, debit_transaction_id, credit_transaction_id);


--
-- Name: investment_position investment_position_pk; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.investment_position
    ADD CONSTRAINT investment_position_pk PRIMARY KEY (id);


--
-- Name: investment_position investment_position_user_id_uk; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.investment_position
    ADD CONSTRAINT investment_position_user_id_uk UNIQUE (user_id, id);


--
-- Name: invoice invoice_cycle_uk; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.invoice
    ADD CONSTRAINT invoice_cycle_uk UNIQUE (card_account_id, cycle_start);


--
-- Name: invoice_item_correction invoice_item_correction_pk; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.invoice_item_correction
    ADD CONSTRAINT invoice_item_correction_pk PRIMARY KEY (id);


--
-- Name: invoice_item invoice_item_pk; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.invoice_item
    ADD CONSTRAINT invoice_item_pk PRIMARY KEY (id);


--
-- Name: invoice_item invoice_item_user_id_uk; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.invoice_item
    ADD CONSTRAINT invoice_item_user_id_uk UNIQUE (user_id, id);


--
-- Name: invoice_payment invoice_payment_pk; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.invoice_payment
    ADD CONSTRAINT invoice_payment_pk PRIMARY KEY (id);


--
-- Name: invoice_payment invoice_payment_user_id_uk; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.invoice_payment
    ADD CONSTRAINT invoice_payment_user_id_uk UNIQUE (user_id, id);


--
-- Name: invoice invoice_pk; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.invoice
    ADD CONSTRAINT invoice_pk PRIMARY KEY (id);


--
-- Name: invoice invoice_user_id_uk; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.invoice
    ADD CONSTRAINT invoice_user_id_uk UNIQUE (user_id, id);


--
-- Name: login_attempt login_attempt_pk; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.login_attempt
    ADD CONSTRAINT login_attempt_pk PRIMARY KEY (id);


--
-- Name: manual_asset_liability manual_asset_liability_pk; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.manual_asset_liability
    ADD CONSTRAINT manual_asset_liability_pk PRIMARY KEY (id);


--
-- Name: manual_asset_liability manual_asset_liability_user_id_uk; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.manual_asset_liability
    ADD CONSTRAINT manual_asset_liability_user_id_uk UNIQUE (user_id, id);


--
-- Name: pluggy_connection pluggy_connection_item_uk; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pluggy_connection
    ADD CONSTRAINT pluggy_connection_item_uk UNIQUE (integration_id, external_item_id_hash);


--
-- Name: pluggy_connection pluggy_connection_pk; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pluggy_connection
    ADD CONSTRAINT pluggy_connection_pk PRIMARY KEY (id);


--
-- Name: pluggy_connection pluggy_connection_user_id_uk; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pluggy_connection
    ADD CONSTRAINT pluggy_connection_user_id_uk UNIQUE (user_id, id);


--
-- Name: pluggy_integration pluggy_integration_pk; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pluggy_integration
    ADD CONSTRAINT pluggy_integration_pk PRIMARY KEY (id);


--
-- Name: pluggy_integration pluggy_integration_user_id_uk; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pluggy_integration
    ADD CONSTRAINT pluggy_integration_user_id_uk UNIQUE (user_id, id);


--
-- Name: pluggy_integration pluggy_integration_user_provider_uk; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pluggy_integration
    ADD CONSTRAINT pluggy_integration_user_provider_uk UNIQUE (user_id, provider);


--
-- Name: spring_session_attributes spring_session_attributes_pk; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.spring_session_attributes
    ADD CONSTRAINT spring_session_attributes_pk PRIMARY KEY (session_primary_id, attribute_name);


--
-- Name: spring_session spring_session_pk; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.spring_session
    ADD CONSTRAINT spring_session_pk PRIMARY KEY (primary_id);


--
-- Name: sync_job sync_job_pk; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sync_job
    ADD CONSTRAINT sync_job_pk PRIMARY KEY (id);


--
-- Name: sync_run sync_run_pk; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sync_run
    ADD CONSTRAINT sync_run_pk PRIMARY KEY (id);


--
-- Name: transaction_correction transaction_correction_pk; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.transaction_correction
    ADD CONSTRAINT transaction_correction_pk PRIMARY KEY (id);


--
-- Name: transaction transaction_pk; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.transaction
    ADD CONSTRAINT transaction_pk PRIMARY KEY (id);


--
-- Name: transaction transaction_user_id_uk; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.transaction
    ADD CONSTRAINT transaction_user_id_uk UNIQUE (user_id, id);


--
-- Name: audit_event_type_time_ix; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX audit_event_type_time_ix ON public.audit_event USING btree (event_type, created_at);


--
-- Name: audit_event_user_time_ix; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX audit_event_user_time_ix ON public.audit_event USING btree (user_id, created_at);


--
-- Name: financial_account_external_uk; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX financial_account_external_uk ON public.financial_account USING btree (connection_id, external_account_id_hash) WHERE ((origin = 'IMPORTED'::text) AND (external_account_id_hash IS NOT NULL));


--
-- Name: financial_account_user_ix; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX financial_account_user_ix ON public.financial_account USING btree (user_id, type, status);


--
-- Name: financial_goal_user_created_ix; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX financial_goal_user_created_ix ON public.financial_goal USING btree (user_id, created_at DESC, id);


--
-- Name: goal_allocation_goal_ix; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX goal_allocation_goal_ix ON public.goal_allocation USING btree (user_id, goal_id, ordinal, id);


--
-- Name: goal_allocation_source_ix; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX goal_allocation_source_ix ON public.goal_allocation USING btree (user_id, source_type, source_id);


--
-- Name: goal_progress_snapshot_goal_date_ix; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX goal_progress_snapshot_goal_date_ix ON public.goal_progress_snapshot USING btree (user_id, goal_id, reference_date DESC, id);


--
-- Name: idempotency_key_expires_at_ix; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idempotency_key_expires_at_ix ON public.idempotency_key USING btree (expires_at);


--
-- Name: internal_match_credit_ix; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX internal_match_credit_ix ON public.internal_match USING btree (credit_transaction_id);


--
-- Name: internal_match_debit_ix; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX internal_match_debit_ix ON public.internal_match USING btree (debit_transaction_id);


--
-- Name: internal_match_pending_ix; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX internal_match_pending_ix ON public.internal_match USING btree (user_id, status, score DESC, created_at);


--
-- Name: investment_position_user_date_ix; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX investment_position_user_date_ix ON public.investment_position USING btree (user_id, reference_date DESC, id DESC);


--
-- Name: invoice_item_correction_ix; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX invoice_item_correction_ix ON public.invoice_item_correction USING btree (invoice_item_id, changed_at);


--
-- Name: invoice_item_invoice_ix; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX invoice_item_invoice_ix ON public.invoice_item USING btree (invoice_id, removed_at);


--
-- Name: invoice_item_transaction_installment_uk; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX invoice_item_transaction_installment_uk ON public.invoice_item USING btree (user_id, transaction_id, COALESCE(installment_number, 0)) WHERE ((transaction_id IS NOT NULL) AND (removed_at IS NULL));


--
-- Name: invoice_item_user_transaction_ix; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX invoice_item_user_transaction_ix ON public.invoice_item USING btree (user_id, transaction_id);


--
-- Name: invoice_payment_invoice_ix; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX invoice_payment_invoice_ix ON public.invoice_payment USING btree (invoice_id, paid_on);


--
-- Name: invoice_user_due_ix; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX invoice_user_due_ix ON public.invoice USING btree (user_id, due_date, id);


--
-- Name: login_attempt_attempted_at_ix; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX login_attempt_attempted_at_ix ON public.login_attempt USING btree (attempted_at);


--
-- Name: login_attempt_ip_time_ix; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX login_attempt_ip_time_ix ON public.login_attempt USING btree (ip_hash, attempted_at);


--
-- Name: login_attempt_username_time_ix; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX login_attempt_username_time_ix ON public.login_attempt USING btree (username_hash, attempted_at);


--
-- Name: manual_asset_liability_user_kind_date_ix; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX manual_asset_liability_user_kind_date_ix ON public.manual_asset_liability USING btree (user_id, kind, reference_date DESC, id DESC);


--
-- Name: pluggy_connection_user_ix; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX pluggy_connection_user_ix ON public.pluggy_connection USING btree (user_id, created_at);


--
-- Name: spring_session_ix1; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX spring_session_ix1 ON public.spring_session USING btree (session_id);


--
-- Name: spring_session_ix2; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX spring_session_ix2 ON public.spring_session USING btree (expiry_time);


--
-- Name: spring_session_ix3; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX spring_session_ix3 ON public.spring_session USING btree (principal_name);


--
-- Name: sync_job_active_uk; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX sync_job_active_uk ON public.sync_job USING btree (job_type, COALESCE(connection_id, integration_id, id)) WHERE (status = ANY (ARRAY['QUEUED'::text, 'RUNNING'::text, 'RETRY'::text]));


--
-- Name: sync_job_due_ix; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX sync_job_due_ix ON public.sync_job USING btree (status, run_after);


--
-- Name: sync_job_user_ix; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX sync_job_user_ix ON public.sync_job USING btree (user_id, created_at);


--
-- Name: sync_run_integration_ix; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX sync_run_integration_ix ON public.sync_run USING btree (integration_id, started_at DESC);


--
-- Name: sync_run_user_ix; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX sync_run_user_ix ON public.sync_run USING btree (user_id, started_at DESC, id);


--
-- Name: transaction_account_posted_ix; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX transaction_account_posted_ix ON public.transaction USING btree (user_id, account_id, posted_on DESC);


--
-- Name: transaction_category_posted_ix; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX transaction_category_posted_ix ON public.transaction USING btree (user_id, category_id, posted_on DESC);


--
-- Name: transaction_correction_ix; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX transaction_correction_ix ON public.transaction_correction USING btree (transaction_id, changed_at);


--
-- Name: transaction_dedup_uk; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX transaction_dedup_uk ON public.transaction USING btree (user_id, dedup_key) WHERE ((source = 'IMPORTED'::text) AND (dedup_key IS NOT NULL));


--
-- Name: transaction_type_posted_ix; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX transaction_type_posted_ix ON public.transaction USING btree (user_id, type, posted_on DESC);


--
-- Name: transaction_user_posted_ix; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX transaction_user_posted_ix ON public.transaction USING btree (user_id, posted_on DESC, id);


--
-- Name: audit_event audit_event_user_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.audit_event
    ADD CONSTRAINT audit_event_user_fk FOREIGN KEY (user_id) REFERENCES public.app_user(id) ON DELETE SET NULL;


--
-- Name: card_profile card_profile_account_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.card_profile
    ADD CONSTRAINT card_profile_account_fk FOREIGN KEY (user_id, account_id) REFERENCES public.financial_account(user_id, id) ON DELETE CASCADE;


--
-- Name: category category_user_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.category
    ADD CONSTRAINT category_user_fk FOREIGN KEY (user_id) REFERENCES public.app_user(id) ON DELETE CASCADE;


--
-- Name: external_identity_history external_identity_history_account_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.external_identity_history
    ADD CONSTRAINT external_identity_history_account_fk FOREIGN KEY (user_id, account_id) REFERENCES public.financial_account(user_id, id) ON DELETE CASCADE;


--
-- Name: external_identity_history external_identity_history_transaction_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.external_identity_history
    ADD CONSTRAINT external_identity_history_transaction_fk FOREIGN KEY (user_id, transaction_id) REFERENCES public.transaction(user_id, id) ON DELETE CASCADE;


--
-- Name: financial_account financial_account_connection_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.financial_account
    ADD CONSTRAINT financial_account_connection_fk FOREIGN KEY (user_id, connection_id) REFERENCES public.pluggy_connection(user_id, id) ON DELETE SET NULL;


--
-- Name: financial_account financial_account_user_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.financial_account
    ADD CONSTRAINT financial_account_user_fk FOREIGN KEY (user_id) REFERENCES public.app_user(id) ON DELETE CASCADE;


--
-- Name: financial_goal financial_goal_user_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.financial_goal
    ADD CONSTRAINT financial_goal_user_fk FOREIGN KEY (user_id) REFERENCES public.app_user(id) ON DELETE CASCADE;


--
-- Name: goal_allocation goal_allocation_goal_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.goal_allocation
    ADD CONSTRAINT goal_allocation_goal_fk FOREIGN KEY (user_id, goal_id) REFERENCES public.financial_goal(user_id, id) ON DELETE CASCADE;


--
-- Name: goal_progress_snapshot goal_progress_snapshot_goal_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.goal_progress_snapshot
    ADD CONSTRAINT goal_progress_snapshot_goal_fk FOREIGN KEY (user_id, goal_id) REFERENCES public.financial_goal(user_id, id) ON DELETE CASCADE;


--
-- Name: idempotency_key idempotency_key_user_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.idempotency_key
    ADD CONSTRAINT idempotency_key_user_fk FOREIGN KEY (user_id) REFERENCES public.app_user(id) ON DELETE CASCADE;


--
-- Name: internal_match internal_match_credit_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.internal_match
    ADD CONSTRAINT internal_match_credit_fk FOREIGN KEY (user_id, credit_transaction_id) REFERENCES public.transaction(user_id, id) ON DELETE CASCADE;


--
-- Name: internal_match internal_match_debit_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.internal_match
    ADD CONSTRAINT internal_match_debit_fk FOREIGN KEY (user_id, debit_transaction_id) REFERENCES public.transaction(user_id, id) ON DELETE CASCADE;


--
-- Name: internal_match internal_match_user_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.internal_match
    ADD CONSTRAINT internal_match_user_fk FOREIGN KEY (user_id) REFERENCES public.app_user(id) ON DELETE CASCADE;


--
-- Name: investment_position investment_position_user_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.investment_position
    ADD CONSTRAINT investment_position_user_fk FOREIGN KEY (user_id) REFERENCES public.app_user(id) ON DELETE CASCADE;


--
-- Name: invoice invoice_account_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.invoice
    ADD CONSTRAINT invoice_account_fk FOREIGN KEY (user_id, card_account_id) REFERENCES public.financial_account(user_id, id) ON DELETE CASCADE;


--
-- Name: invoice_item_correction invoice_item_correction_item_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.invoice_item_correction
    ADD CONSTRAINT invoice_item_correction_item_fk FOREIGN KEY (user_id, invoice_item_id) REFERENCES public.invoice_item(user_id, id) ON DELETE CASCADE;


--
-- Name: invoice_item_correction invoice_item_correction_user_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.invoice_item_correction
    ADD CONSTRAINT invoice_item_correction_user_fk FOREIGN KEY (user_id) REFERENCES public.app_user(id) ON DELETE CASCADE;


--
-- Name: invoice_item invoice_item_invoice_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.invoice_item
    ADD CONSTRAINT invoice_item_invoice_fk FOREIGN KEY (user_id, invoice_id) REFERENCES public.invoice(user_id, id) ON DELETE CASCADE;


--
-- Name: invoice_item invoice_item_transaction_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.invoice_item
    ADD CONSTRAINT invoice_item_transaction_fk FOREIGN KEY (user_id, transaction_id) REFERENCES public.transaction(user_id, id);


--
-- Name: invoice_payment invoice_payment_account_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.invoice_payment
    ADD CONSTRAINT invoice_payment_account_fk FOREIGN KEY (user_id, payment_account_id) REFERENCES public.financial_account(user_id, id);


--
-- Name: invoice_payment invoice_payment_invoice_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.invoice_payment
    ADD CONSTRAINT invoice_payment_invoice_fk FOREIGN KEY (user_id, invoice_id) REFERENCES public.invoice(user_id, id) ON DELETE CASCADE;


--
-- Name: invoice_payment invoice_payment_transaction_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.invoice_payment
    ADD CONSTRAINT invoice_payment_transaction_fk FOREIGN KEY (user_id, source_transaction_id) REFERENCES public.transaction(user_id, id);


--
-- Name: invoice invoice_user_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.invoice
    ADD CONSTRAINT invoice_user_fk FOREIGN KEY (user_id) REFERENCES public.app_user(id) ON DELETE CASCADE;


--
-- Name: manual_asset_liability manual_asset_liability_user_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.manual_asset_liability
    ADD CONSTRAINT manual_asset_liability_user_fk FOREIGN KEY (user_id) REFERENCES public.app_user(id) ON DELETE CASCADE;


--
-- Name: pluggy_connection pluggy_connection_integration_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pluggy_connection
    ADD CONSTRAINT pluggy_connection_integration_fk FOREIGN KEY (user_id, integration_id) REFERENCES public.pluggy_integration(user_id, id) ON DELETE CASCADE;


--
-- Name: pluggy_integration pluggy_integration_user_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pluggy_integration
    ADD CONSTRAINT pluggy_integration_user_fk FOREIGN KEY (user_id) REFERENCES public.app_user(id) ON DELETE CASCADE;


--
-- Name: spring_session_attributes spring_session_attributes_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.spring_session_attributes
    ADD CONSTRAINT spring_session_attributes_fk FOREIGN KEY (session_primary_id) REFERENCES public.spring_session(primary_id) ON DELETE CASCADE;


--
-- Name: sync_job sync_job_connection_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sync_job
    ADD CONSTRAINT sync_job_connection_fk FOREIGN KEY (user_id, connection_id) REFERENCES public.pluggy_connection(user_id, id) ON DELETE CASCADE;


--
-- Name: sync_job sync_job_integration_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sync_job
    ADD CONSTRAINT sync_job_integration_fk FOREIGN KEY (user_id, integration_id) REFERENCES public.pluggy_integration(user_id, id) ON DELETE CASCADE;


--
-- Name: sync_job sync_job_user_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sync_job
    ADD CONSTRAINT sync_job_user_fk FOREIGN KEY (user_id) REFERENCES public.app_user(id) ON DELETE CASCADE;


--
-- Name: sync_run sync_run_connection_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sync_run
    ADD CONSTRAINT sync_run_connection_fk FOREIGN KEY (user_id, connection_id) REFERENCES public.pluggy_connection(user_id, id) ON DELETE CASCADE;


--
-- Name: sync_run sync_run_integration_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sync_run
    ADD CONSTRAINT sync_run_integration_fk FOREIGN KEY (user_id, integration_id) REFERENCES public.pluggy_integration(user_id, id) ON DELETE CASCADE;


--
-- Name: sync_run sync_run_user_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sync_run
    ADD CONSTRAINT sync_run_user_fk FOREIGN KEY (user_id) REFERENCES public.app_user(id) ON DELETE CASCADE;


--
-- Name: transaction transaction_account_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.transaction
    ADD CONSTRAINT transaction_account_fk FOREIGN KEY (user_id, account_id) REFERENCES public.financial_account(user_id, id) ON DELETE CASCADE;


--
-- Name: transaction transaction_category_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.transaction
    ADD CONSTRAINT transaction_category_fk FOREIGN KEY (user_id, category_id) REFERENCES public.category(user_id, id) ON DELETE SET NULL;


--
-- Name: transaction_correction transaction_correction_transaction_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.transaction_correction
    ADD CONSTRAINT transaction_correction_transaction_fk FOREIGN KEY (user_id, transaction_id) REFERENCES public.transaction(user_id, id) ON DELETE CASCADE;


--
-- Name: transaction_correction transaction_correction_user_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.transaction_correction
    ADD CONSTRAINT transaction_correction_user_fk FOREIGN KEY (user_id) REFERENCES public.app_user(id) ON DELETE CASCADE;


--
-- Name: transaction transaction_related_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.transaction
    ADD CONSTRAINT transaction_related_fk FOREIGN KEY (user_id, related_transaction_id) REFERENCES public.transaction(user_id, id);


--
-- PostgreSQL database dump complete
--


