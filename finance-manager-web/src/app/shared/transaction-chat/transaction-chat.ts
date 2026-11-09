import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import {
  afterNextRender, Component, computed, DestroyRef, ElementRef, inject, Injector, input, signal, viewChild,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { LucideMessageCircle, LucideX } from '@lucide/angular';
import { finalize, forkJoin, Subscription, timeout } from 'rxjs';
import { CategoryApi } from '../../core/categories';
import { Category } from '../../core/category.models';
import { FinancialAccountApi } from '../../core/financial-accounts';
import { FinancialAccount } from '../../core/financial-account.models';
import { Notifications } from '../../core/notifications';
import { TransactionApi } from '../../core/transaction';
import { TransactionChanges } from '../../core/transaction-changes';
import { TransactionChatDraft, TransactionChatEntry, TransactionChatMessage } from '../../core/transaction.models';
import { ValuePrivacy } from '../../core/value-privacy';
import { ChatVoiceInput } from '../chat-voice-input/chat-voice-input';
import { PrivateCurrency } from '../private-currency/private-currency';
import {
  chatAmountPrecisionLimit, chatAmountPrecisionMessage, TransactionFormDialog, TransactionFormValues,
} from '../transaction-form-dialog/transaction-form-dialog';


interface ChatExchange extends Omit<TransactionChatEntry, 'drafts'> {
  drafts: TransactionChatDraft[];
  request?: TransactionChatMessage;
  error?: string;
  retryable?: boolean;
}

interface ChatFormData {
  accounts: FinancialAccount[];
  categories: Category[];
  initialValues: Partial<TransactionFormValues>;
  draftId?: string;
}

const chatRequestTimeoutMs = 30_000;

@Component({
  selector: 'app-transaction-chat',
  imports: [ChatVoiceInput, DatePipe, LucideMessageCircle, LucideX, PrivateCurrency, ReactiveFormsModule, TransactionFormDialog],
  templateUrl: './transaction-chat.html',
  styleUrl: './transaction-chat.css',
})
export class TransactionChat {
  readonly suspended = input(false);

  private readonly reviewDrafts = new Map<string, TransactionFormValues>();
  private formTrigger: HTMLButtonElement | null = null;
  private formDataRequest?: Subscription;
  private readonly destroyRef = inject(DestroyRef);
  private readonly injector = inject(Injector);
  private readonly notifications = inject(Notifications);
  private readonly transactionChanges = inject(TransactionChanges);
  private readonly launcher = viewChild.required<ElementRef<HTMLButtonElement>>('launcher');
  private readonly chatPanel = viewChild.required<ElementRef<HTMLElement>>('chatPanel');
  private readonly chatPrompt = viewChild.required<ElementRef<HTMLTextAreaElement>>('chatPrompt');
  private readonly chatLog = viewChild.required<ElementRef<HTMLElement>>('chatLog');
  private readonly voice = viewChild.required<ChatVoiceInput>('voice');

  protected readonly valuePrivacy = inject(ValuePrivacy);
  protected readonly isOpen = signal(false);
  protected readonly form = signal<ChatFormData | null>(null);
  protected readonly isLoadingFormData = signal(false);
  protected readonly chatExchanges = signal<ChatExchange[]>([]);
  protected readonly chatHistoryReady = signal(false);
  protected readonly isLoadingChatHistory = signal(false);
  protected readonly chatHistoryFailed = signal(false);
  protected readonly isRequestingSuggestion = signal(false);
  protected readonly discardingDraftId = signal<string | null>(null);
  protected readonly chatReplyTo = signal<ChatExchange | null>(null);
  protected readonly chatAmountPrecisionLimit = chatAmountPrecisionLimit;
  protected readonly chatAmountPrecisionMessage = chatAmountPrecisionMessage;
  protected readonly promptMaxLength = 500;
  protected readonly chatBusy = computed(() =>
    this.isLoadingChatHistory() || this.isRequestingSuggestion() || this.discardingDraftId() !== null
    || this.isLoadingFormData() || this.form() !== null,
  );
  protected readonly chatPromptControl = inject(FormBuilder).nonNullable.control('', [
    Validators.required, Validators.maxLength(this.promptMaxLength), Validators.pattern(/\S/),
  ]);

  private get transactionApi(): TransactionApi {
    return this.injector.get(TransactionApi);
  }

  protected toggleChat(): void {
    if (this.isOpen()) {
      this.closeChat();
      return;
    }
    this.isOpen.set(true);
    this.loadChatHistory();
    this.scrollChatToLatest();
    this.focusChatPrompt(true);
  }

  protected closeChat(): void {
    this.formDataRequest?.unsubscribe();
    this.isOpen.set(false);
    this.launcher().nativeElement.focus();
  }

  protected appendChatTranscript(transcript: string): void {
    if (!this.isOpen() || this.suspended() || this.chatBusy() || !transcript.trim()) return;
    const current = this.chatPromptControl.value;
    const separator = current && !/\s$/.test(current) ? ' ' : '';
    this.chatPromptControl.setValue(current + separator + transcript.trim());
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

  protected requestSuggestion(): void {
    if (!this.chatHistoryReady() || this.chatPromptControl.invalid || this.chatBusy() || this.voice().isCapturing()) return;

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

  private focusChatPrompt(force = false): void {
    afterNextRender(() => {
      const panel = this.chatPanel().nativeElement;
      if (this.isOpen() && !this.suspended() && !this.form()
          && (force || panel.contains(panel.ownerDocument.activeElement))) {
        this.chatPrompt().nativeElement.focus();
      }
    }, { injector: this.injector });
  }

  private scrollChatToLatest(): void {
    afterNextRender(() => {
      if (!this.isOpen()) return;
      const log = this.chatLog().nativeElement;
      log.scrollTop = log.scrollHeight;
    }, { injector: this.injector });
  }

  protected openSuggestionReview(exchange: ChatExchange, draft: TransactionChatDraft, trigger: HTMLButtonElement): void {
    const suggestion = draft.response;
    if (this.chatBusy() || exchange.superseded || draft.saved || draft.discarded
        || suggestion.valor >= chatAmountPrecisionLimit) return;

    const type = suggestion.tipo === 'DESPESA' ? 'EXPENSE' : 'INCOME';
    this.loadFormData(trigger, categories => this.reviewDrafts.get(draft.id) ?? {
      type,
      categoryId: categories.find(category =>
        category.id === suggestion.categoriaId && category.transactionType === type,
      )?.id ?? '',
      amount: suggestion.valor,
      description: suggestion.descricao,
      ...(suggestion.data ? { occurredOn: suggestion.data } : {}),
    }, draft.id);
  }

  protected openManualFromChat(trigger: HTMLButtonElement, exchange?: ChatExchange): void {
    if (this.form() || this.isLoadingFormData()) return;
    let root = exchange;
    const history = this.chatExchanges();
    while (root?.replyTo) {
      const parent = history.find(item => item.id === root?.replyTo);
      if (!parent) break;
      root = parent;
    }
    const description = (root?.prompt ?? this.chatPromptControl.value).trim().slice(0, 255);
    this.loadFormData(trigger, () => ({ description }));
  }

  private loadFormData(
    trigger: HTMLButtonElement,
    initialValues: (categories: Category[]) => Partial<TransactionFormValues>,
    draftId?: string,
  ): void {
    this.isLoadingFormData.set(true);
    this.formDataRequest = forkJoin({
      accounts: this.injector.get(FinancialAccountApi).getAll(),
      categories: this.injector.get(CategoryApi).getAll(),
    }).pipe(takeUntilDestroyed(this.destroyRef), finalize(() => this.isLoadingFormData.set(false)))
      .subscribe({
        next: ({ accounts, categories }) => {
          if (!this.isOpen() || this.suspended()) return;
          this.formTrigger = trigger;
          this.form.set({ accounts, categories, initialValues: initialValues(categories), draftId });
        },
        error: () => this.notifications.error('Não foi possível carregar as contas e categorias. Tente novamente.'),
      });
  }

  protected closeForm(values: TransactionFormValues): void {
    const draftId = this.form()?.draftId;
    if (draftId) this.reviewDrafts.set(draftId, values);
    const trigger = this.formTrigger;
    this.formTrigger = null;
    this.form.set(null);
    afterNextRender(() => {
      if (!this.isOpen() || this.suspended()) return;
      if (trigger?.isConnected && !trigger.disabled) trigger.focus();
      else this.chatPrompt().nativeElement.focus();
    }, { injector: this.injector });
  }

  protected onTransactionSaved(): void {
    const draftId = this.form()?.draftId;
    if (draftId) {
      this.reviewDrafts.delete(draftId);
      this.updateChatDraft(draftId, { saved: true });
      if (this.chatReplyTo()?.drafts.some(draft => draft.id === draftId)) this.chatReplyTo.set(null);
    }
    this.form.set(null);
    this.formTrigger = null;
    this.focusChatPrompt(true);
    this.transactionChanges.notifySaved();
  }
}
