import type { TransactionType } from './transaction.models';
import type { AccountType } from './financial-account.models';

// Este arquivo define interfaces TypeScript para representar os dados de relatórios financeiros, 
// incluindo resumo de relatórios, saldo de contas e fluxo de caixa.

export interface ReportSummary {
    startDate: string;
    endDate: string;
    totalIncome: number;
    totalExpense: number;
    netBalance: number;
};

export interface CategoryTotal {
    categoryId: string;
    categoryName: string;
    amount: number;
}

export interface CategoryBreakdownResponse {
    startDate: string;
    endDate: string;
    type: TransactionType;
    totalAmount: number;
    categories: CategoryTotal[];
}

export interface CategoryExpense {
    categoryId: string;
    categoryName: string;
    totalExpense: number;
}

export interface CategoryExpensesResponse {
    startDate: string;
    endDate: string;
    totalExpense: number;
    categories: CategoryExpense[];
}

export interface AccountBalance {
    accountId: string;
    accountName: string;
    accountType: AccountType;
    balance: number;
};

export interface CurrentBalance {
    totalBalance: number;
    accounts: AccountBalance[];
};

export interface CashFlowPoint {
    date: string;
    totalIncome: number;
    totalExpense: number;
    netBalance: number;
};

export interface CashFlowResponse {
    startDate: string;
    endDate: string;
    points: CashFlowPoint[];
};

