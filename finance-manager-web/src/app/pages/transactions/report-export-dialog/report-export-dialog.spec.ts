import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Router } from '@angular/router';
import { of, Subject, throwError } from 'rxjs';
import { vi } from 'vitest';
import { Auth } from '../../../core/auth';
import { Notifications } from '../../../core/notifications';
import { ReportExportApi } from '../../../core/report-export';
import { SlowApiLoading } from '../../../core/slow-api-loading';
import { TransactionFilters } from '../../../core/transaction.models';
import { ValuePrivacy } from '../../../core/value-privacy';
import { ReportExportDialog } from './report-export-dialog';

describe('ReportExportDialog', () => {
  let fixture: ComponentFixture<ReportExportDialog>;
  let trigger: HTMLButtonElement;
  const download = vi.fn();
  const save = vi.fn();
  const success = vi.fn();
  const notifyError = vi.fn();
  const logout = vi.fn();
  const navigateByUrl = vi.fn();

  beforeEach(() => {
    vi.clearAllMocks();
    download.mockReset(); save.mockReset();
    // jsdom has dialog elements but does not implement the browser's modal API.
    for (const method of ['showModal', 'close'] as const) {
      if (!HTMLDialogElement.prototype[method]) Object.defineProperty(HTMLDialogElement.prototype, method,
        { value() {}, configurable: true, writable: true });
    }
    vi.spyOn(HTMLDialogElement.prototype, 'showModal').mockImplementation(function(this: HTMLDialogElement) {
      this.setAttribute('open', '');
      this.querySelector<HTMLElement>('[autofocus]')?.focus();
    });
    vi.spyOn(HTMLDialogElement.prototype, 'close').mockImplementation(function(this: HTMLDialogElement) {
      this.removeAttribute('open'); this.dispatchEvent(new Event('close'));
    });
    TestBed.configureTestingModule({ imports: [ReportExportDialog], providers: [
      { provide: ReportExportApi, useValue: { download, save } },
      { provide: Notifications, useValue: { success, error: notifyError } },
      { provide: Auth, useValue: { logout } }, { provide: Router, useValue: { navigateByUrl } },
    ] });
    trigger = document.createElement('button');
    document.body.appendChild(trigger); trigger.focus();
  });
  afterEach(() => { fixture?.destroy(); trigger?.remove(); localStorage.clear(); vi.restoreAllMocks(); vi.useRealTimers(); });

  function refresh() { fixture.changeDetectorRef.markForCheck(); fixture.detectChanges(); }

  function render(filters: TransactionFilters = {}) {
    fixture = TestBed.createComponent(ReportExportDialog);
    fixture.componentRef.setInput('filters', filters);
    fixture.componentRef.setInput('accounts', [{ id: 'account', name: 'Conta', type: 'CHECKING', initialBalance: 0 }]);
    fixture.componentRef.setInput('categories', [{ id: 'food', name: 'Alimentação', transactionType: 'EXPENSE' },
      { id: 'salary', name: 'Salário', transactionType: 'INCOME' }]);
    refresh();
    return fixture.componentInstance;
  }

  it('suggests the current local month only when both dates are absent and focuses format', () => {
    const component = render();
    const today = new Date();
    const month = `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, '0')}`;
    expect(component['form'].controls.startDate.value).toBe(`${month}-01`);
    expect(component['form'].controls.endDate.value).toBe(`${month}-${new Date(today.getFullYear(), today.getMonth() + 1, 0).getDate()}`);
    expect(HTMLDialogElement.prototype.showModal).toHaveBeenCalled();
    expect(document.activeElement?.id).toBe('export-format');
  });

  it('copies applied filters without mutating them and leaves a missing date for the user to fill', () => {
    const filters = { startDate: '2025-01-01', type: 'EXPENSE' as const, accountId: 'account', categoryId: 'food' };
    const component = render(filters);
    expect(component['form'].controls.endDate.value).toBe('');
    component['form'].controls.accountId.setValue('');
    component['submit']();
    refresh();
    expect(download).not.toHaveBeenCalled();
    expect(fixture.nativeElement.textContent).toContain('Preencha a data inicial e a data final');
    expect(filters.accountId).toBe('account');
  });

  it('rejects inverted dates and periods above 366 days', () => {
    const component = render({ startDate: '2025-02-01', endDate: '2025-01-01' });
    component['submit'](); refresh();
    expect(fixture.nativeElement.textContent).toContain('A data inicial não pode ser posterior');
    component['form'].patchValue({ startDate: '2025-01-01', endDate: '2026-01-02' });
    component['submit'](); refresh();
    expect(fixture.nativeElement.textContent).toContain('até 366 dias');
    expect(download).not.toHaveBeenCalled();
    component['form'].controls.endDate.setValue('2026-01-01');
    expect(component['form'].valid).toBe(true);
  });

  it('resets category when type changes and keeps only compatible options', () => {
    const component = render({ type: 'EXPENSE', categoryId: 'food' });
    component['form'].controls.type.setValue('INCOME');
    component['resetCategory'](); refresh();
    expect(component['form'].controls.categoryId.value).toBe('');
    expect(component['availableCategories']().map(c => c.id)).toEqual(['salary']);
  });

  it('shows the complete-values notice while privacy is active', () => {
    render();
    TestBed.inject(ValuePrivacy).setHidden(true); refresh();
    expect(fixture.nativeElement.querySelector('[role="note"]').textContent).toContain('O arquivo terá os valores completos');
  });

  it('blocks repeated submissions, indicates slow loading, and retains filters on failure', () => {
    const pending = new Subject<HttpResponse<Blob>>(); download.mockReturnValue(pending);
    const component = render({ startDate: '2025-01-01', endDate: '2025-01-31', accountId: 'account' });
    component['submit'](); component['submit'](); refresh();
    expect(download).toHaveBeenCalledTimes(1);
    expect(fixture.nativeElement.querySelector('fieldset').disabled).toBe(true);
    expect(fixture.nativeElement.querySelector('[role="status"]').textContent).toContain('Gerando');
    vi.useFakeTimers();
    const stop = TestBed.inject(SlowApiLoading).trackRequest();
    vi.advanceTimersByTime(5000); refresh();
    expect(fixture.nativeElement.textContent).toContain('servidor pode estar iniciando');
    stop();
    const cancel = new Event('cancel', { cancelable: true });
    component['cancel'](cancel);
    expect(cancel.defaultPrevented).toBe(true);
    expect(HTMLDialogElement.prototype.close).not.toHaveBeenCalled();
    pending.error(new HttpErrorResponse({ status: 500 })); refresh();
    expect(component['submitting']()).toBe(false);
    expect(component['form'].controls.accountId.value).toBe('account');
    expect(fixture.nativeElement.querySelector('[role="alert"]').textContent).toContain('Não foi possível gerar');
    expect(save).not.toHaveBeenCalled();
  });

  it.each(['PDF', 'XLSX', 'CSV'] as const)('downloads %s with all filters, announces start and restores focus', format => {
    const response = new HttpResponse({ body: new Blob(['file']) }); download.mockReturnValue(of(response));
    const component = render({ startDate: '2025-01-01', endDate: '2025-01-31', type: 'EXPENSE', accountId: 'account', categoryId: 'food', page: 2 });
    component['form'].controls.format.setValue(format);
    component['submit']();
    const expected = { format, startDate: '2025-01-01', endDate: '2025-01-31', type: 'EXPENSE', accountId: 'account', categoryId: 'food' };
    expect(download).toHaveBeenCalledWith(expected);
    expect(save).toHaveBeenCalledWith(response, expected);
    expect(success).toHaveBeenCalledWith('Download iniciado. Confira os downloads do navegador.');
    expect(document.activeElement).toBe(trigger);
  });

  it.each([
    [400, 'REPORT_EXPORT_LIMIT_EXCEEDED', '5.000 movimentações'],
    [400, 'XLSX_PRECISION_EXCEEDED', 'Escolha PDF ou CSV'],
    [404, '', 'não está mais disponível'], [0, '', 'Sem conexão'], [400, '', 'Revise o período'],
  ])('explains error %s %s and allows retry', (status, code, message) => {
    download.mockReturnValue(throwError(() => new HttpErrorResponse({ status, error: { code } })));
    const component = render(); component['submit'](); refresh();
    expect(fixture.nativeElement.querySelector('[role="alert"]').textContent).toContain(message);
    expect(component['submitting']()).toBe(false);
    expect(HTMLDialogElement.prototype.close).not.toHaveBeenCalled();
  });

  it('logs out and returns to login when the session expires', () => {
    download.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 401 })));
    const component = render(); component['submit']();
    expect(logout).toHaveBeenCalled(); expect(navigateByUrl).toHaveBeenCalledWith('/login');
    expect(notifyError).toHaveBeenCalledWith(expect.stringContaining('sessão expirou'));
  });

  it('closes with Escape when idle and cancels a pending request on destruction', () => {
    const component = render(); component['cancel'](new Event('cancel', { cancelable: true }));
    expect(document.activeElement).toBe(trigger);
    const pending = new Subject<HttpResponse<Blob>>(); download.mockReturnValue(pending);
    component['submit'](); fixture.destroy();
    pending.next(new HttpResponse({ body: new Blob(['file']) }));
    expect(save).not.toHaveBeenCalled();
  });
});
