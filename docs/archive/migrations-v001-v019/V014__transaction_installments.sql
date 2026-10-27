-- V014: metadados de parcelamento por transação e múltiplos itens por compra parcelada.
--
-- A origem pode informar installmentNumber/totalInstallments por transação. Uma compra manual
-- parcelada gera um item por parcela, todos ligados à mesma transação; o índice antigo impedia
-- mais de um item ativo por transação.

ALTER TABLE transaction ADD COLUMN installment_number integer;
ALTER TABLE transaction ADD COLUMN total_installments integer;

ALTER TABLE transaction ADD CONSTRAINT transaction_installment_ck CHECK (
  (installment_number IS NULL AND total_installments IS NULL)
  OR (installment_number >= 1 AND total_installments >= 1
      AND installment_number <= total_installments)
);

DROP INDEX invoice_item_transaction_uk;

CREATE UNIQUE INDEX invoice_item_transaction_installment_uk
  ON invoice_item (user_id, transaction_id, coalesce(installment_number, 0))
  WHERE transaction_id IS NOT NULL AND removed_at IS NULL;

COMMENT ON COLUMN transaction.installment_number IS
  'Número da parcela informado pela origem; nulo quando não parcelada.';
COMMENT ON COLUMN transaction.total_installments IS
  'Total de parcelas informado pela origem; nulo quando não parcelada.';
