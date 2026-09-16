import { TestBed } from '@angular/core/testing';
import { vi } from 'vitest';
import { ValuePrivacy } from './value-privacy';

describe('ValuePrivacy', () => {
  const key = 'finance-manager.values-hidden';
  afterEach(() => {
    vi.restoreAllMocks();
    localStorage.removeItem(key);
  });

  it('saves both choices and restores privacy in a new session', () => {
    const privacy = TestBed.inject(ValuePrivacy);
    expect(privacy.hidden()).toBe(false);
    privacy.toggle();
    expect(localStorage.getItem(key)).toBe('true');
    TestBed.resetTestingModule();
    const restored = TestBed.inject(ValuePrivacy);
    expect(restored.hidden()).toBe(true);
    restored.toggle();
    expect(restored.hidden()).toBe(false);
    expect(localStorage.getItem(key)).toBe('false');
  });

  it('remains usable when storage is blocked', () => {
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => { throw new Error('Blocked'); });
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => { throw new Error('Blocked'); });
    const privacy = TestBed.inject(ValuePrivacy);
    expect(() => privacy.toggle()).not.toThrow();
    expect(privacy.hidden()).toBe(true);
    privacy.toggle();
    expect(privacy.hidden()).toBe(false);
  });
});
