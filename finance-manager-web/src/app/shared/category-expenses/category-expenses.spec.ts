import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { registerLocaleData } from '@angular/common';
import localePt from '@angular/common/locales/pt';
import { CategoryExpenses } from './category-expenses';
import { CategoryTotal } from '../../core/report.models';

describe('CategoryExpenses', () => {
  let fixture: ComponentFixture<CategoryExpenses>;
  const category = (id: string, name: string, amount: number): CategoryTotal => ({ categoryId: id, categoryName: name, amount: amount });
  const setCategories = (categories: CategoryTotal[]) => {
    fixture.componentRef.setInput('data', {
      startDate: '2026-08-01', endDate: '2026-08-31',
      type: 'EXPENSE', totalAmount: categories.reduce((sum, item) => sum + item.amount, 0), categories,
    });
    fixture.detectChanges();
  };
  beforeEach(() => {
    registerLocaleData(localePt, 'pt-BR');
    TestBed.configureTestingModule({ imports: [CategoryExpenses], providers: [provideRouter([])] });
    fixture = TestBed.createComponent(CategoryExpenses);
    fixture.componentRef.setInput('monthLabel', 'agosto de 2026');
  });
  it('ranks expenses and calculates proportional bars and ring segments including cents', () => {
    setCategories([category('food', 'Alimentação', 25.01), category('home', 'Moradia', 75.03)]);
    const rows = fixture.nativeElement.querySelectorAll('.expense-category-row');
    expect(rows[0].textContent).toContain('Moradia');
    expect(rows[0].textContent).toContain('75%');
    expect(rows[1].textContent).toContain('25,01');
    expect(rows[1].querySelector('.share-track span').style.width).toBe('25%');
    const segments = fixture.nativeElement.querySelectorAll('.ring-segment');
    expect(segments[0].getAttribute('stroke-dasharray')).toBe('75 25');
    expect(segments[1].getAttribute('stroke-dashoffset')).toBe('-75');
    expect(fixture.nativeElement.querySelector('.ring-center').textContent).toContain('100,04');
  });
  it('includes every category in the ring while allowing a short list to expand', () => {
    setCategories(Array.from({ length: 7 }, (_, i) => category(`category-${i}`, `Categoria ${i}`, 10)));
    expect(fixture.nativeElement.querySelectorAll('.ring-segment').length).toBe(7);
    expect(fixture.nativeElement.querySelectorAll('.expense-category-row').length).toBe(5);
    const button = fixture.nativeElement.querySelector('.expand-button');
    expect(button.textContent).toContain('Ver mais 2 categorias');
    button.click();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelectorAll('.expense-category-row').length).toBe(7);
    expect(button.getAttribute('aria-expanded')).toBe('true');
  });
  it('keeps the category color when the ranking changes', () => {
    setCategories([category('food', 'Alimentação', 90), category('home', 'Moradia', 10)]);
    const firstColor = fixture.nativeElement.querySelector('.ring-segment').getAttribute('stroke');
    setCategories([category('food', 'Alimentação', 10), category('home', 'Moradia', 90)]);
    expect(fixture.nativeElement.querySelectorAll('.ring-segment')[1].getAttribute('stroke')).toBe(firstColor);
  });
  it('switches to income with matching labels, totals and percentages', () => {
    setCategories([category('food', 'Alimentação', 50)]);
    const buttons = fixture.nativeElement.querySelectorAll('.category-view-switcher button');
    expect(buttons[0].getAttribute('aria-pressed')).toBe('true');
    let selected = '';
    fixture.componentInstance.typeChange.subscribe(type => {
      selected = type;
      fixture.componentRef.setInput('type', type);
      fixture.componentRef.setInput('data', {
        type, totalAmount: 5000.05,
        categories: [category('salary', 'Salário', 4000.04), category('extra', 'Trabalhos extras', 1000.01)],
      });
    });
    buttons[1].click();
    fixture.detectChanges();
    expect(selected).toBe('INCOME');
    expect(buttons[1].getAttribute('aria-pressed')).toBe('true');
    expect(buttons[0].getAttribute('aria-pressed')).toBe('false');
    expect(fixture.nativeElement.querySelector('h2').textContent).toBe('Receitas por categoria');
    expect(fixture.nativeElement.querySelector('.ring-center').textContent).toContain('5.000,05');
    expect(fixture.nativeElement.querySelector('.expense-category-row').textContent).toContain('Salário');
    expect(fixture.nativeElement.querySelector('.share-track span').style.width).toBe('80%');
    expect(fixture.nativeElement.querySelector('.distribution-insight').textContent).toContain('das receitas');
  });

  it('shows a single income source as 100% and distinguishes income loading, empty and error states', () => {
    fixture.componentRef.setInput('type', 'INCOME');
    setCategories([category('salary', 'Salário', 6000)]);
    expect(fixture.nativeElement.querySelector('.ring-segment').getAttribute('stroke-dasharray')).toBe('100 0');
    setCategories([]);
    expect(fixture.nativeElement.textContent).toContain('Um mês sem receitas');
    fixture.componentRef.setInput('loading', true);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[role="status"]').textContent).toContain('Carregando receitas');
    fixture.componentRef.setInput('loading', false);
    fixture.componentRef.setInput('error', true);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[role="alert"]').textContent).toContain('carregar as receitas');
  });

  it('uses warm colors for expenses and green colors for income', () => {
    const categories = Array.from({ length: 8 }, (_, i) => category(`source-${i}`, `Categoria ${i}`, 10));
    setCategories(categories);
    const colors = () => [...fixture.nativeElement.querySelectorAll('.ring-segment')].map((segment: any) => {
      const hex = segment.getAttribute('stroke');
      return { red: parseInt(hex.slice(1, 3), 16), green: parseInt(hex.slice(3, 5), 16) };
    });
    expect(colors().every(color => color.red > color.green)).toBe(true);
    fixture.componentRef.setInput('type', 'INCOME');
    fixture.detectChanges();
    expect(colors().every(color => color.green > color.red)).toBe(true);
    expect(fixture.nativeElement.querySelector('.expenses-panel--income')).not.toBeNull();
  });

  it('distinguishes no expenses, loading and an error with a retry action', () => {
    setCategories([]);
    expect(fixture.nativeElement.textContent).toContain('Um mês sem despesas');
    expect(fixture.nativeElement.querySelector('.ring-svg')).toBeNull();
    fixture.componentRef.setInput('loading', true);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[role="status"]')).not.toBeNull();
    fixture.componentRef.setInput('loading', false);
    fixture.componentRef.setInput('error', true);
    fixture.detectChanges();
    let retried = false;
    fixture.componentInstance.retry.subscribe(() => retried = true);
    fixture.nativeElement.querySelector('.retry-button').click();
    expect(retried).toBe(true);
    expect(fixture.nativeElement.querySelector('[role="alert"]')).not.toBeNull();
  });
});
