import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Notifications } from '../../core/notifications';
import { NotificationHost } from './notification-host';

describe('NotificationHost', () => {
  let fixture: ComponentFixture<NotificationHost>;
  let notifications: Notifications;

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [NotificationHost] }).compileComponents();
    notifications = TestBed.inject(Notifications);
    fixture = TestBed.createComponent(NotificationHost);
    fixture.detectChanges();
  });

  afterEach(() => notifications.clear());

  it('keeps a polite live region mounted even without messages', () => {
    const region = fixture.nativeElement.querySelector('[role="status"]');
    expect(region.getAttribute('aria-live')).toBe('polite');
    expect(region.children.length).toBe(0);
  });

  it('renders the message and an accessible close button', () => {
    notifications.success('Categoria criada com sucesso.');
    fixture.detectChanges();
    const button = fixture.nativeElement.querySelector('button') as HTMLButtonElement;
    expect(fixture.nativeElement.textContent).toContain('Categoria criada com sucesso.');
    expect(button.getAttribute('aria-label')).toContain('Fechar notificação: Categoria criada');
    button.click();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.notification')).toBeNull();
  });

  it('renders errors with their own visual style', () => {
    notifications.error('Não foi possível salvar.');
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.notification--error')).not.toBeNull();
  });
});
