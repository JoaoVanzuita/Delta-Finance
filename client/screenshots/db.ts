/**
 * Banco de dados de teste (in-memory) do roteiro de screenshots.
 *
 * Diferente dos specs de e2e (que montam stubs por tela), este módulo centraliza
 * um único "banco" com dados de teste coerentes entre si, cobrindo todas as
 * entidades consumidas pelas telas do Delta Finance. O handler de API
 * (`mockApi.ts`) serve este banco via `page.route`.
 *
 * Os formatos seguem o contrato OpenAPI (`specs/001-finance/contracts/openapi.yaml`
 * e `src/lib/api/schema.d.ts`). Datas fixadas em outubro/2026 para determinismo.
 */

export interface User {
  id: string
  username: string
  createdAt: string
}

export interface Account {
  id: string
  type: 'BANK' | 'CREDIT' | 'CASH' | 'WALLET' | 'UNKNOWN'
  subtype?: string
  origin: 'IMPORTED' | 'MANUAL'
  institutionName?: string
  alias?: string
  providerName?: string | null
  nameCustomized?: boolean
  currency: string
  currentBalance: string
  referenceBalance?: string
  balanceReferenceAt?: string
  status?: string
  lastSyncAt?: string
  notes?: string
  cardProfile?: CardProfile
  connectionStatus?: string | null
}

export interface CardProfile {
  accountId: string
  closingDay: number
  dueDay: number
  creditLimit?: string
  creditBalance: string
  brand?: string
  level?: string
  last4?: string
  availableCreditLimit?: string
}

export interface Category {
  id: string
  name: string
  kind: 'INCOME' | 'EXPENSE' | 'TRANSFER' | 'OTHER'
  predefined?: boolean
  active: boolean
}

export interface Transaction {
  id: string
  accountId: string
  categoryId?: string
  categoryName?: string
  source: 'IMPORTED' | 'MANUAL'
  type: 'DEBIT' | 'CREDIT' | 'TRANSFER' | 'REFUND' | 'CHARGEBACK' | 'ADJUSTMENT'
  status: 'PENDING' | 'POSTED' | 'SOURCE_REMOVED' | 'RECONCILED'
  postedOn: string
  amount: string
  currency: string
  description: string
  notes?: string
  editedFields?: string[]
  reconciliationState?: string
  invoiceId?: string
  relatedTransactionId?: string
  adjustmentReason?: string
  counterpartTransactionId?: string
}

export interface Invoice {
  id: string
  cardAccountId: string
  cycleStart: string
  cycleEnd: string
  closingDate: string
  dueDate: string
  status: 'OPEN' | 'CLOSED' | 'PARTIALLY_PAID' | 'PAID' | 'OVERDUE'
  total: string
  paymentsTotal: string
  outstanding: string
  appliedCredit: string
  minimumPayment?: string
  source: 'LOCAL'
  hasAdjustments: boolean
  adjustmentCount: number
  providerBill?: ProviderBill
  missingPurchases: boolean
}

export interface ProviderBill {
  closingDate?: string
  dueDate: string
  total: string
  minimumPayment?: string
  paymentsTotal: string
  totalDifference: string
  paymentsDifference: string
  reconciled: boolean
  ignoredPayments: number
  syncedOn: string
  payments: ProviderBillPayment[]
}

export interface ProviderBillPayment {
  paidOn: string
  amount: string
  paymentMode?: string
  valueType?: string
}

export interface InvoiceItem {
  id: string
  invoiceId: string
  transactionId?: string
  kind:
    | 'PURCHASE'
    | 'INSTALLMENT'
    | 'INTEREST'
    | 'FEE'
    | 'TAX'
    | 'CREDIT'
    | 'REFUND'
    | 'MANUAL_ADJUSTMENT'
  reportingEffect: 'EXPENSE_INCREASE' | 'EXPENSE_DECREASE' | 'NONE'
  source: 'IMPORTED' | 'MANUAL'
  description: string
  amount: string
  occurredOn: string
  installmentNumber?: number
  totalInstallments?: number
  correctionCount?: number
}

export interface InvoicePayment {
  id: string
  invoiceId: string
  paymentAccountId: string
  amount: string
  paidOn: string
  status: 'PENDING' | 'CONFIRMED' | 'REVERSED'
  source: 'MANUAL' | 'IMPORTED'
}

export interface InvoiceDetail extends Invoice {
  items: InvoiceItem[]
  payments: InvoicePayment[]
}

export interface InternalMatch {
  id: string
  kind: 'TRANSFER' | 'CARD_PAYMENT'
  debitTransactionId: string
  creditTransactionId: string
  score: number
  status: 'PENDING' | 'CONFIRMED' | 'REJECTED'
  reason?: string
  decidedAt?: string
  criteriaVersion: string
}

export interface CardOverviewItem {
  accountId: string
  alias: string | null
  institutionName: string | null
  status: string
  currency: string
  hasCycle: boolean
  closingDay: number | null
  dueDay: number | null
  creditLimit: string | null
  creditBalance: string | null
  availableCreditLimit?: string | null
  brand?: string | null
  level?: string | null
  last4?: string | null
  usedAmount: string
  outstanding: string
  utilization: string | null
  nextDueDate: string | null
  currentInvoice: Invoice | null
  nextDueInvoice: Invoice | null
  unallocatedCount: number
}

export interface Connection {
  id: string
  itemIdMasked?: string
  institutionName?: string
  displayName?: string | null
  nameCustomized?: boolean
  status: string
  consentExpiresAt?: string
  lastSyncAt?: string
  lastErrorCode?: string
  accountCount?: number
}

export interface InvestmentHolding {
  id: string
  connectionId: string
  name?: string | null
  code?: string | null
  isin?: string | null
  type: string
  subtype?: string | null
  assetClass: string
  classificationSource: string
  status: string
  currency: string
  quantity?: string | null
  unitValue?: string | null
  amount?: string | null
  balance?: string | null
  amountOriginal?: string | null
  amountProfit?: string | null
  amountWithdrawal?: string | null
  referenceDate?: string | null
  providerUpdatedAt?: string | null
  syncedAt?: string | null
  unavailableAt?: string | null
  manualCostBasis?: string | null
  costBasis?: string | null
  costBasisSource?: string
  incomeTotal?: string
  estimatedProfit?: string | null
  estimatedReturnPercentage?: string | null
  userAssetClass?: string | null
  profitSource: string
  stale: boolean
}

export interface InvestmentIncomeItem {
  id?: string | null
  date: string
  investmentId: string
  investmentName?: string | null
  code?: string | null
  type?: string | null
  incomeType: string
  amount: string
  description?: string | null
  source: 'PROVIDER' | 'MANUAL'
  editable: boolean
}

export interface WealthManualItem {
  id: string
  kind: 'ASSET' | 'LIABILITY'
  type: string
  description: string
  referenceValue: string
  referenceDate: string
  notes?: string
}

export interface WealthItem {
  sourceType: 'ACCOUNT' | 'CARD' | 'INVESTMENT' | 'MANUAL_ASSET' | 'MANUAL_LIABILITY'
  sourceId: string
  description: string
  value: string
  included: boolean
  exclusionReason?: string
  origin: 'PROVIDER' | 'MANUAL' | 'IMPORTED'
  referenceDate?: string
  stale: boolean
}

export interface GoalAllocation {
  id: string
  sourceType: string
  sourceId?: string
  sourceName?: string
  allocationMode: 'FIXED_AMOUNT' | 'PERCENTAGE'
  allocatedAmount?: string
  percentage?: string
  contributionAmount: string
  capacityExceeded?: boolean
}

export interface Goal {
  id: string
  name: string
  description?: string
  notes?: string
  type: string
  targetAmount: string
  targetDate?: string
  status: string
  progressRule: string
  changedAt?: string
  createdAt?: string
  currentValue: string
  percentage: string
  remainingAmount: string
  unallocatedAmount: string
  excessAmount: string
  allocations: GoalAllocation[]
  progressHistory: GoalProgressEntry[]
}

export interface GoalProgressEntry {
  referenceDate: string
  currentValue: string
  percentage: string
  remainingAmount: string
  ruleVersion: string
}

export interface SyncRun {
  id: string
  integrationId?: string
  connectionId?: string
  status: string
  startedAt: string
  finishedAt?: string
  readCount?: number
  createdCount?: number
  updatedCount?: number
  skippedCount?: number
  failedCount?: number
  errorCode?: string
}

/** Banco de dados de teste completo. */
export interface Db {
  user: User
  accounts: Account[]
  categories: Category[]
  transactions: Transaction[]
  internalMatches: InternalMatch[]
  invoices: InvoiceDetail[]
  cardOverviewItems: CardOverviewItem[]
  integration: Record<string, unknown>
  connections: Connection[]
  syncRuns: SyncRun[]
  investmentOverview: Record<string, unknown>
  holdings: InvestmentHolding[]
  incomeItems: InvestmentIncomeItem[]
  wealthItems: WealthItem[]
  wealthManualItems: WealthManualItem[]
  goals: Goal[]
}

const USER: User = {
  id: '11111111-1111-1111-1111-111111111111',
  username: 'alice.familia',
  createdAt: '2026-10-22T12:00:00Z',
}

const TODAY = '2026-10-27'

function accounts(): Account[] {
  return [
    {
      id: 'acc-cash',
      type: 'CASH',
      origin: 'MANUAL',
      alias: 'Carteira',
      currency: 'BRL',
      currentBalance: '1250.00',
      referenceBalance: '1250.00',
      balanceReferenceAt: '2026-10-01T12:00:00Z',
      status: 'ACTIVE',
      notes: 'Dinheiro físico da família.',
    },
    {
      id: 'acc-bank',
      type: 'BANK',
      subtype: 'CHECKING_ACCOUNT',
      origin: 'IMPORTED',
      institutionName: 'Banco Alfa',
      alias: 'Conta Corrente',
      providerName: 'Conta Corrente Banco Alfa',
      nameCustomized: true,
      currency: 'BRL',
      currentBalance: '5432.10',
      status: 'ACTIVE',
      lastSyncAt: '2026-10-27T09:30:00Z',
      connectionStatus: 'SYNCED',
    },
    {
      id: 'acc-card',
      type: 'CREDIT',
      subtype: 'CREDIT_CARD',
      origin: 'IMPORTED',
      institutionName: 'Banco Alfa',
      alias: 'Cartão Alfa',
      providerName: 'Cartão Alfa Platinum',
      currency: 'BRL',
      currentBalance: '0.00',
      status: 'ACTIVE',
      lastSyncAt: '2026-10-27T09:30:00Z',
      connectionStatus: 'SYNCED',
      cardProfile: {
        accountId: 'acc-card',
        closingDay: 5,
        dueDay: 15,
        creditLimit: '5000.00',
        creditBalance: '0.00',
        brand: 'VISA',
        level: 'PLATINUM',
        last4: '1234',
        availableCreditLimit: '4650.00',
      },
    },
    {
      id: 'acc-usd',
      type: 'BANK',
      subtype: 'CHECKING_ACCOUNT',
      origin: 'IMPORTED',
      institutionName: 'Banco Dólar',
      alias: 'Conta Dólar',
      currency: 'USD',
      currentBalance: '10.00',
      status: 'ACTIVE',
      lastSyncAt: '2026-10-27T09:30:00Z',
    },
    {
      id: 'acc-inactive',
      type: 'WALLET',
      origin: 'MANUAL',
      alias: 'Cofrinho digital',
      currency: 'BRL',
      currentBalance: '0.00',
      referenceBalance: '0.00',
      balanceReferenceAt: '2026-10-01T12:00:00Z',
      status: 'INACTIVE',
    },
  ]
}

function categories(): Category[] {
  return [
    { id: 'cat-moradia', name: 'Moradia', kind: 'EXPENSE', predefined: true, active: true },
    { id: 'cat-alimentacao', name: 'Alimentação', kind: 'EXPENSE', predefined: true, active: true },
    { id: 'cat-transporte', name: 'Transporte', kind: 'EXPENSE', predefined: true, active: true },
    { id: 'cat-saude', name: 'Saúde', kind: 'EXPENSE', predefined: true, active: true },
    { id: 'cat-lazer', name: 'Lazer', kind: 'EXPENSE', predefined: true, active: true },
    { id: 'cat-salario', name: 'Salário', kind: 'INCOME', predefined: true, active: true },
    {
      id: 'cat-investimentos',
      name: 'Investimentos',
      kind: 'INCOME',
      predefined: true,
      active: true,
    },
    {
      id: 'cat-transferencias',
      name: 'Transferências',
      kind: 'TRANSFER',
      predefined: true,
      active: true,
    },
    { id: 'cat-outros', name: 'Outros', kind: 'OTHER', predefined: true, active: true },
    { id: 'cat-feira', name: 'Feira', kind: 'EXPENSE', predefined: false, active: true },
    { id: 'cat-viagens', name: 'Viagens', kind: 'EXPENSE', predefined: false, active: false },
  ]
}

function transactions(): Transaction[] {
  return [
    {
      id: 'tx-salario',
      accountId: 'acc-bank',
      categoryId: 'cat-salario',
      categoryName: 'Salário',
      source: 'IMPORTED',
      type: 'CREDIT',
      status: 'POSTED',
      postedOn: '2026-10-05',
      amount: '15200.00',
      currency: 'BRL',
      description: 'Salário outubro',
    },
    {
      id: 'tx-aluguel',
      accountId: 'acc-bank',
      categoryId: 'cat-moradia',
      categoryName: 'Moradia',
      source: 'IMPORTED',
      type: 'DEBIT',
      status: 'POSTED',
      postedOn: '2026-10-06',
      amount: '-2500.00',
      currency: 'BRL',
      description: 'Aluguel',
    },
    {
      id: 'tx-saude',
      accountId: 'acc-bank',
      categoryId: 'cat-saude',
      categoryName: 'Saúde',
      source: 'MANUAL',
      type: 'DEBIT',
      status: 'RECONCILED',
      postedOn: '2026-10-08',
      amount: '-300.00',
      currency: 'BRL',
      description: 'Consulta médica',
      reconciliationState: 'MATCHED',
    },
    {
      id: 'tx-feira',
      accountId: 'acc-cash',
      categoryId: 'cat-feira',
      categoryName: 'Feira',
      source: 'MANUAL',
      type: 'DEBIT',
      status: 'POSTED',
      postedOn: '2026-10-12',
      amount: '-232.10',
      currency: 'BRL',
      description: 'Feira da semana',
    },
    {
      id: 'tx-transfer-saida',
      accountId: 'acc-bank',
      categoryId: 'cat-transferencias',
      categoryName: 'Transferências',
      source: 'MANUAL',
      type: 'TRANSFER',
      status: 'POSTED',
      postedOn: '2026-10-14',
      amount: '-1000.00',
      currency: 'BRL',
      description: 'Transferência para carteira',
      counterpartTransactionId: 'tx-transfer-entrada',
    },
    {
      id: 'tx-transfer-entrada',
      accountId: 'acc-cash',
      categoryId: 'cat-transferencias',
      categoryName: 'Transferências',
      source: 'MANUAL',
      type: 'TRANSFER',
      status: 'POSTED',
      postedOn: '2026-10-14',
      amount: '1000.00',
      currency: 'BRL',
      description: 'Entrada da transferência',
      counterpartTransactionId: 'tx-transfer-saida',
    },
    {
      id: 'tx-mercado',
      accountId: 'acc-card',
      categoryId: 'cat-alimentacao',
      categoryName: 'Alimentação',
      source: 'IMPORTED',
      type: 'DEBIT',
      status: 'POSTED',
      postedOn: '2026-10-03',
      amount: '-540.00',
      currency: 'BRL',
      description: 'Supermercado',
      invoiceId: 'inv-closed',
    },
    {
      id: 'tx-refund',
      accountId: 'acc-card',
      categoryId: 'cat-alimentacao',
      categoryName: 'Alimentação',
      source: 'MANUAL',
      type: 'REFUND',
      status: 'POSTED',
      postedOn: '2026-10-10',
      amount: '45.00',
      currency: 'BRL',
      description: 'Estorno parcial mercado',
      relatedTransactionId: 'tx-mercado',
    },
    {
      id: 'tx-pendente',
      accountId: 'acc-card',
      source: 'IMPORTED',
      type: 'DEBIT',
      status: 'PENDING',
      postedOn: '2026-10-20',
      amount: '-99.90',
      currency: 'BRL',
      description: 'Compra pendente no cartão',
    },
    {
      id: 'tx-removida',
      accountId: 'acc-bank',
      source: 'IMPORTED',
      type: 'DEBIT',
      status: 'SOURCE_REMOVED',
      postedOn: '2026-10-02',
      amount: '-50.00',
      currency: 'BRL',
      description: 'Lançamento removido na origem',
    },
    {
      id: 'tx-ajuste',
      accountId: 'acc-cash',
      source: 'MANUAL',
      type: 'ADJUSTMENT',
      status: 'POSTED',
      postedOn: '2026-10-16',
      amount: '-10.00',
      currency: 'BRL',
      description: 'Ajuste de arredondamento',
      adjustmentReason: 'Divergência no troco registrado.',
    },
  ]
}

function internalMatches(): InternalMatch[] {
  return [
    {
      id: 'match-transfer',
      kind: 'TRANSFER',
      debitTransactionId: 'tx-transfer-saida',
      creditTransactionId: 'tx-transfer-entrada',
      score: 100,
      status: 'PENDING',
      criteriaVersion: 'v1',
    },
    {
      id: 'match-card-payment',
      kind: 'CARD_PAYMENT',
      debitTransactionId: 'tx-pagamento-saida',
      creditTransactionId: 'tx-pagamento-entrada',
      score: 88,
      status: 'PENDING',
      criteriaVersion: 'v1',
    },
  ]
}

function invoices(): InvoiceDetail[] {
  return [
    {
      id: 'inv-open',
      cardAccountId: 'acc-card',
      cycleStart: '2026-10-06',
      cycleEnd: '2026-11-05',
      closingDate: '2026-11-05',
      dueDate: '2026-11-15',
      status: 'OPEN',
      total: '220.00',
      paymentsTotal: '0.00',
      outstanding: '220.00',
      appliedCredit: '0.00',
      source: 'LOCAL',
      hasAdjustments: false,
      adjustmentCount: 0,
      missingPurchases: false,
      providerBill: {
        closingDate: '2026-11-05',
        dueDate: '2026-11-15',
        total: '228.50',
        minimumPayment: '34.28',
        paymentsTotal: '0.00',
        totalDifference: '-8.50',
        paymentsDifference: '0.00',
        reconciled: false,
        ignoredPayments: 0,
        syncedOn: '2026-10-27T09:30:00Z',
        payments: [],
      },
      items: [
        {
          id: 'item-open-1',
          invoiceId: 'inv-open',
          kind: 'PURCHASE',
          reportingEffect: 'EXPENSE_INCREASE',
          source: 'IMPORTED',
          description: 'Assinatura de streaming',
          amount: '39.90',
          occurredOn: '2026-10-18',
          transactionId: 'tx-streaming',
        },
        {
          id: 'item-open-2',
          invoiceId: 'inv-open',
          kind: 'INSTALLMENT',
          reportingEffect: 'EXPENSE_INCREASE',
          source: 'IMPORTED',
          description: 'Celular 12x',
          amount: '150.00',
          occurredOn: '2026-10-20',
          installmentNumber: 3,
          totalInstallments: 12,
          transactionId: 'tx-celular',
        },
        {
          id: 'item-open-3',
          invoiceId: 'inv-open',
          kind: 'INTEREST',
          reportingEffect: 'EXPENSE_INCREASE',
          source: 'MANUAL',
          description: 'Juros rotativo',
          amount: '30.10',
          occurredOn: '2026-10-21',
          correctionCount: 2,
        },
      ],
      payments: [],
    },
    {
      id: 'inv-closed',
      cardAccountId: 'acc-card',
      cycleStart: '2026-09-06',
      cycleEnd: '2026-10-05',
      closingDate: '2026-10-05',
      dueDate: '2026-11-15',
      status: 'CLOSED',
      total: '540.00',
      paymentsTotal: '0.00',
      outstanding: '540.00',
      appliedCredit: '0.00',
      minimumPayment: '81.00',
      source: 'LOCAL',
      hasAdjustments: false,
      adjustmentCount: 0,
      missingPurchases: false,
      items: [
        {
          id: 'item-closed-1',
          invoiceId: 'inv-closed',
          transactionId: 'tx-mercado',
          kind: 'PURCHASE',
          reportingEffect: 'EXPENSE_INCREASE',
          source: 'IMPORTED',
          description: 'Supermercado',
          amount: '540.00',
          occurredOn: '2026-10-03',
        },
      ],
      payments: [],
    },
    {
      id: 'inv-paid',
      cardAccountId: 'acc-card',
      cycleStart: '2026-08-06',
      cycleEnd: '2026-09-05',
      closingDate: '2026-09-05',
      dueDate: '2026-10-15',
      status: 'PAID',
      total: '200.00',
      paymentsTotal: '200.00',
      outstanding: '0.00',
      appliedCredit: '0.00',
      source: 'LOCAL',
      hasAdjustments: false,
      adjustmentCount: 0,
      missingPurchases: false,
      items: [
        {
          id: 'item-paid-1',
          invoiceId: 'inv-paid',
          kind: 'PURCHASE',
          reportingEffect: 'EXPENSE_INCREASE',
          source: 'IMPORTED',
          description: 'Farmácia',
          amount: '200.00',
          occurredOn: '2026-08-20',
          transactionId: 'tx-farmacia',
        },
      ],
      payments: [
        {
          id: 'pay-paid-1',
          invoiceId: 'inv-paid',
          paymentAccountId: 'acc-bank',
          amount: '200.00',
          paidOn: '2026-10-10',
          status: 'CONFIRMED',
          source: 'MANUAL',
        },
      ],
    },
    {
      id: 'inv-overdue',
      cardAccountId: 'acc-card',
      cycleStart: '2026-07-06',
      cycleEnd: '2026-08-05',
      closingDate: '2026-08-05',
      dueDate: '2026-08-15',
      status: 'OVERDUE',
      total: '310.00',
      paymentsTotal: '0.00',
      outstanding: '310.00',
      appliedCredit: '0.00',
      source: 'LOCAL',
      hasAdjustments: true,
      adjustmentCount: 1,
      missingPurchases: false,
      items: [
        {
          id: 'item-overdue-1',
          invoiceId: 'inv-overdue',
          kind: 'FEE',
          reportingEffect: 'EXPENSE_INCREASE',
          source: 'MANUAL',
          description: 'Anuidade',
          amount: '310.00',
          occurredOn: '2026-08-01',
          correctionCount: 1,
        },
      ],
      payments: [],
    },
  ]
}

function cardOverviewItems(): CardOverviewItem[] {
  return [
    {
      accountId: 'acc-card',
      alias: 'Cartão Alfa',
      institutionName: 'Banco Alfa',
      status: 'ACTIVE',
      currency: 'BRL',
      hasCycle: true,
      closingDay: 5,
      dueDay: 15,
      creditLimit: '5000.00',
      creditBalance: '0.00',
      availableCreditLimit: '4650.00',
      brand: 'VISA',
      level: 'PLATINUM',
      last4: '1234',
      usedAmount: '220.00',
      outstanding: '1070.00',
      utilization: '21.40',
      nextDueDate: '2026-11-15',
      currentInvoice: null,
      nextDueInvoice: null,
      unallocatedCount: 1,
    },
  ]
}

function connections(): Connection[] {
  return [
    {
      id: 'connection-1',
      itemIdMasked: 'item…-001',
      institutionName: 'Banco Alfa',
      displayName: 'Banco Alfa',
      nameCustomized: false,
      status: 'SYNCED',
      consentExpiresAt: '2027-10-22T12:00:00Z',
      lastSyncAt: '2026-10-27T09:30:00Z',
      accountCount: 3,
    },
  ]
}

function syncRuns(): SyncRun[] {
  return [
    {
      id: 'run-3',
      integrationId: 'integration-1',
      connectionId: 'connection-1',
      status: 'SUCCESS',
      startedAt: '2026-10-27T09:28:00Z',
      finishedAt: '2026-10-27T09:30:00Z',
      readCount: 340,
      createdCount: 12,
      updatedCount: 5,
      skippedCount: 0,
      failedCount: 0,
    },
    {
      id: 'run-2',
      integrationId: 'integration-1',
      connectionId: 'connection-1',
      status: 'SUCCESS',
      startedAt: '2026-10-26T09:28:00Z',
      finishedAt: '2026-10-26T09:29:00Z',
      readCount: 340,
      createdCount: 0,
      updatedCount: 2,
      skippedCount: 0,
      failedCount: 0,
    },
    {
      id: 'run-1',
      integrationId: 'integration-1',
      connectionId: 'connection-1',
      status: 'FAILED',
      startedAt: '2026-10-25T09:28:00Z',
      finishedAt: '2026-10-25T09:28:30Z',
      readCount: 0,
      createdCount: 0,
      updatedCount: 0,
      skippedCount: 0,
      failedCount: 1,
      errorCode: 'RATE_LIMITED',
    },
  ]
}

function investmentOverview(): Record<string, unknown> {
  return {
    referenceDate: TODAY,
    providerValue: '1500.00',
    totalValue: '1500.00',
    activeCount: 2,
    closedCount: 1,
    unavailableCount: 0,
    incomeLast12Months: '12.50',
    incomeSource: 'PROVIDER',
    profitReported: '50.00',
    profitCoverageCount: 1,
    estimatedProfit: '50.00',
    estimatedProfitCoverageCount: 1,
    lastSyncedAt: '2026-10-27T09:30:00Z',
    stale: false,
    allocation: [
      { assetClass: 'FII', value: '1000.00', count: 1, percentage: '66.67' },
      { assetClass: 'ACAO', value: '500.00', count: 1, percentage: '33.33' },
    ],
    coverage: [
      {
        connectionId: 'connection-1',
        scope: 'AVAILABLE',
        resourceCount: 2,
        availableResourceCount: 2,
        pendingResourceCount: 0,
        unavailableResourceCount: 0,
        resourcesCollectedAt: '2026-10-27T09:30:00Z',
        lastSyncedAt: '2026-10-27T09:30:00Z',
        errorCode: null,
      },
      {
        connectionId: 'connection-2',
        scope: 'NOT_SHARED',
        resourceCount: 0,
        availableResourceCount: 0,
        pendingResourceCount: 0,
        unavailableResourceCount: 0,
        resourcesCollectedAt: '2026-10-27T09:30:00Z',
        lastSyncedAt: '2026-10-27T09:30:00Z',
        errorCode: null,
      },
    ],
  }
}

function holdings(): InvestmentHolding[] {
  return [
    {
      id: 'holding-1',
      connectionId: 'connection-1',
      name: 'FII Alfa',
      code: 'AAAA11',
      isin: 'BRAAAA',
      type: 'EQUITY',
      subtype: 'STOCK',
      assetClass: 'FII',
      classificationSource: 'CODE_HEURISTIC',
      status: 'ACTIVE',
      currency: 'BRL',
      quantity: '10',
      unitValue: '100.00',
      amount: '1000.00',
      balance: '1000.00',
      amountOriginal: null,
      amountProfit: null,
      amountWithdrawal: '1000.00',
      referenceDate: TODAY,
      providerUpdatedAt: '2026-10-27T09:30:00Z',
      syncedAt: '2026-10-27T09:30:00Z',
      unavailableAt: null,
      manualCostBasis: null,
      costBasis: null,
      costBasisSource: 'UNAVAILABLE',
      incomeTotal: '12.50',
      estimatedProfit: null,
      estimatedReturnPercentage: null,
      userAssetClass: null,
      profitSource: 'UNAVAILABLE',
      stale: false,
    },
    {
      id: 'holding-2',
      connectionId: 'connection-1',
      name: 'Ações Alfa',
      code: 'AAAA3',
      isin: 'BRAAAA3',
      type: 'EQUITY',
      subtype: 'STOCK',
      assetClass: 'ACAO',
      classificationSource: 'CODE_HEURISTIC',
      status: 'ACTIVE',
      currency: 'BRL',
      quantity: '5',
      unitValue: '100.00',
      amount: '500.00',
      balance: '500.00',
      amountOriginal: '450.00',
      amountProfit: '50.00',
      amountWithdrawal: '500.00',
      referenceDate: TODAY,
      providerUpdatedAt: '2026-10-27T09:30:00Z',
      syncedAt: '2026-10-27T09:30:00Z',
      unavailableAt: null,
      manualCostBasis: null,
      costBasis: '450.00',
      costBasisSource: 'PROVIDER',
      incomeTotal: '0.00',
      estimatedProfit: '50.00',
      estimatedReturnPercentage: '11.11',
      userAssetClass: null,
      profitSource: 'PROVIDER',
      stale: false,
    },
    {
      id: 'holding-3',
      connectionId: 'connection-1',
      name: 'FII Gama',
      code: 'CCCC11',
      isin: 'BRCCCC',
      type: 'EQUITY',
      subtype: 'STOCK',
      assetClass: 'FII',
      classificationSource: 'CODE_HEURISTIC',
      status: 'CLOSED',
      currency: 'BRL',
      quantity: '0',
      unitValue: '0',
      amount: '0',
      balance: '0',
      amountOriginal: null,
      amountProfit: null,
      amountWithdrawal: '0',
      referenceDate: TODAY,
      providerUpdatedAt: '2026-10-27T09:30:00Z',
      syncedAt: '2026-10-27T09:30:00Z',
      unavailableAt: null,
      manualCostBasis: null,
      costBasis: null,
      costBasisSource: 'UNAVAILABLE',
      incomeTotal: '0.00',
      estimatedProfit: null,
      estimatedReturnPercentage: null,
      userAssetClass: null,
      profitSource: 'UNAVAILABLE',
      stale: false,
    },
  ]
}

function incomeItems(): InvestmentIncomeItem[] {
  return [
    {
      id: null,
      date: '2026-10-26',
      investmentId: 'holding-1',
      investmentName: 'FII Alfa',
      code: 'AAAA11',
      type: 'EQUITY',
      incomeType: 'INTEREST',
      amount: '12.50',
      description: 'Rendimento',
      source: 'PROVIDER',
      editable: false,
    },
  ]
}

function wealthItems(): WealthItem[] {
  return [
    {
      sourceType: 'ACCOUNT',
      sourceId: 'acc-bank',
      description: 'Conta Corrente',
      origin: 'IMPORTED',
      value: '5432.10',
      included: true,
      referenceDate: TODAY,
      stale: false,
    },
    {
      sourceType: 'ACCOUNT',
      sourceId: 'acc-cash',
      description: 'Carteira',
      origin: 'MANUAL',
      value: '1250.00',
      included: true,
      referenceDate: TODAY,
      stale: false,
    },
    {
      sourceType: 'ACCOUNT',
      sourceId: 'acc-usd',
      description: 'Conta Dólar',
      value: '0.00',
      included: false,
      exclusionReason: 'Moeda não suportada na consolidação (USD).',
      origin: 'IMPORTED',
      stale: false,
    },
    {
      sourceType: 'CARD',
      sourceId: 'acc-card',
      description: 'Cartão Alfa',
      origin: 'IMPORTED',
      value: '1070.00',
      included: true,
      referenceDate: TODAY,
      stale: false,
    },
    {
      sourceType: 'INVESTMENT',
      sourceId: 'holding-1',
      description: 'FII Alfa',
      origin: 'IMPORTED',
      value: '1000.00',
      included: true,
      referenceDate: TODAY,
      stale: false,
    },
  ]
}

function wealthManualItems(): WealthManualItem[] {
  return [
    {
      id: 'item-1',
      kind: 'ASSET',
      type: 'IMOVEL',
      description: 'Apartamento',
      referenceValue: '250000.00',
      referenceDate: '2026-10-20',
    },
    {
      id: 'item-2',
      kind: 'LIABILITY',
      type: 'LOAN',
      description: 'Empréstimo consignado',
      referenceValue: '12000.00',
      referenceDate: '2026-10-20',
    },
  ]
}

function goals(): Goal[] {
  return [
    {
      id: 'goal-1',
      name: 'Reserva de emergência',
      description: 'Seis meses de despesas essenciais.',
      type: 'EMERGENCY_FUND',
      targetAmount: '20000.00',
      targetDate: '2027-10-01',
      status: 'ACTIVE',
      progressRule: 'ALLOCATIONS',
      createdAt: '2026-05-01T12:00:00Z',
      currentValue: '10000.00',
      percentage: '50.00',
      remainingAmount: '10000.00',
      unallocatedAmount: '10000.00',
      excessAmount: '0.00',
      allocations: [
        {
          id: 'alloc-1',
          sourceType: 'ACCOUNT',
          sourceId: 'acc-bank',
          sourceName: 'Conta Corrente',
          allocationMode: 'PERCENTAGE',
          percentage: '30',
          contributionAmount: '6000.00',
        },
        {
          id: 'alloc-2',
          sourceType: 'MANUAL',
          allocationMode: 'PERCENTAGE',
          percentage: '20',
          contributionAmount: '4000.00',
        },
      ],
      progressHistory: [
        {
          referenceDate: '2026-10-24',
          currentValue: '10000.00',
          percentage: '50.00',
          remainingAmount: '10000.00',
          ruleVersion: 'v1',
        },
      ],
    },
    {
      id: 'goal-2',
      name: 'Viagem em família',
      type: 'TRAVEL',
      targetAmount: '15000.00',
      targetDate: '2027-07-15',
      status: 'CHANGED',
      progressRule: 'ALLOCATIONS',
      createdAt: '2026-06-01T12:00:00Z',
      changedAt: '2026-10-20T12:00:00Z',
      currentValue: '3000.00',
      percentage: '20.00',
      remainingAmount: '12000.00',
      unallocatedAmount: '12000.00',
      excessAmount: '0.00',
      allocations: [
        {
          id: 'alloc-3',
          sourceType: 'MANUAL',
          allocationMode: 'FIXED_AMOUNT',
          allocatedAmount: '3000.00',
          contributionAmount: '3000.00',
        },
      ],
      progressHistory: [
        {
          referenceDate: '2026-10-24',
          currentValue: '3000.00',
          percentage: '20.00',
          remainingAmount: '12000.00',
          ruleVersion: 'v1',
        },
      ],
    },
  ]
}

/** Cria uma nova instância do banco de dados de teste. */
export function seedDb(): Db {
  return {
    user: USER,
    accounts: accounts(),
    categories: categories(),
    transactions: transactions(),
    internalMatches: internalMatches(),
    invoices: invoices(),
    cardOverviewItems: cardOverviewItems(),
    integration: {
      id: 'integration-1',
      provider: 'MEU_PLUGGY',
      clientIdMasked: 'clie…ente',
      status: 'ACTIVE',
      lastSyncAttemptAt: '2026-10-27T09:28:00Z',
      lastSuccessfulSyncAt: '2026-10-27T09:30:00Z',
      connectionCount: 1,
      connectionLimit: 5,
      connectionLimitCheckedAt: '2026-10-27',
    },
    connections: connections(),
    syncRuns: syncRuns(),
    investmentOverview: investmentOverview(),
    holdings: holdings(),
    incomeItems: incomeItems(),
    wealthItems: wealthItems(),
    wealthManualItems: wealthManualItems(),
    goals: goals(),
  }
}

/**
 * Banco vazio, com o mesmo usuário e categorias predefinidas.
 *
 * Usado para capturar estados “sem dados” sem inventar valores financeiros.
 */
export function emptyDb(): Db {
  const base = seedDb()
  return {
    ...base,
    accounts: [],
    transactions: [],
    internalMatches: [],
    invoices: [],
    cardOverviewItems: [],
    connections: [],
    syncRuns: [],
    holdings: [],
    incomeItems: [],
    wealthItems: [],
    wealthManualItems: [],
    goals: [],
    investmentOverview: {
      ...base.investmentOverview,
      providerValue: '0.00',
      totalValue: '0.00',
      activeCount: 0,
      closedCount: 0,
      unavailableCount: 0,
      incomeLast12Months: '0.00',
      incomeSource: 'UNAVAILABLE',
      profitReported: null,
      profitCoverageCount: 0,
      estimatedProfit: null,
      estimatedProfitCoverageCount: 0,
      allocation: [],
      coverage: [],
    },
  }
}
