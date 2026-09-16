import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { registerLocaleData } from '@angular/common';
import localePt from '@angular/common/locales/pt';
import { of, Subject, throwError } from 'rxjs';
import { vi } from 'vitest';
import { Dashboard } from './dashboard';
import { Report } from '../../core/report';
import { CategoryBreakdownResponse } from '../../core/report.models';
import { TransactionApi } from '../../core/transaction';

describe('Dashboard', () => {
  let fixture: ComponentFixture<Dashboard>;
  const expenses: CategoryBreakdownResponse = {
    startDate: '2026-08-01', endDate: '2026-08-31', type: 'EXPENSE', totalAmount: 10,
    categories: [{ categoryId: 'food', categoryName: 'Alimentação', amount: 10 }],
  };
  let report: {
    getSummary: ReturnType<typeof vi.fn>; getCurrentBalance: ReturnType<typeof vi.fn>;
    getCashFlow: ReturnType<typeof vi.fn>; getCategoryBreakdown: ReturnType<typeof vi.fn>;
  };
  beforeEach(() => {
    registerLocaleData(localePt, 'pt-BR');
    report = {
      getSummary: vi.fn(() => of({ totalIncome: 100, totalExpense: 10, netBalance: 90 })),
      getCurrentBalance: vi.fn(() => of({ totalBalance: 90, accounts: [] })),
      getCashFlow: vi.fn(() => of({ points: [] })),
      getCategoryBreakdown: vi.fn(() => of(expenses)),
    };
    TestBed.configureTestingModule({
      imports: [Dashboard],
      providers: [
        provideRouter([]),
        { provide: Report, useValue: report },
        { provide: TransactionApi, useValue: { getRecent: () => of({ content: [], totalElements: 0 }) } },
      ],
    });
    fixture = TestBed.createComponent(Dashboard);
  });
  it('loads the expense breakdown for the same period as the dashboard and updates on month navigation', () => {
    fixture.detectChanges();
    expect(report.getCategoryBreakdown.mock.calls[0].slice(0, 2)).toEqual(report.getSummary.mock.calls[0]);
    fixture.nativeElement.querySelector('.period-switcher button').click();
    fixture.detectChanges();
    expect(report.getCategoryBreakdown).toHaveBeenCalledTimes(2);
    expect(report.getCategoryBreakdown.mock.calls[1].slice(0, 2)).toEqual(report.getSummary.mock.calls[1]);
    expect(report.getCategoryBreakdown.mock.calls[1]).not.toEqual(report.getCategoryBreakdown.mock.calls[0]);
  });
  it('keeps the dashboard available if the category report fails and retries only that report', () => {
    report.getCategoryBreakdown.mockReturnValueOnce(throwError(() => new Error('offline')));
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.stats-grid')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('app-category-expenses [role="alert"]')).not.toBeNull();
    fixture.nativeElement.querySelector('app-category-expenses .retry-button').click();
    fixture.detectChanges();
    expect(report.getSummary).toHaveBeenCalledTimes(1);
    expect(report.getCategoryBreakdown).toHaveBeenCalledTimes(2);
    expect(fixture.nativeElement.querySelector('.ring-svg')).not.toBeNull();
  });
  it('caches both views and keeps income selected when the month changes', () => {
    fixture.detectChanges();
    const select = (index: number) => {
      fixture.nativeElement.querySelectorAll('.category-view-switcher button')[index].click();
      fixture.detectChanges();
    };
    select(1);
    expect(report.getCategoryBreakdown.mock.calls[1][2]).toBe('INCOME');
    select(0);
    select(1);
    expect(report.getCategoryBreakdown).toHaveBeenCalledTimes(2);
    fixture.nativeElement.querySelector('.period-switcher button').click();
    fixture.detectChanges();
    expect(report.getCategoryBreakdown).toHaveBeenCalledTimes(3);
    expect(report.getCategoryBreakdown.mock.calls[2][2]).toBe('INCOME');
    expect(report.getCategoryBreakdown.mock.calls[2].slice(0, 2)).toEqual(report.getSummary.mock.calls[1]);
    expect(fixture.nativeElement.querySelector('app-category-expenses h2').textContent).toBe('Receitas por categoria');
  });

  it('retries an income error without changing expenses or reloading the dashboard', () => {
    fixture.detectChanges();
    report.getCategoryBreakdown.mockReturnValueOnce(throwError(() => new Error('offline')));
    fixture.nativeElement.querySelectorAll('.category-view-switcher button')[1].click();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('app-category-expenses [role="alert"]').textContent).toContain('receitas');
    fixture.nativeElement.querySelector('.retry-button').click();
    fixture.detectChanges();
    expect(report.getCategoryBreakdown.mock.calls[2][2]).toBe('INCOME');
    expect(report.getSummary).toHaveBeenCalledTimes(1);
    fixture.nativeElement.querySelectorAll('.category-view-switcher button')[0].click();
    fixture.detectChanges();
    expect(report.getCategoryBreakdown).toHaveBeenCalledTimes(3);
    expect(fixture.nativeElement.querySelector('app-category-expenses [role="alert"]')).toBeNull();
  });

  it('cancels a pending response on view switching so the previous view cannot overwrite the selection', () => {
    const pending = new Subject<CategoryBreakdownResponse>();
    report.getCategoryBreakdown.mockReturnValueOnce(pending);
    fixture.detectChanges();
    fixture.nativeElement.querySelectorAll('.category-view-switcher button')[1].click();
    fixture.detectChanges();
    expect(pending.observed).toBe(false);
    expect(fixture.nativeElement.querySelector('.ring-svg')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('app-category-expenses h2').textContent).toBe('Receitas por categoria');
  });

  it('cancels a pending category report on month navigation and when leaving the page', () => {
    const pending = new Subject<CategoryBreakdownResponse>();
    report.getCategoryBreakdown.mockReturnValue(pending);
    fixture.detectChanges();
    expect(pending.observed).toBe(true);
    report.getCategoryBreakdown.mockReturnValue(of(expenses));
    fixture.nativeElement.querySelector('.period-switcher button').click();
    fixture.detectChanges();
    expect(pending.observed).toBe(false);
    report.getCategoryBreakdown.mockReturnValue(pending);
    fixture.nativeElement.querySelector('.period-switcher button').click();
    fixture.detectChanges();
    fixture.destroy();
    expect(pending.observed).toBe(false);
  });
});
