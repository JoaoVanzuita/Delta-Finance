-- Bills oficiais do provedor, mantidas como referência de conciliação.
-- A fatura local continua sendo a fonte de verdade; estes dados apenas
-- permitem comparar total, pagamento mínimo, vencimento e pagamentos oficiais.
create table provider_bill (
    id uuid not null,
    user_id uuid not null,
    card_account_id uuid not null,
    external_bill_id_hash bytea not null,
    closing_date date,
    due_date date not null,
    total_enc bytea not null,
    minimum_payment_enc bytea,
    payments_total_enc bytea not null,
    currency text not null,
    synced_at timestamp with time zone not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    version bigint default 0 not null,
    constraint provider_bill_pk primary key (id),
    constraint provider_bill_user_id_uk unique (user_id, id),
    constraint provider_bill_external_uk unique (user_id, card_account_id, external_bill_id_hash),
    constraint provider_bill_card_fk foreign key (user_id, card_account_id)
        references financial_account (user_id, id) on delete cascade
);

create index provider_bill_due_idx
    on provider_bill (user_id, card_account_id, due_date desc);

comment on table provider_bill is
    'Bill oficial do provedor; referência de conciliação, não altera a fatura local.';

create table provider_bill_payment (
    id uuid not null,
    user_id uuid not null,
    provider_bill_id uuid not null,
    external_payment_id_hash bytea not null,
    payment_date date not null,
    amount_enc bytea not null,
    payment_mode text,
    value_type text,
    currency text not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    version bigint default 0 not null,
    constraint provider_bill_payment_pk primary key (id),
    constraint provider_bill_payment_user_id_uk unique (user_id, id),
    constraint provider_bill_payment_external_uk
        unique (user_id, provider_bill_id, external_payment_id_hash),
    constraint provider_bill_payment_bill_fk foreign key (user_id, provider_bill_id)
        references provider_bill (user_id, id) on delete cascade
);

comment on table provider_bill_payment is
    'Pagamento oficial informado na Bill; usado apenas para conciliação.';
