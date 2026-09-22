import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { defer, finalize } from 'rxjs';
import { SlowApiLoading } from './slow-api-loading';

export const slowApiLoadingInterceptor: HttpInterceptorFn = (request, next) => {
  const loading = inject(SlowApiLoading);

  return defer(() => {
    const stopTracking = loading.trackRequest();
    return next(request).pipe(finalize(stopTracking));
  });
};
