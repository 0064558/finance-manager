import { HttpClient } from '@angular/common/http';
import { inject, Service } from '@angular/core';
import { Observable } from 'rxjs';

import {
    AccountTransfer,
    CreateAccountTransferRequest,
    CreateFinancialAccountRequest,
    FinancialAccount,
    UpdateFinancialAccountRequest,
} from './financial-account.models';
import { PageResponse } from './transaction.models';

// Serviço para interagir com a API de contas financeiras, fornecendo métodos para obter todas as contas, 
// criar uma nova conta e atualizar uma conta existente.
@Service()
export class FinancialAccountApi {
    // Injeção do HttpClient para realizar requisições HTTP à API.
    private readonly http = inject(HttpClient);

    // Endpoint base da API para contas financeiras.
    private readonly endpoint = '/api/v1/financial-accounts';
    private readonly transfersEndpoint = '/api/v1/transfers';

    getTransfers(page: number): Observable<PageResponse<AccountTransfer>> {
        return this.http.get<PageResponse<AccountTransfer>>(this.transfersEndpoint, { params: { page } });
    }

    createTransfer(request: CreateAccountTransferRequest, key: string): Observable<AccountTransfer> {
        return this.http.post<AccountTransfer>(this.transfersEndpoint, request, {
            headers: { 'Idempotency-Key': key },
        });
    }

    // Método para listar todas as contas financeiras.
    getAll(): Observable<FinancialAccount[]> {
        return this.http.get<FinancialAccount[]>(this.endpoint);
    }

    // Método para criar uma nova conta financeira.
    create(request: CreateFinancialAccountRequest): Observable<FinancialAccount> {
        return this.http.post<FinancialAccount>(this.endpoint, request);
    }

    // Método para atualizar uma conta financeira existente.
    update(accountId: string, request: UpdateFinancialAccountRequest): Observable<FinancialAccount> {
        return this.http.put<FinancialAccount>(`${this.endpoint}/${accountId}`, request);
    }

    // Método para excluir uma conta financeira existente.
    delete(accountId: string): Observable<void> {
        return this.http.delete<void>(`${this.endpoint}/${accountId}`);
    }
}
