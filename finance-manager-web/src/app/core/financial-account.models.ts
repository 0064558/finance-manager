export type AccountType = 'CASH' | 'CHECKING' | 'SAVINGS';

// Interface para representar uma conta financeira, 
// incluindo informações como ID, nome, tipo, saldo inicial e datas de criação e atualização.

export interface FinancialAccount {
    id: string;
    name: string;
    type: AccountType;
    initialBalance: number;
    createdAt: string;
    updatedAt: string;
}

export interface CreateFinancialAccountRequest {
    name: string;
    type: AccountType;
    initialBalance: number;
}

export type UpdateFinancialAccountRequest = CreateFinancialAccountRequest;