import { HttpClient } from '@angular/common/http';
import { inject, Service } from '@angular/core';
import { Observable } from 'rxjs';

import {
    CreateFinancialAccountRequest,
    FinancialAccount,
    UpdateFinancialAccountRequest,
} from './financial-account.models';

// Serviço para interagir com a API de contas financeiras, fornecendo métodos para obter todas as contas, 
// criar uma nova conta e atualizar uma conta existente.
@Service()
export class FinancialAccountApi {
    // Injeção do HttpClient para realizar requisições HTTP à API.
    private readonly http = inject(HttpClient);

    // Endpoint base da API para contas financeiras.
    private readonly endpoint = '/api/v1/financial-accounts';

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