import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Router, provideRouter } from '@angular/router';
import { of, Subject, throwError } from 'rxjs';
import { vi } from 'vitest';
import { Auth } from '../../core/auth';
import { CategoryApi } from '../../core/categories';
import { FinancialAccountApi } from '../../core/financial-accounts';
import { ReportExportApi } from '../../core/report-export';
import { Reports } from './reports';

describe('Reports', () => {
  let fixture: ComponentFixture<Reports>;
  const getAccounts = vi.fn();
  const getCategories = vi.fn();
  const download = vi.fn();
  const save = vi.fn();
  const logout = vi.fn();

  beforeEach(() => {
    vi.resetAllMocks();
    getAccounts.mockReturnValue(of([{ id: 'account', name: 'Conta principal' }]));
    getCategories.mockReturnValue(of([{ id: 'category', name: 'Salário', transactionType: 'INCOME' }]));
    download.mockReturnValue(of(new HttpResponse({ body: new Blob(['report']) })));
    for (const method of ['showModal', 'close'] as const) {
      if (!HTMLDialogElement.prototype[method]) Object.defineProperty(HTMLDialogElement.prototype, method,
        { value() {}, configurable: true, writable: true });
    }
    vi.spyOn(HTMLDialogElement.prototype, 'showModal').mockImplementation(function(this: HTMLDialogElement) {
      this.setAttribute('open', '');
    });
    vi.spyOn(HTMLDialogElement.prototype, 'close').mockImplementation(function(this: HTMLDialogElement) {
      this.removeAttribute('open'); this.dispatchEvent(new Event('close'));
    });
    TestBed.configureTestingModule({ imports: [Reports], providers: [provideRouter([]),
      { provide: FinancialAccountApi, useValue: { getAll: getAccounts } },
      { provide: CategoryApi, useValue: { getAll: getCategories } },
      { provide: ReportExportApi, useValue: { download, save } },
      { provide: Auth, useValue: { logout } },
    ] });
  });
  afterEach(() => { fixture?.destroy(); vi.restoreAllMocks(); });
  function render() { fixture = TestBed.createComponent(Reports); fixture.detectChanges(); }
  function refresh() { fixture.changeDetectorRef.markForCheck(); fixture.detectChanges(); }

  it.each(['PDF', 'XLSX', 'CSV'])('opens and downloads %s with the selected filters', (format) => {
    render();
    const buttons: HTMLButtonElement[] = [...fixture.nativeElement.querySelectorAll('.export-button')];
    buttons[['PDF', 'XLSX', 'CSV'].indexOf(format)].click(); refresh();
    const select: HTMLSelectElement = fixture.nativeElement.querySelector('#export-format');
    expect(select.value).toBe(format);
    expect(fixture.nativeElement.querySelector('#export-account').textContent).toContain('Conta principal');
    expect(fixture.nativeElement.querySelector('#export-category').textContent).toContain('Salário');
    for (const [id, value] of [['export-start', '2026-10-01'], ['export-end', '2026-10-31'], ['export-account', 'account']]) {
      const field = fixture.nativeElement.querySelector(`#${id}`);
      field.value = value;
      field.dispatchEvent(new Event(id === 'export-account' ? 'change' : 'input'));
    }
    fixture.nativeElement.querySelector('form').dispatchEvent(new Event('submit', { cancelable: true }));
    refresh();
    expect(download).toHaveBeenCalledWith({ format, startDate: '2026-10-01', endDate: '2026-10-31', accountId: 'account' });
    expect(save).toHaveBeenCalledOnce();
    expect(fixture.nativeElement.querySelector('dialog')).toBeNull();
  });

  it('disables export while loading options', () => {
    const pending = new Subject<never[]>();
    getAccounts.mockReturnValue(pending);
    render();
    expect([...fixture.nativeElement.querySelectorAll('.export-button')].every((button: any) => button.disabled)).toBe(true);
    pending.next([]); pending.complete(); refresh();
    expect(fixture.nativeElement.querySelector('.export-button').disabled).toBe(false);
  });

  it('allows retry after an options failure and exports with empty accounts and categories', () => {
    getAccounts.mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 500 })));
    render();
    expect(fixture.nativeElement.querySelector('[role="alert"]').textContent).toContain('Não foi possível');
    expect(fixture.nativeElement.querySelector('.export-button').disabled).toBe(true);
    getAccounts.mockReturnValue(of([])); getCategories.mockReturnValue(of([]));
    fixture.nativeElement.querySelector('.retry').click(); refresh();
    expect(fixture.nativeElement.querySelector('[role="alert"]')).toBeNull();
    fixture.nativeElement.querySelector('.export-button').click(); refresh();
    expect(fixture.nativeElement.querySelector('dialog')).not.toBeNull();
  });

  it('returns to login when loading options finds an expired session', () => {
    getAccounts.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 401 })));
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    render();
    expect(logout).toHaveBeenCalledOnce();
    expect(navigate).toHaveBeenCalledWith('/login');
  });

  it('cancels pending options requests on leaving the page', () => {
    const pending = new Subject<never[]>();
    getAccounts.mockReturnValue(pending);
    render();
    expect(pending.observed).toBe(true);
    fixture.destroy();
    expect(pending.observed).toBe(false);
  });
});
