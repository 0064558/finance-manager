import {
  Component, DestroyRef, ElementRef, NgZone, computed, effect, inject, input, viewChild,
} from '@angular/core';
import { ValuePrivacy, hiddenAmount } from '../../core/value-privacy';

const currencyFormatter = new Intl.NumberFormat('pt-BR', {
  style: 'currency', currency: 'BRL', minimumFractionDigits: 2, maximumFractionDigits: 2,
});
const integerFormatter = new Intl.NumberFormat('pt-BR', { maximumFractionDigits: 0 });

@Component({
  selector: 'app-animated-number',
  template: `<span #visual aria-hidden="true">{{ formattedValue() }}</span><span class="sr-only">{{ formattedValue() }}</span>`,
  styles: `:host { display: inline; font-variant-numeric: tabular-nums; }`,
})
export class AnimatedNumber {
  readonly value = input.required<number>();
  readonly format = input<'currency' | 'integer'>('currency');
  private readonly visual = viewChild<ElementRef<HTMLElement>>('visual');
  private readonly zone = inject(NgZone);
  private readonly privacy = inject(ValuePrivacy);
  private readonly masked = computed(() => this.format() === 'currency' && this.privacy.hidden());
  private readonly destroyRef = inject(DestroyRef);
  private displayedValue = 0;
  private frame: number | null = null;
  private readonly reducedMotion = typeof window !== 'undefined' && typeof window.matchMedia === 'function'
    ? window.matchMedia('(prefers-reduced-motion: reduce)') : null;
  protected readonly formattedValue = computed(() => this.masked() ? hiddenAmount : this.formatNumber(this.safeValue()));

  constructor() {
    const onMotionChange = () => {
      if (this.reducedMotion?.matches) {
        this.cancelFrame();
        this.render(this.safeValue());
      }
    };
    this.reducedMotion?.addEventListener('change', onMotionChange);
    this.destroyRef.onDestroy(() => {
      this.cancelFrame();
      this.reducedMotion?.removeEventListener('change', onMotionChange);
    });

    effect((onCleanup) => {
      const target = this.safeValue();
      const format = this.format();
      const element = this.visual()?.nativeElement;
      if (!element) return;
      onCleanup(() => this.cancelFrame());
      if (this.masked()) {
        this.cancelFrame();
        this.displayedValue = 0;
        element.textContent = hiddenAmount;
        return;
      }
      if (this.reducedMotion?.matches || typeof requestAnimationFrame !== 'function') {
        this.render(target);
        return;
      }

      const start = this.displayedValue;
      if (start === target) {
        this.render(target);
        return;
      }
      // Update only the visual text outside Angular; assistive technology reads the final value.
      this.zone.runOutsideAngular(() => {
        element.textContent = this.formatNumber(start, format);
        let startedAt: number | null = null;
        const tick = (timestamp: number) => {
          startedAt ??= timestamp;
          const progress = Math.min((timestamp - startedAt) / 900, 1);
          const eased = 1 - (1 - progress) ** 3;
          this.displayedValue = progress === 1 ? target : start + (target - start) * eased;
          element.textContent = this.formatNumber(this.displayedValue, format);
          this.frame = progress < 1 ? requestAnimationFrame(tick) : null;
        };
        this.frame = requestAnimationFrame(tick);
      });
    });
  }

  private safeValue(): number {
    return Number.isFinite(this.value()) ? this.value() : 0;
  }

  private formatNumber(value: number, format = this.format()): string {
    return (format === 'currency' ? currencyFormatter : integerFormatter).format(value);
  }

  private render(value: number): void {
    this.displayedValue = value;
    const element = this.visual()?.nativeElement;
    if (element) element.textContent = this.masked() ? hiddenAmount : this.formatNumber(value);
  }

  private cancelFrame(): void {
    if (this.frame !== null) cancelAnimationFrame(this.frame);
    this.frame = null;
  }
}
