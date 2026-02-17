import type { ImageSourcePropType } from 'react-native';

type ImageSyncStatus = 'PENDENTE' | 'ERRO' | 'SYNCED' | string | null | undefined;

type ResolveProductImageCandidatesInput = {
  apiBaseUrl: string;
  productId?: string | null;
  localImageId?: string | null;
  remoteImageId?: string | null;
  localUri?: string | null;
  cachedUri?: string | null;
  productImageUrl?: string | null;
  fallbackImageUrl?: string | null;
  syncStatus?: ImageSyncStatus;
};

const ABSOLUTE_HTTP_REGEX = /^https?:\/\//i;

const normalizeUri = (uri: string | null | undefined, apiBaseUrl: string): string | null => {
  const value = uri?.trim();
  if (!value) {
    return null;
  }

  if (value.startsWith('/')) {
    return `${apiBaseUrl}${value}`;
  }

  return value;
};

const buildRemoteUri = (apiBaseUrl: string, productId: string, imageId: string) => {
  return `${apiBaseUrl}/api/v1/products/${productId}/images/${imageId}/content`;
};

const unique = (values: Array<string | null | undefined>) => {
  const seen = new Set<string>();
  const result: string[] = [];

  for (const value of values) {
    if (!value) {
      continue;
    }
    if (seen.has(value)) {
      continue;
    }
    seen.add(value);
    result.push(value);
  }

  return result;
};

export const resolveRemoteImageId = (
  localImageId?: string | null,
  remoteImageId?: string | null,
) => {
  const remoteId = remoteImageId?.trim();
  if (remoteId) {
    return remoteId;
  }

  const localId = localImageId?.trim();
  if (localId && localId.startsWith('remote-')) {
    return localId.replace('remote-', '');
  }

  return null;
};

export const resolveProductImageCandidates = ({
  apiBaseUrl,
  productId,
  localImageId,
  remoteImageId,
  localUri,
  cachedUri,
  productImageUrl,
  fallbackImageUrl,
  syncStatus,
}: ResolveProductImageCandidatesInput) => {
  const resolvedRemoteId = resolveRemoteImageId(localImageId, remoteImageId);
  const remoteUri =
    productId && resolvedRemoteId ? buildRemoteUri(apiBaseUrl, productId, resolvedRemoteId) : null;
  const normalizedLocalUri = normalizeUri(localUri, apiBaseUrl);
  const normalizedCachedUri = normalizeUri(cachedUri, apiBaseUrl);
  const normalizedProductImageUrl = normalizeUri(productImageUrl, apiBaseUrl);
  const normalizedFallbackImageUrl = normalizeUri(fallbackImageUrl, apiBaseUrl);

  const isPendingLike = syncStatus === 'PENDENTE' || syncStatus === 'ERRO';
  if (isPendingLike) {
    return unique([
      normalizedCachedUri,
      normalizedLocalUri,
      remoteUri,
      normalizedProductImageUrl,
      normalizedFallbackImageUrl,
    ]);
  }

  return unique([
    normalizedCachedUri,
    remoteUri,
    normalizedLocalUri,
    normalizedProductImageUrl,
    normalizedFallbackImageUrl,
  ]);
};

export const buildProductImageSource = (
  uri: string | null | undefined,
  token?: string | null,
): ImageSourcePropType | undefined => {
  if (!uri) {
    return undefined;
  }

  if (ABSOLUTE_HTTP_REGEX.test(uri) && token) {
    return {
      uri,
      headers: {
        Authorization: `Bearer ${token}`,
      },
    };
  }

  return { uri };
};
