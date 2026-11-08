import { registerLocaleData } from '@angular/common';
import localePt from '@angular/common/locales/pt';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { map, of, Subject, throwError, type Observable } from 'rxjs';
import { vi, type Mock } from 'vitest';
import { Transactions } from './transactions';
import { TransactionApi } from '../../core/transaction';
import { TransactionChatEntry, TransactionChatMessage, TransactionResponse, TransactionSuggestion, TransactionSuggestionResponse } from '../../core/transaction.models';
import { FinancialAccountApi } from '../../core/financial-accounts';
import { CategoryApi } from '../../core/categories';
import { Auth } from '../../core/auth';
import { ReportExportApi } from '../../core/report-export';
import { ValuePrivacy } from '../../core/value-privacy';
import { ReportExportDialog } from './report-export-dialog/report-export-dialog';
import { By } from '@angular/platform-browser';

registerLocaleData(localePt, 'pt-BR');

describe('Transactions', () => {
  let fixture: ComponentFixture<Transactions>;
  let accounts: ReturnType<typeof vi.fn>;
  let categories: ReturnType<typeof vi.fn>;
  let transactions: ReturnType<typeof vi.fn>;
  let create: ReturnType<typeof vi.fn>;
  let suggest: Mock<(prompt: string) => Observable<TransactionSuggestionResponse>>;
  let sendChatMessage: Mock<(message: TransactionChatMessage) => Observable<TransactionChatEntry>>;
  let getChatHistory: ReturnType<typeof vi.fn>;
  let discardChatDraft: ReturnType<typeof vi.fn>;
  const page = { content: [], number: 0, totalPages: 0, totalElements: 0 };
  beforeEach(() => {
    accounts = vi.fn(() => of([{ id: 'account', name: 'Conta', type: 'CHECKING', initialBalance: 0 }]));
    categories = vi.fn(() => of([{ id: 'food', name: 'Alimentação', transactionType: 'EXPENSE' }]));
    transactions = vi.fn(() => of(page));
    create = vi.fn();
    suggest = vi.fn();
    sendChatMessage = vi.fn(({ description, replyTo }) => suggest(description).pipe(map(response => ({
      id: crypto.randomUUID(), prompt: description, response, saved: false, replyTo,
    }))));
    getChatHistory = vi.fn(() => of([]));
    discardChatDraft = vi.fn(() => of(undefined));
    TestBed.configureTestingModule({
      imports: [Transactions],
      providers: [provideRouter([]),
        { provide: TransactionApi, useValue: {
          getAll: transactions, create, getChatHistory, sendChatMessage, discardChatDraft,
        } },
        { provide: FinancialAccountApi, useValue: { getAll: accounts } },
        { provide: CategoryApi, useValue: { getAll: categories } },
      ],
    });
  });
  const render = () => { fixture = TestBed.createComponent(Transactions); fixture.detectChanges(); };
  const element = <T extends HTMLElement = HTMLElement>(selector: string): T => fixture.nativeElement.querySelector(selector);
  const click = (selector: string) => {
    element(selector).click();
    fixture.detectChanges();
  };
  const clickChatAction = (label: string) => {
    const buttons = (fixture.nativeElement as HTMLElement).querySelectorAll<HTMLButtonElement>('#transaction-chat-panel button');
    const action = Array.from(buttons).find(button => button.textContent?.trim() === label);
    action!.click();
    fixture.detectChanges();
  };
  const input = (selector: string, value: string, event = 'input') => {
    const field = element<HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement>(selector);
    field.value = value;
    field.dispatchEvent(new Event(event, { bubbles: true }));
    fixture.detectChanges();
  };
  const sendPrompt = (prompt: string) => {
    input('#transaction-prompt', prompt);
    click('#transaction-chat-panel button[type="submit"]');
  };
  const lunchSuggestion: TransactionSuggestion = {
    status: 'SUGESTAO', descricao: 'Almoço', valor: 47.90, tipo: 'DESPESA',
    categoriaSugerida: 'Alimentação', categoriaId: 'food',
  };

  it('restaura mensagens e confirmação ao reabrir Transações e preserva o histórico entre abas', () => {
    const prompt = 'Gastei 47,90 no almoço.';
    getChatHistory.mockReturnValue(of([{ id: 'lunch', prompt, response: lunchSuggestion, saved: true }]));
    render();
    click('#transaction-chat-tab');
    expect(element('.chat-log').textContent).toContain(prompt);
    click('#transaction-list-tab');
    click('#transaction-chat-tab');
    expect(getChatHistory).toHaveBeenCalledTimes(1);
    fixture.destroy();

    render();
    click('#transaction-chat-tab');
    expect(getChatHistory).toHaveBeenCalledTimes(2);
    expect(element('.chat-log').textContent).toContain(prompt);
    expect(element('.chat-log').textContent).toContain('Transação registrada');
    expect(element('.chat-draft-review')).toBeNull();
    expect(create).not.toHaveBeenCalled();
  });

  it('só cria após confirmar os dados revisados e atualiza a lista uma única vez', async () => {
    const pending = new Subject<TransactionResponse>();
    suggest.mockReturnValue(of(lunchSuggestion));
    create.mockReturnValue(pending);
    render();
    click('#transaction-chat-tab');
    expect(element('#transaction-list-panel').hidden).toBe(true);
    sendPrompt(' Gastei 47,90 no almoço. ');
    expect(suggest).toHaveBeenCalledWith('Gastei 47,90 no almoço.');
    expect(element('#transaction-chat-panel').textContent).toContain('Almoço');
    expect(transactions).toHaveBeenCalledTimes(1);
    expect(create).not.toHaveBeenCalled();

    click('.chat-draft-review');
    await fixture.whenStable();
    expect(element<HTMLInputElement>('#transaction-amount').value).toBe('47.9');
    expect(element<HTMLSelectElement>('#transaction-category').value).toBe('food');
    expect(element('[role="dialog"] button[type="submit"]').textContent).toContain('Confirmar transação');
    click('[role="dialog"] button[type="submit"]');
    expect(create).not.toHaveBeenCalled();
    input('#transaction-account', 'account', 'change');
    input('#transaction-amount', '48.25');
    input('#transaction-date', '2026-01-02');
    input('#transaction-description', 'Almoço revisado');

    click('[role="dialog"] button[type="submit"]');
    click('[role="dialog"] button[type="submit"]');
    expect(create).toHaveBeenCalledTimes(1);
    const expected = {
      accountId: 'account', categoryId: 'food', type: 'EXPENSE',
      amount: 48.25, occurredOn: '2026-01-02', description: 'Almoço revisado',
    } as const;
    expect(create).toHaveBeenCalledWith(expected, expect.stringMatching(/^[a-f0-9-]{36}$/));
    const saved = { ...expected, id: 'saved', createdAt: '', updatedAt: '' };
    transactions.mockReturnValueOnce(of({ ...page, content: [saved], totalElements: 1, totalPages: 1 }));
    pending.next(saved);
    pending.complete();
    fixture.detectChanges();
    expect(element('.chat-draft-review')).toBeNull();
    expect(element('#transaction-chat-panel').textContent).toContain('Transação registrada');
    expect(transactions).toHaveBeenCalledTimes(2);

    click('#transaction-list-tab');
    expect(element('#transaction-list-panel').hidden).toBe(false);
    expect(element('.transactions-table tbody').textContent).toContain('Almoço revisado');
  });

  it('pede esclarecimento, mantém a lista intacta e não abre um rascunho', () => {
    const pending = new Subject<TransactionSuggestionResponse>();
    suggest.mockReturnValue(pending);
    render();
    click('#transaction-chat-tab');
    sendPrompt('   ');
    expect(suggest).not.toHaveBeenCalled();
    sendPrompt('paguei o almoço');
    click('#transaction-chat-panel button[type="submit"]');
    expect(suggest).toHaveBeenCalledTimes(1);
    expect(element('#transaction-chat-panel').textContent).toContain('Gerando sugestão');
    click('#transaction-list-tab');
    const message = 'O valor está faltando. Repita seu prompt informando também o valor.';
    pending.next({ status: 'ESCLARECIMENTO', motivo: 'VALOR_AUSENTE', mensagem: message });
    pending.complete();
    fixture.detectChanges();
    expect(element('#transaction-list-panel').hidden).toBe(false);
    expect(element('[role="dialog"]')).toBeNull();
    click('#transaction-chat-tab');
    expect(element('#transaction-chat-panel').textContent).toContain(message);
    expect(element('.chat-draft-review')).toBeNull();
    expect(transactions).toHaveBeenCalledTimes(1);
    expect(create).not.toHaveBeenCalled();
  });

  it('responde ao esclarecimento, preserva a data e substitui uma sugestão ao corrigir o valor', () => {
    const date = '2026-01-02';
    sendChatMessage
      .mockReturnValueOnce(of({
        id: 'clarification', prompt: 'Paguei o almoço ontem', saved: false,
        response: { status: 'ESCLARECIMENTO', motivo: 'VALOR_AUSENTE', mensagem: 'Qual foi o valor?' },
      }))
      .mockReturnValueOnce(of({
        id: 'lunch', replyTo: 'clarification', prompt: '47,90', saved: false,
        response: { ...lunchSuggestion, data: date },
      }))
      .mockReturnValueOnce(of({
        id: 'corrected', replyTo: 'lunch', prompt: 'Na verdade, foi 42,90', saved: false,
        response: { ...lunchSuggestion, valor: 42.90, data: date },
      }));
    render();
    click('#transaction-chat-tab');

    sendPrompt('Paguei o almoço ontem');
    expect(element('#chat-reply-context').textContent).toContain('Respondendo ao pedido de esclarecimento');
    sendPrompt('47,90');
    expect(sendChatMessage.mock.calls[1][0]).toEqual(expect.objectContaining({
      description: '47,90', replyTo: 'clarification',
    }));
    expect(element('time[datetime="2026-01-02"]').textContent).toBe('02/01/2026');

    clickChatAction('Corrigir no chat');
    sendPrompt('Na verdade, foi 42,90');
    expect(sendChatMessage.mock.calls[2][0].replyTo).toBe('lunch');
    expect(element('.chat-log').textContent).toContain('Sugestão substituída');
    expect(fixture.nativeElement.querySelectorAll('.chat-draft-review')).toHaveLength(1);
    click('.chat-draft-review');
    expect(element<HTMLInputElement>('#transaction-date').value).toBe(date);
    expect(element<HTMLInputElement>('#transaction-amount').value).toBe('42.9');
    expect(create).not.toHaveBeenCalled();
  });

  it('mantém rascunhos antigos acessíveis e permite confirmar ou descartar itens do lote de forma independente', () => {
    const busSuggestion: TransactionSuggestion = { ...lunchSuggestion, descricao: 'Ônibus', valor: 8 };
    getChatHistory.mockReturnValue(of([
      {
        id: 'batch', prompt: 'Gastei R$ 47,90 no almoço e R$ 8 no ônibus', saved: false,
        drafts: [
          { id: 'lunch-draft', response: lunchSuggestion, saved: false, discarded: false },
          { id: 'bus-draft', response: busSuggestion, saved: false, discarded: false },
        ],
      },
      { id: 'more-recent', prompt: 'Outra transação', response: busSuggestion, saved: true },
    ]));
    create.mockReturnValue(of({ id: 'created' }));
    render();
    click('#transaction-chat-tab');
    click('.chat-draft-review');
    input('#transaction-description', 'Almoço revisado');
    click('[role="dialog"] button[aria-label="Fechar formulário"]');

    click('.chat-draft:nth-child(2) .chat-draft-review');
    expect(element<HTMLTextAreaElement>('#transaction-description').value).toBe('Ônibus');
    click('[role="dialog"] button[aria-label="Fechar formulário"]');
    click('.chat-draft:nth-child(2) .chat-draft-discard');
    expect(discardChatDraft).toHaveBeenCalledExactlyOnceWith('bus-draft');
    expect(element('.chat-draft:nth-child(2)').textContent).toContain('Sugestão descartada');

    click('.chat-draft-review');
    expect(element<HTMLTextAreaElement>('#transaction-description').value).toBe('Almoço revisado');
    input('#transaction-account', 'account', 'change');
    click('[role="dialog"] button[type="submit"]');
    expect(create).toHaveBeenCalledExactlyOnceWith(expect.objectContaining({ description: 'Almoço revisado' }), 'lunch-draft');
    expect(element('.chat-draft:first-child').textContent).toContain('Transação registrada');
    expect(element('.chat-draft:nth-child(2)').textContent).toContain('Sugestão descartada');
  });

  it('deixa a categoria para o usuário quando o ID sugerido não é compatível', () => {
    suggest.mockReturnValue(of({ ...lunchSuggestion, tipo: 'RECEITA' }));
    render();
    click('#transaction-chat-tab');
    sendPrompt('Recebi 47,90.');
    click('.chat-draft-review');
    expect(element<HTMLSelectElement>('#transaction-type').value).toBe('INCOME');
    expect(element<HTMLSelectElement>('#transaction-category').value).toBe('');
    expect(create).not.toHaveBeenCalled();
  });

  it('permite reabrir e reenviar a revisão com a mesma chave quando a resposta se perde', () => {
    suggest.mockReturnValue(of(lunchSuggestion));
    create.mockReturnValue(throwError(() => ({ status: 0 })));
    render();
    click('#transaction-chat-tab');
    sendPrompt('Gastei 47,90 no almoço.');
    click('.chat-draft-review');
    input('#transaction-account', 'account', 'change');
    input('#transaction-description', 'Almoço revisado');
    click('[role="dialog"] button[type="submit"]');
    click('[role="dialog"] button[aria-label="Fechar formulário"]');
    click('#transaction-list-tab');
    click('#transaction-chat-tab');
    click('.chat-draft-review');
    expect(element<HTMLTextAreaElement>('#transaction-description').value).toBe('Almoço revisado');
    click('[role="dialog"] button[type="submit"]');
    expect(create).toHaveBeenCalledTimes(2);
    expect(create.mock.calls[1]).toEqual(create.mock.calls[0]);
  });

  it('mantém o texto para reenvio quando o provedor falha', () => {
    suggest.mockReturnValueOnce(throwError(() => ({ status: 502 }))).mockReturnValueOnce(of(lunchSuggestion));
    render();
    click('#transaction-chat-tab');
    sendPrompt('Gastei 47,90 no almoço.');
    expect(element('#transaction-chat-panel').textContent).toContain('Não foi possível gerar a sugestão.');
    expect(element<HTMLTextAreaElement>('#transaction-prompt').value).toBe('Gastei 47,90 no almoço.');
    clickChatAction('Tentar novamente');
    expect(sendChatMessage.mock.calls[1][0]).toEqual(sendChatMessage.mock.calls[0][0]);
    expect(fixture.nativeElement.querySelectorAll('.chat-message--user')).toHaveLength(1);
    expect(element('.chat-draft-review')).not.toBeNull();
    expect(create).not.toHaveBeenCalled();
  });

  it('aproveita a descrição original no formulário manual durante uma resposta demorada', () => {
    const pending = new Subject<TransactionChatEntry>();
    getChatHistory.mockReturnValue(of([{
      id: 'clarification', prompt: 'Paguei o almoço ontem', saved: false,
      response: { status: 'ESCLARECIMENTO', motivo: 'VALOR_AUSENTE', mensagem: 'Qual foi o valor?' },
    }]));
    sendChatMessage.mockReturnValue(pending);
    render();
    click('#transaction-chat-tab');
    sendPrompt('47,90');
    clickChatAction('Abrir formulário manual');
    expect(element<HTMLTextAreaElement>('#transaction-description').value).toBe('Paguei o almoço ontem');
    expect(element('[role="dialog"] button[type="submit"]').textContent).toContain('Salvar transação');
    expect(create).not.toHaveBeenCalled();
  });

  it('mantém um novo lançamento que já estava sendo escrito ao repetir uma mensagem antiga', () => {
    suggest.mockReturnValueOnce(throwError(() => ({ status: 502 }))).mockReturnValueOnce(of({
      status: 'ESCLARECIMENTO', motivo: 'VALOR_AUSENTE', mensagem: 'Qual foi o valor?',
    })).mockReturnValueOnce(of(lunchSuggestion));
    render();
    click('#transaction-chat-tab');
    sendPrompt('Paguei o almoço');
    input('#transaction-prompt', 'Paguei R$ 10 na farmácia');
    clickChatAction('Tentar novamente');

    expect(element<HTMLTextAreaElement>('#transaction-prompt').value).toBe('Paguei R$ 10 na farmácia');
    expect(element('#chat-reply-context')).toBeNull();
    click('#transaction-chat-panel button[type="submit"]');
    expect(sendChatMessage.mock.calls[2][0].replyTo).toBeUndefined();
  });

  it('impede a revisão de um valor que o navegador não representa com precisão de centavos', () => {
    suggest.mockReturnValue(of({ ...lunchSuggestion, valor: 2 ** 46 }));
    render();
    click('#transaction-chat-tab');
    sendPrompt('Gastei R$ 70.368.744.177.664 no almoço.');
    expect(element('.chat-log').textContent).toContain('Este valor excede a precisão do chat.');
    expect(element('.chat-draft-review')).toBeNull();
    expect(create).not.toHaveBeenCalled();
  });

  it('impede salvar um rascunho editado com um valor acima da precisão do chat', () => {
    suggest.mockReturnValue(of(lunchSuggestion));
    render();
    click('#transaction-chat-tab');
    sendPrompt('Gastei 47,90 no almoço.');
    click('.chat-draft-review');
    input('#transaction-account', 'account', 'change');
    input('#transaction-amount', String(2 ** 46));
    click('[role="dialog"] button[type="submit"]');
    expect(element('[role="dialog"] [role="alert"]').textContent).toContain('Este valor excede a precisão do chat.');
    expect(create).not.toHaveBeenCalled();
  });

  it('oculta o texto da conversa e o valor sugerido quando o modo de privacidade está ativo', () => {
    suggest.mockReturnValue(of(lunchSuggestion));
    render();
    click('#transaction-chat-tab');
    sendPrompt('Gastei 47,90 no almoço.');
    const privacy = TestBed.inject(ValuePrivacy);
    privacy.setHidden(true);
    try {
      fixture.detectChanges();
      expect(element('.chat-log').textContent).toContain('Mensagem oculta pelo modo de privacidade.');
      expect(element('.chat-log').textContent).not.toContain('47,90');
      expect(element('.chat-log').textContent).not.toContain('Almoço');
    } finally {
      privacy.setHidden(false);
    }
  });

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

  it('exports applied filters while ignoring unsaved edits and keeps the list unchanged', () => {
    for (const method of ['showModal', 'close'] as const) {
      if (!HTMLDialogElement.prototype[method]) Object.defineProperty(HTMLDialogElement.prototype, method,
        { value() {}, configurable: true, writable: true });
    }
    TestBed.configureTestingModule({ providers: [
      { provide: ReportExportApi, useValue: {} }, { provide: Auth, useValue: { logout: vi.fn() } },
    ] });
    vi.spyOn(HTMLDialogElement.prototype, 'showModal').mockImplementation(function(this: HTMLDialogElement) {
      this.setAttribute('open', '');
    });
    vi.spyOn(HTMLDialogElement.prototype, 'close').mockImplementation(function(this: HTMLDialogElement) {
      this.removeAttribute('open'); this.dispatchEvent(new Event('close'));
    });
    render();
    fixture.componentInstance['filterForm'].patchValue({ startDate: '2025-01-01', endDate: '2025-01-31', type: 'EXPENSE', categoryId: 'food' });
    fixture.componentInstance['applyFilters']();
    fixture.componentInstance['filterForm'].patchValue({ startDate: '2025-04-01', type: 'INCOME' });
    fixture.nativeElement.querySelector('[aria-haspopup="dialog"]').click(); fixture.detectChanges();
    const dialog = fixture.debugElement.query(By.directive(ReportExportDialog)).componentInstance as ReportExportDialog;
    expect(dialog['form'].controls.startDate.value).toBe('2025-01-01');
    expect(dialog['form'].controls.type.value).toBe('EXPENSE');
    dialog['form'].controls.startDate.setValue('2024-12-01');
    expect(fixture.componentInstance['appliedFilters']().startDate).toBe('2025-01-01');
    expect(fixture.componentInstance['filterForm'].controls.startDate.value).toBe('2025-04-01');
    expect(transactions).toHaveBeenCalledTimes(2);
    fixture.destroy(); vi.restoreAllMocks();
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
