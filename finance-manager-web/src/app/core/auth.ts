import { HttpClient } from '@angular/common/http';
import { inject, Service } from '@angular/core';
import { Observable, tap } from 'rxjs';
import {
    LoginRequest,
    LoginResponse,
} from './auth.models';

@Service()
// Classe responsável por gerenciar a autenticação do usuário
export class Auth {
    // Injeta o HttpClient para realizar requisições HTTP
    private readonly http = inject(HttpClient);
    // Chave usada para armazenar o token de acesso no armazenamento local
    private readonly tokenStorageKey = 'finance-manager.access-token';

    login(request: LoginRequest): Observable<LoginResponse> {
        return this.http
            // Realiza uma requisição POST para o endpoint de login da API, enviando os dados do formulário de login
            .post<LoginResponse>('/api/v1/auth/login', request)
            // Armazena o token de acesso no armazenamento local quando a resposta for recebida
            .pipe(
                // O operador tap é usado para executar efeitos colaterais sem alterar o fluxo de dados do Observable. 
                // Neste caso, ele armazena o token de acesso no armazenamento local quando a resposta de login é recebida. 
                tap((response) => {
                    localStorage.setItem(
                        this.tokenStorageKey,
                        response.accessToken,
                    );
                }),
            );
    }

    getToken(): string | null {
        return localStorage.getItem(this.tokenStorageKey);
    }

    logout(): void {
        localStorage.removeItem(this.tokenStorageKey);
    }
}