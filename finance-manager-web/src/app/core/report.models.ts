// Este arquivo define interfaces TypeScript para representar os dados de relatórios financeiros, 
// incluindo resumo de relatórios, saldo de contas e fluxo de caixa.

export interface ReportSummary {
    startDate: string;
    endDate: string;
    totalIncome: number;
    totalExpense: number;
    netBalance: number;
};

export interface AccountBalance {
    accountId: string;
    accountName: string;
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

