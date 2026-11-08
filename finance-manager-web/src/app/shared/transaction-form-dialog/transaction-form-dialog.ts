import { HttpErrorResponse } from '@angular/common/http';
import {
  afterNextRender, Component, DestroyRef, ElementRef, inject, input, OnInit, output, signal,
  viewChild,
} from '@angular/core';
import { AbstractControl, FormBuilder, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { finalize } from 'rxjs';
import { Category } from '../../core/category.models';
import { FinancialAccount } from '../../core/financial-account.models';
import { Notifications } from '../../core/notifications';
import { TransactionApi } from '../../core/transaction';
import { CreateTransactionRequest, TransactionType } from '../../core/transaction.models';

export interface TransactionFormValues {
  accountId: string;
  categoryId: string;
  type: TransactionType | '';
  amount: number | null;
  occurredOn: string;
  description: string;
}

// At this magnitude, adjacent JavaScript numbers are more than one cent apart.
export const chatAmountPrecisionLimit = 2 ** 46;
export const chatAmountPrecisionMessage = 'Este valor excede a precisão do chat. Confira o valor original antes de registrar manualmente.';

function notFutureDate(control: AbstractControl): ValidationErrors | null {
  const value = control.value;
  return !value || value <= formatLocalDate(new Date()) ? null : { futureDate: true };
}

export function formatLocalDate(date: Date): string {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

@Component({
  selector: 'app-transaction-form-dialog',
  imports: [ReactiveFormsModule],
  templateUrl: './transaction-form-dialog.html',
  styleUrl: './transaction-form-dialog.css',
})
export class TransactionFormDialog implements OnInit {
  readonly accounts = input.required<FinancialAccount[]>();
  readonly categories = input.required<Category[]>();
  readonly initialValues = input<Partial<TransactionFormValues>>({});
  readonly transactionId = input<string>();
  readonly draftId = input<string>();
  readonly idPrefix = input('transaction');
  readonly closed = output<TransactionFormValues>();
  readonly saved = output<void>();
  readonly draftConflict = output<void>();

  private readonly dialog = viewChild.required<ElementRef<HTMLDialogElement>>('dialog');
  private readonly transactionAccount = viewChild.required<ElementRef<HTMLSelectElement>>('transactionAccount');
  private readonly destroyRef = inject(DestroyRef);
  private readonly transactionApi = inject(TransactionApi);
  private readonly notifications = inject(Notifications);
  private readonly formBuilder = inject(FormBuilder);
  private pendingCreation: { body: string; key: string } | null = null;

  protected readonly isTransactionSubmitting = signal(false);
  protected readonly transactionFormError = signal<string | null>(null);
  protected readonly isSuggestionReview = this.draftId;
  protected readonly transactionForm = this.formBuilder.nonNullable.group({
    accountId: ['', Validators.required],
    categoryId: this.formBuilder.nonNullable.control({ value: '', disabled: true }, Validators.required),
    type: this.formBuilder.nonNullable.control<TransactionType | ''>('', Validators.required),
    amount: this.formBuilder.control<number | null>(null, [
      Validators.required, Validators.min(0.01), Validators.pattern(/^\d{1,17}(\.\d{1,2})?$/),
    ]),
    occurredOn: [formatLocalDate(new Date()), [Validators.required, notFutureDate]],
    description: ['', Validators.maxLength(255)],
  });

  constructor() {
    afterNextRender(() => {
      this.dialog().nativeElement.showModal();
      this.transactionAccount().nativeElement.focus();
    });
  }

  ngOnInit(): void {
    this.transactionForm.patchValue(this.initialValues());
    if (this.transactionForm.controls.type.value) {
      this.transactionForm.controls.categoryId.enable();
    }
  }

  protected closeTransactionForm(): void {
    if (this.isTransactionSubmitting()) return;
    this.dialog().nativeElement.close();
    this.closed.emit(this.transactionForm.getRawValue());
  }

  protected submitTransaction(): void {
    if (this.isTransactionSubmitting()) return;
    const { accountId, categoryId, type, amount, occurredOn, description } = this.transactionForm.getRawValue();
    if (this.transactionForm.invalid || !accountId || !categoryId || !type || amount === null || !occurredOn) {
      this.transactionForm.markAllAsTouched();
      return;
    }

    const draftId = this.draftId();
    if (draftId && amount >= chatAmountPrecisionLimit) {
      this.transactionFormError.set(chatAmountPrecisionMessage);
      return;
    }

    const request: CreateTransactionRequest = {
      accountId, categoryId, type, amount, occurredOn, description: description.trim() || undefined,
    };
    const transactionId = this.transactionId();
    let creationKey = draftId;
    if (!transactionId && !draftId) {
      const body = JSON.stringify(request);
      // Keep the operation key when retrying after an uncertain response.
      if (this.pendingCreation?.body !== body) {
        this.pendingCreation = { body, key: crypto.randomUUID() };
      }
      creationKey = this.pendingCreation.key;
    }

    this.isTransactionSubmitting.set(true);
    this.transactionFormError.set(null);
    const save$ = transactionId
      ? this.transactionApi.update(transactionId, request)
      : this.transactionApi.create(request, creationKey);
    const action = transactionId ? 'atualizar' : 'criar';
    save$.pipe(finalize(() => this.isTransactionSubmitting.set(false))).subscribe({
      next: () => {
        if (this.destroyRef.destroyed) return;
        this.dialog().nativeElement.close();
        this.notifications.success(transactionId ? 'Transação atualizada com sucesso.' : 'Transação criada com sucesso.');
        this.saved.emit();
      },
      error: (error: HttpErrorResponse) => {
        if (this.destroyRef.destroyed) return;
        if (error.status === 409 && error.error?.code === 'TRANSACTION_TYPE_MISMATCH') {
          this.transactionFormError.set('O tipo da transação deve ser igual ao tipo da categoria.');
        } else if (draftId && error.status === 409 && error.error?.code === 'IDEMPOTENCY_CONFLICT') {
          this.transactionFormError.set('Esta sugestão já foi confirmada. Confira a transação em Transações.');
          this.draftConflict.emit();
        } else if (draftId && error.status === 409 && error.error?.code === 'CHAT_DRAFT_UNAVAILABLE') {
          this.transactionFormError.set('Esta sugestão já mudou. Feche a revisão e confira o histórico.');
          this.draftConflict.emit();
        } else {
          this.transactionFormError.set(`Erro ao ${action} a transação.`);
        }
      },
    });
  }

  protected availableTransactionCategories(): Category[] {
    const type = this.transactionForm.controls.type.value;
    return this.categories().filter(category => category.transactionType === type);
  }

  protected onTransactionTypeChange(): void {
    const { categoryId, type } = this.transactionForm.controls;
    categoryId.setValue('');
    if (type.value) categoryId.enable();
    else categoryId.disable();
  }
}
