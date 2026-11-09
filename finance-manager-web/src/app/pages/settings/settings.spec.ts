import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { of, Subject, throwError } from 'rxjs';
import { vi } from 'vitest';
import { ChatHistoryActions } from '../../core/chat-history-actions';
import { Notifications } from '../../core/notifications';
import { Theme } from '../../core/theme';
import { ValuePrivacy } from '../../core/value-privacy';
import { Settings } from './settings';

describe('Settings', () => {
  const busy = signal(false);
  const chatInUse = signal(false);
  const clear = vi.fn();

  beforeEach(() => {
    localStorage.removeItem('finance-manager.theme');
    localStorage.removeItem('finance-manager.values-hidden');
    vi.stubGlobal('matchMedia', () => ({ matches: false }));
    busy.set(false);
    chatInUse.set(false);
    clear.mockReset();
    TestBed.configureTestingModule({ providers: [
      { provide: ChatHistoryActions, useValue: { busy, chatInUse, clear } },
    ] });
  });

  afterEach(() => {
    TestBed.resetTestingModule();
    localStorage.removeItem('finance-manager.theme');
    localStorage.removeItem('finance-manager.values-hidden');
    delete document.documentElement.dataset['theme'];
    vi.unstubAllGlobals();
  });

  it('changes and saves dark mode through an accessible switch', () => {
    const fixture = TestBed.createComponent(Settings);
    fixture.detectChanges();
    const control: HTMLButtonElement = fixture.nativeElement.querySelector('.theme-switch');
    expect(control.getAttribute('aria-checked')).toBe('false');
    expect(control.getAttribute('aria-label')).toBe('Ativar modo escuro');
    control.click();
    fixture.detectChanges();
    expect(TestBed.inject(Theme).dark()).toBe(true);
    expect(control.getAttribute('aria-checked')).toBe('true');
    expect(control.getAttribute('aria-label')).toBe('Desativar modo escuro');
    expect(localStorage.getItem('finance-manager.theme')).toBe('dark');
  });

  it('keeps the privacy setting synchronized and saved', () => {
    const fixture = TestBed.createComponent(Settings);
    fixture.detectChanges();
    const control: HTMLButtonElement = fixture.nativeElement.querySelector('.privacy-switch');
    expect(control.getAttribute('aria-checked')).toBe('false');
    expect(control.getAttribute('aria-label')).toBe('Ocultar valores financeiros');
    control.click();
    fixture.detectChanges();
    expect(TestBed.inject(ValuePrivacy).hidden()).toBe(true);
    expect(control.getAttribute('aria-checked')).toBe('true');
    expect(control.getAttribute('aria-label')).toBe('Mostrar valores financeiros');
    expect(localStorage.getItem('finance-manager.values-hidden')).toBe('true');
    expect(fixture.nativeElement.textContent).toContain('••••••');
  });

  function renderHistorySettings() {
    const fixture = TestBed.createComponent(Settings);
    fixture.detectChanges();
    const page: HTMLElement = fixture.nativeElement;
    const trigger = page.querySelector<HTMLButtonElement>('.history-trigger')!;
    const dialog = page.querySelector('dialog')!;
    const cancel = dialog.querySelector<HTMLButtonElement>('button[autofocus]')!;
    const confirm = dialog.querySelector<HTMLButtonElement>('.history-confirm')!;
    // jsdom does not implement the native modal API.
    Object.defineProperties(dialog, {
      showModal: { value: () => { dialog.open = true; } },
      close: { value: () => { dialog.open = false; } },
    });
    return { fixture, trigger, dialog, cancel, confirm };
  }

  it('describes which records are erased and only clears after confirmation', () => {
    const { fixture, trigger, dialog, cancel, confirm } = renderHistorySettings();
    const result = new Subject<void>();
    clear.mockReturnValue(result);

    trigger.click();
    expect(dialog.open).toBe(true);
    expect(dialog.textContent).toContain('rascunhos pendentes');
    expect(dialog.textContent).toContain('As transações já registradas serão mantidas.');
    expect(clear).not.toHaveBeenCalled();
    cancel.click();
    expect(dialog.open).toBe(false);
    expect(clear).not.toHaveBeenCalled();

    trigger.click();
    confirm.click();
    confirm.click();
    fixture.detectChanges();
    expect(clear).toHaveBeenCalledOnce();
    expect(confirm.disabled).toBe(true);
    const escape = new Event('cancel', { cancelable: true });
    dialog.dispatchEvent(escape);
    expect(escape.defaultPrevented).toBe(true);
    expect(dialog.open).toBe(true);

    result.next();
    result.complete();
    expect(dialog.open).toBe(false);
    expect(TestBed.inject(Notifications).items()).toEqual([
      expect.objectContaining({ kind: 'success', message: 'Histórico do chat limpo.' }),
    ]);
  });

  it('waits for chat activity and keeps the confirmation open for retry on failure', () => {
    const { fixture, trigger, dialog, confirm } = renderHistorySettings();
    chatInUse.set(true);
    fixture.detectChanges();
    expect(trigger.getAttribute('aria-disabled')).toBe('true');
    trigger.click();
    expect(dialog.open).toBe(false);

    chatInUse.set(false);
    trigger.click();
    busy.set(true);
    fixture.detectChanges();
    expect(confirm.disabled).toBe(true);
    confirm.click();
    expect(clear).not.toHaveBeenCalled();

    busy.set(false);
    clear.mockReturnValueOnce(throwError(() => new Error('Request failed'))).mockReturnValueOnce(of(undefined));
    fixture.detectChanges();
    confirm.click();
    fixture.detectChanges();
    expect(dialog.open).toBe(true);
    expect(dialog.querySelector('[role="alert"]')?.textContent).toContain('Não foi possível limpar o histórico');
    expect(confirm.disabled).toBe(false);
    expect(TestBed.inject(Notifications).items()).toEqual([]);

    confirm.click();
    expect(clear).toHaveBeenCalledTimes(2);
    expect(dialog.open).toBe(false);
  });
});
