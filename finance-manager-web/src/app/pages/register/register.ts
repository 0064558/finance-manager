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

function passwordsMatch(control: AbstractControl): ValidationErrors | null {
  const password = control.get('password')?.value;
  const confirmPassword = control.get('confirmPassword')?.value;

  if (!password || !confirmPassword) {
    return null;
  }

  return password === confirmPassword
    ? null
    : { passwordsMismatch: true };
}

@Component({
  imports: [ReactiveFormsModule, RouterLink],
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

  // Cria o formulário de registro com validação para os campos de nome, email, senha e confirmação de senha
  protected readonly registerForm = this.formBuilder.nonNullable.group(
    {
      name: [
        '', // Valor inicial do campo de nome
        [
          Validators.required,
          Validators.minLength(2),
          Validators.maxLength(100),
        ],
      ],
      email: [
        '',
        [
          Validators.required,
          Validators.email,
          Validators.maxLength(254),
        ],
      ],
      password: [
        '',
        [
          Validators.required,
          Validators.minLength(8),
          Validators.maxLength(72),
          Validators.pattern(/^(?=.*[A-Za-z])(?=.*\d).*$/)
        ],
      ],
      confirmPassword: [
        '',
        [
          Validators.required,
        ],
      ],
    },
    {
      validators: passwordsMatch,
    });

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
      // Finaliza o estado de envio quando a requisição é concluída, independentemente do resultado
      .pipe(finalize(() => this.isSubmitting.set(false)))
      .subscribe({
        next: () => {
          this.router.navigate(['/login'], {
            queryParams: { registered: 'true' },
          });
        },
        error: (error) => {
          if (
            error.status === 409 &&
            error.error?.code === 'EMAIL_ALREADY_REGISTERED'
          ) {
            this.errorMessage.set('Este e-mail já está cadastrado.');
            return;
          }

          this.errorMessage.set(
            'Não foi possível criar sua conta. Tente novamente.',
          );
        },
      });
  }



}
