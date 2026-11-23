import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { Register } from './register';

describe('Register', () => {
  let component: Register;
  let fixture: ComponentFixture<Register>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Register],
      providers: [provideHttpClient(), provideRouter([])],
    }).compileComponents();

    fixture = TestBed.createComponent(Register);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('aceita 72 bytes e exibe o aviso ao ultrapassar o limite usando Unicode', () => {
    const password = component['registerForm'].controls.password;
    password.setValue('A1' + 'é'.repeat(35));
    expect(password.valid).toBe(true);
    password.setValue('A1' + 'é'.repeat(33) + '😀');
    expect(password.valid).toBe(true);
    password.setValue(password.value + 'a');
    password.markAsTouched();
    fixture.detectChanges();
    expect(password.hasError('maxBytes')).toBe(true);
    expect(fixture.nativeElement.textContent).toContain('72 bytes em UTF-8');
  });
});
