-- Fase 7: snapshots mensais de patrimônio e investimentos.
-- Snapshots são imutáveis e idempotentes por (user_id, reference_date).
-- Valores, descrições e totais são cifrados; coverage e motivos não contêm valores.
create table wealth_snapshot (
    id uuid not null,
    user_id uuid not null,
    reference_date date not null,
    captured_at timestamp with time zone not null,
    capture_trigger text not null,
    completeness text not null,
    gross_assets_enc bytea not null,
    liabilities_enc bytea not null,
    net_worth_enc bytea not null,
    coverage jsonb not null default '{}'::jsonb,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    version bigint default 0 not null,
    constraint wealth_snapshot_pk primary key (id),
    constraint wealth_snapshot_user_id_uk unique (user_id, id),
    constraint wealth_snapshot_reference_uk unique (user_id, reference_date),
    constraint wealth_snapshot_user_fk foreign key (user_id)
        references app_user (id) on delete cascade,
    constraint wealth_snapshot_trigger_ck check (capture_trigger in ('SCHEDULED', 'MANUAL')),
    constraint wealth_snapshot_completeness_ck
        check (completeness in ('COMPLETE', 'PARTIAL', 'ESTIMATED'))
);

create index wealth_snapshot_user_date_idx
    on wealth_snapshot (user_id, reference_date desc);

comment on table wealth_snapshot is
    'Snapshot mensal imutável da posição patrimonial; não é cotação nem rentabilidade.';
comment on column wealth_snapshot.reference_date is
    'Mês de referência do snapshot; único por usuário.';
comment on column wealth_snapshot.captured_at is
    'Momento real da captura, distinto da data de referência.';
comment on column wealth_snapshot.coverage is
    'Contagens e flags de cobertura; nunca contém valores financeiros.';
comment on column wealth_snapshot.gross_assets_enc is
    'Ativos consolidados em BRL, cifrados.';
comment on column wealth_snapshot.liabilities_enc is
    'Passivos consolidados em BRL, cifrados.';
comment on column wealth_snapshot.net_worth_enc is
    'Ativos menos passivos em BRL, cifrado.';

create table wealth_snapshot_item (
    id uuid not null,
    snapshot_id uuid not null,
    user_id uuid not null,
    source_type text not null,
    source_id uuid,
    asset_class text,
    description_enc bytea not null,
    value_enc bytea not null,
    currency varchar(3) not null,
    included boolean not null,
    exclusion_reason text,
    source_reference_date date,
    stale boolean not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    version bigint default 0 not null,
    constraint wealth_snapshot_item_pk primary key (id),
    constraint wealth_snapshot_item_user_id_uk unique (user_id, id),
    constraint wealth_snapshot_item_snapshot_fk
        foreign key (user_id, snapshot_id) references wealth_snapshot (user_id, id)
        on delete cascade,
    constraint wealth_snapshot_item_user_fk foreign key (user_id)
        references app_user (id) on delete cascade,
    constraint wealth_snapshot_item_source_ck check (
        source_type in ('ACCOUNT', 'CARD', 'INVESTMENT', 'MANUAL_ASSET', 'MANUAL_LIABILITY')
    ),
    constraint wealth_snapshot_item_currency_ck check (length(currency) = 3)
);

create index wealth_snapshot_item_snapshot_idx
    on wealth_snapshot_item (snapshot_id);

create index wealth_snapshot_item_user_source_idx
    on wealth_snapshot_item (user_id, source_type);

create index wealth_snapshot_item_snapshot_class_idx
    on wealth_snapshot_item (snapshot_id, asset_class);

comment on table wealth_snapshot_item is
    'Composição de um snapshot; preserva fonte, moeda, inclusão e motivo de exclusão.';
comment on column wealth_snapshot_item.value_enc is
    'Valor na moeda original, cifrado; não é normalizado para BRL.';
comment on column wealth_snapshot_item.asset_class is
    'Classe de investimento quando source_type = INVESTMENT.';
comment on column wealth_snapshot_item.exclusion_reason is
    'Motivo não financeiro para o item não compor o consolidado.';
