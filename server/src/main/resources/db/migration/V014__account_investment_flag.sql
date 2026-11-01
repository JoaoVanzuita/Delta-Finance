-- Conta usada como fronteira de caixa do perímetro de investimento.
--
-- Aportes e resgates são fluxos que entram ou saem desta conta. Compras e vendas
-- de ativos acontecem dentro dela e não são aportes. A marcação é explícita para
-- não depender do nome da conta nem do tipo devolvido pelo provedor.
alter table financial_account add column investment_account boolean not null default false;
