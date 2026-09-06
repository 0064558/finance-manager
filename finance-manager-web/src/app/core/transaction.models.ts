export type TransactionType = 'INCOME' | 'EXPENSE'; // union type

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

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
  first: boolean;
  last: boolean;
  empty: boolean;
}
