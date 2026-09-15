import { Component, computed, input, output, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { LucideArrowUpRight, LucideChartPie, LucideRefreshCw } from '@lucide/angular';
import { CategoryExpensesResponse } from '../../core/report.models';
import { AnimatedNumber } from '../animated-number/animated-number';

const palette = ['#19856a', '#ad8050', '#8072b1', '#438fa7', '#c46f71', '#7a9050'];

@Component({
  selector: 'app-category-expenses',
  imports: [AnimatedNumber, DecimalPipe, RouterLink, LucideArrowUpRight, LucideChartPie, LucideRefreshCw],
  templateUrl: './category-expenses.html',
  styleUrl: './category-expenses.css',
})
export class CategoryExpenses {
  readonly data = input<CategoryExpensesResponse | null>(null);
  readonly monthLabel = input.required<string>();
  readonly loading = input(false);
  readonly error = input(false);
  readonly retry = output<void>();
  protected readonly expanded = signal(false);
  protected readonly total = computed(() => this.data()?.totalExpense ?? 0);
  protected readonly categories = computed(() => {
    const total = this.total();
    let offset = 0;
    return [...(this.data()?.categories ?? [])]
      .filter(category => category.totalExpense > 0)
      .sort((a, b) => b.totalExpense - a.totalExpense || a.categoryName.localeCompare(b.categoryName, 'pt-BR'))
      .map(category => {
        const share = total > 0 ? category.totalExpense / total * 100 : 0;
        // Category identity keeps the same color when the month or ranking changes.
        const hash = [...category.categoryId].reduce((value, char) => (value * 31 + char.charCodeAt(0)) >>> 0, 0);
        const result = { ...category, share, offset, color: palette[hash % palette.length] };
        offset += share;
        return result;
      });
  });
  protected readonly visibleCategories = computed(() => this.expanded() ? this.categories() : this.categories().slice(0, 5));
  protected readonly remainingCount = computed(() => Math.max(0, this.categories().length - 5));
  protected readonly remainingShare = computed(() => this.categories().slice(5).reduce((sum, category) => sum + category.share, 0));
}
