import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { vi } from 'vitest';
import { Auth } from '../../core/auth';
import { ValuePrivacy } from '../../core/value-privacy';
import { AppShell } from './app-shell';

describe('AppShell privacy control', () => {
  beforeEach(() => vi.stubGlobal('matchMedia', () => ({ matches: false })));
  afterEach(() => {
    localStorage.removeItem('finance-manager.values-hidden');
    vi.unstubAllGlobals();
  });

  it('toggles global privacy with matching accessible labels and state', () => {
    TestBed.configureTestingModule({
      imports: [AppShell],
      providers: [provideRouter([]), { provide: Auth, useValue: { getCurrentUser: () => of({ name: 'Maria' }) } }],
    });
    const fixture = TestBed.createComponent(AppShell);
    fixture.detectChanges();
    const button: HTMLButtonElement = fixture.nativeElement.querySelector('.privacy-button');
    expect(button.getAttribute('aria-label')).toBe('Ocultar valores');
    expect(button.getAttribute('aria-pressed')).toBe('false');
    button.click();
    fixture.detectChanges();
    expect(TestBed.inject(ValuePrivacy).hidden()).toBe(true);
    expect(button.getAttribute('aria-label')).toBe('Mostrar valores');
    expect(button.getAttribute('aria-pressed')).toBe('true');
    expect(button.title).toBe('Mostrar valores');
    button.click();
    fixture.detectChanges();
    expect(TestBed.inject(ValuePrivacy).hidden()).toBe(false);
    expect(button.getAttribute('aria-label')).toBe('Ocultar valores');
  });
});
