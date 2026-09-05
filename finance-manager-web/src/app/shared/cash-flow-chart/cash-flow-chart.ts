import { Component, computed, input, output, signal } from '@angular/core';
import { LucideChartColumnIncreasing, LucideChartNoAxesCombined } from '@lucide/angular';
import { CashFlowPoint } from '../../core/report.models';

@Component({
  selector: 'app-cash-flow-chart',
  imports: [LucideChartColumnIncreasing, LucideChartNoAxesCombined],
  templateUrl: './cash-flow-chart.html',
  styleUrl: './cash-flow-chart.css',
})
export class CashFlowChart {
  readonly points = input<readonly CashFlowPoint[]>([]);
  readonly monthLabel = input.required<string>();
  readonly previousMonthLabel = input.required<string>();
  readonly previousMonth = output<void>();

  protected readonly view = signal<'bars' | 'lines'>('bars');
  protected readonly activeDate = signal<string | null>(null);
  protected readonly activePoint = computed(
    () => this.points().find((point) => point.date === this.activeDate()) ?? null,
  );
  protected readonly hasActivity = computed(() =>
    this.points().some((point) => point.totalIncome > 0 || point.totalExpense > 0),
  );
  protected readonly totals = computed(() =>
    this.points().reduce(
      (total, point) => ({
        income: total.income + point.totalIncome,
        expense: total.expense + point.totalExpense,
      }),
      { income: 0, expense: 0 },
    ),
  );

  // A escala começa em zero e usa intervalos legíveis, iguais para as duas séries.
  protected readonly scale = computed(() => {
    const peak = Math.max(0, ...this.points().flatMap((p) => [p.totalIncome, p.totalExpense]));
    if (peak === 0) return { ceiling: 100, step: 25 };
    const rawStep = peak / 4;
    const magnitude = 10 ** Math.floor(Math.log10(rawStep));
    const factor = [1, 2, 2.5, 5, 10].find((value) => value * magnitude >= rawStep) ?? 10;
    const step = Math.max(0.01, factor * magnitude);
    return { ceiling: step * 4, step };
  });

  protected readonly ticks = computed(() =>
    Array.from({ length: 5 }, (_, index) => ({
      position: index * 25,
      label: this.axisFormatter.format((4 - index) * this.scale().step),
    })),
  );

  protected readonly bars = computed(() => {
    const band = 1000 / Math.max(this.points().length, 1);
    return this.points().map((point, index) => ({
      ...point,
      x: (index + 0.5) * band,
      band,
      width: Math.min(band * 0.28, 18),
      incomeHeight: this.height(point.totalIncome),
      expenseHeight: this.height(point.totalExpense),
    }));
  });

  protected readonly dateLabels = computed(() => {
    const points = this.points();
    const last = points.length - 1;
    // Cinco rótulos mantêm o mês legível também em telas pequenas.
    return [
      ...new Set([0, Math.round(last / 4), Math.round(last / 2), Math.round((last * 3) / 4), last]),
    ]
      .filter((index) => index >= 0 && points[index])
      .map((index) => ({ label: points[index].date.slice(8), position: this.xPercent(index) }));
  });

  protected readonly incomeLine = computed(() => this.linePath('totalIncome'));
  protected readonly expenseLine = computed(() => this.linePath('totalExpense'));
  protected readonly incomeArea = computed(() => this.areaPath(this.incomeLine()));
  protected readonly expenseArea = computed(() => this.areaPath(this.expenseLine()));
  protected readonly activeX = computed(() => {
    const index = this.points().findIndex((point) => point.date === this.activeDate());
    return this.xPercent(Math.max(index, 0));
  });
  protected readonly tooltipX = computed(() => Math.min(78, Math.max(22, this.activeX())));

  private readonly currencyFormatter = new Intl.NumberFormat('pt-BR', {
    style: 'currency',
    currency: 'BRL',
  });
  private readonly axisFormatter = new Intl.NumberFormat('pt-BR', {
    notation: 'compact',
    maximumFractionDigits: 2,
  });
  private readonly dateFormatter = new Intl.DateTimeFormat('pt-BR', {
    day: '2-digit',
    month: 'long',
  });

  protected money(value: number): string {
    return this.currencyFormatter.format(value);
  }

  protected dateLabel(date: string): string {
    return this.dateFormatter.format(new Date(`${date}T00:00:00`));
  }

  protected yPercent(value: number): number {
    return 100 - (value / this.scale().ceiling) * 100;
  }

  protected selectFromPointer(event: PointerEvent): void {
    const bounds = (event.currentTarget as HTMLElement).getBoundingClientRect();
    const index = Math.floor(((event.clientX - bounds.left) / bounds.width) * this.points().length);
    this.selectIndex(index);
  }

  protected clearHover(event: PointerEvent): void {
    // O toque também dispara pointerleave ao soltar o dedo; mantenha o valor visível.
    if (event.pointerType !== 'touch') this.activeDate.set(null);
  }

  protected handleKeyboard(event: KeyboardEvent): void {
    const index = this.points().findIndex((point) => point.date === this.activeDate());
    switch (event.key) {
      case 'ArrowRight':
        this.selectIndex(index + 1);
        break;
      case 'ArrowLeft':
        this.selectIndex(index < 0 ? this.points().length - 1 : index - 1);
        break;
      case 'Home':
        this.selectIndex(0);
        break;
      case 'End':
        this.selectIndex(this.points().length - 1);
        break;
      case 'Escape':
        this.activeDate.set(null);
        break;
      default:
        return;
    }
    event.preventDefault();
  }

  private selectIndex(index: number): void {
    this.activeDate.set(
      this.points()[Math.min(this.points().length - 1, Math.max(0, index))]?.date ?? null,
    );
  }

  private xPercent(index: number): number {
    return ((index + 0.5) / Math.max(this.points().length, 1)) * 100;
  }

  private height(value: number): number {
    return (value / this.scale().ceiling) * 240;
  }

  private linePath(key: 'totalIncome' | 'totalExpense'): string {
    return this.points()
      .map(
        (point, index) =>
          `${index === 0 ? 'M' : 'L'} ${this.xPercent(index) * 10} ${240 - this.height(point[key])}`,
      )
      .join(' ');
  }

  private areaPath(line: string): string {
    if (!line) return '';
    return `${line} L ${this.xPercent(this.points().length - 1) * 10} 240 L ${this.xPercent(0) * 10} 240 Z`;
  }
}
