import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Auth } from '../../core/auth';
import { finalize } from 'rxjs';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';

@Component({
  imports: [ReactiveFormsModule, RouterLink],
  selector: 'app-login',
  styleUrl: './login.css',
  templateUrl: './login.html',
})
export class Login {
  // Injeta o FormBuilder para criar o formulário de login
  private readonly formBuilder = inject(FormBuilder);
  // Injeta o serviço de autenticação para realizar o login do usuário
  private readonly auth = inject(Auth);

  // Sinais para controlar o estado de envio do formulário e mensagens de erro
  protected readonly isSubmitting = signal(false);
  protected readonly errorMessage = signal('');
  protected readonly isPasswordVisible = signal(false);

  // Injeta o Router para navegação após o login bem-sucedido
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  protected readonly successMessage = signal(
    this.route.snapshot.queryParamMap.get('registered') === 'true'
      ? 'Cadastro realizado com sucesso. Faça login para continuar.'
      : '',
  );

  // Cria o formulário de login com validação para os campos de email e senha
  protected readonly loginForm = this.formBuilder.nonNullable.group({
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
        Validators.maxLength(72),
      ],
    ],
  });

  // Método chamado quando o formulário é enviado
  protected submit(): void {
    // Verifica se o formulário é válido antes de prosseguir
    if (this.loginForm.invalid) {
      this.loginForm.markAllAsTouched();
      return;
    }

    // Define o estado de envio como verdadeiro e limpa qualquer mensagem de erro anterior
    this.isSubmitting.set(true);
    this.errorMessage.set('');

    // Chama o método de login do serviço de autenticação com os dados do formulário
    this.auth
      .login(this.loginForm.getRawValue())
      // Finaliza o estado de envio quando a requisição é concluída, independentemente do resultado
      .pipe(
        finalize(() => this.isSubmitting.set(false)),
      )
      // Assina o Observable retornado pelo método de login para lidar com a resposta ou erro
      .subscribe({
        // Se o login for bem-sucedido, exibe informações do usuário e do token no console
        next: (response) => {
          this.router.navigateByUrl('/dashboard');
        },
        // Se ocorrer um erro durante o login, verifica o status e o código do erro para definir a mensagem de erro apropriada
        error: (error) => {
          if (
            error.status === 401 &&
            error.error?.code === 'INVALID_CREDENTIALS'
          ) {
            this.errorMessage.set('Credenciais inválidas.');
            return;
          }

          this.errorMessage.set(
            'Não foi possível realizar o login. Tente novamente.',
          );
        },
      });
  }

  // Método para alternar a visibilidade da senha no formulário de login
  protected togglePasswordVisibility(): void {
    this.isPasswordVisible.update((visible) => !visible);
  }
}
