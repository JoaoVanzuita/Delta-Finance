-- V015: permite auditar o vínculo manual de reembolso/chargeback à transação original.
ALTER TABLE transaction_correction DROP CONSTRAINT transaction_correction_field_ck;

ALTER TABLE transaction_correction ADD CONSTRAINT transaction_correction_field_ck CHECK (
  field_name IN ('description', 'postedOn', 'amount', 'category', 'notes', 'relatedTransactionId')
);
