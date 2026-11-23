import { HttpErrorResponse } from '@angular/common/http';
import { afterNextRender, Component, DestroyRef, ElementRef, inject, input, output, signal, viewChild } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { finalize } from 'rxjs';
import { CreateAccountTransferRequest, FinancialAccount } from '../../core/financial-account.models';
import { FinancialAccountApi } from '../../core/financial-accounts';
import { formatLocalDate, notFutureDate } from '../../shared/transaction-form-dialog/transaction-form-dialog';

@Component({
  selector: 'app-account-transfer-dialog',
  imports: [ReactiveFormsModule],
  templateUrl: './account-transfer-dialog.html',
  styleUrl: '../../shared/transaction-form-dialog/transaction-form-dialog.css',
})
export class AccountTransferDialog {
  readonly accounts = input.required<FinancialAccount[]>();
  readonly accountNames = input.required<ReadonlyMap<string, string>>();
  readonly closed = output<void>();
  readonly saved = output<void>();

  private readonly dialog = viewChild.required<ElementRef<HTMLDialogElement>>('dialog');
  private readonly destroyRef = inject(DestroyRef);
  private readonly api = inject(FinancialAccountApi);
  private readonly formBuilder = inject(FormBuilder);
  private pending: { body: string; key: string } | null = null;

  protected readonly today = formatLocalDate(new Date());
  protected readonly submitting = signal(false);
  protected readonly error = signal('');
  protected readonly form = this.formBuilder.nonNullable.group({
    sourceAccountId: ['', Validators.required],
    destinationAccountId: ['', Validators.required],
    amount: ['', [Validators.required, Validators.pattern(/^(?=.*[1-9])\d{1,17}([.,]\d{1,2})?$/)]],
    occurredOn: [this.today, [Validators.required, notFutureDate]],
    description: ['', Validators.maxLength(255)],
  });

  constructor() {
    afterNextRender(() => this.dialog().nativeElement.showModal());
  }

  protected close(): void {
    if (this.submitting()) return;
    this.dialog().nativeElement.close();
    this.closed.emit();
  }

  protected submit(): void {
    if (this.submitting()) return;
    this.error.set('');
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    const values = this.form.getRawValue();
    if (values.sourceAccountId === values.destinationAccountId) {
      this.error.set('Selecione contas diferentes para a origem e o destino.');
      return;
    }

    const request: CreateAccountTransferRequest = {
      ...values,
      amount: values.amount.replace(',', '.'),
      description: values.description.trim() || undefined,
    };
    const body = JSON.stringify(request);
    if (this.pending?.body !== body) {
      this.pending = { body, key: crypto.randomUUID() };
    }

    this.submitting.set(true);
    this.api.createTransfer(request, this.pending.key)
      .pipe(finalize(() => this.submitting.set(false)))
      .subscribe({
        next: () => {
          if (this.destroyRef.destroyed) return;
          this.dialog().nativeElement.close();
          this.saved.emit();
        },
        error: (error: HttpErrorResponse) => {
          if (this.destroyRef.destroyed) return;
          if (error.error?.code === 'FINANCIAL_ACCOUNT_NOT_FOUND') {
            this.error.set('Uma das contas não está disponível. Feche o formulário e recarregue as contas.');
          } else if (error.error?.code === 'SAME_TRANSFER_ACCOUNT') {
            this.error.set('Selecione contas diferentes para a origem e o destino.');
          } else {
            this.error.set('Não foi possível confirmar a transferência. Confira os dados e tente novamente.');
          }
        },
      });
  }
}
