import { Buffer } from 'buffer';
import * as FileSystem from 'expo-file-system/legacy';
import { Platform } from 'react-native';

import { api, withApiPrefix } from './api';

const IMAGE_CACHE_DIR = `${FileSystem.cacheDirectory ?? ''}visionstock-images`;

const ensureCacheDirectory = async () => {
  if (!FileSystem.cacheDirectory) {
    throw new Error('Cache directory not available on this device.');
  }
  await FileSystem.makeDirectoryAsync(IMAGE_CACHE_DIR, { intermediates: true });
};

const getImageExtension = (contentType?: string | null) => {
  const normalized = contentType?.split(';')[0]?.trim().toLowerCase();
  if (normalized === 'image/png') {
    return 'png';
  }
  if (normalized === 'image/webp') {
    return 'webp';
  }
  if (normalized === 'image/heic') {
    return 'heic';
  }
  if (normalized === 'image/heif') {
    return 'heif';
  }
  return 'jpg';
};

const asUint8Array = (raw: unknown): Uint8Array => {
  if (raw instanceof ArrayBuffer) {
    return new Uint8Array(raw);
  }
  if (ArrayBuffer.isView(raw)) {
    return new Uint8Array(raw.buffer);
  }
  if (Array.isArray(raw)) {
    return Uint8Array.from(raw);
  }
  if (typeof raw === 'string') {
    return Uint8Array.from(Buffer.from(raw, 'binary'));
  }
  return new Uint8Array();
};

const startsWithBytes = (bytes: Uint8Array, signature: number[]) => {
  if (bytes.length < signature.length) {
    return false;
  }
  return signature.every((value, index) => bytes[index] === value);
};

const isLikelyImagePayload = (bytes: Uint8Array, contentType?: string | null) => {
  if (bytes.length === 0) {
    return false;
  }

  const normalizedType = contentType?.split(';')[0]?.trim().toLowerCase() ?? '';
  if (normalizedType === 'image/jpeg' || normalizedType === 'image/jpg') {
    return startsWithBytes(bytes, [0xff, 0xd8, 0xff]);
  }
  if (normalizedType === 'image/png') {
    return startsWithBytes(bytes, [0x89, 0x50, 0x4e, 0x47]);
  }
  if (normalizedType === 'image/webp') {
    if (bytes.length < 12) {
      return false;
    }
    const riff = String.fromCharCode(bytes[0], bytes[1], bytes[2], bytes[3]);
    const webp = String.fromCharCode(bytes[8], bytes[9], bytes[10], bytes[11]);
    return riff === 'RIFF' && webp === 'WEBP';
  }

  // For HEIC/HEIF and unknown types, only ensure we have non-empty bytes.
  return true;
};

export const cacheRemoteProductImageContent = async (
  productId: string,
  imageId: string,
): Promise<{
  cachedUri: string;
  contentType: string | null;
  byteLength: number;
}> => {
  if (Platform.OS === 'web') {
    const response = await api.get<Blob>(
      withApiPrefix(`/products/${productId}/images/${imageId}/content`),
      {
        responseType: 'blob',
      },
    );

    const blob = response.data;
    const contentType =
      (typeof response.headers['content-type'] === 'string'
        ? response.headers['content-type']
        : blob.type || null) ?? null;

    if (!blob || blob.size <= 0) {
      throw new Error('Invalid or empty image payload');
    }

    return {
      cachedUri: URL.createObjectURL(blob),
      contentType,
      byteLength: blob.size,
    };
  }

  await ensureCacheDirectory();

  const response = await api.get<ArrayBuffer>(
    withApiPrefix(`/products/${productId}/images/${imageId}/content`),
    {
      responseType: 'arraybuffer',
    },
  );

  const contentType =
    (typeof response.headers['content-type'] === 'string'
      ? response.headers['content-type']
      : null) ?? null;
  const bytes = asUint8Array(response.data);

  if (!isLikelyImagePayload(bytes, contentType)) {
    throw new Error('Invalid or empty image payload');
  }

  const extension = getImageExtension(contentType);
  const fileUri = `${IMAGE_CACHE_DIR}/${productId}-${imageId}-${Date.now()}.${extension}`;
  const base64 = Buffer.from(bytes).toString('base64');

  await FileSystem.writeAsStringAsync(fileUri, base64, {
    encoding: FileSystem.EncodingType.Base64,
  });

  return {
    cachedUri: fileUri,
    contentType,
    byteLength: bytes.length,
  };
};
