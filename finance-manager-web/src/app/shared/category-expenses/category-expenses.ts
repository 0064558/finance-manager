import { Component, computed, input, output, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { LucideArrowUpRight, LucideChartPie, LucideRefreshCw } from '@lucide/angular';
import { CategoryBreakdownResponse } from '../../core/report.models';
import { TransactionType } from '../../core/transaction.models';
import { AnimatedNumber } from '../animated-number/animated-number';

const incomePalette = ['#19856a', '#42a985', '#65a66e', '#328d72', '#6fa889', '#57886d'];
const expensePalette = ['#c46f71', '#d18b56', '#ad8050', '#b56b8f', '#b19064', '#a46b62'];

@Component({
  selector: 'app-category-expenses',
  imports: [AnimatedNumber, DecimalPipe, RouterLink, LucideArrowUpRight, LucideChartPie, LucideRefreshCw],
  templateUrl: './category-expenses.html',
  styleUrl: './category-expenses.css',
})
export class CategoryExpenses {
  readonly data = input<CategoryBreakdownResponse | null>(null);
  readonly type = input<TransactionType>('EXPENSE');
  readonly typeChange = output<TransactionType>();
  readonly monthLabel = input.required<string>();
  readonly loading = input(false);
  readonly error = input(false);
  readonly retry = output<void>();
  protected readonly expanded = signal(false);
  protected readonly copy = computed(() => this.type() === 'INCOME' ? {
    title: 'Receitas por categoria', description: 'Veja de onde seu dinheiro vem.',
    noun: 'receitas', article: 'as receitas', singular: 'uma receita',
  } : {
    title: 'Gastos por categoria', description: 'Entenda para onde seu dinheiro vai.',
    noun: 'despesas', article: 'os gastos', singular: 'uma despesa',
  });
  protected readonly total = computed(() => this.data()?.totalAmount ?? 0);
  protected readonly categories = computed(() => {
    const total = this.total();
    const palette = this.type() === 'INCOME' ? incomePalette : expensePalette;
    let offset = 0;
    return [...(this.data()?.categories ?? [])]
      .filter(category => category.amount > 0)
      .sort((a, b) => b.amount - a.amount || a.categoryName.localeCompare(b.categoryName, 'pt-BR'))
      .map(category => {
        const share = total > 0 ? category.amount / total * 100 : 0;
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

  protected selectType(type: TransactionType): void {
    if (type === this.type()) return;
    this.expanded.set(false);
    this.typeChange.emit(type);
  }
}
