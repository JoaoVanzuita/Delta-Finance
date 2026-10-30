-- Nomes personalizados pelo usuário, separados dos nomes recebidos do Pluggy.
-- Não há backfill: contas e conexões existentes continuam exibindo o nome do
-- provedor até o usuário personalizar. Copiar o ciphertext de alias_enc ou
-- institution_name_enc seria inválido porque o nome da coluna faz parte do AAD.

alter table financial_account
    add column display_name_enc bytea;

alter table pluggy_connection
    add column display_name_enc bytea;

comment on column financial_account.display_name_enc is
    'Nome personalizado pelo usuário; não substitui o nome importado do provedor.';

comment on column pluggy_connection.display_name_enc is
    'Nome personalizado do item; não substitui o nome da instituição do provedor.';
