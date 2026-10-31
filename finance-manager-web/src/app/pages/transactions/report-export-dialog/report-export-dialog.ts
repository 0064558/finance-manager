import { DOCUMENT } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { AfterViewInit, Component, computed, DestroyRef, ElementRef, inject, input, OnDestroy, OnInit, output, signal, ViewChild } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { AbstractControl, FormBuilder, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { finalize, map } from 'rxjs';
import { Auth } from '../../../core/auth';
import { Category } from '../../../core/category.models';
import { FinancialAccount } from '../../../core/financial-account.models';
import { Notifications } from '../../../core/notifications';
import { ReportExportApi, ReportExportFormat, ReportExportRequest } from '../../../core/report-export';
import { SlowApiLoading } from '../../../core/slow-api-loading';
import { TransactionFilters, TransactionType } from '../../../core/transaction.models';
import { ValuePrivacy } from '../../../core/value-privacy';

function dateRange(control: AbstractControl): ValidationErrors | null {
  const { startDate, endDate } = control.value;
  if (!startDate || !endDate) return null;
  if (startDate > endDate) return { invalidDateRange: true };
  const days = (Date.parse(`${endDate}T00:00:00Z`) - Date.parse(`${startDate}T00:00:00Z`)) / 86400000 + 1;
  return !Number.isFinite(days) ? { invalidDateRange: true } : days > 366 ? { periodTooLong: true } : null;
}

function localDate(date: Date): string {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
}

@Component({
  selector: 'app-report-export-dialog',
  imports: [ReactiveFormsModule],
  templateUrl: './report-export-dialog.html',
  styleUrl: './report-export-dialog.css',
})
export class ReportExportDialog implements OnInit, AfterViewInit, OnDestroy {
  readonly initialFormat = input<ReportExportFormat>('PDF');
  readonly filters = input.required<TransactionFilters>();
  readonly accounts = input.required<FinancialAccount[]>();
  readonly categories = input.required<Category[]>();
  readonly closed = output<void>();
  @ViewChild('dialog', { static: true }) private dialog!: ElementRef<HTMLDialogElement>;
  private readonly document = inject(DOCUMENT);
  private readonly destroyRef = inject(DestroyRef);
  private readonly api = inject(ReportExportApi);
  private readonly auth = inject(Auth);
  private readonly router = inject(Router);
  private readonly notifications = inject(Notifications);
  protected readonly privacy = inject(ValuePrivacy);
  protected readonly slowLoading = inject(SlowApiLoading);
  protected readonly submitting = signal(false);
  protected readonly errorMessage = signal<string | null>(null);
  private previousFocus: HTMLElement | null = null;
  private previousOverflow = '';
  private readonly builder = inject(FormBuilder);
  protected readonly form = this.builder.nonNullable.group({
    format: this.builder.nonNullable.control<ReportExportFormat>('PDF', Validators.required),
    startDate: ['', Validators.required], endDate: ['', Validators.required],
    type: this.builder.nonNullable.control<TransactionType | ''>(''),
    accountId: [''], categoryId: [''],
  }, { validators: dateRange });
  protected readonly selectedFormat = toSignal(this.form.controls.format.valueChanges, { initialValue: this.form.controls.format.value });
  private readonly selectedType = toSignal(this.form.controls.type.valueChanges, { initialValue: this.form.controls.type.value });
  protected readonly availableCategories = computed(() => this.categories().filter(category => !this.selectedType() || category.transactionType === this.selectedType()));
  protected readonly dateError = toSignal(this.form.events.pipe(map(() => this.dateValidationMessage())), { initialValue: null });

  ngOnInit(): void {
    const filters = this.filters();
    const today = new Date();
    const defaultMonth = !filters.startDate && !filters.endDate;
    this.form.patchValue({
      format: this.initialFormat(),
      startDate: filters.startDate || (defaultMonth ? localDate(new Date(today.getFullYear(), today.getMonth(), 1)) : ''),
      endDate: filters.endDate || (defaultMonth ? localDate(new Date(today.getFullYear(), today.getMonth() + 1, 0)) : ''),
      type: filters.type ?? '', accountId: filters.accountId ?? '', categoryId: filters.categoryId ?? '',
    });
  }

  ngAfterViewInit(): void {
    this.previousFocus = this.document.activeElement instanceof HTMLElement ? this.document.activeElement : null;
    this.previousOverflow = this.document.body.style.overflow;
    this.document.body.style.overflow = 'hidden';
    this.dialog.nativeElement.showModal();
  }

  ngOnDestroy(): void {
    if (this.dialog.nativeElement.open) this.dialog.nativeElement.close();
    this.document.body.style.overflow = this.previousOverflow;
    this.restoreFocus();
  }

  protected resetCategory(): void {
    this.form.controls.categoryId.setValue('');
  }

  protected cancel(event: Event): void {
    event.preventDefault();
    this.close();
  }

  protected close(): void {
    if (!this.submitting()) this.dialog.nativeElement.close();
  }

  protected onClosed(): void {
    this.document.body.style.overflow = this.previousOverflow;
    this.restoreFocus();
    this.closed.emit();
  }

  protected submit(): void {
    if (this.submitting()) return;
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      const field = this.form.controls.endDate.invalid && !this.form.controls.startDate.invalid ? '#export-end' : '#export-start';
      this.dialog.nativeElement.querySelector<HTMLElement>(field)?.focus();
      return;
    }
    const { format, startDate, endDate, type, accountId, categoryId } = this.form.getRawValue();
    const request: ReportExportRequest = { format, startDate, endDate,
      ...(type ? { type } : {}), ...(accountId ? { accountId } : {}), ...(categoryId ? { categoryId } : {}),
    };
    this.submitting.set(true);
    this.errorMessage.set(null);
    this.api.download(request).pipe(
      takeUntilDestroyed(this.destroyRef), finalize(() => this.submitting.set(false)),
    ).subscribe({
      next: response => {
        try {
          this.api.save(response, request);
          this.notifications.success('Download iniciado. Confira os downloads do navegador.');
          this.dialog.nativeElement.close();
        } catch {
          this.errorMessage.set('Não foi possível iniciar o download. Tente novamente.');
        }
      },
      error: (error: unknown) => {
        if (error instanceof HttpErrorResponse && error.status === 401) {
          this.auth.logout();
          this.notifications.error('Sua sessão expirou. Entre novamente para exportar.');
          void this.router.navigateByUrl('/login');
          return;
        }
        const status = error instanceof HttpErrorResponse ? error.status : undefined;
        const code = error instanceof HttpErrorResponse ? error.error?.code : undefined;
        this.errorMessage.set(code === 'REPORT_EXPORT_LIMIT_EXCEEDED'
          ? 'O relatório aceita até 366 dias e 5.000 movimentações. Reduza o período ou ajuste os filtros.'
          : code === 'XLSX_PRECISION_EXCEEDED'
            ? 'O Excel não consegue preservar os centavos de alguns valores deste relatório. Escolha PDF ou CSV, ou reduza o período.'
            : status === 404 ? 'A conta ou categoria não está mais disponível. Revise os filtros.'
              : status === 400 ? 'Revise o período e os filtros antes de tentar novamente.'
                : status === 0 ? 'Sem conexão com o servidor. Verifique sua conexão e tente novamente.'
                  : 'Não foi possível gerar o relatório. Tente novamente.');
      },
    });
  }

  private restoreFocus(): void {
    if (this.previousFocus?.isConnected) this.previousFocus.focus();
  }

  private dateValidationMessage(): string | null {
    if (!this.form.touched) return null;
    if (this.form.controls.startDate.hasError('required') || this.form.controls.endDate.hasError('required')) {
      return 'Preencha a data inicial e a data final.';
    }
    if (this.form.hasError('invalidDateRange')) return 'A data inicial não pode ser posterior à data final.';
    if (this.form.hasError('periodTooLong')) return 'Escolha um período de até 366 dias.';
    return null;
  }
}
