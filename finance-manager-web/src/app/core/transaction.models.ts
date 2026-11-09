export type TransactionType = 'INCOME' | 'EXPENSE'; // union type

export interface TransactionChatEntry {
  id: string;
  requestId?: string;
  prompt: string;
  response?: TransactionSuggestionResponse;
  saved: boolean;
  drafts?: TransactionChatDraft[];
  replyTo?: string;
  superseded?: boolean;
}

export interface TransactionChatMessage {
  description: string;
  requestId: string;
  replyTo?: string;
  timeZone?: string;
}

export interface TransactionChatDraft {
  id: string;
  response: TransactionSuggestion;
  saved: boolean;
  discarded: boolean;
}

export interface TransactionSuggestion {
  status: 'SUGESTAO';
  descricao: string;
  valor: number;
  tipo: 'DESPESA' | 'RECEITA';
  categoriaSugerida?: string;
  categoriaId?: string;
  data?: string;
}

export type TransactionSuggestionResponse = TransactionSuggestion | {
  status: 'ESCLARECIMENTO';
  motivo: string;
  mensagem: string;
};

// Representa uma transação financeira, incluindo informações como conta, categoria, tipo, valor, data de ocorrência e descrição.
export interface TransactionResponse {
  id: string;
  accountId: string;
  categoryId: string;
  type: TransactionType;
  amount: number;
  occurredOn: string;
  description: string | null;
  createdAt: string;
  updatedAt: string;
}

// Representa a solicitação para criar uma nova transação financeira, incluindo informações como conta, categoria, tipo, valor, data de ocorrência e descrição.
export interface CreateTransactionRequest {
  accountId: string;
  categoryId: string;
  type: TransactionType;
  amount: number;
  occurredOn: string;
  description?: string;
}

// Representa a solicitação para atualizar uma transação financeira existente, incluindo informações como conta, categoria, tipo, valor, data de ocorrência e descrição.
export type UpdateTransactionRequest = CreateTransactionRequest;

// Representa os filtros que podem ser aplicados ao buscar transações financeiras, incluindo data de início
export interface TransactionFilters {
  startDate?: string;
  endDate?: string;
  type?: TransactionType;
  accountId?: string;
  categoryId?: string;
  page?: number;
  size?: number;
}

// Representa a resposta paginada de uma lista de itens.
export interface PageResponse<T> {
  content: T[];
  empty: boolean;
  first: boolean;
  last: boolean;
  number: number;
  numberOfElements: number;
  size: number;
  totalElements: number;
  totalPages: number;
}
