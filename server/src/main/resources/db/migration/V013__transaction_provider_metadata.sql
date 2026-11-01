-- Metadados do provedor usados para alocação determinística de faturas.
--
-- operationType identifica pagamentos sintéticos (ex.: PAGAMENTO_FATURA);
-- providerId/billId/billClosingDate permitem ancorar a transação na Bill oficial
-- sem depender de heurísticas de data/valor.
alter table transaction add column operation_type varchar(32);
alter table transaction add column provider_id varchar(128);
alter table transaction add column provider_bill_id varchar(128);
alter table transaction add column provider_bill_closing_date date;
