import { Injectable, signal } from '@angular/core';

const SLOW_REQUEST_DELAY_MS = 5_000;

@Injectable({ providedIn: 'root' })
export class SlowApiLoading {
  private readonly pendingRequests = new Map<symbol, number>();
  private readonly isSlowSignal = signal(false);
  private timer: ReturnType<typeof setTimeout> | null = null;

  readonly isSlow = this.isSlowSignal.asReadonly();

  trackRequest(): () => void {
    const requestId = Symbol('api-request');
    this.pendingRequests.set(requestId, Date.now());
    this.updateNotice();

    return () => {
      if (this.pendingRequests.delete(requestId)) {
        this.updateNotice();
      }
    };
  }

  private updateNotice(): void {
    if (this.timer !== null) {
      clearTimeout(this.timer);
      this.timer = null;
    }

    const oldestRequestStartedAt = Math.min(...this.pendingRequests.values());
    if (!Number.isFinite(oldestRequestStartedAt)) {
      this.isSlowSignal.set(false);
      return;
    }

    const timeUntilNotice = SLOW_REQUEST_DELAY_MS - (Date.now() - oldestRequestStartedAt);
    if (timeUntilNotice <= 0) {
      this.isSlowSignal.set(true);
      return;
    }

    this.isSlowSignal.set(false);
    this.timer = setTimeout(() => this.updateNotice(), timeUntilNotice);
  }
}
