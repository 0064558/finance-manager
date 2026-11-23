import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { Login } from '../pages/login/login';
import { authGuard } from './auth-guard';
import { authInterceptor } from './auth.interceptor';

@Component({ template: 'Minhas contas' })
class AccountsStub {}

describe('expired session', () => {
  const tokenKey = 'finance-manager.access-token';
  const accountsUrl = '/accounts?tab=history#latest';

  beforeEach(() => {
    localStorage.removeItem(tokenKey);
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
        provideRouter([
          { path: 'login', component: Login },
          { path: 'accounts', component: AccountsStub, canActivate: [authGuard] },
        ]),
      ],
    });
  });

  afterEach(() => {
    TestBed.inject(HttpTestingController).verify();
    localStorage.removeItem(tokenKey);
  });

  it('retoma a URL após login quando duas requisições recebem 401', async () => {
    localStorage.setItem(tokenKey, 'expired-token');
    const harness = await RouterTestingHarness.create(accountsUrl);
    const router = TestBed.inject(Router);
    const http = TestBed.inject(HttpClient);
    const requests = TestBed.inject(HttpTestingController);
    const redirect = vi.spyOn(router, 'navigate');
    const unauthorized = { status: 401, statusText: 'Unauthorized' };
    http.get('/api/v1/auth/me').subscribe({ error: () => {} });
    http.get('/api/v1/transfers').subscribe({ error: () => {} });
    const profile = requests.expectOne('/api/v1/auth/me');
    expect(profile.request.headers.get('Authorization')).toBe('Bearer expired-token');
    profile.flush({}, unauthorized);
    requests.expectOne('/api/v1/transfers').flush({}, unauthorized);

    await harness.fixture.whenStable();
    harness.detectChanges();
    expect(redirect).toHaveBeenCalledOnce();
    expect(localStorage.getItem(tokenKey)).toBeNull();
    expect(router.parseUrl(router.url).queryParams['returnUrl']).toBe(accountsUrl);
    expect(harness.routeNativeElement?.textContent).toContain('Sua sessão expirou');

    const login = harness.routeDebugElement!.componentInstance as Login;
    login['loginForm'].setValue({ email: 'test@example.com', password: 'password123' });
    login['submit']();
    const rejectedLogin = requests.expectOne('/api/v1/auth/login');
    expect(rejectedLogin.request.headers.has('Authorization')).toBe(false);
    rejectedLogin.flush({ code: 'INVALID_CREDENTIALS' }, unauthorized);
    expect(redirect).toHaveBeenCalledOnce();

    login['submit']();
    requests.expectOne('/api/v1/auth/login').flush({ accessToken: 'fresh-token' });
    await harness.fixture.whenStable();
    expect(localStorage.getItem(tokenKey)).toBe('fresh-token');
    expect(router.url).toBe(accountsUrl);
  });

  it('carrega o login com a URL original quando o acesso começa sem token', async () => {
    const harness = await RouterTestingHarness.create(accountsUrl);
    const router = TestBed.inject(Router);
    expect(router.parseUrl(router.url).queryParams['returnUrl']).toBe(accountsUrl);
    expect(harness.routeNativeElement?.textContent).not.toContain('Sua sessão expirou');
  });
});
