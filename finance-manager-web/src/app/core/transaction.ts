import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Service } from '@angular/core';
import { Observable } from 'rxjs';
import { PageResponse, TransactionFilters, TransactionResponse } from './transaction.models';

@Service()
export class TransactionApi {
  private readonly http = inject(HttpClient);

  private readonly endpoint = '/api/v1/transactions';

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

  getAll(filters: TransactionFilters = {}): Observable<PageResponse<TransactionResponse>> {
    let params = new HttpParams()
      .set('page', filters.page ?? 0)
      .set('size', filters.size ?? 20);

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
}
