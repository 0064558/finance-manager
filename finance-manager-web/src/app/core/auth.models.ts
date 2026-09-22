// Este arquivo define as interfaces relacionadas à autenticação, 
// incluindo solicitações de login, 
// informações do usuário autenticado e respostas de login.

export interface LoginRequest {
  email: string;
  password: string;
}

export interface RegisterRequest {
  name: string;
  email: string;
  password: string;
}

export interface AuthUser {
  id: string;
  name: string;
  email: string;
  createdAt: string;
  onboardingVersion: number;
}

export interface LoginResponse {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
  user: AuthUser;
}

// Esta interface define a estrutura da solicitação para atualizar a versão de onboarding do usuário.
export interface UpdateOnboardingVersionRequest {
  onboardingVersion: number;
}
