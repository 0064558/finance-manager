import { TestBed } from '@angular/core/testing';
import { vi } from 'vitest';
import { Theme } from '../../core/theme';
import { ValuePrivacy } from '../../core/value-privacy';
import { Settings } from './settings';

describe('Settings', () => {
  beforeEach(() => {
    localStorage.removeItem('finance-manager.theme');
    localStorage.removeItem('finance-manager.values-hidden');
    vi.stubGlobal('matchMedia', () => ({ matches: false }));
  });

  afterEach(() => {
    TestBed.resetTestingModule();
    localStorage.removeItem('finance-manager.theme');
    localStorage.removeItem('finance-manager.values-hidden');
    delete document.documentElement.dataset['theme'];
    vi.unstubAllGlobals();
  });

  it('changes and saves dark mode through an accessible switch', () => {
    const fixture = TestBed.createComponent(Settings);
    fixture.detectChanges();
    const control: HTMLButtonElement = fixture.nativeElement.querySelector('.theme-switch');
    expect(control.getAttribute('aria-checked')).toBe('false');
    expect(control.getAttribute('aria-label')).toBe('Ativar modo escuro');
    control.click();
    fixture.detectChanges();
    expect(TestBed.inject(Theme).dark()).toBe(true);
    expect(control.getAttribute('aria-checked')).toBe('true');
    expect(control.getAttribute('aria-label')).toBe('Desativar modo escuro');
    expect(localStorage.getItem('finance-manager.theme')).toBe('dark');
  });

  it('keeps the privacy setting synchronized and saved', () => {
    const fixture = TestBed.createComponent(Settings);
    fixture.detectChanges();
    const control: HTMLButtonElement = fixture.nativeElement.querySelector('.privacy-switch');
    expect(control.getAttribute('aria-checked')).toBe('false');
    expect(control.getAttribute('aria-label')).toBe('Ocultar valores financeiros');
    control.click();
    fixture.detectChanges();
    expect(TestBed.inject(ValuePrivacy).hidden()).toBe(true);
    expect(control.getAttribute('aria-checked')).toBe('true');
    expect(control.getAttribute('aria-label')).toBe('Mostrar valores financeiros');
    expect(localStorage.getItem('finance-manager.values-hidden')).toBe('true');
    expect(fixture.nativeElement.textContent).toContain('••••••');
  });
});
