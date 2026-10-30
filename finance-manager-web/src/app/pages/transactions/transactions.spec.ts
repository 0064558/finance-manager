import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { of, Subject, throwError } from 'rxjs';
import { vi } from 'vitest';
import { Transactions } from './transactions';
import { TransactionApi } from '../../core/transaction';
import { FinancialAccountApi } from '../../core/financial-accounts';
import { CategoryApi } from '../../core/categories';

describe('Transactions empty-state actions', () => {
  let fixture: ComponentFixture<Transactions>;
  let accounts: ReturnType<typeof vi.fn>;
  let categories: ReturnType<typeof vi.fn>;
  let transactions: ReturnType<typeof vi.fn>;
  let create: ReturnType<typeof vi.fn>;
  const page = { content: [], number: 0, totalPages: 0, totalElements: 0 };
  beforeEach(() => {
    accounts = vi.fn(() => of([{ id: 'account', name: 'Conta', type: 'CHECKING', initialBalance: 0 }]));
    categories = vi.fn(() => of([{ id: 'food', name: 'Alimentação', transactionType: 'EXPENSE' }]));
    transactions = vi.fn(() => of(page));
    create = vi.fn();
    TestBed.configureTestingModule({
      imports: [Transactions],
      providers: [provideRouter([]),
        { provide: TransactionApi, useValue: { getAll: transactions, create } },
        { provide: FinancialAccountApi, useValue: { getAll: accounts } },
        { provide: CategoryApi, useValue: { getAll: categories } },
      ],
    });
  });
  const render = () => { fixture = TestBed.createComponent(Transactions); fixture.detectChanges(); };

  const fillTransaction = () => {
    fixture.componentInstance['openCreateForm']();
    fixture.componentInstance['transactionForm'].patchValue({
      accountId: 'account', categoryId: 'food', type: 'EXPENSE', amount: 10,
    });
  };

  it('quando a resposta se perde, então reutiliza a chave no reenvio da mesma operação', () => {
    render();
    fillTransaction();
    create.mockReturnValue(throwError(() => ({ status: 0 })));
    fixture.componentInstance['submitTransaction']();
    fixture.componentInstance['submitTransaction']();
    expect(create).toHaveBeenCalledTimes(2);
    expect(create.mock.calls[0][1]).toMatch(/^[a-f0-9-]{36}$/);
    expect(create.mock.calls[1]).toEqual(create.mock.calls[0]);
  });

  it('quando altera o valor após falha, então envia os novos dados com uma nova chave', () => {
    render();
    fillTransaction();
    create.mockReturnValue(throwError(() => ({ status: 0 })));
    fixture.componentInstance['submitTransaction']();
    fixture.componentInstance['transactionForm'].controls.amount.setValue(20);
    fixture.componentInstance['submitTransaction']();
    expect(create.mock.calls[1][0].amount).toBe(20);
    expect(create.mock.calls[1][1]).not.toBe(create.mock.calls[0][1]);
  });

  it('quando envia duas vezes enquanto aguarda a resposta, então chama a API apenas uma vez', () => {
    render();
    fillTransaction();
    const pending = new Subject();
    create.mockReturnValue(pending);
    fixture.componentInstance['submitTransaction']();
    fixture.componentInstance['submitTransaction']();
    expect(create).toHaveBeenCalledTimes(1);
    expect(fixture.componentInstance['isTransactionSubmitting']()).toBe(true);
    pending.error({ status: 0 });
    expect(fixture.componentInstance['isTransactionSubmitting']()).toBe(false);
  });

  it('quando conclui uma operação e inicia outra igual, então usa uma nova chave', () => {
    render();
    fillTransaction();
    create.mockReturnValue(of({ id: 'created' }));
    fixture.componentInstance['submitTransaction']();
    fillTransaction();
    fixture.componentInstance['submitTransaction']();
    expect(create).toHaveBeenCalledTimes(2);
    expect(create.mock.calls[1][1]).not.toBe(create.mock.calls[0][1]);
  });

  it('opens the creation form from an unfiltered empty list', () => {
    render();
    fixture.nativeElement.querySelector('app-empty-state button').click();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[role="dialog"]')).not.toBeNull();
  });

  it('guides users without accounts or categories to create the missing prerequisite', () => {
    accounts.mockReturnValue(of([]));
    render();
    expect(fixture.nativeElement.querySelector('app-empty-state a').getAttribute('href')).toBe('/accounts?action=create');
    accounts.mockReturnValue(of([{ id: 'account', name: 'Conta' }]));
    categories.mockReturnValue(of([]));
    fixture.componentInstance['loadTransactions']();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('app-empty-state a').getAttribute('href')).toBe('/categories?action=create');
  });

  it('clears applied filters and requests the first page without those filters', () => {
    render();
    fixture.componentInstance['filterForm'].controls.type.setValue('INCOME');
    fixture.componentInstance['applyFilters']();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('app-empty-state button').textContent).toContain('Limpar filtros');
    fixture.nativeElement.querySelector('app-empty-state button').click();
    fixture.detectChanges();
    expect(transactions.mock.calls.at(-1)?.[0]).toEqual({ page: 0, size: 10 });
    expect(fixture.componentInstance['filterForm'].controls.type.value).toBe('');
    expect(fixture.nativeElement.querySelector('app-empty-state button').textContent).toContain('Adicionar transação');
  });

  it('opens a linked expense form with its category selector enabled', () => {
    TestBed.overrideProvider(ActivatedRoute, { useValue: { snapshot: { queryParamMap: convertToParamMap({ action: 'create', type: 'EXPENSE' }) } } });
    render();
    expect(fixture.nativeElement.querySelector('[role="dialog"]')).not.toBeNull();
    expect(fixture.componentInstance['transactionForm'].controls.type.value).toBe('EXPENSE');
    expect(fixture.componentInstance['transactionForm'].controls.categoryId.enabled).toBe(true);
  });

  it('does not open an impossible form when a linked action has no accounts', () => {
    TestBed.overrideProvider(ActivatedRoute, { useValue: { snapshot: { queryParamMap: convertToParamMap({ action: 'create' }) } } });
    accounts.mockReturnValue(of([]));
    render();
    expect(fixture.nativeElement.querySelector('[role="dialog"]')).toBeNull();
    expect(fixture.nativeElement.querySelector('app-empty-state a').getAttribute('href')).toBe('/accounts?action=create');
  });

  it('keeps loading and failures separate from empty data', () => {
    const pending = new Subject<typeof page>();
    transactions.mockReturnValue(pending);
    render();
    expect(fixture.nativeElement.querySelector('app-empty-state')).toBeNull();
    vi.spyOn(console, 'error').mockImplementation(() => {});
    pending.error(new Error('offline'));
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('app-empty-state')).toBeNull();
    expect(fixture.nativeElement.querySelector('.state-card--error')).not.toBeNull();
    vi.restoreAllMocks();
  });
});
