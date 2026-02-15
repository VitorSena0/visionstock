import axios from 'axios';
import { router } from 'expo-router';
import * as SecureStore from 'expo-secure-store';
import { create } from 'zustand';

import {
  api,
  TOKEN_STORAGE_KEY,
  USER_STORAGE_KEY,
  withApiPrefix,
} from '../services/api';
import type { AuthResponseDTO, LoginRequestDTO, User } from '../types/auth';

type AuthStore = {
  token: string | null;
  user: User | null;
  isHydrated: boolean;
  isLoading: boolean;
  error: string | null;
  hydrate: () => Promise<void>;
  login: (email: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
  clearSession: () => Promise<void>;
};

const normalizeEmail = (email: string) => email.trim().toLowerCase();

const extractErrorMessage = (error: unknown): string => {
  if (!axios.isAxiosError(error)) {
    return 'Falha ao autenticar. Tente novamente.';
  }

  const status = error.response?.status;
  const payload = error.response?.data;
  if (payload && typeof payload === 'object' && 'message' in payload) {
    const message = payload.message;
    if (typeof message === 'string' && message.trim().length > 0) {
      return status ? `Erro ${status}: ${message}` : message;
    }
  }

  if (status === 401) {
    return 'Credenciais inválidas.';
  }

  if (!error.response) {
    return 'Nao foi possivel conectar na API. Verifique IP/porta do backend e EXPO_PUBLIC_API_URL.';
  }

  return `Falha ao autenticar (HTTP ${status ?? 'desconhecido'}).`;
};

export const useAuthStore = create<AuthStore>((set, get) => ({
  token: null,
  user: null,
  isHydrated: false,
  isLoading: false,
  error: null,

  hydrate: async () => {
    const [token, rawUser] = await Promise.all([
      SecureStore.getItemAsync(TOKEN_STORAGE_KEY),
      SecureStore.getItemAsync(USER_STORAGE_KEY),
    ]);

    let parsedUser: User | null = null;

    if (rawUser) {
      try {
        parsedUser = JSON.parse(rawUser) as User;
      } catch {
        parsedUser = null;
      }
    }

    set({
      token,
      user: parsedUser,
      isHydrated: true,
    });
  },

  login: async (email: string, password: string) => {
    set({ isLoading: true, error: null });

    const payload: LoginRequestDTO = {
      email: normalizeEmail(email),
      password,
    };

    try {
      const { data } = await api.post<AuthResponseDTO>(
        withApiPrefix('/auth/login'),
        payload,
      );

      const user: User = {
        userId: data.userId,
        email: payload.email,
        role: data.role,
      };

      await Promise.all([
        SecureStore.setItemAsync(TOKEN_STORAGE_KEY, data.token),
        SecureStore.setItemAsync(USER_STORAGE_KEY, JSON.stringify(user)),
      ]);

      set({
        token: data.token,
        user,
        isLoading: false,
        error: null,
      });

      router.replace('/(tabs)');
    } catch (error) {
      set({
        isLoading: false,
        error: extractErrorMessage(error),
      });

      throw error;
    }
  },

  clearSession: async () => {
    await Promise.all([
      SecureStore.deleteItemAsync(TOKEN_STORAGE_KEY),
      SecureStore.deleteItemAsync(USER_STORAGE_KEY),
    ]);

    set({ token: null, user: null, error: null });
  },

  logout: async () => {
    await get().clearSession();
    router.replace('/login');
  },
}));
