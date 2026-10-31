import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Router } from '@angular/router';
import { LucideArrowRight, LucideFileText, LucideSheet, LucideTable2 } from '@lucide/angular';
import { finalize, forkJoin } from 'rxjs';
import { Auth } from '../../core/auth';
import { CategoryApi } from '../../core/categories';
import { Category } from '../../core/category.models';
import { FinancialAccountApi } from '../../core/financial-accounts';
import { FinancialAccount } from '../../core/financial-account.models';
import { ReportExportFormat } from '../../core/report-export';
import { TransactionFilters } from '../../core/transaction.models';
import { SlowApiNotice } from '../../shared/slow-api-notice/slow-api-notice';
import { ReportExportDialog } from '../transactions/report-export-dialog/report-export-dialog';

@Component({
  selector: 'app-reports',
  imports: [ReportExportDialog, SlowApiNotice, LucideArrowRight, LucideFileText, LucideSheet, LucideTable2],
  templateUrl: './reports.html',
  styleUrl: './reports.css',
})
export class Reports implements OnInit {
  private readonly accountApi = inject(FinancialAccountApi);
  private readonly categoryApi = inject(CategoryApi);
  private readonly auth = inject(Auth);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  protected readonly accounts = signal<FinancialAccount[]>([]);
  protected readonly categories = signal<Category[]>([]);
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly selectedFormat = signal<ReportExportFormat | null>(null);
  protected readonly filters: TransactionFilters = {};
  protected readonly formats = [
    { id: 'PDF', title: 'Documento PDF', tag: 'PARA LER E COMPARTILHAR',
      description: 'Um retrato das suas movimentações, pronto para guardar ou imprimir.',
      detail: 'Resumo financeiro e lista de movimentações.', action: 'Gerar PDF' },
    { id: 'XLSX', title: 'Planilha Excel', tag: 'PARA ANALISAR',
      description: 'Explore seus números em uma planilha organizada e pronta para editar.',
      detail: 'Abas de resumo e transações, com datas e valores formatados.', action: 'Gerar Excel' },
    { id: 'CSV', title: 'Arquivo CSV', tag: 'PARA IMPORTAR',
      description: 'Leve suas movimentações para outros programas e ferramentas.',
      detail: 'Dados em texto, sem formatação ou ajuste de largura das colunas.', action: 'Gerar CSV' },
  ] as const;

  ngOnInit(): void { this.loadOptions(); }

  protected loadOptions(): void {
    if (this.loading()) return;
    this.loading.set(true);
    this.error.set(null);
    forkJoin({ accounts: this.accountApi.getAll(), categories: this.categoryApi.getAll() }).pipe(
      takeUntilDestroyed(this.destroyRef), finalize(() => this.loading.set(false)),
    ).subscribe({
      next: ({ accounts, categories }) => {
        this.accounts.set(accounts);
        this.categories.set(categories);
      },
      error: (error: unknown) => {
        if (error instanceof HttpErrorResponse && error.status === 401) {
          this.auth.logout();
          void this.router.navigateByUrl('/login');
          return;
        }
        this.error.set('Não foi possível carregar as contas e categorias. Tente novamente para preparar seu relatório.');
      },
    });
  }

  protected openExport(format: ReportExportFormat): void {
    if (!this.loading() && !this.error()) this.selectedFormat.set(format);
  }
}
