-- Inclusão explícita da conta no saldo consolidado.
--
-- Antes a regra era implícita (tipo BANK/CASH/WALLET, ativa, BRL). O usuário
-- passa a poder excluir uma conta do consolidado sem perder histórico,
-- relatórios por conta nem filtros. O backfill reproduz exatamente a regra
-- anterior para que nenhum total mude na migração.
alter table financial_account
    add column include_in_total boolean not null default true;

update financial_account
set include_in_total = false
where not (
    type in ('BANK', 'CASH', 'WALLET')
    and status = 'ACTIVE'
    and currency = 'BRL'
);

comment on column financial_account.include_in_total is
    'Quando falso, a conta não soma no saldo consolidado (dashboard, patrimônio e resumo de contas); relatórios por conta e filtros não são afetados.';
