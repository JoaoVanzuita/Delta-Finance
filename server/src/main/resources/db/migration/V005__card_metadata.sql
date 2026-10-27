-- Metadados do cartão informados pelo provedor.
-- Bandeira e nível são códigos públicos; final e limite disponível são cifrados.
alter table card_profile add column brand text;
alter table card_profile add column level text;
alter table card_profile add column last4_enc bytea;
alter table card_profile add column available_credit_limit_enc bytea;

comment on column card_profile.brand is 'Bandeira do cartão (ex.: VISA, MASTERCARD).';
comment on column card_profile.level is 'Nível do cartão (ex.: PLATINUM, GOLD).';
comment on column card_profile.last4_enc is 'Últimos quatro dígitos do cartão, cifrados.';
comment on column card_profile.available_credit_limit_enc is
    'Limite disponível oficial do provedor, cifrado.';
