import { CurrencyPipe } from '@angular/common';
import { Pipe, PipeTransform, inject } from '@angular/core';
import { ValuePrivacy, hiddenAmount } from '../../core/value-privacy';

@Pipe({ name: 'privateCurrency', pure: false })
export class PrivateCurrency implements PipeTransform {
  private readonly privacy = inject(ValuePrivacy);
  private readonly currency = new CurrencyPipe('pt-BR');

  transform(value: number | string | null | undefined, currencyCode = 'BRL',
    display = 'symbol', digitsInfo = '1.2-2', locale = 'pt-BR'): string | null {
    return this.privacy.hidden() ? hiddenAmount
      : this.currency.transform(value, currencyCode, display, digitsInfo, locale);
  }
}
