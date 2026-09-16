import { AnimatedNumber } from '../../shared/animated-number/animated-number';
import { RouterLink } from '@angular/router';
import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import {
  LucideCalendarDays,
  LucideChevronLeft,
  LucideChevronRight,
  LucideLandmark,
  LucideRefreshCw,
  LucideScale,
  LucideTrendingDown,
  LucideTrendingUp,
  LucideWalletCards,
} from '@lucide/angular';
import { finalize, forkJoin, Subscription } from 'rxjs';
import { Report } from '../../core/report';
import { CashFlowResponse, CategoryBreakdownResponse, CurrentBalance, ReportSummary } from '../../core/report.models';
import { TransactionApi } from '../../core/transaction';
import { PageResponse, TransactionResponse, TransactionType } from '../../core/transaction.models';
import { RecentTransactions } from '../../shared/recent-transactions/recent-transactions';
import { CashFlowChart } from '../../shared/cash-flow-chart/cash-flow-chart';
import { CategoryExpenses } from '../../shared/category-expenses/category-expenses';

@Component({
  selector: 'app-dashboard',
  imports: [
    AnimatedNumber,
    RouterLink,
    LucideCalendarDays,
    LucideChevronLeft,
    LucideChevronRight,
    LucideLandmark,
    LucideRefreshCw,
    LucideScale,
    LucideTrendingDown,
    LucideTrendingUp,
    LucideWalletCards,
    RecentTransactions,
    CashFlowChart,
    CategoryExpenses,
  ],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.css',
})
export class Dashboard implements OnInit {
  private readonly report = inject(Report);
  private readonly transactionApi = inject(TransactionApi);
  private readonly destroyRef = inject(DestroyRef);
  private categorySubscription?: Subscription;
  private readonly categoryCache = new Map<TransactionType, CategoryBreakdownResponse>();
  protected readonly selectedCategoryType = signal<TransactionType>('EXPENSE');

  protected readonly categoryBreakdown = signal<CategoryBreakdownResponse | null>(null);
  protected readonly categoryLoading = signal(true);
  protected readonly categoryError = signal(false);

  private readonly monthFormatter = new Intl.DateTimeFormat('pt-BR', {
    month: 'long',
    year: 'numeric',
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

  ngOnInit(): void {
    this.destroyRef.onDestroy(() => this.categorySubscription?.unsubscribe());
    this.loadDashboard();
  }

  protected selectCategoryType(type: TransactionType): void {
    if (type === this.selectedCategoryType()) return;
    this.selectedCategoryType.set(type);
    this.loadCategoryBreakdown();
  }

  protected loadCategoryBreakdown(force = false): void {
    this.categorySubscription?.unsubscribe();
    const type = this.selectedCategoryType();
    if (force) this.categoryCache.delete(type);
    const cached = this.categoryCache.get(type);
    this.categoryError.set(false);
    this.categoryBreakdown.set(cached ?? null);
    this.categoryLoading.set(!cached);
    if (cached) return;
    const month = this.selectedMonth();
    this.categorySubscription = this.report.getCategoryBreakdown(
      this.formatDate(month),
      this.formatDate(new Date(month.getFullYear(), month.getMonth() + 1, 0)),
      type,
    ).pipe(finalize(() => this.categoryLoading.set(false)))
      .subscribe({
        next: (data) => {
          this.categoryCache.set(type, data);
          this.categoryBreakdown.set(data);
        },
        error: () => this.categoryError.set(true),
      });
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

  private changeMonth(offset: number): void {
    const selectedMonth = this.selectedMonth();
    this.selectedMonth.set(
      new Date(selectedMonth.getFullYear(), selectedMonth.getMonth() + offset, 1),
    );
    this.loadDashboard();
  }

  private loadDashboard(): void {
    this.categoryCache.clear();
    this.loadCategoryBreakdown();
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
      .pipe(takeUntilDestroyed(this.destroyRef), finalize(() => this.isLoading.set(false)))
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
