import { ComponentFixture, TestBed } from '@angular/core/testing';
import { vi } from 'vitest';
import { AnimatedNumber } from './animated-number';
import { ValuePrivacy, hiddenAmount } from '../../core/value-privacy';

describe('AnimatedNumber', () => {
  let fixture: ComponentFixture<AnimatedNumber>;
  let frames: Map<number, FrameRequestCallback>;
  let nextId: number;
  let media: { matches: boolean; addEventListener: ReturnType<typeof vi.fn>; removeEventListener: ReturnType<typeof vi.fn> };
  const money = (value: number) => new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(value);
  const visual = () => fixture.nativeElement.querySelector('[aria-hidden]').textContent;
  const step = (time: number) => {
    const callbacks = [...frames.values()];
    frames.clear();
    callbacks.forEach(callback => callback(time));
  };

  beforeEach(() => {
    frames = new Map();
    nextId = 0;
    media = { matches: false, addEventListener: vi.fn(), removeEventListener: vi.fn() };
    vi.stubGlobal('matchMedia', vi.fn(() => media));
    vi.stubGlobal('requestAnimationFrame', (callback: FrameRequestCallback) => {
      frames.set(++nextId, callback);
      return nextId;
    });
    vi.stubGlobal('cancelAnimationFrame', (id: number) => frames.delete(id));
    TestBed.configureTestingModule({ imports: [AnimatedNumber] });
    fixture = TestBed.createComponent(AnimatedNumber);
  });

  afterEach(() => {
    fixture.destroy();
    vi.unstubAllGlobals();
    localStorage.removeItem('finance-manager.values-hidden');
  });

  it('counts from zero and finishes at the exact amount with cents', () => {
    fixture.componentRef.setInput('value', 1234.56);
    fixture.detectChanges();
    expect(visual()).toBe(money(0));
    expect(fixture.nativeElement.querySelector('.sr-only').textContent).toBe(money(1234.56));
    step(0);
    step(450);
    expect(visual()).toBe(money(1234.56 * 0.875));
    step(900);
    expect(visual()).toBe(money(1234.56));
    expect(frames.size).toBe(0);
  });

  it('retargets from the current displayed amount and supports negative balances', () => {
    fixture.componentRef.setInput('value', 100);
    fixture.detectChanges();
    step(0);
    step(450);
    fixture.componentRef.setInput('value', -25.01);
    fixture.detectChanges();
    expect(frames.size).toBe(1);
    expect(visual()).toBe(money(87.5));
    step(500);
    step(1400);
    expect(visual()).toBe(money(-25.01));
  });

  it('shows the final value immediately when reduced motion is enabled', () => {
    media.matches = true;
    fixture.componentRef.setInput('value', 15);
    fixture.componentRef.setInput('format', 'integer');
    fixture.detectChanges();
    expect(visual()).toBe('15');
    expect(frames.size).toBe(0);
  });

  it('stops pending frames when destroyed', () => {
    fixture.componentRef.setInput('value', 100);
    fixture.detectChanges();
    fixture.destroy();
    expect(frames.size).toBe(0);
    expect(media.removeEventListener).toHaveBeenCalled();
  });

  it('cancels counting and conceals visual and accessible values, then animates the latest value', () => {
    const privacy = TestBed.inject(ValuePrivacy);
    fixture.componentRef.setInput('value', 100);
    fixture.detectChanges();
    step(0);
    step(450);
    privacy.toggle();
    fixture.detectChanges();
    expect(frames.size).toBe(0);
    expect(visual()).toBe(hiddenAmount);
    expect(fixture.nativeElement.querySelector('.sr-only').textContent).toBe(hiddenAmount);
    fixture.componentRef.setInput('value', 250.01);
    fixture.detectChanges();
    expect(visual()).toBe(hiddenAmount);
    expect(frames.size).toBe(0);
    privacy.toggle();
    fixture.detectChanges();
    step(500);
    step(1400);
    expect(visual()).toBe(money(250.01));
  });

  it('keeps integer counts visible when monetary values are hidden', () => {
    TestBed.inject(ValuePrivacy).toggle();
    fixture.componentRef.setInput('value', 15);
    fixture.componentRef.setInput('format', 'integer');
    fixture.detectChanges();
    step(0);
    step(900);
    expect(visual()).toBe('15');
  });
});
