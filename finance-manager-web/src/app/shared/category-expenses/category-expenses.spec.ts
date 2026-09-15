import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { registerLocaleData } from '@angular/common';
import localePt from '@angular/common/locales/pt';
import { CategoryExpenses } from './category-expenses';
import { CategoryExpense } from '../../core/report.models';

describe('CategoryExpenses', () => {
  let fixture: ComponentFixture<CategoryExpenses>;
  const category = (id: string, name: string, amount: number): CategoryExpense => ({ categoryId: id, categoryName: name, totalExpense: amount });
  const setCategories = (categories: CategoryExpense[]) => {
    fixture.componentRef.setInput('data', {
      startDate: '2026-08-01', endDate: '2026-08-31',
      totalExpense: categories.reduce((sum, item) => sum + item.totalExpense, 0), categories,
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
