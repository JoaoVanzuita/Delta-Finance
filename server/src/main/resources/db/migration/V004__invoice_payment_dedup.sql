-- Deduplicação de pagamentos de fatura importados.
-- A chave é HMAC de cartão + valor + data + descrição normalizada. Pagamentos manuais
-- permanecem sem chave (dedup_key nulo).
alter table invoice_payment add column dedup_key bytea;

create unique index invoice_payment_dedup_uk
    on invoice_payment (user_id, dedup_key)
    where dedup_key is not null;

comment on column invoice_payment.dedup_key is
    'HMAC do pagamento importado; impede duplicidade quando a origem alterna PENDING/POSTED.';
