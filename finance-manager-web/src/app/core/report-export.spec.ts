import { HttpErrorResponse, HttpHeaders, HttpResponse, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { vi } from 'vitest';
import { Auth } from './auth';
import { authInterceptor } from './auth.interceptor';
import { ReportExportApi, ReportExportFormat, ReportExportRequest } from './report-export';

describe('ReportExportApi', () => {
  let api: ReportExportApi;
  let http: HttpTestingController;
  const request: ReportExportRequest = { format: 'PDF', startDate: '2025-01-01', endDate: '2025-01-31' };

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(withInterceptors([authInterceptor])),
      provideHttpClientTesting(), { provide: Auth, useValue: { getToken: () => 'test-token' } }] });
    api = TestBed.inject(ReportExportApi);
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => { http.verify(); vi.restoreAllMocks(); vi.unstubAllGlobals(); vi.useRealTimers(); });

  it.each(['PDF', 'XLSX', 'CSV'] as ReportExportFormat[])('requests %s as a Blob with auth and no pagination', format => {
    let result: HttpResponse<Blob> | undefined;
    const blob = new Blob(['file']);
    api.download({ ...request, format, type: 'EXPENSE', accountId: 'account', categoryId: 'category' })
      .subscribe(response => result = response);
    const pending = http.expectOne(req => req.url === '/api/v1/reports/export');
    expect(pending.request.method).toBe('GET');
    expect(pending.request.responseType).toBe('blob');
    expect(pending.request.headers.get('Authorization')).toBe('Bearer test-token');
    expect(pending.request.params.keys().sort()).toEqual(['accountId', 'categoryId', 'endDate', 'format', 'startDate', 'type']);
    expect(pending.request.params.get('format')).toBe(format);
    expect(pending.request.params.get('startDate')).toBe(request.startDate);
    expect(pending.request.params.get('endDate')).toBe(request.endDate);
    expect(pending.request.params.get('accountId')).toBe('account');
    pending.flush(blob, { headers: { 'Content-Disposition': 'attachment; filename="report.pdf"' } });
    expect(result?.body).toBe(blob);
  });

  it('omits empty optional filters', () => {
    api.download(request).subscribe();
    const pending = http.expectOne(req => req.url === '/api/v1/reports/export');
    expect(pending.request.params.keys().sort()).toEqual(['endDate', 'format', 'startDate']);
    pending.flush(new Blob(['file']));
  });

  it('decodes ProblemDetail received as a Blob without losing status', async () => {
    const result = new Promise<HttpErrorResponse>(resolve => api.download(request).subscribe({ error: resolve }));
    http.expectOne(req => req.url === '/api/v1/reports/export').flush(
      new Blob([JSON.stringify({ code: 'REPORT_EXPORT_LIMIT_EXCEEDED' })], { type: 'application/problem+json' }),
      { status: 400, statusText: 'Bad Request' },
    );
    const error = await result;
    expect(error.status).toBe(400);
    expect(error.error.code).toBe('REPORT_EXPORT_LIMIT_EXCEEDED');
  });

  it('keeps status when the error body is not JSON', async () => {
    const result = new Promise<HttpErrorResponse>(resolve => api.download(request).subscribe({ error: resolve }));
    http.expectOne(req => req.url === '/api/v1/reports/export').flush(new Blob(['server unavailable']),
      { status: 503, statusText: 'Unavailable' });
    expect((await result).status).toBe(503);
  });

  it('preserves network errors', () => {
    let error: HttpErrorResponse | undefined;
    api.download(request).subscribe({ error: value => error = value });
    http.expectOne(req => req.url === '/api/v1/reports/export').error(new ProgressEvent('error'));
    expect(error?.status).toBe(0);
  });

  it.each([
    ['attachment; filename="server-report.pdf"', 'server-report.pdf'],
    ['', 'finance-manager_2025-01-01_2025-01-31.pdf'],
    ['attachment; filename="../../other.pdf"', 'finance-manager_2025-01-01_2025-01-31.pdf'],
    ['attachment; filename="other.html"', 'finance-manager_2025-01-01_2025-01-31.pdf'],
  ])('starts download and releases the URL for disposition %s', (disposition, filename) => {
    vi.useFakeTimers();
    const createObjectURL = vi.fn(() => 'blob:report');
    const revokeObjectURL = vi.fn();
    vi.stubGlobal('URL', class extends URL { static override createObjectURL = createObjectURL; static override revokeObjectURL = revokeObjectURL; });
    let clickedName = '';
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function(this: HTMLAnchorElement) {
      expect(this.isConnected).toBe(true);
      clickedName = this.download;
    });
    api.save(new HttpResponse({ body: new Blob(['file']), headers: new HttpHeaders({ 'Content-Disposition': disposition }) }), request);
    expect(clickedName).toBe(filename);
    expect(document.querySelector('a[download]')).toBeNull();
    expect(revokeObjectURL).not.toHaveBeenCalled();
    vi.runAllTimers();
    expect(revokeObjectURL).toHaveBeenCalledWith('blob:report');
  });

  it('rejects empty responses instead of downloading an empty file', () => {
    expect(() => api.save(new HttpResponse({ body: new Blob([]) }), request)).toThrow('Empty report');
  });
});
