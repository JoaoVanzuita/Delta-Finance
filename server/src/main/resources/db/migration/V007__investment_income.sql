-- Proventos informados manualmente pelo usuário.
-- Complementam os proventos INTEREST do provedor quando o conector não os envia.
-- Cada provento aponta para uma posição importada ou manual, nunca para as duas.
create table investment_income (
    id uuid not null,
    user_id uuid not null,
    provider_investment_id uuid,
    investment_position_id uuid,
    income_type text not null,
    income_date date not null,
    amount_enc bytea not null,
    description_enc bytea,
    source text not null default 'MANUAL',
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    version bigint default 0 not null,
    constraint investment_income_pk primary key (id),
    constraint investment_income_user_id_uk unique (user_id, id),
    constraint investment_income_target_ck check (
        (provider_investment_id is not null and investment_position_id is null)
        or (provider_investment_id is null and investment_position_id is not null)
    ),
    constraint investment_income_type_ck check (
        income_type = any (array['DIVIDEND'::text, 'INTEREST'::text, 'JCP'::text,
            'RENT'::text, 'COUPON'::text, 'OTHER'::text])
    ),
    constraint investment_income_source_ck check (source = 'MANUAL'::text),
    constraint investment_income_provider_fk foreign key (user_id, provider_investment_id)
        references provider_investment (user_id, id) on delete cascade,
    constraint investment_income_position_fk foreign key (user_id, investment_position_id)
        references investment_position (user_id, id) on delete cascade
);

create index investment_income_date_idx
    on investment_income (user_id, income_date desc, created_at desc);

comment on table investment_income is
    'Provento manual; complementa INTEREST do provedor e é identificado como origem MANUAL.';
comment on column investment_income.amount_enc is
    'Valor do provento em BRL, cifrado.';
comment on column investment_income.description_enc is
    'Descrição livre do provento, cifrada.';
