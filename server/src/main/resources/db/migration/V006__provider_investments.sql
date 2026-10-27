-- Posições de investimento compartilhadas por um item do provedor.
-- Investimento não é conta: o vínculo é com a conexão/item, não com financial_account.
-- Valores, quantidades e identificação do ativo são cifrados; tipo/status/moeda ficam em claro
-- para consulta. Ausência na origem marca indisponibilidade e nunca apaga o histórico.
create table provider_investment (
    id uuid not null,
    user_id uuid not null,
    connection_id uuid not null,
    external_investment_id_enc bytea not null,
    external_investment_id_hash bytea not null,
    name_enc bytea,
    code_enc bytea,
    isin_enc bytea,
    owner_enc bytea,
    institution_enc bytea,
    issuer_enc bytea,
    type text not null,
    subtype text,
    status text not null,
    currency text not null,
    quantity_enc bytea,
    unit_value_enc bytea,
    amount_enc bytea,
    balance_enc bytea,
    amount_original_enc bytea,
    amount_profit_enc bytea,
    amount_withdrawal_enc bytea,
    taxes_enc bytea,
    taxes2_enc bytea,
    rate_enc bytea,
    rate_type text,
    fixed_annual_rate_enc bytea,
    last_month_rate_enc bytea,
    annual_rate_enc bytea,
    last_twelve_months_rate_enc bytea,
    reference_date date,
    purchase_date date,
    due_date date,
    grace_period_date date,
    provider_updated_at timestamp with time zone,
    synced_at timestamp with time zone not null,
    unavailable_at timestamp with time zone,
    manual_cost_basis_enc bytea,
    manual_cost_basis_updated_at timestamp with time zone,
    user_asset_class text,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    version bigint default 0 not null,
    constraint provider_investment_pk primary key (id),
    constraint provider_investment_user_id_uk unique (user_id, id),
    constraint provider_investment_external_uk
        unique (user_id, connection_id, external_investment_id_hash),
    constraint provider_investment_connection_fk foreign key (user_id, connection_id)
        references pluggy_connection (user_id, id) on delete cascade,
    constraint provider_investment_currency_ck check (length(currency) = 3)
);

create index provider_investment_status_idx
    on provider_investment (user_id, status, type);

create index provider_investment_connection_idx
    on provider_investment (user_id, connection_id);

comment on table provider_investment is
    'Posição importada do provedor; balance é o valor atual e não é conta bancária.';

comment on column provider_investment.external_investment_id_hash is
    'HMAC do ID externo; unicidade por usuário e conexão.';
comment on column provider_investment.balance_enc is
    'Valor líquido atual informado pelo provedor, cifrado.';
comment on column provider_investment.unavailable_at is
    'Posição ausente em leitura completa; preserva histórico e sai dos totais atuais.';
comment on column provider_investment.manual_cost_basis_enc is
    'Custo informado pelo usuário, separado do valor do provedor.';
comment on column provider_investment.user_asset_class is
    'Classificação informada pelo usuário para sobrepor type/subtype do provedor.';

-- Movimentações de investimento: aplicações, resgates, impostos, transferências,
-- proventos e amortizações. Não viram transações bancárias nem alteram o fluxo de caixa.
create table provider_investment_transaction (
    id uuid not null,
    user_id uuid not null,
    provider_investment_id uuid not null,
    external_transaction_id_enc bytea,
    external_transaction_id_hash bytea,
    dedup_key bytea not null,
    type text not null,
    movement_type text,
    trade_date date,
    transaction_date date,
    quantity_enc bytea,
    unit_value_enc bytea,
    amount_enc bytea,
    net_amount_enc bytea,
    description_enc bytea,
    brokerage_number_enc bytea,
    expenses_enc bytea,
    last_seen_at timestamp with time zone not null,
    removed_at timestamp with time zone,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    version bigint default 0 not null,
    constraint provider_investment_transaction_pk primary key (id),
    constraint provider_investment_transaction_user_id_uk unique (user_id, id),
    constraint provider_investment_transaction_dedup_uk
        unique (user_id, provider_investment_id, dedup_key),
    constraint provider_investment_transaction_investment_fk
        foreign key (user_id, provider_investment_id)
        references provider_investment (user_id, id) on delete cascade
);

create index provider_investment_transaction_date_idx
    on provider_investment_transaction
        (user_id, provider_investment_id, transaction_date desc, created_at desc);

comment on table provider_investment_transaction is
    'Movimentação de investimento do provedor; INTEREST representa dividendos e proventos.';

comment on column provider_investment_transaction.dedup_key is
    'HMAC do ID externo ou fingerprint dos campos quando o ID não é informado.';
comment on column provider_investment_transaction.removed_at is
    'Movimentação ausente em leitura completa; excluída dos agregados, preservada no histórico.';

-- Diagnóstico de cobertura por conexão. products não prova compartilhamento:
-- a fonte é /items/{id}/resources, com resourcesCollectedAt e pendingAuthorization.
create table provider_investment_sync (
    id uuid not null,
    user_id uuid not null,
    connection_id uuid not null,
    scope text not null,
    resource_count integer default 0 not null,
    available_resource_count integer default 0 not null,
    pending_resource_count integer default 0 not null,
    unavailable_resource_count integer default 0 not null,
    resources_collected_at timestamp with time zone,
    last_synced_at timestamp with time zone not null,
    error_code text,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    version bigint default 0 not null,
    constraint provider_investment_sync_pk primary key (id),
    constraint provider_investment_sync_user_id_uk unique (user_id, id),
    constraint provider_investment_sync_connection_uk unique (user_id, connection_id),
    constraint provider_investment_sync_connection_fk foreign key (user_id, connection_id)
        references pluggy_connection (user_id, id) on delete cascade,
    constraint provider_investment_sync_scope_ck check
        (scope = any (array['AVAILABLE'::text, 'NOT_SHARED'::text,
            'PENDING_AUTHORIZATION'::text, 'UNKNOWN'::text, 'ERROR'::text])),
    constraint provider_investment_sync_counts_ck check
        (resource_count >= 0 and available_resource_count >= 0
            and pending_resource_count >= 0 and unavailable_resource_count >= 0)
);

comment on table provider_investment_sync is
    'Cobertura de investimentos por conexão; distingue não compartilhado de falha de leitura.';
