import axios, { AxiosError } from 'axios';
import { router } from 'expo-router';
import * as SecureStore from 'expo-secure-store';

export const TOKEN_STORAGE_KEY = 'visionstock.token';
export const USER_STORAGE_KEY = 'visionstock.user';
const API_PREFIX = '/api/v1';
const RAW_API_BASE_URL = process.env.EXPO_PUBLIC_API_URL;

const normalizeBaseUrl = (rawUrl: string) => {
  return rawUrl
    .trim()
    .replace(/\/+$/, '')
    .replace(/\/api\/v1$/, '');
};

export const API_BASE_URL = normalizeBaseUrl(
  RAW_API_BASE_URL ?? 'http://192.168.0.102:8080',
);

export const IS_API_URL_FROM_ENV = Boolean(RAW_API_BASE_URL);

export const withApiPrefix = (path: string) => {
  const normalizedPath = path.startsWith('/') ? path : `/${path}`;
  return `${API_PREFIX}${normalizedPath}`;
};

type UnauthorizedHandler = () => void | Promise<void>;

let onUnauthorized: UnauthorizedHandler | null = null;
let isHandlingUnauthorized = false;

export const setUnauthorizedHandler = (handler: UnauthorizedHandler | null) => {
  onUnauthorized = handler;
};

export const api = axios.create({
  baseURL: API_BASE_URL,
  timeout: 10000,
  headers: {
    'Content-Type': 'application/json',
  },
});

api.interceptors.request.use(async (config) => {
  const token = await SecureStore.getItemAsync(TOKEN_STORAGE_KEY);

  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }

  return config;
});

api.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    if (error.response?.status === 401 && !isHandlingUnauthorized) {
      isHandlingUnauthorized = true;

      try {
        await SecureStore.deleteItemAsync(TOKEN_STORAGE_KEY);
        await SecureStore.deleteItemAsync(USER_STORAGE_KEY);

        if (onUnauthorized) {
          await onUnauthorized();
        } else {
          router.replace('/login');
        }
      } finally {
        isHandlingUnauthorized = false;
      }
    }

    return Promise.reject(error);
  },
);
