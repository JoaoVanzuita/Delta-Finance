-- Posições manuais de investimento foram removidas: o módulo usa apenas posições importadas.
-- Proventos manuais passam a apontar exclusivamente para uma posição do provedor.
delete from investment_income where investment_position_id is not null;

alter table investment_income
    drop constraint investment_income_position_fk;

alter table investment_income
    drop constraint investment_income_target_ck;

alter table investment_income
    drop column investment_position_id;

alter table investment_income
    add constraint investment_income_target_ck check (provider_investment_id is not null);

drop table investment_position;

comment on table investment_income is
    'Provento manual vinculado a uma posição importada; origem sempre MANUAL.';
