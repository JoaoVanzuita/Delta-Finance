-- Moeda original de transações internacionais.
--
-- O valor efetivo passa a ser gravado na moeda da conta (BRL), enquanto
-- amount_source preserva o valor original. Sem a moeda de origem, não é
-- possível distinguir "USD 3,00" de "BRL 3,00" ao auditar a conversão.
alter table transaction add column source_currency varchar(3);
