import { CurrencyPipe } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import {
  LucideChartNoAxesCombined,
  LucideChevronLeft,
  LucideChevronRight,
  LucideLandmark,
  LucideRefreshCw,
  LucideScale,
  LucideTrendingDown,
  LucideTrendingUp,
  LucideWalletCards,
} from '@lucide/angular';
import { finalize, forkJoin } from 'rxjs';
import { Report } from '../../core/report';
import {
  CashFlowPoint,
  CashFlowResponse,
  CurrentBalance,
  ReportSummary,
} from '../../core/report.models';
import { TransactionApi } from '../../core/transaction';
import { PageResponse, TransactionResponse } from '../../core/transaction.models';
import { RecentTransactions } from '../../shared/recent-transactions/recent-transactions';

@Component({
  selector: 'app-dashboard',
  imports: [
    CurrencyPipe,
    LucideChartNoAxesCombined,
    LucideChevronLeft,
    LucideChevronRight,
    LucideLandmark,
    LucideRefreshCw,
    LucideScale,
    LucideTrendingDown,
    LucideTrendingUp,
    LucideWalletCards,
    RecentTransactions,
  ],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.css',
})
export class Dashboard implements OnInit {
  private readonly report = inject(Report);
  private readonly transactionApi = inject(TransactionApi);

  private readonly monthFormatter = new Intl.DateTimeFormat('pt-BR', {
    month: 'long',
    year: 'numeric',
  });

  private readonly currencyFormatter = new Intl.NumberFormat('pt-BR', {
    style: 'currency',
    currency: 'BRL',
  });

  protected readonly isLoading = signal(true);
  protected readonly errorMessage = signal('');
  protected readonly summary = signal<ReportSummary | null>(null);
  protected readonly currentBalance = signal<CurrentBalance | null>(null);
  protected readonly cashFlow = signal<CashFlowResponse | null>(null);
  protected readonly recentTransactions = signal<PageResponse<TransactionResponse> | null>(null);
  protected readonly selectedMonth = signal(this.firstDayOfMonth(new Date()));

  protected readonly monthLabel = computed(() => this.monthFormatter.format(this.selectedMonth()));

  protected readonly previousMonthLabel = computed(() => {
    const selectedMonth = this.selectedMonth();
    return this.monthFormatter.format(
      new Date(selectedMonth.getFullYear(), selectedMonth.getMonth() - 1, 1),
    );
  });

  protected readonly isCurrentMonth = computed(() => {
    const selectedMonth = this.selectedMonth();
    const currentMonth = this.firstDayOfMonth(new Date());

    return selectedMonth.getTime() === currentMonth.getTime();
  });

  protected readonly hasCashFlowActivity = computed(() =>
    (this.cashFlow()?.points ?? []).some(
      (point) => point.totalIncome > 0 || point.totalExpense > 0,
    ),
  );

  protected readonly maxCashFlowValue = computed(() => {
    const points = this.cashFlow()?.points ?? [];
    const values = points.flatMap((point) => [point.totalIncome, point.totalExpense]);

    return Math.max(...values, 1);
  });

  ngOnInit(): void {
    this.loadDashboard();
  }

  protected retry(): void {
    this.loadDashboard();
  }

  protected goToPreviousMonth(): void {
    this.changeMonth(-1);
  }

  protected goToNextMonth(): void {
    if (!this.isCurrentMonth()) {
      this.changeMonth(1);
    }
  }

  protected barHeight(value: number): number {
    if (value === 0) {
      return 0;
    }

    return Math.max((value / this.maxCashFlowValue()) * 100, 6);
  }

  protected cashFlowPointLabel(point: CashFlowPoint): string {
    const date = new Intl.DateTimeFormat('pt-BR').format(new Date(`${point.date}T00:00:00`));

    return `${date}: receitas ${this.currencyFormatter.format(point.totalIncome)}; despesas ${this.currencyFormatter.format(point.totalExpense)}`;
  }

  private changeMonth(offset: number): void {
    const selectedMonth = this.selectedMonth();
    this.selectedMonth.set(
      new Date(selectedMonth.getFullYear(), selectedMonth.getMonth() + offset, 1),
    );
    this.loadDashboard();
  }

  private loadDashboard(): void {
    const selectedMonth = this.selectedMonth();
    const startDate = this.formatDate(selectedMonth);
    const endDate = this.formatDate(
      new Date(selectedMonth.getFullYear(), selectedMonth.getMonth() + 1, 0),
    );

    this.isLoading.set(true);
    this.errorMessage.set('');

    forkJoin({
      summary: this.report.getSummary(startDate, endDate),
      currentBalance: this.report.getCurrentBalance(),
      cashFlow: this.report.getCashFlow(startDate, endDate),
      recentTransactions: this.transactionApi.getRecent(startDate, endDate),
    })
      .pipe(finalize(() => this.isLoading.set(false)))
      .subscribe({
        next: (response) => {
          this.summary.set(response.summary);
          this.currentBalance.set(response.currentBalance);
          this.cashFlow.set(response.cashFlow);
          this.recentTransactions.set(response.recentTransactions);
        },
        error: () => {
          this.errorMessage.set('Não foi possível carregar os dados do dashboard.');
        },
      });
  }

  private firstDayOfMonth(date: Date): Date {
    return new Date(date.getFullYear(), date.getMonth(), 1);
  }

  private formatDate(date: Date): string {
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');

    return `${year}-${month}-${day}`;
  }
}
