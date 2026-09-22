import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { Component } from '@angular/core';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { of } from 'rxjs';
import { vi } from 'vitest';
import { Auth } from '../../core/auth';
import { apiUrlInterceptor } from '../../core/api-url-interceptor';
import { authInterceptor } from '../../core/auth.interceptor';
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

describe('AppShell onboarding guide', () => {
  beforeEach(() => vi.stubGlobal('matchMedia', () => ({ matches: false })));
  afterEach(() => vi.unstubAllGlobals());

  it('keeps the page visible and follows manual navigation between sections', async () => {
    const updateOnboardingVersion = vi.fn(() => of({ name: 'Maria', onboardingVersion: 1 }));
    TestBed.configureTestingModule({
      imports: [AppShell],
      providers: [
        provideRouter(['dashboard', 'accounts', 'transactions', 'categories', 'settings']
          .map(path => ({ path, component: NavigationPage }))),
        { provide: Auth, useValue: {
          getCurrentUser: () => of({ name: 'Maria', onboardingVersion: 0 }),
          updateOnboardingVersion,
        } },
      ],
    });
    const router = TestBed.inject(Router);
    await router.navigateByUrl('/dashboard');
    const fixture = TestBed.createComponent(AppShell);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.onboarding-backdrop')).toBeNull();
    expect(fixture.nativeElement.querySelector('.onboarding-guide[role="region"]')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('.app-shell').getAttribute('data-onboarding-step')).toBe('dashboard');
    expect(fixture.nativeElement.querySelector('.sidebar-link--tour-target').getAttribute('href')).toBe('/dashboard');
    expect(fixture.nativeElement.textContent).toContain('Conteúdo da página');

    fixture.nativeElement.querySelector('.onboarding-next-button').click();
    await fixture.whenStable();
    fixture.detectChanges();
    expect(router.url).toBe('/accounts');
    expect(fixture.nativeElement.querySelector('.app-shell').getAttribute('data-onboarding-step')).toBe('accounts');

    fixture.nativeElement.querySelector('.sidebar-link[href="/transactions"]').click();
    await fixture.whenStable();
    fixture.detectChanges();
    expect(router.url).toBe('/transactions');
    expect(fixture.nativeElement.querySelector('.app-shell').getAttribute('data-onboarding-step')).toBe('transactions');
    expect(fixture.nativeElement.querySelector('.onboarding-guide__section').textContent).toContain('TRANSAÇÕES');

    fixture.nativeElement.querySelector('.onboarding-skip').click();
    fixture.detectChanges();
    expect(updateOnboardingVersion).toHaveBeenCalledWith({ onboardingVersion: 1 });
    expect(fixture.nativeElement.querySelector('.onboarding-guide')).toBeNull();
  });

  it('requests the authenticated user and opens the guide for an asynchronous HTTP response with version 0', async () => {
    localStorage.setItem('finance-manager.access-token', 'test-token');
    try {
      TestBed.configureTestingModule({
        imports: [AppShell],
        providers: [
          provideRouter([{ path: 'dashboard', component: NavigationPage }]),
          provideHttpClient(withInterceptors([apiUrlInterceptor, authInterceptor])),
          provideHttpClientTesting(),
        ],
      });
      await TestBed.inject(Router).navigateByUrl('/dashboard');
      const fixture = TestBed.createComponent(AppShell);
      fixture.detectChanges();

      const request = TestBed.inject(HttpTestingController)
        .expectOne(({ url }) => url.endsWith('/api/v1/auth/me'));
      expect(request.request.headers.get('Authorization')).toBe('Bearer test-token');
      request.flush({
        id: '26ad795e-dcd0-4030-b09b-902288545825',
        name: 'User 2',
        email: 'user2@gmail.com',
        createdAt: '2026-09-22T21:13:04.424168Z',
        onboardingVersion: 0,
      });
      await fixture.whenStable();
      fixture.detectChanges();

      expect(fixture.nativeElement.querySelector('.onboarding-guide')).not.toBeNull();
      expect(fixture.nativeElement.querySelector('.app-shell').getAttribute('data-onboarding-step')).toBe('dashboard');
      TestBed.inject(HttpTestingController).verify();
    } finally {
      localStorage.removeItem('finance-manager.access-token');
    }
  });
});
