-- C09: distingue a suspensão automática do espelho de fatura de uma remoção manual.
--
-- Quando a transação deixa de ser efetiva (PENDING ou remoção na origem ainda não
-- confirmada), o item espelho é logicamente removido com mirror_suspended = true.
-- Isso o exclui dos totais (as consultas já filtram removed_at), mas permite
-- reativá-lo com os mesmos dados se a transação voltar a ser efetiva.
ALTER TABLE invoice_item
  ADD COLUMN mirror_suspended boolean NOT NULL DEFAULT false;

COMMENT ON COLUMN invoice_item.mirror_suspended IS
  'Item suspenso por perda de efetividade da transação; pode ser reativado.';
