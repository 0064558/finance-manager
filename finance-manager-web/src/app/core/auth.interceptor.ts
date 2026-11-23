import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { tap } from 'rxjs';
import { Auth } from './auth';

const publicAuthEndpoints = new Set(['/api/v1/auth/login', '/api/v1/auth/register']);

export const authInterceptor: HttpInterceptorFn = (request, next) => {
  const path = request.url.split(/[?#]/, 1)[0];
  if (!path.startsWith('/api/v1/') || publicAuthEndpoints.has(path)) {
    return next(request);
  }

  const auth = inject(Auth);
  const router = inject(Router);
  const token = auth.getToken();
  const authenticatedRequest = token
    ? request.clone({ setHeaders: { Authorization: 'Bearer ' + token } })
    : request;

  return next(authenticatedRequest).pipe(tap({
    error: (error: unknown) => {
      // A parallel failure or a response for an older token cannot end the current session.
      if (!(error instanceof HttpErrorResponse) || error.status !== 401 || !token || auth.getToken() !== token) {
        return;
      }

      const pendingUrl = router.currentNavigation()?.finalUrl;
      const returnUrl = pendingUrl ? router.serializeUrl(pendingUrl) : router.url;
      auth.logout();
      void router.navigate(['/login'], {
        queryParams: { expired: 'true', returnUrl },
        replaceUrl: true,
      });
    },
  }));
};
