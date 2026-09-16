import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { Component } from '@angular/core';
import { of } from 'rxjs';
import { vi } from 'vitest';
import { Auth } from '../../core/auth';
import { ValuePrivacy } from '../../core/value-privacy';
import { AppShell } from './app-shell';

@Component({ template: '<p>Conteúdo da página</p>' })
class NavigationPage {}

describe('AppShell privacy control', () => {
  beforeEach(() => vi.stubGlobal('matchMedia', () => ({ matches: false })));
  afterEach(() => {
    localStorage.removeItem('finance-manager.values-hidden');
    localStorage.removeItem('finance-manager.theme');
    delete document.documentElement.dataset['theme'];
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

  it('opens settings from the sidebar', async () => {
    TestBed.configureTestingModule({
      imports: [AppShell],
      providers: [
        provideRouter([
          { path: 'dashboard', component: NavigationPage },
          { path: 'settings', component: NavigationPage },
        ]),
        { provide: Auth, useValue: { getCurrentUser: () => of({ name: 'Maria' }) } },
      ],
    });
    const fixture = TestBed.createComponent(AppShell);
    const router = TestBed.inject(Router);
    await router.navigateByUrl('/dashboard');
    fixture.detectChanges();
    const link: HTMLAnchorElement = fixture.nativeElement.querySelector('.sidebar-link--settings');
    expect(link.getAttribute('href')).toBe('/settings');
    link.click();
    await fixture.whenStable();
    fixture.detectChanges();
    expect(router.url).toBe('/settings');
    expect(link.getAttribute('aria-current')).toBe('page');
  });

});

describe('AppShell mobile navigation', () => {
  beforeEach(() => {
    vi.stubGlobal('matchMedia', () => ({ matches: true }));
    TestBed.configureTestingModule({
      imports: [AppShell],
      providers: [
        provideRouter(['dashboard', 'accounts', 'transactions', 'categories'].map(path => ({ path, component: NavigationPage }))),
        { provide: Auth, useValue: { getCurrentUser: () => of({ name: 'Maria' }) } },
      ],
    });
  });
  afterEach(() => vi.unstubAllGlobals());

  it('navigates through all shortcuts and marks only the current page', async () => {
    const fixture = TestBed.createComponent(AppShell);
    const router = TestBed.inject(Router);
    fixture.detectChanges();
    await router.navigateByUrl('/dashboard');
    await fixture.whenStable();
    fixture.detectChanges();
    const links: HTMLAnchorElement[] = [...fixture.nativeElement.querySelectorAll('.mobile-bottom-nav a')];
    for (const link of links) {
      link.click();
      await fixture.whenStable();
      fixture.detectChanges();
      expect(router.url).toBe(link.getAttribute('href'));
      expect(link.getAttribute('aria-current')).toBe('page');
      expect(fixture.nativeElement.querySelectorAll('.mobile-bottom-nav [aria-current="page"]').length).toBe(1);
      expect(fixture.nativeElement.textContent).toContain('Conteúdo da página');
    }
  });

  it('keeps the right shortcut active on creation links and external route changes', async () => {
    const fixture = TestBed.createComponent(AppShell);
    const router = TestBed.inject(Router);
    fixture.detectChanges();
    await router.navigateByUrl('/accounts?action=create');
    await fixture.whenStable();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.mobile-bottom-nav [aria-current="page"]').textContent).toContain('Contas');
    await router.navigateByUrl('/transactions');
    await fixture.whenStable();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.mobile-bottom-nav [aria-current="page"]').textContent).toContain('Transações');
    fixture.nativeElement.querySelector('.menu-button').click();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.mobile-bottom-nav').hasAttribute('inert')).toBe(true);
    fixture.nativeElement.querySelector('.sidebar-close').click();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.mobile-bottom-nav').hasAttribute('inert')).toBe(false);
  });
});
