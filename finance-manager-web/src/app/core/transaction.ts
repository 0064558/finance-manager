import { HttpClient } from '@angular/common/http';
import { inject, Service } from '@angular/core';
import { Observable } from 'rxjs';
import { PageResponse, TransactionResponse } from './transaction.models';

@Service()
export class TransactionApi {
  private readonly http = inject(HttpClient);

  getRecent(
    startDate: string,
    endDate: string,
    size = 5,
  ): Observable<PageResponse<TransactionResponse>> {
    return this.http.get<PageResponse<TransactionResponse>>('/api/v1/transactions', {
      params: {
        startDate,
        endDate,
        page: 0,
        size,
      },
    });
  }
}
