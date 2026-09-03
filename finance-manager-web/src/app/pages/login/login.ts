import { Component, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';

@Component({
  imports: [ReactiveFormsModule],
  selector: 'app-login',
  styleUrl: './login.css',
  templateUrl: './login.html',
})
export class Login {
  // Injeta o FormBuilder para criar o formulário de login
  private readonly formBuilder = inject(FormBuilder);

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
    // Verifica se o formulário é inválido e marca todos os campos como tocados para exibir mensagens de erro
    if (this.loginForm.invalid) {
      this.loginForm.markAllAsTouched();
      return;
    }
    console.log(this.loginForm.getRawValue());
  }
}
