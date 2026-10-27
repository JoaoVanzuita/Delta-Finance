-- Conta com tipo desconhecido (C06).
--
-- O importador não deve converter um tipo não reconhecido em BANK: isso
-- inventaria comportamento e poderia incluir a conta no saldo consolidado.
ALTER TABLE financial_account DROP CONSTRAINT financial_account_type_ck;
ALTER TABLE financial_account ADD CONSTRAINT financial_account_type_ck
  CHECK (type IN ('BANK', 'CREDIT', 'CASH', 'WALLET', 'UNKNOWN'));
