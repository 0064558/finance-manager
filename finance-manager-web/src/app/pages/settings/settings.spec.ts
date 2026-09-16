import { TestBed } from '@angular/core/testing';
import { vi } from 'vitest';
import { Theme } from '../../core/theme';
import { Settings } from './settings';

describe('Settings', () => {
  beforeEach(() => {
    localStorage.removeItem('finance-manager.theme');
    vi.stubGlobal('matchMedia', () => ({ matches: false }));
  });

  afterEach(() => {
    TestBed.resetTestingModule();
    localStorage.removeItem('finance-manager.theme');
    delete document.documentElement.dataset['theme'];
    vi.unstubAllGlobals();
  });

  it('changes and saves dark mode through an accessible switch', () => {
    const fixture = TestBed.createComponent(Settings);
    fixture.detectChanges();
    const control: HTMLButtonElement = fixture.nativeElement.querySelector('[role="switch"]');
    expect(control.getAttribute('aria-checked')).toBe('false');
    expect(control.getAttribute('aria-label')).toBe('Ativar modo escuro');
    control.click();
    fixture.detectChanges();
    expect(TestBed.inject(Theme).dark()).toBe(true);
    expect(control.getAttribute('aria-checked')).toBe('true');
    expect(control.getAttribute('aria-label')).toBe('Desativar modo escuro');
    expect(localStorage.getItem('finance-manager.theme')).toBe('dark');
  });
});
