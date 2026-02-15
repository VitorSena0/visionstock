export type UserRole = 'ADMIN' | 'USER';

export interface User {
  userId: string;
  email: string;
  role: UserRole;
}

export interface LoginRequestDTO {
  email: string;
  password: string;
}

export interface AuthResponseDTO {
  token: string;
  role: UserRole;
  userId: string;
}
