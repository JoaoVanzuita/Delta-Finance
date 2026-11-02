-- Saldos reservados dentro de contas bancárias (cofrinhos, caixinhas, bloqueios etc.).
--
-- Reserva não é conta e não é investimento: é dinheiro earmarked dentro da conta.
-- O nome do provedor é preservado; o nome local fica em display_name_enc.
alter table financial_account
  add column has_reserved_balance boolean not null default false,
  add column reserved_balance_checked_at timestamptz,
  add column reserved_balance_treatment varchar(32) not null default 'UNKNOWN';

create table reserved_balance (
  id uuid primary key,
  user_id uuid not null,
  account_id uuid not null,
  connection_id uuid not null,
  provider_identification_enc bytea,
  provider_identification_hash bytea not null,
  provider_name_enc bytea,
  display_name_enc bytea,
  currency varchar(3) not null,
  amount_enc bytea not null,
  details_enc bytea,
  reference_date date,
  last_seen_at timestamptz not null,
  removed_at timestamptz,
  created_at timestamptz not null,
  updated_at timestamptz not null,
  version bigint not null default 0,
  constraint reserved_balance_user_uk unique (user_id, account_id, provider_identification_hash),
  constraint reserved_balance_account_fk foreign key (user_id, account_id)
    references financial_account (user_id, id) on delete cascade,
  constraint reserved_balance_connection_fk foreign key (user_id, connection_id)
    references pluggy_connection (user_id, id) on delete cascade
);

create index reserved_balance_account_idx on reserved_balance (user_id, account_id);
create index reserved_balance_seen_idx on reserved_balance (user_id, account_id, removed_at);
