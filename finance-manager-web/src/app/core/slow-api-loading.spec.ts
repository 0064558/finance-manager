import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { vi } from 'vitest';
import { SlowApiLoading } from './slow-api-loading';
import { slowApiLoadingInterceptor } from './slow-api-loading.interceptor';

describe('SlowApiLoading', () => {
  beforeEach(() => vi.useFakeTimers());
  afterEach(() => vi.useRealTimers());

  it('does not show a notice for a request completed before five seconds', () => {
    const loading = TestBed.inject(SlowApiLoading);
    const stopTracking = loading.trackRequest();

    vi.advanceTimersByTime(4_999);
    expect(loading.isSlow()).toBe(false);
    stopTracking();
    vi.advanceTimersByTime(1);
    expect(loading.isSlow()).toBe(false);
  });

  it('stays visible only while a request has individually waited five seconds', () => {
    const loading = TestBed.inject(SlowApiLoading);
    const stopFirst = loading.trackRequest();
    vi.advanceTimersByTime(3_000);
    const stopSecond = loading.trackRequest();

    vi.advanceTimersByTime(2_000);
    expect(loading.isSlow()).toBe(true);

    stopFirst();
    expect(loading.isSlow()).toBe(false);
    vi.advanceTimersByTime(3_000);
    expect(loading.isSlow()).toBe(true);

    stopSecond();
    expect(loading.isSlow()).toBe(false);
  });

  it('tracks an HTTP request until it completes', () => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([slowApiLoadingInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    const loading = TestBed.inject(SlowApiLoading);
    TestBed.inject(HttpClient).get('/api/v1/auth/me').subscribe();
    const request = TestBed.inject(HttpTestingController).expectOne('/api/v1/auth/me');

    vi.advanceTimersByTime(5_000);
    expect(loading.isSlow()).toBe(true);
    request.flush({ name: 'User 2' });
    expect(loading.isSlow()).toBe(false);
    TestBed.inject(HttpTestingController).verify();
  });
});
