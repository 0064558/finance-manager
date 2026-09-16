import { TestBed } from '@angular/core/testing';
import { vi } from 'vitest';
import { Theme } from './theme';

describe('Theme', () => {
  const key = 'finance-manager.theme';
  let listener: (event: { matches: boolean }) => void;
  let media: { matches: boolean; addEventListener: ReturnType<typeof vi.fn>; removeEventListener: ReturnType<typeof vi.fn> };
  beforeEach(() => {
    localStorage.removeItem(key);
    media = { matches: false, addEventListener: vi.fn((_event, callback) => { listener = callback; }), removeEventListener: vi.fn() };
    vi.stubGlobal('matchMedia', () => media);
  });
  afterEach(() => {
    TestBed.resetTestingModule();
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
    localStorage.removeItem(key);
    delete document.documentElement.dataset['theme'];
  });

  it('follows device changes until the user chooses a theme', () => {
    const theme = TestBed.inject(Theme);
    expect(theme.dark()).toBe(false);
    expect(document.documentElement.dataset['theme']).toBe('light');
    listener({ matches: true });
    TestBed.tick();
    expect(theme.dark()).toBe(true);
    expect(document.documentElement.dataset['theme']).toBe('dark');
    theme.toggle();
    listener({ matches: true });
    TestBed.tick();
    expect(theme.dark()).toBe(false);
    expect(document.documentElement.dataset['theme']).toBe('light');
  });

  it('restores the saved choice even when the device theme differs', () => {
    media.matches = true;
    localStorage.setItem(key, 'light');
    const theme = TestBed.inject(Theme);
    expect(theme.dark()).toBe(false);
    theme.toggle();
    expect(localStorage.getItem(key)).toBe('dark');
    TestBed.resetTestingModule();
    media.matches = false;
    expect(TestBed.inject(Theme).dark()).toBe(true);
  });

  it('ignores an invalid saved preference', () => {
    media.matches = true;
    localStorage.setItem(key, 'invalid');
    expect(TestBed.inject(Theme).dark()).toBe(true);
  });

  it('keeps the theme usable when storage is blocked', () => {
    media.matches = true;
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => { throw new Error('Blocked'); });
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => { throw new Error('Blocked'); });
    const theme = TestBed.inject(Theme);
    expect(theme.dark()).toBe(true);
    expect(() => theme.toggle()).not.toThrow();
    expect(document.documentElement.dataset['theme']).toBe('light');
  });

  it('falls back to light when device preferences are unavailable', () => {
    vi.stubGlobal('matchMedia', undefined);
    expect(TestBed.inject(Theme).dark()).toBe(false);
  });

  it('removes the device listener when destroyed', () => {
    TestBed.inject(Theme);
    TestBed.resetTestingModule();
    expect(media.removeEventListener).toHaveBeenCalledWith('change', listener);
  });
});
