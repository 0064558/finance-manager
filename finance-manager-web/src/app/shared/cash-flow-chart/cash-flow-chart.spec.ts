import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CashFlowChart } from './cash-flow-chart';
import { CashFlowPoint } from '../../core/report.models';

describe('CashFlowChart', () => {
  let fixture: ComponentFixture<CashFlowChart>;
  const point = (date: string, income: number, expense = 0): CashFlowPoint => ({
    date,
    totalIncome: income,
    totalExpense: expense,
    netBalance: income - expense,
  });

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [CashFlowChart] });
    fixture = TestBed.createComponent(CashFlowChart);
    fixture.componentRef.setInput('monthLabel', 'agosto de 2026');
    fixture.componentRef.setInput('previousMonthLabel', 'julho de 2026');
  });

  it('preserves the ratio of small amounts and keeps zero at the baseline', () => {
    fixture.componentRef.setInput('points', [
      point('2026-08-01', 1000),
      point('2026-08-02', 1),
      point('2026-08-03', 0),
    ]);
    fixture.detectChanges();
    const bars = fixture.nativeElement.querySelectorAll('.income-bar');
    const heights = [...bars].map((bar: Element) => Number(bar.getAttribute('height')));
    expect(heights[0] / heights[1]).toBeCloseTo(1000);
    expect(heights[2]).toBe(0);
    expect(heights[0]).toBeLessThanOrEqual(240);
  });

  it('allows keyboard inspection and switches to lines without changing the values', () => {
    fixture.componentRef.setInput('points', [
      point('2026-08-01', 150, 25),
      point('2026-08-02', 0, 10),
    ]);
    fixture.detectChanges();
    const plot = fixture.nativeElement.querySelector('.plot');
    plot.dispatchEvent(new KeyboardEvent('keydown', { key: 'Home' }));
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.chart-tooltip').textContent).toContain('150,00');
    const totals = fixture.nativeElement.querySelector('.chart-totals').textContent;
    fixture.nativeElement.querySelectorAll('.view-switcher button')[1].click();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.income-line')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('.chart-totals').textContent).toBe(totals);
    plot.dispatchEvent(new KeyboardEvent('keydown', { key: 'End' }));
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.chart-tooltip').textContent).toContain(
      '02 de agosto',
    );
    plot.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.chart-tooltip')).toBeNull();
  });

  it('shows an honest empty state and emits previous-month navigation', () => {
    fixture.componentRef.setInput('points', [point('2026-08-01', 0)]);
    const previous = vi.fn();
    fixture.componentInstance.previousMonth.subscribe(previous);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.chart-empty')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('.plot').hasAttribute('tabindex')).toBe(false);
    fixture.nativeElement.querySelector('.chart-empty button').click();
    expect(previous).toHaveBeenCalledOnce();
  });

  it('keeps the tooltip open after releasing a touch, but dismisses mouse hover', () => {
    fixture.componentRef.setInput('points', [point('2026-08-01', 150)]);
    fixture.detectChanges();
    const plot = fixture.nativeElement.querySelector('.plot');
    plot.dispatchEvent(new KeyboardEvent('keydown', { key: 'Home' }));
    plot.dispatchEvent(Object.assign(new Event('pointerleave'), { pointerType: 'touch' }));
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.chart-tooltip')).not.toBeNull();
    plot.dispatchEvent(Object.assign(new Event('pointerleave'), { pointerType: 'mouse' }));
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.chart-tooltip')).toBeNull();
  });

  it('supports cents and clears a selected date when the period changes', () => {
    fixture.componentRef.setInput('points', [point('2026-08-01', 0.01, 0.02)]);
    fixture.detectChanges();
    fixture.nativeElement
      .querySelector('.plot')
      .dispatchEvent(new KeyboardEvent('keydown', { key: 'Home' }));
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.chart-tooltip').textContent).toContain('0,01');
    const height = Number(
      fixture.nativeElement.querySelector('.income-bar').getAttribute('height'),
    );
    expect(height).toBeGreaterThan(0);
    expect(height).toBeLessThanOrEqual(240);
    fixture.componentRef.setInput('points', [point('2026-09-01', 250)]);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.chart-tooltip')).toBeNull();
  });
});
