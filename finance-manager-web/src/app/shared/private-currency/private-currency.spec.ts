import { Component } from '@angular/core';
import { registerLocaleData } from '@angular/common';
import localePt from '@angular/common/locales/pt';
import { TestBed } from '@angular/core/testing';
import { ValuePrivacy, hiddenAmount } from '../../core/value-privacy';
import { PrivateCurrency } from './private-currency';
registerLocaleData(localePt);

@Component({ imports: [PrivateCurrency], template: `{{ amount | privateCurrency }}` })
class CurrencyHost { amount = 1234.56; }

describe('PrivateCurrency', () => {
  afterEach(() => localStorage.removeItem('finance-manager.values-hidden'));

  it('updates unchanged amounts on toggle and conceals new values until revealed', () => {
    const fixture = TestBed.createComponent(CurrencyHost);
    const privacy = TestBed.inject(ValuePrivacy);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('1.234,56');
    privacy.toggle();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent.trim()).toBe(hiddenAmount);
    fixture.componentInstance.amount = -25.01;
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent.trim()).toBe(hiddenAmount);
    privacy.toggle();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('25,01');
    expect(fixture.nativeElement.textContent).toContain('-');
  });
});
