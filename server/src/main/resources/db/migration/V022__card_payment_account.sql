-- Conta padrão para pagar as faturas do cartão.
--
-- Pré-seleciona a conta no pagamento de fatura e serve como sinal de menor
-- precedência na classificação de pagamentos importados (nunca decide sozinho).
-- A posse e o tipo são validados no serviço; não há FK para permitir a remoção
-- independente da conta.
alter table card_profile add column payment_account_id uuid;

comment on column card_profile.payment_account_id is
    'Conta bancária padrão para pagar as faturas deste cartão; validada no serviço (mesmo usuário, ativa, não-cartão, mesma moeda).';
