import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Service } from '@angular/core';
import { Observable } from 'rxjs';
import { CreateTransactionRequest, PageResponse, TransactionFilters, TransactionResponse } from './transaction.models';

@Service()
export class TransactionApi {
  private readonly http = inject(HttpClient);

  private readonly endpoint = '/api/v1/transactions';

  // Busca as transações financeiras mais recentes com base nas datas de início e término fornecidas, 
  // limitando o número de resultados retornados.
  getRecent(
    startDate: string,
    endDate: string,
    size = 5,
  ): Observable<PageResponse<TransactionResponse>> {
    return this.getAll({
      startDate,
      endDate,
      page: 0,
      size,
    });
  }

  // Busca todas as transações financeiras com base nos filtros fornecidos, incluindo data de início, 
  // data de término, tipo de transação, ID da conta, ID da categoria, página e tamanho da página.
  getAll(filters: TransactionFilters = {}): Observable<PageResponse<TransactionResponse>> {
    // Cria um objeto HttpParams para armazenar os parâmetros de consulta da solicitação HTTP,
    // definindo os valores padrão para a página e o tamanho da página, e adicionando os filtros fornecidos.
    let params = new HttpParams()
      .set('page', filters.page ?? 0)
      .set('size', filters.size ?? 20);

    // Adiciona os filtros fornecidos aos parâmetros de consulta, se estiverem presentes.

    if (filters.startDate) {
      params = params.set(
        'startDate',
        filters.startDate,
      );
    }

    if (filters.endDate) {
      params = params.set(
        'endDate',
        filters.endDate,
      );
    }

    if (filters.type) {
      params = params.set('type', filters.type);
    }

    if (filters.accountId) {
      params = params.set(
        'accountId',
        filters.accountId,
      );
    }

    if (filters.categoryId) {
      params = params.set(
        'categoryId',
        filters.categoryId,
      );
    }

    return this.http.get<PageResponse<TransactionResponse>>(
      this.endpoint,
      { params },
    );
  }

  create(transactionRequest: CreateTransactionRequest): Observable<TransactionResponse> {
    return this.http.post<TransactionResponse>(
      this.endpoint,
      transactionRequest,
    );
  }
}
