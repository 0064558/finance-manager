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

export interface CreateAccountTransferRequest {
  sourceAccountId: string;
  destinationAccountId: string;
  amount: string;
  occurredOn: string;
  description?: string;
}

export interface AccountTransfer extends Omit<CreateAccountTransferRequest, 'amount' | 'description'> {
  id: string;
  amount: number;
  description: string | null;
  createdAt: string;
}
