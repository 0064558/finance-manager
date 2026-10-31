import { DOCUMENT } from '@angular/common';
import { HttpClient, HttpErrorResponse, HttpParams, HttpResponse } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { catchError, from, mergeMap, Observable, throwError } from 'rxjs';
import { TransactionType } from './transaction.models';

export type ReportExportFormat = 'PDF' | 'XLSX' | 'CSV';

export interface ReportExportRequest {
  format: ReportExportFormat;
  startDate: string;
  endDate: string;
  type?: TransactionType;
  accountId?: string;
  categoryId?: string;
}

@Injectable({ providedIn: 'root' })
export class ReportExportApi {
  private readonly http = inject(HttpClient);
  private readonly document = inject(DOCUMENT);

  download(request: ReportExportRequest): Observable<HttpResponse<Blob>> {
    let params = new HttpParams().set('format', request.format)
      .set('startDate', request.startDate).set('endDate', request.endDate);
    for (const key of ['type', 'accountId', 'categoryId'] as const) {
      if (request[key]) params = params.set(key, request[key]);
    }
    return this.http.get('/api/v1/reports/export', { params, observe: 'response', responseType: 'blob' }).pipe(
      catchError((error: unknown) => {
        if (!(error instanceof HttpErrorResponse) || !(error.error instanceof Blob)) {
          return throwError(() => error);
        }
        // Binary requests also receive ProblemDetail JSON as a Blob on failure.
        return from(error.error.text()).pipe(
          catchError(() => from(Promise.resolve(''))),
          mergeMap((text) => {
            let problem: unknown = null;
            try { problem = JSON.parse(text); } catch { /* Keep the HTTP status for non-JSON failures. */ }
            return throwError(() => new HttpErrorResponse({
              error: problem, status: error.status, statusText: error.statusText,
              headers: error.headers, url: error.url ?? undefined,
            }));
          }),
        );
      }),
    );
  }

  save(response: HttpResponse<Blob>, request: ReportExportRequest): void {
    if (!response.body || response.body.size === 0) throw new Error('Empty report response');
    const extension = request.format.toLowerCase();
    const fallback = `finance-manager_${request.startDate}_${request.endDate}.${extension}`;
    const disposition = response.headers.get('Content-Disposition') ?? '';
    const match = disposition.match(/(?:^|;)\s*filename=(?:"([^"]+)"|([^;]+))/i);
    const candidate = (match?.[1] ?? match?.[2] ?? '').trim();
    const filename = /^[a-zA-Z0-9_.-]+$/.test(candidate) && candidate.toLowerCase().endsWith(`.${extension}`)
      ? candidate : fallback;
    const url = URL.createObjectURL(response.body);
    const link = this.document.createElement('a');
    try {
      link.href = url;
      link.download = filename;
      this.document.body.appendChild(link);
      link.click();
    } finally {
      link.remove();
      // Give the browser time to start consuming the Blob before releasing it.
      setTimeout(() => URL.revokeObjectURL(url), 1000);
    }
  }
}
