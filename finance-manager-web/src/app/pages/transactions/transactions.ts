import { EmptyState } from '../../shared/empty-state/empty-state';
import { PrivateCurrency } from '../../shared/private-currency/private-currency';
import { ReportExportDialog } from './report-export-dialog/report-export-dialog';
import {
  afterNextRender, Component, computed, DestroyRef, ElementRef, inject, Injector, OnInit, signal, viewChild,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute } from '@angular/router';
import { finalize, forkJoin, timeout } from 'rxjs';

import { CategoryApi } from '../../core/categories';
import { FinancialAccountApi } from '../../core/financial-accounts';
import { TransactionApi } from '../../core/transaction';
import { Notifications } from '../../core/notifications';
import { ValuePrivacy } from '../../core/value-privacy';

import { Category } from '../../core/category.models';
import { FinancialAccount } from '../../core/financial-account.models';
import {
  CreateTransactionRequest, TransactionChatDraft, TransactionChatEntry, TransactionChatMessage,
  TransactionFilters, TransactionResponse, TransactionType,
} from '../../core/transaction.models';
import { DatePipe } from '@angular/common';
import {
  AbstractControl,
  FormBuilder,
  ReactiveFormsModule,
  ValidationErrors,
  Validators,
} from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import {
  LucideChevronLeft,
  LucideChevronRight,
  LucideMessageCircle,
  LucidePencil,
  LucidePlus,
  LucideSlidersHorizontal,
  LucideTrash2,
  LucideDownload,
} from '@lucide/angular';

// Representa uma transação financeira, incluindo informações como conta, categoria, tipo, valor, data de ocorrência e descrição.
interface TransactionViewModel extends TransactionResponse {
  accountName: string;
  categoryName: string;
}

interface ChatExchange extends Omit<TransactionChatEntry, 'drafts'> {
  drafts: TransactionChatDraft[];
  request?: TransactionChatMessage;
  error?: string;
  retryable?: boolean;
}

// At this magnitude, adjacent JavaScript numbers are more than one cent apart.
const chatAmountPrecisionLimit = 2 ** 46;
const chatAmountPrecisionMessage = 'Este valor excede a precisão do chat. Confira o valor original antes de registrar manualmente.';
const chatRequestTimeoutMs = 30_000;

function dateRangeValidator(
  control: AbstractControl,
): ValidationErrors | null {
  const startDate = control.get('startDate')?.value;
  const endDate = control.get('endDate')?.value;

  if (!startDate || !endDate) {
    return null; // Se qualquer uma das datas estiver vazia, não há erro de validação.
  }

  if (startDate <= endDate) {
    return null; // Se a data de início for menor ou igual à data de término, não há erro de validação.
  }

  return { invalidDateRange: true }; // Retorna um erro de validação.

}

// Valida se a data fornecida não é uma data futura, retornando um erro de validação se for o caso.
function notFutureDate(
  control: AbstractControl,
): ValidationErrors | null {
  // Obtém o valor do controle de formulário, que é esperado ser uma string representando uma data no formato "YYYY-MM-DD".
  const value = control.value;

  // Se o valor estiver vazio, não há erro de validação.
  if (!value) {
    return null;
  }

  // Cria uma data representando a data atual para comparação com a data fornecida.
  const today = new Date();

  // Compara a data fornecida com a data atual. Se a data fornecida for menor ou igual à data atual, 
  // não há erro de validação. Caso contrário, retorna um erro de validação indicando que a data é futura.
  if (value <= formatLocalDate(today)) {
    return null; // Se a data for menor ou igual à data atual, não há erro de validação.
  }

  return { futureDate: true }; // Retorna um erro de validação.
}

// Formata uma data no formato "YYYY-MM-DD" para ser usada em campos de entrada de data.
function formatLocalDate(date: Date): string {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');

  return `${year}-${month}-${day}`;
}

@Component({
  imports: [
    EmptyState,
    ReportExportDialog, LucideDownload,
    PrivateCurrency, DatePipe, ReactiveFormsModule,
    LucideChevronLeft, LucideChevronRight, LucideMessageCircle,
    LucidePencil, LucidePlus, LucideSlidersHorizontal, LucideTrash2,
  ],
  selector: 'app-transactions',
  styleUrls: ['./transactions.css', './transaction-chat.css'],
  templateUrl: './transactions.html',
})
export class Transactions implements OnInit {
  private pendingCreation: { body: string; key: string } | null = null;
  private readonly reviewDrafts = new Map<string, ReturnType<Transactions['transactionForm']['getRawValue']>>();
  private readonly activeChatDraftId = signal<string | null>(null);
  private formTrigger: HTMLButtonElement | null = null;
  private readonly destroyRef = inject(DestroyRef);
  private readonly injector = inject(Injector);
  private readonly transactionAccount = viewChild<ElementRef<HTMLSelectElement>>('transactionAccount');
  private readonly chatPrompt = viewChild<ElementRef<HTMLTextAreaElement>>('chatPrompt');
  private readonly chatLog = viewChild<ElementRef<HTMLElement>>('chatLog');
  private readonly route = inject(ActivatedRoute, { optional: true });
  private pendingCreate = this.route?.snapshot.queryParamMap.get('action') === 'create';

  private readonly transactionApi = inject(TransactionApi);
  private readonly notifications = inject(Notifications);
  private readonly accountApi = inject(FinancialAccountApi);
  private readonly categoryApi = inject(CategoryApi);


  // Sinais para armazenar o estado das transações, contas financeiras, categorias, carregamento, mensagens de erro e informações de paginação.
  protected readonly transactions = signal<TransactionViewModel[]>([]);
  protected readonly accounts = signal<FinancialAccount[]>([]);
  protected readonly categories = signal<Category[]>([]);
  protected readonly isLoading = signal(false);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly currentPage = signal(0);
  protected readonly totalPages = signal(0);
  protected readonly totalElements = signal(0);

  // FormBuilder é injetado para criar formulários reativos, permitindo a criação e validação de formulários de maneira mais fácil e estruturada.
  protected readonly formBuilder = inject(FormBuilder);

  protected readonly valuePrivacy = inject(ValuePrivacy);
  protected readonly transactionTab = signal<'list' | 'chat'>('list');
  protected readonly chatExchanges = signal<ChatExchange[]>([]);
  protected readonly chatHistoryReady = signal(false);
  protected readonly isLoadingChatHistory = signal(false);
  protected readonly chatHistoryFailed = signal(false);
  protected readonly isRequestingSuggestion = signal(false);
  protected readonly discardingDraftId = signal<string | null>(null);
  protected readonly isSuggestionReview = computed(() => this.activeChatDraftId() !== null);
  protected readonly chatReplyTo = signal<ChatExchange | null>(null);
  protected readonly chatAmountPrecisionLimit = chatAmountPrecisionLimit;
  protected readonly chatAmountPrecisionMessage = chatAmountPrecisionMessage;
  protected readonly chatBusy = computed(() =>
    this.isLoadingChatHistory() || this.isRequestingSuggestion() || this.discardingDraftId() !== null
    || this.isTransactionFormOpen(),
  );
  protected readonly chatPromptControl = this.formBuilder.nonNullable.control('', [
    Validators.required, Validators.maxLength(500), Validators.pattern(/\S/),
  ]);

  // Sinal para armazenar os filtros aplicados, permitindo que o componente rastreie e aplique filtros de transações com base nos valores do formulário de filtro.
  protected readonly appliedFilters = signal<TransactionFilters>({});
  protected readonly isReportExportOpen = signal(false);
  protected readonly hasAppliedFilters = computed(() => Object.values(this.appliedFilters()).some(Boolean));

  // Sinais para controlar o estado do formulário de transação, incluindo se o formulário está aberto, se está sendo enviado e mensagens de erro relacionadas ao envio do formulário.
  protected readonly isTransactionFormOpen = signal(false);
  protected readonly isTransactionSubmitting = signal(false);
  protected readonly transactionFormError = signal<string | null>(null);

  // Sinal para armazenar a transação selecionada, permitindo que o componente rastreie qual transação está sendo visualizada ou editada.
  protected readonly selectedTransaction = signal<TransactionViewModel | null>(null);

  // Sinais para controlar o estado da exclusão de transações, incluindo a transação pendente de exclusão, 
  // se a exclusão está em andamento e mensagens de erro relacionadas à exclusão.
  protected readonly transactionPendingDeletion = signal<TransactionViewModel | null>(null);
  protected readonly isDeleting = signal(false);
  protected readonly deleteError = signal<string | null>(null);

  // Cria um formulário reativo para filtrar transações com campos para data de início, data de término, 
  // tipo de transação, ID da conta e ID da categoria. E valida o intervalo de datas usando a função dateRangeValidator.
  protected readonly filterForm = this.formBuilder.nonNullable.group({
    startDate: [''],
    endDate: [''],
    type: this.formBuilder.nonNullable.control<TransactionType | ''>(''),
    accountId: [''],
    categoryId: [''],
  }, {
    validators: [dateRangeValidator],
  });

  // Cria um formulário reativo para criar ou editar transações com campos para ID da conta, 
  // ID da categoria, tipo de transação, valor, data de ocorrência e descrição.
  // Incluindo validações para cada campo, como obrigatoriedade, valores mínimos e máximos, padrões de formato e limites de comprimento.
  protected readonly transactionForm = this.formBuilder.nonNullable.group({
    accountId: ['',
      [Validators.required]
    ],
    categoryId: this.formBuilder.nonNullable.control(
      { value: '', disabled: true },
      [Validators.required],
    ),
    type: this.formBuilder.nonNullable.control<TransactionType | ''>('', [Validators.required]),
    amount: this.formBuilder.control<number | null>(null, [Validators.required,
    Validators.min(0.01), Validators.pattern(/^\d{1,17}(\.\d{1,2})?$/)]),
    occurredOn: [formatLocalDate(new Date()),
    [Validators.required, notFutureDate]
    ],
    description: ['',
      [Validators.maxLength(255)]
    ],
  });

  // ngOnInit é um método do ciclo de vida do Angular que é chamado após a criação do componente.
  // Neste caso, ele é usado para carregar as transações financeiras, contas e categorias quando o componente é inicializado.
  ngOnInit(): void {
    this.loadTransactions();
  }

  // Carrega as transações financeiras, contas e categorias com base nos filtros aplicados, atualizando os sinais correspondentes e lidando com erros de carregamento.
  protected loadTransactions(): void {

    this.isLoading.set(true);
    this.errorMessage.set(null);

    const filters = this.appliedFilters();

    // forkJoin é usado para criar um único observable que aguarda a conclusão de várias requisições HTTP simultâneas, 
    // que emite um objeto contendo os resultados de todas as requisições quando todas forem concluídas.
    // "$" indica que a variável é um Observable, e o forkJoin aguarda todos os Observables completarem antes de emitir os resultados.
    const request$ = forkJoin({
      accounts: this.accountApi.getAll(),
      categories: this.categoryApi.getAll(),
      transactionsPage: this.transactionApi.getAll({
        ...filters,
        page: this.currentPage(),
        size: 10
      }),
    });

    // Assina o Observable resultante para processar os dados recebidos.
    request$
      // Pipe serve para encadear operadores de transformação e manipulação de dados em Observables, 
      // permitindo processar os resultados antes de assiná-los.
      .pipe(
        finalize(() => this.isLoading.set(false)) // O operador finalize é usado para executar uma ação (neste caso, definir isLoading como false) quando o Observable é concluído, independentemente de ter sido bem-sucedido ou não.
      )
      .subscribe({
        next: ({ accounts, categories, transactionsPage }) => {
          this.accounts.set(accounts);
          this.categories.set(categories);

          this.currentPage.set(transactionsPage.number);
          this.totalPages.set(transactionsPage.totalPages);
          this.totalElements.set(transactionsPage.totalElements);

          // criar um map de contas usando id como chave e name como valor
          // O operador map é usado para transformar um array em outro array, aplicando uma função a cada elemento do array original.
          const accountMap = accounts.map(
            (account): [string, string] => [
              account.id,
              account.name,
            ],
          );
          const accountNameById = new Map<string, string>(accountMap);

          // criar um map de categorias usando id como chave e name como valor
          const categoryMap = categories.map(
            (category): [string, string] => [
              category.id,
              category.name,
            ],
          );
          const categoryNameById = new Map<string, string>(categoryMap);

          // Mapeia as transações para incluir os nomes das contas e categorias correspondentes.
          const transactionsWithNames: TransactionViewModel[] = transactionsPage.content.map(
            (transaction) => ({
              // Spread operator é usado para copiar todas as propriedades do objeto transaction para o novo objeto, e em seguida adiciona as propriedades accountName e categoryName.
              ...transaction,
              accountName: accountNameById.get(transaction.accountId) ?? 'Conta desconhecida',
              categoryName: categoryNameById.get(transaction.categoryId) ?? 'Categoria desconhecida',
            }),
          );

          this.transactions.set(transactionsWithNames);
          if (this.pendingCreate) {
            this.pendingCreate = false;
            if (accounts.length > 0 && categories.length > 0) {
              this.openCreateForm();
              const type = this.route?.snapshot.queryParamMap.get('type');
              if (type === 'INCOME' || type === 'EXPENSE') {
                this.transactionForm.controls.type.setValue(type);
                this.onTransactionTypeChange();
              }
            }
          }
        },
        error: (error) => {
          console.error('Error loading data:', error);
          this.errorMessage.set('Erro ao carregar os dados. Por favor, tente novamente mais tarde.');
        }
      });
  }

  // Navega para a página anterior de transações, se houver uma página anterior disponível. Se a página atual for a primeira, a função não faz nada.
  protected goToPreviousPage(): void {
    if (this.currentPage() === 0) {
      return;
    }

    this.currentPage.set(this.currentPage() - 1);
    this.loadTransactions();
  }

  // Navega para a próxima página de transações, se houver uma próxima página disponível. Se a página atual for a última, a função não faz nada.
  protected goToNextPage(): void {
    if (this.currentPage() >= this.totalPages() - 1) {
      return;
    }
    this.currentPage.set(this.currentPage() + 1);
    this.loadTransactions();
  }

  // Aplica os filtros definidos no formulário de filtro e recarrega as transações com base nos filtros aplicados.
  protected applyFilters(): void {
    if (this.filterForm.invalid) {
      // Se o formulário de filtro for inválido, marca todos os campos como "tocados" para exibir mensagens de erro de validação.
      this.filterForm.markAllAsTouched();
      return;
    }

    // Obtém os valores do formulário de filtro.
    const formValue = this.filterForm.getRawValue();

    // Cria um objeto TransactionFilters com os filtros aplicados, usando os valores do formulário de filtro.
    const filters: TransactionFilters = {
      startDate: formValue.startDate || undefined,
      endDate: formValue.endDate || undefined,
      type: formValue.type || undefined,
      accountId: formValue.accountId || undefined,
      categoryId: formValue.categoryId || undefined,
    };

    // Atualiza o sinal appliedFilters com os filtros aplicados, permitindo que o componente rastreie e aplique os filtros de transações.
    this.appliedFilters.set(filters);

    this.currentPage.set(0); // Reseta para a primeira página ao aplicar filtros
    this.loadTransactions();
  }

  // Retorna uma lista de categorias disponíveis com base no tipo de transação selecionado no formulário de filtro.
  protected availableFilterCategories(): Category[] {
    // Obtém o tipo de transação selecionado no formulário de filtro.
    const selectedType = this.filterForm.controls.type.value;

    // Se nenhum tipo de transação estiver selecionado (string vazia), retorna todas as categorias disponíveis. Caso contrário, filtra as categorias com base no tipo de transação selecionado.
    if (selectedType === '') {
      return this.categories();
    }

    // Filtra as categorias disponíveis com base no tipo de transação selecionado, retornando apenas as categorias que correspondem ao tipo de transação selecionado.
    return this.categories().filter(category => category.transactionType === selectedType);
  }

  // Reseta a categoria selecionada no formulário de filtro, definindo o valor do campo categoryId como uma string vazia. 
  // Isso é útil quando o tipo de transação é alterado, garantindo que a categoria selecionada seja compatível com o novo tipo de transação.
  protected resetCategory(): void {
    this.filterForm.controls.categoryId.setValue(''); // Reseta a categoria selecionada
  }

  // Limpa os filtros aplicados no formulário de filtro, redefinindo todos os campos para seus valores padrão (vazios) e recarregando as transações sem filtros.
  protected clearFilters(): void {
    this.filterForm.reset({
      startDate: '',
      endDate: '',
      type: '',
      accountId: '',
      categoryId: '',
    });
    this.appliedFilters.set({});
    this.currentPage.set(0);
    this.loadTransactions();
  }

  protected selectTransactionTab(tab: 'list' | 'chat'): void {
    this.transactionTab.set(tab);
    if (tab === 'chat') {
      this.loadChatHistory();
      this.scrollChatToLatest();
    }
  }

  protected loadChatHistory(refresh = false): void {
    if (this.isLoadingChatHistory() || (this.chatHistoryReady() && !refresh)) return;

    this.isLoadingChatHistory.set(true);
    this.chatHistoryFailed.set(false);
    this.transactionApi.getChatHistory()
      .pipe(takeUntilDestroyed(this.destroyRef), finalize(() => this.isLoadingChatHistory.set(false)))
      .subscribe({
        next: entries => {
          const history = entries.map(entry => this.toChatExchange(entry));
          const loadedIds = new Set(history.map(entry => entry.id));
          const localFailures = this.chatExchanges().filter(entry => entry.request && !loadedIds.has(entry.id));
          this.chatExchanges.set([...history, ...localFailures]);
          const replyTo = this.chatReplyTo();
          if (refresh && replyTo) {
            const updated = history.find(entry => entry.id === replyTo.id);
            this.chatReplyTo.set(updated && this.canReplyTo(updated) ? updated : null);
          } else if (!refresh) {
            this.offerClarificationReply(history.at(-1));
          }
          this.chatHistoryReady.set(true);
          if (!refresh) this.scrollChatToLatest();
        },
        error: () => this.chatHistoryFailed.set(true),
      });
  }

  protected onTabKeyDown(event: KeyboardEvent, listTab: HTMLButtonElement, chatTab: HTMLButtonElement): void {
    if (!['ArrowLeft', 'ArrowRight', 'Home', 'End'].includes(event.key)) return;

    event.preventDefault();
    const showChat = event.key === 'End' || (event.key !== 'Home' && this.transactionTab() === 'list');
    this.selectTransactionTab(showChat ? 'chat' : 'list');
    (showChat ? chatTab : listTab).focus();
  }

  protected requestSuggestion(): void {
    if (!this.chatHistoryReady() || this.chatPromptControl.invalid || this.chatBusy()) return;

    const prompt = this.chatPromptControl.value.trim();
    const replyTo = this.chatReplyTo()?.id;
    const latest = this.chatExchanges().at(-1);
    if (latest?.retryable && latest.request?.description === prompt && latest.request.replyTo === replyTo) {
      this.retryChatMessage(latest);
      return;
    }

    const request: TransactionChatMessage = {
      description: prompt,
      requestId: crypto.randomUUID(),
      replyTo,
      timeZone: Intl.DateTimeFormat().resolvedOptions().timeZone,
    };
    const exchange: ChatExchange = { id: request.requestId, prompt, request, replyTo, drafts: [], saved: false };
    this.chatExchanges.update(exchanges => [...exchanges, exchange]);
    this.scrollChatToLatest();
    this.sendChatRequest(request);
  }

  protected retryChatMessage(exchange: ChatExchange): void {
    if (!exchange.request || !exchange.retryable || this.chatBusy()) return;
    this.updateChatExchange(exchange.id, { error: undefined, retryable: false });
    this.sendChatRequest(exchange.request);
  }

  private sendChatRequest(request: TransactionChatMessage): void {
    this.isRequestingSuggestion.set(true);
    this.transactionApi.sendChatMessage(request)
      .pipe(
        timeout(chatRequestTimeoutMs),
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.isRequestingSuggestion.set(false)),
      )
      .subscribe({
        next: entry => {
          const exchange = this.toChatExchange(entry);
          this.chatExchanges.update(exchanges => [
            ...exchanges
              .filter(item => item.id !== request.requestId && item.id !== entry.id)
              .map(item => item.id === request.replyTo ? { ...item, superseded: true } : item),
            exchange,
          ]);
          if (request.replyTo) {
            this.reviewDrafts.delete(request.replyTo);
          }
          if (this.chatReplyTo()?.id === request.replyTo) {
            if (this.chatPromptControl.value.trim() === request.description) this.chatPromptControl.reset('');
            if (this.chatPromptControl.value.trim()) this.chatReplyTo.set(null);
            else this.offerClarificationReply(exchange);
          }
          this.scrollChatToLatest();
          this.focusChatPrompt();
        },
        error: (error: unknown) => this.handleChatRequestError(request, error),
      });
  }

  private handleChatRequestError(request: TransactionChatMessage, error: unknown): void {
    const http = error instanceof HttpErrorResponse ? error : null;
    const code = http?.error?.code;
    if (code === 'CHAT_DRAFT_UNAVAILABLE' || code === 'CHAT_ENTRY_NOT_FOUND') {
      this.updateChatExchange(request.requestId, { error: 'Esta conversa já mudou. Confira o histórico atualizado.' });
      this.loadChatHistory(true);
    } else if (code === 'CHAT_CONTEXT_LIMIT') {
      this.updateChatExchange(request.requestId, {
        error: 'Esta conversa atingiu o limite de respostas. Inicie um novo lançamento com os dados completos.',
      });
      this.chatReplyTo.set(null);
    } else if (http?.status === 503 || http?.status === 404 || http?.status === 405) {
      this.updateChatExchange(request.requestId, {
        error: 'O chat com IA está indisponível. Tente novamente ou use o formulário manual.',
        retryable: true,
      });
    } else {
      this.updateChatExchange(request.requestId, {
        error: 'Não foi possível gerar a sugestão. Tente novamente ou use o formulário manual.',
        retryable: true,
      });
    }
  }

  private toChatExchange(entry: TransactionChatEntry): ChatExchange {
    const drafts = entry.drafts ?? (entry.response?.status === 'SUGESTAO'
      ? [{ id: entry.id, response: entry.response, saved: entry.saved, discarded: false }]
      : []);
    return { ...entry, drafts };
  }

  private updateChatExchange(id: string, patch: Partial<ChatExchange>): void {
    this.chatExchanges.update(exchanges =>
      exchanges.map(exchange => exchange.id === id ? { ...exchange, ...patch } : exchange),
    );
  }

  private updateChatDraft(id: string, patch: Partial<TransactionChatDraft>): void {
    this.chatExchanges.update(exchanges => exchanges.map(exchange => {
      if (!exchange.drafts.some(draft => draft.id === id)) return exchange;
      return { ...exchange, drafts: exchange.drafts.map(draft => draft.id === id ? { ...draft, ...patch } : draft) };
    }));
  }

  protected canReplyTo(exchange: ChatExchange): boolean {
    if (exchange.superseded || exchange.request) return false;
    if (exchange.response?.status === 'ESCLARECIMENTO') {
      return ['VALOR_AUSENTE', 'DADOS_AMBIGUOS', 'DATA_AMBIGUA'].includes(exchange.response.motivo);
    }
    return exchange.drafts.length === 1 && !exchange.drafts[0].saved && !exchange.drafts[0].discarded;
  }

  private offerClarificationReply(exchange?: ChatExchange): void {
    this.chatReplyTo.set(
      exchange?.response?.status === 'ESCLARECIMENTO' && this.canReplyTo(exchange) ? exchange : null,
    );
  }

  protected replyToChatExchange(exchange: ChatExchange | null): void {
    if (this.chatBusy() || (exchange && !this.canReplyTo(exchange))) return;
    this.chatReplyTo.set(exchange);
    this.focusChatPrompt();
  }

  protected discardChatDraft(exchange: ChatExchange, draft: TransactionChatDraft): void {
    if (this.chatBusy() || exchange.superseded || draft.saved || draft.discarded) return;

    this.discardingDraftId.set(draft.id);
    this.transactionApi.discardChatDraft(draft.id)
      .pipe(takeUntilDestroyed(this.destroyRef), finalize(() => this.discardingDraftId.set(null)))
      .subscribe({
        next: () => {
          this.updateChatDraft(draft.id, { discarded: true });
          this.reviewDrafts.delete(draft.id);
          if (this.chatReplyTo()?.id === exchange.id) this.chatReplyTo.set(null);
          this.focusChatPrompt();
        },
        error: (error: HttpErrorResponse) => {
          this.notifications.error('Não foi possível descartar a sugestão.');
          if (error.status === 409 || error.status === 404) this.loadChatHistory(true);
        },
      });
  }

  private focusChatPrompt(): void {
    afterNextRender(() => {
      if (this.transactionTab() === 'chat' && !this.isTransactionFormOpen()) {
        this.chatPrompt()?.nativeElement.focus();
      }
    }, { injector: this.injector });
  }

  private scrollChatToLatest(): void {
    afterNextRender(() => {
      const log = this.chatLog()?.nativeElement;
      if (log) log.scrollTop = log.scrollHeight;
    }, { injector: this.injector });
  }

  protected openSuggestionReview(exchange: ChatExchange, draft: TransactionChatDraft, trigger: HTMLButtonElement): void {
    const suggestion = draft.response;
    if (this.chatBusy() || exchange.superseded || draft.saved || draft.discarded
        || suggestion.valor >= chatAmountPrecisionLimit || this.isLoading() || this.errorMessage()) return;

    const type = suggestion.tipo === 'DESPESA' ? 'EXPENSE' : 'INCOME';
    const category = this.categories().find(({ id, transactionType }) =>
      id === suggestion.categoriaId && transactionType === type,
    );
    this.openNewTransactionForm(this.reviewDrafts.get(draft.id) ?? {
      type,
      categoryId: category?.id ?? '',
      amount: suggestion.valor,
      description: suggestion.descricao,
      occurredOn: suggestion.data ?? formatLocalDate(new Date()),
    });
    this.activeChatDraftId.set(draft.id);
    this.formTrigger = trigger;
    afterNextRender(() => this.transactionAccount()?.nativeElement.focus(), { injector: this.injector });
  }

  protected openManualFromChat(trigger: HTMLButtonElement, exchange?: ChatExchange): void {
    if (this.isTransactionFormOpen() || this.isLoading() || this.errorMessage()) return;
    let root = exchange;
    const history = this.chatExchanges();
    while (root?.replyTo) {
      const parent = history.find(item => item.id === root?.replyTo);
      if (!parent) break;
      root = parent;
    }
    const description = (root?.prompt ?? this.chatPromptControl.value).trim().slice(0, 255);
    this.pendingCreation = null;
    this.openNewTransactionForm({ description });
    this.formTrigger = trigger;
    afterNextRender(() => this.transactionAccount()?.nativeElement.focus(), { injector: this.injector });
  }

  protected openCreateForm(): void {
    this.pendingCreation = null;
    this.openNewTransactionForm();
  }

  private openNewTransactionForm(initialValues: Transactions['transactionForm']['value'] = {}): void {
    this.transactionFormError.set(null);
    this.selectedTransaction.set(null);

    // Reseta o formulário de transação para seus valores padrão, incluindo accountId, categoryId, type, amount, occurredOn e description.
    this.transactionForm.reset({
      accountId: '',
      categoryId: '',
      type: '',
      amount: null,
      occurredOn: formatLocalDate(new Date()),
      description: '',
      ...initialValues,
    });

    if (this.transactionForm.controls.type.value) {
      this.transactionForm.controls.categoryId.enable();
    } else {
      this.transactionForm.controls.categoryId.disable();
    }

    this.isTransactionFormOpen.set(true);
  }

  // Fecha o formulário de criação de transação.
  protected closeTransactionForm(): void {
    if (this.isTransactionSubmitting()) {
      return; // Impede o fechamento do formulário enquanto a transação está sendo enviada.
    }

    const draftId = this.activeChatDraftId();
    if (draftId) {
      this.reviewDrafts.set(draftId, this.transactionForm.getRawValue());
      this.activeChatDraftId.set(null);
    }
    this.isTransactionFormOpen.set(false);
    this.transactionFormError.set(null);
    this.selectedTransaction.set(null);
    const trigger = this.formTrigger;
    this.formTrigger = null;
    if (trigger) {
      afterNextRender(() => {
        if (trigger.isConnected && !trigger.disabled) trigger.focus();
        else if (this.transactionTab() === 'chat') this.chatPrompt()?.nativeElement.focus();
      }, { injector: this.injector });
    }
  }

  protected submitTransaction(): void {
    if (!this.isTransactionFormOpen() || this.isTransactionSubmitting()) return;
    if (this.transactionForm.invalid) {
      // Se o formulário de transação for inválido, não envia o formulário.
      this.transactionForm.markAllAsTouched();
      return;
    }

    // Obtém os valores do formulário de transação usando getRawValue(), que retorna um objeto contendo os valores dos campos do formulário.
    const {
      accountId,
      categoryId,
      type,
      amount,
      occurredOn,
      description,
    } = this.transactionForm.getRawValue();

    // Verifica se algum campo obrigatório está vazio (accountId, categoryId, type, amount ou occurredOn). Se algum desses campos estiver vazio, o formulário não será enviado e todos os campos serão marcados como "tocados" para exibir mensagens de erro de validação.
    if (
      !accountId ||
      !categoryId ||
      !type ||
      amount === null ||
      !occurredOn
    ) {
      // Se algum campo obrigatório estiver vazio, não envia o formulário.
      this.transactionForm.markAllAsTouched();
      return;
    }

    const descriptionTrimmed = description.trim();
    const draftId = this.activeChatDraftId();
    if (draftId && amount >= chatAmountPrecisionLimit) {
      this.transactionFormError.set(chatAmountPrecisionMessage);
      return;
    }

    // Cria um objeto CreateTransactionRequest com os valores do formulário de transação, incluindo accountId, categoryId, type, amount, occurredOn e description (se não estiver vazia).
    const transactionRequest: CreateTransactionRequest = {
      accountId,
      categoryId,
      type,
      amount,
      occurredOn,
      // Se a descrição estiver vazia, define como undefined para não enviar uma string vazia.
      description: descriptionTrimmed === '' ? undefined : descriptionTrimmed,
    };

    const selectedTransaction = this.selectedTransaction();
    let creationKey = draftId ?? undefined;
    if (!selectedTransaction && !draftId) {
      const body = JSON.stringify(transactionRequest);
      // Keep the operation key after an uncertain response so retrying does not
      // create a second financial effect. Changed data starts a new operation.
      if (this.pendingCreation?.body !== body) {
        this.pendingCreation = { body, key: crypto.randomUUID() };
      }
      creationKey = this.pendingCreation.key;
    }
    // Define o sinal isTransactionSubmitting como true para indicar que a transação está sendo enviada, e limpa qualquer mensagem de erro anterior.
    this.isTransactionSubmitting.set(true);
    this.transactionFormError.set(null);

    // Se houver uma transação selecionada, significa que estamos editando uma transação existente, caso contrário, estamos criando uma nova transação.
    const request$ = selectedTransaction
      ? this.transactionApi.update(selectedTransaction.id, transactionRequest)
      : this.transactionApi.create(transactionRequest, creationKey);

    // Define a ação como "atualizar" se houver uma transação selecionada (indicando que estamos editando), ou "criar" se não houver transação selecionada (indicando que estamos criando uma nova transação).
    const action = selectedTransaction ? 'atualizar' : 'criar';

    // Chama o método create da API de transações para enviar a solicitação de criação da transação, e usa o operador finalize para definir isTransactionSubmitting como false quando a solicitação for concluída (independentemente de ter sido bem-sucedida ou não).
    request$
      .pipe(
        finalize(() => this.isTransactionSubmitting.set(false))
      )
      // Assina o Observable retornado pelo método create para lidar com a resposta da solicitação de criação da transação. 
      // Se a solicitação for bem-sucedida, o formulário de transação é fechado e os filtros são limpos. 
      // Se houver um erro, uma mensagem de erro apropriada é definida com base no tipo de erro retornado pela API.
      .subscribe({
        next: () => {
          if (this.destroyRef.destroyed) return;
          if (draftId) {
            this.reviewDrafts.delete(draftId);
            this.activeChatDraftId.set(null);
            this.updateChatDraft(draftId, { saved: true });
            if (this.chatReplyTo()?.id === draftId) this.chatReplyTo.set(null);
          } else {
            this.pendingCreation = null;
          }
          this.selectedTransaction.set(null);
          this.isTransactionFormOpen.set(false);
          if (this.formTrigger) this.focusChatPrompt();
          this.formTrigger = null;
          this.notifications.success(selectedTransaction ? 'Transação atualizada com sucesso.' : 'Transação criada com sucesso.');
          this.clearFilters();
        },
        error: (error: HttpErrorResponse) => {
          if (error.status === 409 && error.error?.code === 'TRANSACTION_TYPE_MISMATCH') {
            this.transactionFormError.set('O tipo da transação deve ser igual ao tipo da categoria.');
          } else if (draftId && error.status === 409 && error.error?.code === 'IDEMPOTENCY_CONFLICT') {
            this.transactionFormError.set('Esta sugestão já foi confirmada. Confira a transação na aba Lista.');
            this.loadChatHistory(true);
          } else if (draftId && error.status === 409 && error.error?.code === 'CHAT_DRAFT_UNAVAILABLE') {
            this.transactionFormError.set('Esta sugestão já mudou. Feche a revisão e confira o histórico.');
            this.loadChatHistory(true);
          } else {
            this.transactionFormError.set(`Erro ao ${action} a transação.`);
          }
        }
      });
  }

  // Retorna uma lista de categorias disponíveis com base no tipo de transação selecionado no formulário de criação de transação.
  protected availableTransactionCategories(): Category[] {
    // Obtém o tipo de transação selecionado no formulário de criação de transação.
    const selectedType = this.transactionForm.controls.type.value;

    // Se nenhum tipo de transação estiver selecionado (string vazia), retorna uma lista vazia, indicando que não há categorias disponíveis. Caso contrário, filtra as categorias com base no tipo de transação selecionado.
    if (selectedType === '') {
      return [];
    }

    // Filtra as categorias disponíveis com base no tipo de transação selecionado, retornando apenas as categorias que correspondem ao tipo de transação selecionado.
    return this.categories().filter(category => category.transactionType === selectedType);
  }

  // Quando o tipo de transação é alterado no formulário de criação de transação, reseta a categoria selecionada para garantir que a categoria seja compatível com o novo tipo de transação.
  protected onTransactionTypeChange(): void {
    this.transactionForm.controls.categoryId.setValue('');

    if (this.transactionForm.controls.type.value === '') {
      this.transactionForm.controls.categoryId.disable();
      return;
    }

    this.transactionForm.controls.categoryId.enable();
  }

  protected openEditForm(transaction: TransactionViewModel): void {
    // Limpar transactionFormError
    this.transactionFormError.set(null);

    // Armazenar transaction em selectedTransaction
    this.selectedTransaction.set(transaction);

    // Resetar o formulário preenchendo os seis campos
    this.transactionForm.reset({
      accountId: transaction.accountId,
      categoryId: transaction.categoryId,
      type: transaction.type,
      amount: transaction.amount,
      occurredOn: transaction.occurredOn,
      description: transaction.description ?? '',
    })

    // Habilitar categoryId, pois existe um tipo selecionado
    this.transactionForm.controls.categoryId.enable();

    // Abrir o modal
    this.isTransactionFormOpen.set(true);
  }

  protected openDeleteConfirmation(transaction: TransactionViewModel): void {
    // limpar deleteError
    this.deleteError.set(null);

    // armazenar transaction em transactionPendingDeletion
    this.transactionPendingDeletion.set(transaction);
  }

  protected closeDeleteConfirmation(): void {
    // impedir fechamento quando isDeleting for true
    if (this.isDeleting()) {
      return;
    }

    // limpar transactionPendingDeletion
    this.transactionPendingDeletion.set(null);

    // limpar deleteError
    this.deleteError.set(null);
  }

  // Confirma a exclusão de uma transação pendente de exclusão, chamando a API para excluir a transação e atualizando a lista de trans
  protected confirmDelete(): void {
    const transactionToDelete = this.transactionPendingDeletion();

    // Se não houver uma transação pendente de exclusão ou se a exclusão já estiver em andamento, a função retorna sem fazer nada.
    if (!transactionToDelete || this.isDeleting()) {
      return;
    }

    // Define o sinal isDeleting como true para indicar que a exclusão está em andamento, e limpa qualquer mensagem de erro anterior relacionada à exclusão.
    this.isDeleting.set(true);
    this.deleteError.set(null);

    // Chama o método delete da API de transações para excluir a transação, e usa o operador finalize para definir isDeleting como false quando a solicitação for concluída (independentemente de ter sido bem-sucedida ou não).
    this.transactionApi.delete(transactionToDelete.id)
      .pipe(finalize(() => this.isDeleting.set(false)))
      // Assina o Observable retornado pelo método delete para lidar com a resposta da solicitação de exclusão da transação.
      .subscribe({
        // Se a exclusão for bem-sucedida, a função verifica se a página atual é maior que 0 e se há apenas uma transação na lista. 
        // Se essas condições forem atendidas, a página atual é decrementada em 1 para exibir a página anterior. 
        // Em seguida, a transação pendente de exclusão é limpa e as transações são recarregadas.
        next: () => {
          if (this.currentPage() > 0 && this.transactions().length === 1) {
            this.currentPage.set(this.currentPage() - 1);
          }
          this.transactionPendingDeletion.set(null);
          this.notifications.success('Transação excluída com sucesso.');
          this.loadTransactions();
        },
        error: (error: HttpErrorResponse) => {
          console.error('Error deleting transaction:', error);
          this.deleteError.set('Não foi possível excluir a transação.');
        }
      });
  }
}
