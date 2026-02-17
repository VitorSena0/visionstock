import { Ionicons } from '@expo/vector-icons';
import { useMutation, useQuery } from '@tanstack/react-query';
import axios from 'axios';
import { router, useLocalSearchParams } from 'expo-router';
import * as ImagePicker from 'expo-image-picker';
import type { ImagePickerAsset } from 'expo-image-picker';
import { useEffect, useState } from 'react';
import {
  ActivityIndicator,
  Alert,
  Image,
  Pressable,
  ScrollView,
  Text,
  View,
  type ImageSourcePropType,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { AppButton } from '../../../src/components/ui/AppButton';
import { productImageRepository } from '../../../src/database/productImageRepository';
import { productRepository } from '../../../src/database/productRepository';
import { syncQueueRepository } from '../../../src/database/syncQueueRepository';
import {
  API_BASE_URL,
  type UploadImagePayload,
  buildProductImageContentUrl,
  deleteProductImage,
  getProductImages,
  setProductImagePrimary,
  uploadProductImage,
} from '../../../src/services/api';
import { queryClient } from '../../../src/services/queryClient';
import { cacheRemoteProductImageContent } from '../../../src/services/imageContentService';
import {
  PRODUCT_DETAIL_QUERY_KEY,
  PRODUCT_IMAGES_QUERY_KEY,
  PRODUCTS_QUERY_KEY,
} from '../../../src/services/syncService';
import { useAuthStore } from '../../../src/store/authStore';
import type { LocalProductImage } from '../../../src/types/product';
import { normalizeImageForUpload } from '../../../src/utils/imageUpload';
import {
  buildProductImageSource,
  resolveProductImageCandidates,
  resolveRemoteImageId,
} from '../../../src/utils/productImage';

const formatCurrency = (value?: number | null) => {
  if (value === null || value === undefined) {
    return '--';
  }

  return value.toLocaleString('pt-BR', {
    style: 'currency',
    currency: 'BRL',
  });
};

const isNetworkIssue = (error: unknown) =>
  axios.isAxiosError(error) && (!error.response || error.code === 'ERR_NETWORK');

const extractApiMessage = (error: unknown, fallbackMessage: string) => {
  if (!axios.isAxiosError(error)) {
    return fallbackMessage;
  }

  const payload = error.response?.data;
  if (payload && typeof payload === 'object' && 'message' in payload) {
    const rawMessage = payload.message;
    if (typeof rawMessage === 'string' && rawMessage.trim().length > 0) {
      return rawMessage;
    }
  }

  if (!error.response) {
    return 'Nao foi possivel conectar ao backend. Verifique rede, IP e porta.';
  }

  return `${fallbackMessage} (HTTP ${error.response.status ?? 'desconhecido'})`;
};

const UUID_REGEX =
  /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

const isUuid = (value: string) => UUID_REGEX.test(value.trim());

const toUploadPayloadObject = (payload: UploadImagePayload) =>
  typeof payload === 'string'
    ? { uri: payload, mimeType: null as string | null, fileName: null as string | null }
    : {
        uri: payload.uri,
        mimeType: payload.mimeType ?? null,
        fileName: payload.fileName ?? null,
      };

type DetailRowProps = {
  label: string;
  value?: string | number | null;
};

function DetailRow({ label, value }: DetailRowProps) {
  return (
    <View className="rounded-xl border border-slate-800 bg-slate-900 px-4 py-3">
      <Text className="text-xs uppercase tracking-wide text-slate-400">{label}</Text>
      <Text className="mt-1 text-base text-slate-100">{value ?? '--'}</Text>
    </View>
  );
}

export default function ProductDetailScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const token = useAuthStore((state) => state.token);
  const userRole = useAuthStore((state) => state.user?.role ?? 'USER');
  const productId = Array.isArray(id) ? id[0] : id;
  const [imageFallbackIndex, setImageFallbackIndex] = useState<Record<string, number>>({});

  const productQuery = useQuery({
    queryKey: [...PRODUCT_DETAIL_QUERY_KEY, productId],
    queryFn: () => {
      if (!productId) {
        return Promise.resolve(null);
      }
      return productRepository.getById(productId);
    },
    enabled: Boolean(productId),
  });

  const imagesQuery = useQuery({
    queryKey: [...PRODUCT_IMAGES_QUERY_KEY, productId],
    queryFn: () => {
      if (!productId) {
        return Promise.resolve([]);
      }
      return productImageRepository.getByProductId(productId);
    },
    enabled: Boolean(productId),
    initialData: [],
  });

  const cacheRemoteImage = async (image: LocalProductImage) => {
    if (!productId || !image.remoteImageId || !isUuid(image.remoteImageId)) {
      return;
    }

    try {
      const cached = await cacheRemoteProductImageContent(productId, image.remoteImageId);
      await productImageRepository.updateCache(image.id, {
        cachedUri: cached.cachedUri,
        status: 'SYNCED',
        error: null,
      });
    } catch (error) {
      await productImageRepository.updateCache(image.id, {
        cachedUri: null,
        status: 'ERROR',
        error: error instanceof Error ? error.message : 'Falha ao baixar imagem remota',
      });
    }
  };

  const refreshImagesMutation = useMutation({
    mutationFn: async () => {
      if (!productId) {
        return;
      }
      const remoteImages = await getProductImages(productId);
      await productImageRepository.upsertRemoteMetadata(productId, remoteImages);

      const localImages = await productImageRepository.getByProductId(productId);
      for (const image of localImages) {
        if (!image.remoteImageId || !isUuid(image.remoteImageId)) {
          continue;
        }
        await cacheRemoteImage(image);
      }
    },
    onSuccess: async () => {
      await queryClient.invalidateQueries({
        queryKey: [...PRODUCT_IMAGES_QUERY_KEY, productId],
      });
    },
  });

  useEffect(() => {
    if (!productId) {
      return;
    }
    refreshImagesMutation.mutate();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [productId]);

  useEffect(() => {
    setImageFallbackIndex({});
  }, [productId, imagesQuery.data?.length]);

  const invalidateViews = async () => {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: PRODUCTS_QUERY_KEY }),
      queryClient.invalidateQueries({ queryKey: [...PRODUCT_DETAIL_QUERY_KEY, productId] }),
      queryClient.invalidateQueries({ queryKey: [...PRODUCT_IMAGES_QUERY_KEY, productId] }),
    ]);
  };

  const prepareImageForUpload = async (
    asset: { uri: string; mimeType?: string | null; fileName?: string | null },
  ): Promise<{ localUri: string; uploadPayload: UploadImagePayload }> => {
    try {
      const normalized = await normalizeImageForUpload({
        uri: asset.uri,
        mimeType: asset.mimeType,
        fileName: asset.fileName,
        forceJpeg: true,
      });
      return {
        localUri: normalized.uri,
        uploadPayload: {
          uri: normalized.uri,
          fileName: normalized.fileName,
          mimeType: normalized.mimeType,
        },
      };
    } catch (error) {
      if (__DEV__) {
        console.warn('[image-upload] Failed to normalize image. Falling back to original URI.', {
          rawUri: asset.uri,
          mimeType: asset.mimeType,
          error,
        });
      }
      return {
        localUri: asset.uri,
        uploadPayload: {
          uri: asset.uri,
          fileName: asset.fileName ?? `img-${Date.now()}.jpg`,
          mimeType: asset.mimeType ?? 'image/jpeg',
        },
      };
    }
  };

  const getPersistedImageUrl = (image: LocalProductImage | null | undefined) => {
    if (!image || !productId) {
      return null;
    }

    const candidates = resolveProductImageCandidates({
      apiBaseUrl: API_BASE_URL,
      productId,
      localImageId: image.id,
      remoteImageId: image.remoteImageId,
      localUri: image.localUri,
      cachedUri: image.cachedUri,
      syncStatus: image.syncStatus,
      productImageUrl: productQuery.data?.imagemUrl ?? null,
    });

    return candidates[0] ?? null;
  };

  const resolveImageSource = (image: LocalProductImage): ImageSourcePropType | undefined => {
    const candidates = resolveProductImageCandidates({
      apiBaseUrl: API_BASE_URL,
      productId,
      localImageId: image.id,
      remoteImageId: image.remoteImageId,
      localUri: image.localUri,
      cachedUri: image.cachedUri,
      syncStatus: image.syncStatus,
      productImageUrl: productQuery.data?.imagemUrl ?? null,
    });
    const fallbackIndex = Math.min(
      imageFallbackIndex[image.id] ?? 0,
      Math.max(candidates.length - 1, 0),
    );
    const finalUri = candidates[fallbackIndex] ?? null;
    return buildProductImageSource(finalUri, token);
  };

  const handleDetailImageError = (image: LocalProductImage) => {
    const candidates = resolveProductImageCandidates({
      apiBaseUrl: API_BASE_URL,
      productId,
      localImageId: image.id,
      remoteImageId: image.remoteImageId,
      localUri: image.localUri,
      cachedUri: image.cachedUri,
      syncStatus: image.syncStatus,
      productImageUrl: productQuery.data?.imagemUrl ?? null,
    });
    const fallbackIndex = imageFallbackIndex[image.id] ?? 0;
    const attemptedUri = candidates[fallbackIndex] ?? null;

    if (__DEV__) {
      console.warn('[image-render] Detail image load failed', {
        productId,
        localImageId: image.id,
        remoteImageId: image.remoteImageId,
        syncStatus: image.syncStatus,
        attemptedUri,
        fallbackIndex,
        candidates,
      });
    }

    setImageFallbackIndex((previous) => {
      const current = previous[image.id] ?? 0;
      const next = current + 1;
      if (next >= candidates.length) {
        void (async () => {
          await productImageRepository.markCacheError(
            image.id,
            `Render fallback exhausted for image ${image.id}`,
          );
          await queryClient.invalidateQueries({
            queryKey: [...PRODUCT_IMAGES_QUERY_KEY, productId],
          });
        })();
        return previous;
      }
      return { ...previous, [image.id]: next };
    });
  };

  const pickFromCamera = async (): Promise<ImagePickerAsset | null> => {
    const permission = await ImagePicker.requestCameraPermissionsAsync();
    if (!permission.granted) {
      Alert.alert('Permissao da camera', 'Permita o acesso para anexar imagens.');
      return null;
    }

    const result = await ImagePicker.launchCameraAsync({
      mediaTypes: 'images',
      quality: 0.9,
      allowsEditing: false,
    });

    if (result.canceled || result.assets.length === 0) {
      return null;
    }
    return result.assets[0];
  };

  const pickFromGallery = async (): Promise<ImagePickerAsset | null> => {
    const permission = await ImagePicker.requestMediaLibraryPermissionsAsync();
    if (!permission.granted) {
      Alert.alert('Permissao da galeria', 'Permita o acesso para anexar imagens.');
      return null;
    }

    const result = await ImagePicker.launchImageLibraryAsync({
      mediaTypes: 'images',
      quality: 0.9,
      allowsEditing: false,
    });

    if (result.canceled || result.assets.length === 0) {
      return null;
    }
    return result.assets[0];
  };

  const addImageFromAsset = async (
    asset: { uri: string; mimeType?: string | null; fileName?: string | null },
    preferredPrimary = false,
  ) => {
    if (!productId) {
      return;
    }

    const preparedImage = await prepareImageForUpload(asset);
    const uploadPayloadObject = toUploadPayloadObject(preparedImage.uploadPayload);
    const currentImages = (imagesQuery.data ?? []).filter(
      (image) =>
        Boolean(image.localUri && image.localUri.trim().length > 0) ||
        Boolean(image.cachedUri && image.cachedUri.trim().length > 0) ||
        Boolean(image.remoteImageId && image.remoteImageId.trim().length > 0) ||
        image.id.startsWith('remote-'),
    );
    const isPrimary = preferredPrimary || currentImages.length === 0;
    const localImageId = await productImageRepository.saveDraftImage({
      productId,
      localUri: preparedImage.localUri,
      isPrimary,
      sourceType: 'POST_EDIT',
      syncStatus: 'PENDENTE',
    });

    if (isPrimary) {
      await productRepository.setImageUrl(productId, preparedImage.localUri);
    }
    await invalidateViews();

    const queuePayload = {
      productId,
      localImageId,
      localUri: preparedImage.localUri,
      mimeType: uploadPayloadObject.mimeType,
      fileName: uploadPayloadObject.fileName,
      isPrimary,
    };

    try {
      const response = await uploadProductImage(productId, preparedImage.uploadPayload, isPrimary);
      if (response.status === 'UPDATED') {
        const remoteId = response.resourceId ?? null;
        await productImageRepository.markSynced(
          localImageId,
          remoteId,
          remoteId ? buildProductImageContentUrl(productId, remoteId) : null,
        );
        if (remoteId) {
          await cacheRemoteImage({
            id: localImageId,
            productId,
            remoteImageId: remoteId,
            localUri: preparedImage.localUri,
            isPrimary,
            sourceType: 'POST_EDIT',
            syncStatus: 'SYNCED',
            createdAt: new Date().toISOString(),
            updatedAt: new Date().toISOString(),
          });
        }
        if (isPrimary && remoteId) {
          await productRepository.setImageUrl(productId, buildProductImageContentUrl(productId, remoteId));
        }
      }
    } catch (error) {
      if (isNetworkIssue(error)) {
        await syncQueueRepository.enqueue('IMAGE_ADD', queuePayload);
      } else {
        await productImageRepository.markError(localImageId);
        Alert.alert('Imagem', 'Falha ao anexar imagem agora. Tente novamente.');
      }
    }

    await invalidateViews();
  };

  const handleSetPrimary = async (image: LocalProductImage) => {
    if (!productId) {
      return;
    }

    const remoteImageId = resolveRemoteImageId(image.id, image.remoteImageId);
    const previousPrimaryId = (imagesQuery.data ?? []).find((candidate) => candidate.isPrimary)?.id ?? null;

    if (!remoteImageId || !isUuid(remoteImageId)) {
      Alert.alert('Imagem', 'Imagem remota invalida para definir como principal.');
      await refreshImagesMutation.mutateAsync();
      await invalidateViews();
      return;
    }

    await productImageRepository.setPrimary(productId, image.id);
    await productRepository.setImageUrl(productId, getPersistedImageUrl(image));
    await invalidateViews();

    try {
      const response = await setProductImagePrimary(productId, remoteImageId);
      if (response.status === 'PENDING_APPROVAL') {
        await productImageRepository.markPending(image.id);
      } else {
        await productImageRepository.markSynced(
          image.id,
          remoteImageId,
          buildProductImageContentUrl(productId, remoteImageId),
        );
        await cacheRemoteImage({
          ...image,
          remoteImageId,
          syncStatus: 'SYNCED',
        });
      }
      try {
        await refreshImagesMutation.mutateAsync();
      } catch {
        // Keep local optimistic state; a later manual sync will reconcile.
      }
      const refreshedImages = await productImageRepository.getByProductId(productId);
      const nextPrimary =
        refreshedImages.find((candidate) => candidate.isPrimary) ?? refreshedImages[0];
      await productRepository.setImageUrl(productId, getPersistedImageUrl(nextPrimary));
    } catch (error) {
      if (isNetworkIssue(error)) {
        await syncQueueRepository.enqueue('IMAGE_SET_PRIMARY', {
          productId,
          localImageId: image.id,
          remoteImageId,
        });
      } else {
        try {
          await refreshImagesMutation.mutateAsync();
        } catch {
          // noop
        }
        if (previousPrimaryId) {
          const refreshedImages = await productImageRepository.getByProductId(productId);
          const previousPrimary = refreshedImages.find(
            (candidate) => candidate.id === previousPrimaryId,
          );
          if (previousPrimary) {
            await productRepository.setImageUrl(productId, getPersistedImageUrl(previousPrimary));
          }
        }
        Alert.alert('Imagem', extractApiMessage(error, 'Falha ao alterar imagem principal.'));
      }
    }
    await invalidateViews();
  };

  const handleDeleteImage = async (image: LocalProductImage) => {
    if (!productId) {
      return;
    }

    await productImageRepository.markDeleted(image.id);
    await invalidateViews();

    const remoteImageId = resolveRemoteImageId(image.id, image.remoteImageId);
    if (!remoteImageId) {
      await productImageRepository.deleteById(image.id);
      const remainingImages = await productImageRepository.getByProductId(productId);
      const nextPrimary = remainingImages.find((candidate) => candidate.isPrimary) ?? remainingImages[0];
      await productRepository.setImageUrl(productId, getPersistedImageUrl(nextPrimary));
      await invalidateViews();
      return;
    }

    try {
      const response = await deleteProductImage(productId, remoteImageId);
      if (response.status === 'UPDATED') {
        await productImageRepository.deleteById(image.id);
        const remainingImages = await productImageRepository.getByProductId(productId);
        const nextPrimary =
          remainingImages.find((candidate) => candidate.isPrimary) ?? remainingImages[0];
        await productRepository.setImageUrl(productId, getPersistedImageUrl(nextPrimary));
        try {
          await refreshImagesMutation.mutateAsync();
        } catch {
          // noop
        }
      }
    } catch (error) {
      if (isNetworkIssue(error)) {
        await syncQueueRepository.enqueue('IMAGE_DELETE', {
          productId,
          localImageId: image.id,
          remoteImageId,
        });
      } else {
        Alert.alert('Imagem', extractApiMessage(error, 'Falha ao remover imagem.'));
      }
    }

    await invalidateViews();
  };

  const handleReuploadImage = (image: LocalProductImage) => {
    Alert.alert('Reenviar imagem', 'Escolha uma nova imagem para substituir visualmente.', [
      {
        text: 'Camera',
        onPress: () => {
          void (async () => {
            const asset = await pickFromCamera();
            if (!asset) {
              return;
            }
            await addImageFromAsset(
              {
                uri: asset.uri,
                mimeType: asset.mimeType ?? null,
                fileName: asset.fileName ?? null,
              },
              image.isPrimary,
            );
          })();
        },
      },
      {
        text: 'Galeria',
        onPress: () => {
          void (async () => {
            const asset = await pickFromGallery();
            if (!asset) {
              return;
            }
            await addImageFromAsset(
              {
                uri: asset.uri,
                mimeType: asset.mimeType ?? null,
                fileName: asset.fileName ?? null,
              },
              image.isPrimary,
            );
          })();
        },
      },
      { text: 'Cancelar', style: 'cancel' },
    ]);
  };

  const openEditScreen = () => {
    if (!productId) {
      return;
    }
    router.push(`/product/${productId}/edit` as never);
  };

  if (productQuery.isLoading) {
    return (
      <SafeAreaView className="flex-1 items-center justify-center bg-slate-950">
        <ActivityIndicator color="#34d399" />
      </SafeAreaView>
    );
  }

  const product = productQuery.data;
  if (!product) {
    return (
      <SafeAreaView className="flex-1 bg-slate-950 px-6 pt-6">
        <Text className="text-2xl font-bold text-slate-100">Produto nao encontrado</Text>
        <Text className="mt-2 text-slate-400">
          Este item nao esta no banco local. Execute a sincronizacao na Home.
        </Text>
        <AppButton className="mt-6" label="Voltar" onPress={() => router.back()} />
      </SafeAreaView>
    );
  }

  const showAdminFinancialFields = userRole === 'ADMIN';
  const images = (imagesQuery.data ?? []).filter(
    (image) =>
      Boolean(image.localUri && image.localUri.trim().length > 0) ||
      Boolean(image.cachedUri && image.cachedUri.trim().length > 0) ||
      Boolean(image.remoteImageId && image.remoteImageId.trim().length > 0) ||
      image.cacheStatus === 'ERROR' ||
      image.id.startsWith('remote-'),
  );

  return (
    <SafeAreaView className="flex-1 bg-slate-950">
      <ScrollView
        className="flex-1"
        contentContainerStyle={{ paddingHorizontal: 24, paddingTop: 24, paddingBottom: 28 }}
        showsVerticalScrollIndicator={false}
      >
        <Text className="text-3xl font-bold text-emerald-400">Detalhes do Produto</Text>
        <Text className="mt-2 text-slate-300">{product.descricao}</Text>

        <View className="mt-5 rounded-2xl border border-slate-800 bg-slate-900 p-4">
          <View className="mb-3 flex-row items-center justify-between">
            <Text className="text-base font-semibold text-slate-100">Imagens</Text>
            <View className="flex-row gap-2">
              <Pressable
                className="rounded-lg bg-slate-800 px-3 py-2"
                onPress={() => {
                  void (async () => {
                    const asset = await pickFromCamera();
                    if (asset) {
                      await addImageFromAsset({
                        uri: asset.uri,
                        mimeType: asset.mimeType ?? null,
                        fileName: asset.fileName ?? null,
                      });
                    }
                  })();
                }}
              >
                <Text className="text-xs font-semibold text-slate-200">Camera</Text>
              </Pressable>
              <Pressable
                className="rounded-lg bg-slate-800 px-3 py-2"
                onPress={() => {
                  void (async () => {
                    const asset = await pickFromGallery();
                    if (asset) {
                      await addImageFromAsset({
                        uri: asset.uri,
                        mimeType: asset.mimeType ?? null,
                        fileName: asset.fileName ?? null,
                      });
                    }
                  })();
                }}
              >
                <Text className="text-xs font-semibold text-slate-200">Galeria</Text>
              </Pressable>
            </View>
          </View>

          {images.length === 0 ? (
            <Text className="text-xs text-slate-400">Nenhuma imagem cadastrada.</Text>
          ) : (
            <ScrollView horizontal showsHorizontalScrollIndicator={false}>
              <View className="flex-row gap-3">
                {images.map((image) => (
                  <View
                    key={image.id}
                    className={`w-36 rounded-xl border p-2 ${
                      image.isPrimary ? 'border-emerald-500 bg-emerald-500/10' : 'border-slate-700'
                    }`}
                  >
                    {resolveImageSource(image) ? (
                      <Image
                        className="h-24 w-full rounded-lg"
                        onError={() => {
                          handleDetailImageError(image);
                        }}
                        resizeMode="cover"
                        source={resolveImageSource(image)}
                      />
                    ) : (
                      <View className="h-24 w-full items-center justify-center rounded-lg bg-slate-800">
                        <Ionicons color="#94a3b8" name="image-outline" size={24} />
                      </View>
                    )}
                    <Text className="mt-2 text-[11px] text-slate-300">
                      {image.isPrimary ? 'Principal' : 'Secundaria'}
                    </Text>
                    <Text className="text-[11px] text-slate-400">{image.syncStatus}</Text>
                    {image.cacheStatus === 'ERROR' ? (
                      <Text className="text-[11px] text-amber-300">Imagem invalida, reenviar</Text>
                    ) : null}
                    <View className="mt-2 gap-1">
                      {!image.isPrimary ? (
                        <Pressable
                          className="rounded-md bg-slate-800 px-2 py-1"
                          onPress={() => {
                            void handleSetPrimary(image);
                          }}
                        >
                          <Text className="text-[11px] text-slate-200">Definir principal</Text>
                        </Pressable>
                      ) : null}
                      {image.cacheStatus === 'ERROR' ? (
                        <Pressable
                          className="rounded-md bg-amber-500/20 px-2 py-1"
                          onPress={() => {
                            handleReuploadImage(image);
                          }}
                        >
                          <Text className="text-[11px] text-amber-300">Reenviar</Text>
                        </Pressable>
                      ) : null}
                      <Pressable
                        className="rounded-md bg-rose-500/20 px-2 py-1"
                        onPress={() => {
                          void handleDeleteImage(image);
                        }}
                      >
                        <Text className="text-[11px] text-rose-300">Remover</Text>
                      </Pressable>
                    </View>
                  </View>
                ))}
              </View>
            </ScrollView>
          )}
        </View>

        <View className="mt-5 gap-3">
          <DetailRow label="ID" value={product.id} />
          <DetailRow label="Referencia" value={product.referencia} />
          <DetailRow label="Codigo de barras" value={product.codigoBarras} />
          <DetailRow label="Marca" value={product.marca} />
          <DetailRow label="Cor" value={product.cor} />
          <DetailRow label="Tamanho" value={product.tamanho} />
          <DetailRow label="Preco de venda" value={formatCurrency(product.precoVenda)} />

          {showAdminFinancialFields ? (
            <>
              <DetailRow label="Preco de custo" value={formatCurrency(product.precoCusto)} />
              <DetailRow
                label="Markup percentual"
                value={
                  product.markupPercentual !== null &&
                  product.markupPercentual !== undefined
                    ? `${product.markupPercentual.toFixed(2)}%`
                    : '--'
                }
              />
            </>
          ) : null}

          <DetailRow label="Quantidade atual" value={product.quantidadeAtual} />
          <DetailRow label="Quantidade minima" value={product.quantidadeMinima} />
          <DetailRow label="Status validacao" value={product.statusValidacao} />
          <DetailRow label="Status sync" value={product.syncStatus} />
          <DetailRow label="Atualizado em" value={product.updatedAt} />
        </View>

        <View className="mt-6 gap-3">
          <AppButton
            className="bg-slate-800"
            label="Editar"
            onPress={openEditScreen}
            textClassName="text-slate-100"
          />
          <AppButton label="Voltar" onPress={() => router.back()} />
        </View>
      </ScrollView>
    </SafeAreaView>
  );
}
