-- Saldos reservados entram no snapshot patrimonial como ativo separado.
alter table wealth_snapshot_item drop constraint wealth_snapshot_item_source_ck;
alter table wealth_snapshot_item add constraint wealth_snapshot_item_source_ck check (
    source_type in ('ACCOUNT', 'RESERVED_BALANCE', 'CARD', 'INVESTMENT', 'MANUAL_ASSET', 'MANUAL_LIABILITY')
);
