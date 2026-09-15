import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { registerLocaleData } from '@angular/common';
import localePt from '@angular/common/locales/pt';
import { of, Subject, throwError } from 'rxjs';
import { vi } from 'vitest';
import { Dashboard } from './dashboard';
import { Report } from '../../core/report';
import { CategoryExpensesResponse } from '../../core/report.models';
import { TransactionApi } from '../../core/transaction';

describe('Dashboard', () => {
  let fixture: ComponentFixture<Dashboard>;
  const expenses: CategoryExpensesResponse = {
    startDate: '2026-08-01', endDate: '2026-08-31', totalExpense: 10,
    categories: [{ categoryId: 'food', categoryName: 'Alimentação', totalExpense: 10 }],
  };
  let report: {
    getSummary: ReturnType<typeof vi.fn>; getCurrentBalance: ReturnType<typeof vi.fn>;
    getCashFlow: ReturnType<typeof vi.fn>; getExpensesByCategory: ReturnType<typeof vi.fn>;
  };
  beforeEach(() => {
    registerLocaleData(localePt, 'pt-BR');
    report = {
      getSummary: vi.fn(() => of({ totalIncome: 100, totalExpense: 10, netBalance: 90 })),
      getCurrentBalance: vi.fn(() => of({ totalBalance: 90, accounts: [] })),
      getCashFlow: vi.fn(() => of({ points: [] })),
      getExpensesByCategory: vi.fn(() => of(expenses)),
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
    expect(report.getExpensesByCategory.mock.calls[0]).toEqual(report.getSummary.mock.calls[0]);
    fixture.nativeElement.querySelector('.period-switcher button').click();
    fixture.detectChanges();
    expect(report.getExpensesByCategory).toHaveBeenCalledTimes(2);
    expect(report.getExpensesByCategory.mock.calls[1]).toEqual(report.getSummary.mock.calls[1]);
    expect(report.getExpensesByCategory.mock.calls[1]).not.toEqual(report.getExpensesByCategory.mock.calls[0]);
  });
  it('keeps the dashboard available if the category report fails and retries only that report', () => {
    report.getExpensesByCategory.mockReturnValueOnce(throwError(() => new Error('offline')));
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.stats-grid')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('app-category-expenses [role="alert"]')).not.toBeNull();
    fixture.nativeElement.querySelector('app-category-expenses .retry-button').click();
    fixture.detectChanges();
    expect(report.getSummary).toHaveBeenCalledTimes(1);
    expect(report.getExpensesByCategory).toHaveBeenCalledTimes(2);
    expect(fixture.nativeElement.querySelector('.ring-svg')).not.toBeNull();
  });
  it('cancels a pending category report on month navigation and when leaving the page', () => {
    const pending = new Subject<CategoryExpensesResponse>();
    report.getExpensesByCategory.mockReturnValue(pending);
    fixture.detectChanges();
    expect(pending.observed).toBe(true);
    report.getExpensesByCategory.mockReturnValue(of(expenses));
    fixture.nativeElement.querySelector('.period-switcher button').click();
    fixture.detectChanges();
    expect(pending.observed).toBe(false);
    report.getExpensesByCategory.mockReturnValue(pending);
    fixture.nativeElement.querySelector('.period-switcher button').click();
    fixture.detectChanges();
    fixture.destroy();
    expect(pending.observed).toBe(false);
  });
});
