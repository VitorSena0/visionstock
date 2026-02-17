import axios from 'axios';

import { productImageRepository } from '../database/productImageRepository';
import { productRepository } from '../database/productRepository';
import { syncQueueRepository } from '../database/syncQueueRepository';
import { cacheRemoteProductImageContent } from './imageContentService';
import {
  adjustStock,
  buildProductImageContentUrl,
  deleteProductImage,
  getProductImages,
  getMyValidationRequests,
  getProducts,
  setProductImagePrimary,
  updateProduct,
  uploadProductImage,
} from './api';
import { queryClient } from './queryClient';
import type { ProductUpdateDTO, SyncQueueOperationType } from '../types/product';

export const PRODUCTS_QUERY_KEY = ['products'] as const;
export const PRODUCT_DETAIL_QUERY_KEY = ['product'] as const;
export const PRODUCT_IMAGES_QUERY_KEY = ['product-images'] as const;

export type PullProductsResult =
  | { status: 'offline'; syncedCount: 0; message: string }
  | { status: 'synced'; syncedCount: number };

type NetworkState = {
  isConnected?: boolean | null;
  isInternetReachable?: boolean | null;
};

type ExpoNetworkModule = {
  getNetworkStateAsync: () => Promise<NetworkState>;
};

type QueuePayloadMap = {
  PRODUCT_UPDATE: {
    productId: string;
    patch: ProductUpdateDTO;
  };
  STOCK_ADJUSTMENT: {
    productId: string;
    quantidadeDelta: number;
    motivo?: string;
  };
  IMAGE_ADD: {
    productId: string;
    localImageId: string;
    localUri: string;
    mimeType?: string | null;
    fileName?: string | null;
    isPrimary: boolean;
  };
  IMAGE_DELETE: {
    productId: string;
    localImageId: string;
    remoteImageId?: string | null;
  };
  IMAGE_SET_PRIMARY: {
    productId: string;
    localImageId: string;
    remoteImageId?: string | null;
  };
};

const OFFLINE_MESSAGE = 'Sem conexao com a internet. Exibindo dados locais.';

const loadExpoNetworkModule = async (): Promise<ExpoNetworkModule | null> => {
  try {
    const dynamicImport = new Function(
      'modulePath',
      'return import(modulePath);',
    ) as (modulePath: string) => Promise<unknown>;
    const module = (await dynamicImport('expo-network')) as Partial<ExpoNetworkModule>;
    if (typeof module.getNetworkStateAsync === 'function') {
      return module as ExpoNetworkModule;
    }
  } catch {
    // noop
  }

  return null;
};

const hasInternetConnection = async () => {
  const networkModule = await loadExpoNetworkModule();
  if (!networkModule) {
    return true;
  }

  const state = await networkModule.getNetworkStateAsync();
  return Boolean(state.isConnected) && (state.isInternetReachable ?? true);
};

const isNetworkIssue = (error: unknown) =>
  axios.isAxiosError(error) && (!error.response || error.code === 'ERR_NETWORK');

const parseQueuePayload = <T>(
  payload: string,
): T | null => {
  try {
    return JSON.parse(payload) as T;
  } catch {
    return null;
  }
};

const extractErrorMessage = (error: unknown) => {
  if (error instanceof Error && error.message.trim().length > 0) {
    return error.message;
  }
  return 'Image cache download failed';
};

const cacheRemoteImageLocally = async (
  productId: string,
  localImageId: string,
  remoteImageId: string,
) => {
  try {
    const result = await cacheRemoteProductImageContent(productId, remoteImageId);
    await productImageRepository.updateCache(localImageId, {
      cachedUri: result.cachedUri,
      status: 'SYNCED',
      error: null,
    });
  } catch (error) {
    await productImageRepository.updateCache(localImageId, {
      cachedUri: null,
      status: 'ERROR',
      error: extractErrorMessage(error),
    });
  }
};

const handleValidationReconciliation = async () => {
  try {
    const requests = await getMyValidationRequests();
    for (const request of requests) {
      if (!request.id) {
        continue;
      }
      if (request.status === 'APPROVED') {
        await productRepository.clearPendingEditByValidation(request.id);
      }
      if (request.status === 'REJECTED') {
        await productRepository.markPendingAsRejected(request.id);
      }
    }
  } catch {
    // Validation reconciliation is best-effort.
  }
};

const processQueueItem = async (
  type: SyncQueueOperationType,
  payloadRaw: string,
) => {
  if (type === 'PRODUCT_UPDATE') {
    const payload = parseQueuePayload<QueuePayloadMap['PRODUCT_UPDATE']>(payloadRaw);
    if (!payload) {
      return { handled: true };
    }
    const response = await updateProduct(payload.productId, payload.patch);
    if (response.status === 'UPDATED' && response.product) {
      await productRepository.saveOne(response.product);
      await productRepository.clearPendingEditByProduct(payload.productId);
    } else if (response.status === 'PENDING_APPROVAL') {
      await productRepository.applyLocalProductUpdate(
        payload.productId,
        payload.patch,
        response.validationRequestId ?? null,
      );
    }
    return { handled: true };
  }

  if (type === 'STOCK_ADJUSTMENT') {
    const payload = parseQueuePayload<QueuePayloadMap['STOCK_ADJUSTMENT']>(payloadRaw);
    if (!payload) {
      return { handled: true };
    }
    const response = await adjustStock(payload.productId, {
      quantidadeDelta: payload.quantidadeDelta,
      motivo: payload.motivo,
    });
    if (response.status === 'UPDATED') {
      await productRepository.clearPendingEditByProduct(payload.productId);
    } else if (response.status === 'PENDING_APPROVAL') {
      await productRepository.applyLocalStockAdjustment(
        payload.productId,
        payload.quantidadeDelta,
        payload.motivo,
        response.validationRequestId ?? null,
      );
    }
    return { handled: true };
  }

  if (type === 'IMAGE_ADD') {
    const payload = parseQueuePayload<QueuePayloadMap['IMAGE_ADD']>(payloadRaw);
    if (!payload) {
      return { handled: true };
    }
    const response = await uploadProductImage(
      payload.productId,
      {
        uri: payload.localUri,
        mimeType: payload.mimeType ?? undefined,
        fileName: payload.fileName ?? undefined,
      },
      payload.isPrimary,
    );
    if (response.status === 'UPDATED') {
      const remoteId = response.resourceId ?? null;
      await productImageRepository.markSynced(
        payload.localImageId,
        remoteId,
        remoteId ? buildProductImageContentUrl(payload.productId, remoteId) : null,
      );
      if (remoteId) {
        await cacheRemoteImageLocally(payload.productId, payload.localImageId, remoteId);
      }
    } else {
      await productImageRepository.markPending(payload.localImageId);
    }
    return { handled: true };
  }

  if (type === 'IMAGE_DELETE') {
    const payload = parseQueuePayload<QueuePayloadMap['IMAGE_DELETE']>(payloadRaw);
    if (!payload) {
      return { handled: true };
    }

    if (!payload.remoteImageId) {
      await productImageRepository.deleteById(payload.localImageId);
      return { handled: true };
    }

    const response = await deleteProductImage(payload.productId, payload.remoteImageId);
    if (response.status === 'UPDATED') {
      await productImageRepository.deleteById(payload.localImageId);
    } else {
      await productImageRepository.markPending(payload.localImageId);
    }
    return { handled: true };
  }

  if (type === 'IMAGE_SET_PRIMARY') {
    const payload = parseQueuePayload<QueuePayloadMap['IMAGE_SET_PRIMARY']>(payloadRaw);
    if (!payload) {
      return { handled: true };
    }
    if (!payload.remoteImageId) {
      return { handled: true };
    }
    let response;
    try {
      response = await setProductImagePrimary(payload.productId, payload.remoteImageId);
    } catch (error) {
      if (axios.isAxiosError(error) && error.response?.status === 409) {
        // Server already converged to another primary. Treat as handled and let pull reconcile.
        return { handled: true };
      }
      throw error;
    }
    if (response.status === 'UPDATED') {
      await productImageRepository.markSynced(
        payload.localImageId,
        payload.remoteImageId,
        buildProductImageContentUrl(payload.productId, payload.remoteImageId),
      );
      await cacheRemoteImageLocally(payload.productId, payload.localImageId, payload.remoteImageId);
    } else {
      await productImageRepository.markPending(payload.localImageId);
    }
    return { handled: true };
  }

  return { handled: true };
};

export const pushPendingChanges = async () => {
  const isOnline = await hasInternetConnection();
  if (!isOnline) {
    return { status: 'offline' as const, processed: 0 };
  }

  const items = await syncQueueRepository.list();
  let processed = 0;

  for (const item of items) {
    try {
      await processQueueItem(item.type, item.payload);
      await syncQueueRepository.remove(item.id);
      processed += 1;
    } catch (error) {
      if (isNetworkIssue(error)) {
        return { status: 'offline' as const, processed };
      }
      await syncQueueRepository.incrementRetry(item.id);
    }
  }

  return { status: 'processed' as const, processed };
};

export const pullProducts = async (): Promise<PullProductsResult> => {
  const isOnline = await hasInternetConnection();
  if (!isOnline) {
    return {
      status: 'offline',
      syncedCount: 0,
      message: OFFLINE_MESSAGE,
    };
  }

  const pushResult = await pushPendingChanges();
  if (pushResult.status === 'offline') {
    return {
      status: 'offline',
      syncedCount: 0,
      message: OFFLINE_MESSAGE,
    };
  }

  let products;
  try {
    products = await getProducts();
  } catch (error) {
    if (isNetworkIssue(error)) {
      return {
        status: 'offline',
        syncedCount: 0,
        message: OFFLINE_MESSAGE,
      };
    }
    throw error;
  }

  await productRepository.saveBatch(products);

  for (const product of products) {
    const productId = product.id?.trim();
    if (!productId) {
      continue;
    }

    try {
      const remoteImages = await getProductImages(productId);
      await productImageRepository.upsertRemoteMetadata(productId, remoteImages);

      const localImages = await productImageRepository.getByProductId(productId);
      const primaryImage = localImages.find(
        (image) =>
          image.isPrimary &&
          Boolean(image.remoteImageId && image.remoteImageId.trim().length > 0),
      );
      const remotePrimaryId = primaryImage?.remoteImageId?.trim();
      if (primaryImage && remotePrimaryId) {
        await cacheRemoteImageLocally(productId, primaryImage.id, remotePrimaryId);
      }
    } catch {
      // Best-effort image metadata sync. Product list sync should still succeed.
    }
  }

  await handleValidationReconciliation();

  await Promise.all([
    queryClient.invalidateQueries({ queryKey: PRODUCTS_QUERY_KEY }),
    queryClient.invalidateQueries({ queryKey: PRODUCT_DETAIL_QUERY_KEY }),
    queryClient.invalidateQueries({ queryKey: PRODUCT_IMAGES_QUERY_KEY }),
  ]);

  return {
    status: 'synced',
    syncedCount: products.length,
  };
};
