import axios, { AxiosError } from 'axios';
import { router } from 'expo-router';

import type {
  ActionResponseDTO,
  ProductImageMetadataDTO,
  ProductCreateDTO,
  ProductResponseDTO,
  ProductSyncDTO,
  ProductUpdateDTO,
  ProductUpdateResponseDTO,
  StockAdjustmentDTO,
  ValidationRequestDTO,
} from '../types/product';
import { deleteStoredItem, getStoredItem } from './storage';

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

const getFileNameFromUri = (uri: string) => {
  const sanitizedUri = uri.split('?')[0] ?? uri;
  const parts = sanitizedUri.split('/');
  return parts[parts.length - 1] ?? `scan-${Date.now()}.jpg`;
};

const getMimeTypeFromFileName = (fileName: string) => {
  const extension = fileName.split('.').pop()?.toLowerCase();

  if (extension === 'png') {
    return 'image/png';
  }

  if (extension === 'heic') {
    return 'image/heic';
  }

  if (extension === 'heif') {
    return 'image/heif';
  }

  if (extension === 'webp') {
    return 'image/webp';
  }

  return 'image/jpeg';
};

export type UploadImagePayload =
  | string
  | {
    uri: string;
    fileName?: string | null;
    mimeType?: string | null;
  };

const resolveUploadImagePayload = (payload: UploadImagePayload) => {
  if (typeof payload === 'string') {
    const fileName = getFileNameFromUri(payload);
    return {
      uri: payload,
      fileName,
      mimeType: getMimeTypeFromFileName(fileName),
    };
  }

  const fileName = payload.fileName?.trim() || getFileNameFromUri(payload.uri);
  return {
    uri: payload.uri,
    fileName,
    mimeType: payload.mimeType?.trim() || getMimeTypeFromFileName(fileName),
  };
};

export const uploadImage = async (
  payload: UploadImagePayload,
): Promise<ProductResponseDTO> => {
  const image = resolveUploadImagePayload(payload);
  const formData = new FormData();

  formData.append(
    'image',
    {
      uri: image.uri,
      name: image.fileName,
      type: image.mimeType,
    } as unknown as Blob,
  );

  const { data } = await api.post<ProductResponseDTO>(
    withApiPrefix('/scan'),
    formData,
    {
      headers: {
        'Content-Type': 'multipart/form-data',
      },
    },
  );

  return data;
};

const toFormImagePart = (payload: UploadImagePayload) => {
  const image = resolveUploadImagePayload(payload);
  return {
    uri: image.uri,
    name: image.fileName,
    type: image.mimeType,
  } as unknown as Blob;
};

export const createProduct = async (
  payload: ProductCreateDTO,
): Promise<ProductResponseDTO> => {
  const { data } = await api.post<ProductResponseDTO>(
    withApiPrefix('/products'),
    payload,
  );

  return data;
};

export const getProducts = async (): Promise<ProductSyncDTO[]> => {
  const { data } = await api.get<ProductSyncDTO[]>(withApiPrefix('/products'));
  return data;
};

export const updateProduct = async (
  productId: string,
  payload: ProductUpdateDTO,
): Promise<ProductUpdateResponseDTO> => {
  const { data } = await api.put<ProductUpdateResponseDTO>(
    withApiPrefix(`/products/${productId}`),
    payload,
  );
  return data;
};

export const adjustStock = async (
  productId: string,
  payload: StockAdjustmentDTO,
): Promise<ActionResponseDTO> => {
  const { data } = await api.post<ActionResponseDTO>(
    withApiPrefix(`/products/${productId}/stock-adjustments`),
    payload,
  );
  return data;
};

export const getProductImages = async (
  productId: string,
): Promise<ProductImageMetadataDTO[]> => {
  const { data } = await api.get<ProductImageMetadataDTO[]>(
    withApiPrefix(`/products/${productId}/images`),
  );
  return data;
};

export const buildProductImageContentUrl = (productId: string, imageId: string) =>
  `${API_BASE_URL}${withApiPrefix(`/products/${productId}/images/${imageId}/content`)}`;

export const uploadProductImage = async (
  productId: string,
  payload: UploadImagePayload,
  isPrimary = false,
): Promise<ActionResponseDTO> => {
  const formData = new FormData();
  formData.append('image', toFormImagePart(payload));
  formData.append('isPrimary', String(isPrimary));

  const { data } = await api.post<ActionResponseDTO>(
    withApiPrefix(`/products/${productId}/images`),
    formData,
    {
      headers: {
        'Content-Type': 'multipart/form-data',
      },
    },
  );
  return data;
};

export const setProductImagePrimary = async (
  productId: string,
  imageId: string,
): Promise<ActionResponseDTO> => {
  const { data } = await api.patch<ActionResponseDTO>(
    withApiPrefix(`/products/${productId}/images/${imageId}/primary`),
  );
  return data;
};

export const deleteProductImage = async (
  productId: string,
  imageId: string,
): Promise<ActionResponseDTO> => {
  const { data } = await api.delete<ActionResponseDTO>(
    withApiPrefix(`/products/${productId}/images/${imageId}`),
  );
  return data;
};

export const getMyValidationRequests = async (): Promise<ValidationRequestDTO[]> => {
  const { data } = await api.get<ValidationRequestDTO[]>(withApiPrefix('/validation/my'));
  return data;
};

type UnauthorizedHandler = () => void | Promise<void>;

let onUnauthorized: UnauthorizedHandler | null = null;
let isHandlingUnauthorized = false;

export const setUnauthorizedHandler = (handler: UnauthorizedHandler | null) => {
  onUnauthorized = handler;
};

export const api = axios.create({
  baseURL: API_BASE_URL,
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json',
  },
});

api.interceptors.request.use(async (config) => {
  const token = await getStoredItem(TOKEN_STORAGE_KEY);

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
        await deleteStoredItem(TOKEN_STORAGE_KEY);
        await deleteStoredItem(USER_STORAGE_KEY);

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
