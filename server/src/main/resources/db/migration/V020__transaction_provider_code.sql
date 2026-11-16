-- Ancora de identidade alternativa para transações recriadas pelo provedor.
--
-- O Pluggy pode remover e recriar uma transação (novo id externo) quando data,
-- descrição ou valor mudam além do tolerado. O providerId (Open Finance) e o
-- providerCode permitem reconhecer a recriação e atualizar o registro local
-- em vez de criar uma segunda linha, preservando correções do usuário e
-- vínculos de reembolso/movimento interno.
alter table transaction add column provider_code varchar(128);

comment on column transaction.provider_code is
    'Código da transação na instituição de origem, quando o conector informa; âncora alternativa ao providerId na reconciliação de identidade.';
