-- Mês da fatura previsto pelo provedor (creditCardMetadata.billForecastDate).
-- Compras usam o próprio mês; pagamentos usam o mês anterior, pois o Pluggy
-- informa o ciclo em que o pagamento foi lançado, não a fatura quitada.
alter table transaction add column provider_bill_month date;
