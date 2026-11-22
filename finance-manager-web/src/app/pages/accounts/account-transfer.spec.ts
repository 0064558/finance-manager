import { registerLocaleData } from '@angular/common';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import localePt from '@angular/common/locales/pt';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Accounts } from './accounts';

registerLocaleData(localePt, 'pt-BR');

describe('Transferências entre contas', () => {
  let fixture: ComponentFixture<Accounts>;
  let http: HttpTestingController;
  const transfers = '/api/v1/transfers';
  const accounts = [
    { id: 'source', name: 'Conta corrente', type: 'CHECKING', initialBalance: 100 },
    { id: 'destination', name: 'Poupança', type: 'SAVINGS', initialBalance: 20 },
  ];

  beforeEach(() => {
    if (!HTMLDialogElement.prototype.showModal) {
      Object.defineProperty(HTMLDialogElement.prototype, 'showModal', {
        configurable: true,
        value(this: HTMLDialogElement) { this.open = true; },
      });
      Object.defineProperty(HTMLDialogElement.prototype, 'close', {
        configurable: true,
        value(this: HTMLDialogElement) { this.open = false; },
      });
    }
    TestBed.configureTestingModule({
      imports: [Accounts],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(Accounts);
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

  function openTransfer(visibleAccounts = accounts): void {
    flushBalances(100, 20, visibleAccounts);
    http.expectOne(transfers + '?page=0').flush({ content: [], empty: true });
    fixture.detectChanges();
    field<HTMLButtonElement>('.page-actions .secondary-button').click();
    fixture.detectChanges();
  }

  function field<T extends HTMLElement = HTMLElement>(selector: string): T {
    return (fixture.nativeElement as HTMLElement).querySelector<T>(selector)!;
  }

  function flushBalances(source: number, destination: number, visibleAccounts = accounts): void {
    http.expectOne('/api/v1/financial-accounts').flush(visibleAccounts);
    http.expectOne('/api/v1/reports/balances').flush({
      totalBalance: source + destination,
      accounts: [
        { accountId: 'source', balance: source },
        { accountId: 'destination', balance: destination },
      ],
    });
  }

  function fill(selector: string, value: string, event: 'input' | 'change'): void {
    const control = field<HTMLInputElement>(selector);
    control.value = value;
    control.dispatchEvent(new Event(event, { bubbles: true }));
    fixture.detectChanges();
  }

  function submit(): void {
    field<HTMLButtonElement>('dialog button[type="submit"]').click();
    fixture.detectChanges();
  }

  it('impede uma transferência para a própria origem e exige valor com até duas casas decimais', () => {
    openTransfer();
    fill('#transfer-source', 'source', 'change');
    fill('#transfer-destination', 'destination', 'change');
    fill('#transfer-amount', '0,001', 'input');
    submit();
    expect(field('.transaction-form__error').textContent).toContain('duas casas');
    fill('#transfer-destination', 'source', 'change');
    fill('#transfer-amount', '25,50', 'input');
    submit();
    expect(field('dialog [role="alert"]').textContent).toContain('contas diferentes');
    http.expectNone(transfers);
  });

  it('reutiliza a chave após erro e atualiza os dois saldos e o histórico após confirmação', () => {
    openTransfer();
    fill('#transfer-source', 'source', 'change');
    fill('#transfer-destination', 'destination', 'change');
    fill('#transfer-amount', '25,50', 'input');
    submit();
    const first = http.expectOne(transfers);
    expect(first.request.method).toBe('POST');
    expect(first.request.body).toMatchObject({
      sourceAccountId: 'source', destinationAccountId: 'destination', amount: '25.50',
    });
    expect(first.request.headers.get('Idempotency-Key')).toBeTruthy();
    first.error(new ProgressEvent('error'));
    fixture.detectChanges();
    expect(field('dialog [role="alert"]').textContent).toContain('tente novamente');
    expect(field('.transfer-history').textContent).toContain('Nenhuma transferência');
    http.expectNone('/api/v1/reports/balances');

    submit();
    const retry = http.expectOne(transfers);
    expect(retry.request.headers.get('Idempotency-Key')).toBe(first.request.headers.get('Idempotency-Key'));
    const transfer = { ...retry.request.body, id: 'transfer', amount: 25.50, description: 'Reserva' };
    retry.flush(transfer, { status: 201, statusText: 'Created' });
    flushBalances(74.50, 45.50);
    http.expectOne(transfers + '?page=0').flush({ content: [transfer], empty: false, totalPages: 1 });
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('dialog')).toBeNull();
    const amounts = (fixture.nativeElement as HTMLElement).querySelectorAll('.balance-block .sr-only');
    expect(Array.from(amounts, (amount) => amount.textContent)).toEqual([
      expect.stringContaining('74,50'), expect.stringContaining('45,50'),
    ]);
    expect(field('.overview-item--balance .sr-only').textContent).toContain('120,00');
    expect(field('.transfer-history').textContent).toContain('Conta corrente → Poupança');
  });

  it('distingue contas de mesmo nome com os mesmos rótulos no cartão e no formulário', () => {
    openTransfer(accounts.map((account) => ({ ...account, name: 'Reserva' })));
    const labels = ['1. Reserva', '2. Reserva'];
    const options = field<HTMLSelectElement>('#transfer-source').options;
    const cards = (fixture.nativeElement as HTMLElement).querySelectorAll('.account-heading h2');
    expect(Array.from(options, (option) => option.text).slice(1)).toEqual(labels);
    expect(Array.from(cards, (card) => card.textContent?.trim())).toEqual(labels);
  });
});
