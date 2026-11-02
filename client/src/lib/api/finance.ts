/**
 * Contratos HTTP por domínio.
 *
 * Cada consumidor importa o cliente do seu domínio (`accountsApi`, `cardsApi`, ...). Não há
 * mais um objeto `financeApi` global pertencente à feature de integração.
 */
export * from './domains/accounts'
export * from './domains/cards'
export * from './domains/categories'
export * from './domains/goals'
export * from './domains/integrations'
export * from './domains/investments'
export * from './domains/reconciliation'
export * from './domains/transactions'
export * from './domains/wealth'
