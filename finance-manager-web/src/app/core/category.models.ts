import type { TransactionType } from './transaction.models';

// Interface para representar uma categoria, incluindo informações como ID, nome, tipo de transação e datas de criação e atualização.
export interface Category {
    id: string;
    name: string;
    transactionType: TransactionType;
    createdAt: string;
    updatedAt: string;
}

// Interface para representar a solicitação de criação de uma nova categoria, incluindo nome e tipo de trans
export interface CreateCategoryRequest {
    name: string;
    transactionType: TransactionType;
}

// Tipo para representar a solicitação de atualização de uma categoria existente, que é igual à solicitação de criação.
export type UpdateCategoryRequest = CreateCategoryRequest;