-- Tratamento padrão dos saldos reservados por conexão.
--
-- A conta pode sobrescrever o padrão; UNKNOWN na conta significa "herdar da conexão".
alter table pluggy_connection
  add column reserved_balance_treatment varchar(32) not null default 'UNKNOWN';
