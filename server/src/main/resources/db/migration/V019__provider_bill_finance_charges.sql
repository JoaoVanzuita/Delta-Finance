-- Encargos financeiros da Bill entram na verificação de quitação da Bill anterior:
-- totalAmount(Bill N) + sum(financeCharges(Bill N+1)) = sum(payments(Bill N+1)).
alter table provider_bill
    add column finance_charges_total_enc bytea;

comment on column provider_bill.finance_charges_total_enc is
    'Soma dos encargos financeiros da Bill; usada na quitação da Bill anterior.';
