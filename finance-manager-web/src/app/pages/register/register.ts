import { Component, inject, signal } from '@angular/core';
import {
  AbstractControl,
  FormBuilder,
  ReactiveFormsModule,
  ValidationErrors,
  Validators,
} from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { Auth } from '../../core/auth';
import { GradientWave } from '../login/gradient-wave';
import { LoginBrandPreview } from '../login/login-brand-preview';

function passwordsMatch(control: AbstractControl): ValidationErrors | null {
  const password = control.get('password')?.value;
  const confirmPassword = control.get('confirmPassword')?.value;

  if (!password || !confirmPassword) {
    return null;
  }

  return password === confirmPassword ? null : { passwordsMismatch: true };
}

@Component({
  imports: [ReactiveFormsModule, RouterLink, LoginBrandPreview, GradientWave],
  selector: 'app-register',
  styleUrl: './register.css',
  templateUrl: './register.html',
})
export class Register {
  // Injeta o FormBuilder para criar o formulário de registro
  private readonly formBuilder = inject(FormBuilder);
  private readonly auth = inject(Auth);
  private readonly router = inject(Router);

  protected readonly isSubmitting = signal(false);
  protected readonly errorMessage = signal('');
  protected readonly isPasswordVisible = signal(false);
  protected readonly isConfirmPasswordVisible = signal(false);

  // readonly serve para indicar que a propriedade não pode ser reatribuída após a inicialização, garantindo que o sinal seja imutável.

  // Cria o formulário de registro com validação para os campos de nome, email, senha e confirmação de senha
  protected readonly registerForm = this.formBuilder.nonNullable.group(
    {
      name: [
        '', // Valor inicial do campo de nome
        [Validators.required, Validators.minLength(2), Validators.maxLength(100)],
      ],
      email: ['', [Validators.required, Validators.email, Validators.maxLength(254)]],
      password: [
        '',
        [
          Validators.required,
          Validators.minLength(8),
          Validators.maxLength(72),
          Validators.pattern(/^(?=.*[A-Za-z])(?=.*\d).*$/),
        ],
      ],
      confirmPassword: ['', [Validators.required]],
    },
    {
      validators: passwordsMatch,
    },
  );

  // Método chamado quando o formulário de registro é enviado
  protected submit(): void {
    // Verifica se o formulário é válido antes de prosseguir
    if (this.registerForm.invalid) {
      this.registerForm.markAllAsTouched();
      return;
    }

    // Obtém os valores do formulário de registro
    const { name, email, password } = this.registerForm.getRawValue();

    // Define o estado de envio como verdadeiro e limpa qualquer mensagem de erro anterior
    this.isSubmitting.set(true);
    this.errorMessage.set('');

    // Chama o método de registro do serviço de autenticação com os dados do formulário
    this.auth
      .register({ name, email, password })
      // pipe é usado para encadear operadores que podem transformar, filtrar ou executar efeitos colaterais nos dados do Observable.
      .pipe(finalize(() => this.isSubmitting.set(false)))
      // Assina o Observable retornado pelo método de registro para lidar com a resposta ou erro
      .subscribe({
        // Se o registro for bem-sucedido, redireciona o usuário para a página de login com um parâmetro de consulta indicando sucesso
        next: () => {
          this.router.navigate(['/login'], {
            queryParams: { registered: 'true' },
          });
        },
        // Se ocorrer um erro durante o registro, verifica o status e o código do erro para definir a mensagem de erro apropriada
        error: (error) => {
          // Se o e-mail já estiver registrado, define uma mensagem de erro específica
          if (error.status === 409 && error.error?.code === 'EMAIL_ALREADY_REGISTERED') {
            this.errorMessage.set('Este e-mail já está cadastrado.');
            return;
          }

          this.errorMessage.set('Não foi possível criar sua conta. Tente novamente.');
        },
      });
  }

  protected togglePasswordVisibility(): void {
    this.isPasswordVisible.update((visible) => !visible);
  }

  protected toggleConfirmPasswordVisibility(): void {
    this.isConfirmPasswordVisible.update((visible) => !visible);
  }

  protected moveCard(event: PointerEvent): void {
    if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
      return;
    }

    const card = event.currentTarget as HTMLElement;
    const bounds = card.getBoundingClientRect();
    const x = (event.clientX - bounds.left) / bounds.width - 0.5;
    const y = (event.clientY - bounds.top) / bounds.height - 0.5;

    card.style.setProperty('--card-rotate-x', `${y * -18}deg`);
    card.style.setProperty('--card-rotate-y', `${x * 22}deg`);
    card.style.setProperty('--card-glow-x', `${(x + 0.5) * 100}%`);
    card.style.setProperty('--card-glow-y', `${(y + 0.5) * 100}%`);
  }

  protected resetCard(event: PointerEvent): void {
    const card = event.currentTarget as HTMLElement;

    card.style.setProperty('--card-rotate-x', '0deg');
    card.style.setProperty('--card-rotate-y', '0deg');
    card.style.setProperty('--card-glow-x', '50%');
    card.style.setProperty('--card-glow-y', '50%');
  }
}
