import { Ionicons } from '@expo/vector-icons';
import { useMutation, useQuery } from '@tanstack/react-query';
import axios from 'axios';
import { router } from 'expo-router';
import * as ImagePicker from 'expo-image-picker';
import type { ImagePickerAsset } from 'expo-image-picker';
import { useEffect, useMemo, useRef, useState } from 'react';
import {
  ActivityIndicator,
  Alert,
  FlatList,
  Image,
  Modal,
  Pressable,
  ScrollView,
  Text,
  View,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { ImagePickerButton } from '../../src/components/ImagePickerButton';
import {
  ProductForm,
  type ProductFormValues,
} from '../../src/components/ProductForm';
import { AppButton } from '../../src/components/ui/AppButton';
import { AppInput } from '../../src/components/ui/AppInput';
import { productImageRepository } from '../../src/database/productImageRepository';
import { productRepository } from '../../src/database/productRepository';
import { syncQueueRepository } from '../../src/database/syncQueueRepository';
import {
  API_BASE_URL,
  type UploadImagePayload,
  adjustStock,
  buildProductImageContentUrl,
  createProduct,
  uploadImage,
  uploadProductImage,
} from '../../src/services/api';
import { queryClient } from '../../src/services/queryClient';
import {
  PRODUCT_DETAIL_QUERY_KEY,
  PRODUCT_IMAGES_QUERY_KEY,
  PRODUCTS_QUERY_KEY,
  pullProducts,
} from '../../src/services/syncService';
import { cacheRemoteProductImageContent } from '../../src/services/imageContentService';
import { useAuthStore } from '../../src/store/authStore';
import type {
  LocalProduct,
  ProductCreateDTO,
  ProductResponseDTO,
} from '../../src/types/product';
import { normalizeImageForUpload } from '../../src/utils/imageUpload';
import {
  buildProductImageSource,
  resolveProductImageCandidates,
} from '../../src/utils/productImage';

const toOptionalString = (value?: string | null) => {
  const normalized = value?.trim();
  return normalized ? normalized : undefined;
};

const toPriceNumber = (value: string) => {
  const normalized = value.trim().replace(',', '.');
  if (!normalized) {
    return null;
  }

  const numericValue = Number(normalized);
  return Number.isFinite(numericValue) ? numericValue : null;
};

const toIntegerNumber = (value: string) => {
  const normalized = value.trim();
  if (!normalized) {
    return null;
  }

  const numericValue = Number(normalized);
  if (!Number.isInteger(numericValue) || numericValue < 0) {
    return null;
  }

  return numericValue;
};

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
    return 'Nao foi possivel conectar ao backend. Verifique IP, porta e token.';
  }

  return `${fallbackMessage} (HTTP ${error.response.status ?? 'desconhecido'})`;
};

const extractScanErrorMessage = (error: unknown) => {
  if (axios.isAxiosError(error)) {
    if (error.response?.status === 429) {
      const retryAfterHeader = error.response.headers?.['retry-after'];
      const retryAfterRaw = Array.isArray(retryAfterHeader)
        ? retryAfterHeader[0]
        : retryAfterHeader;
      const retryAfterSeconds = retryAfterRaw ? Number.parseInt(String(retryAfterRaw), 10) : NaN;

      if (Number.isFinite(retryAfterSeconds) && retryAfterSeconds > 0) {
        return `A IA atingiu o limite temporario de consultas. Aguarde ${retryAfterSeconds}s e tente novamente.`;
      }

      return 'A IA atingiu o limite temporario de consultas. Aguarde alguns segundos e tente novamente.';
    }

    const isNetworkError =
      !error.response ||
      error.message === 'Network Error' ||
      error.code === 'ERR_NETWORK';

    if (isNetworkError) {
      return 'Network Error no upload da imagem. Verifique se o backend esta acessivel na mesma rede.';
    }
  }

  return extractApiMessage(error, 'Falha ao consultar a IA.');
};

const isNetworkIssue = (error: unknown) =>
  axios.isAxiosError(error) &&
  (!error.response || error.message === 'Network Error' || error.code === 'ERR_NETWORK');

const isDuplicateProductConflictError = (error: unknown) => {
  if (!axios.isAxiosError(error)) {
    return false;
  }

  if (error.response?.status === 409) {
    return true;
  }

  const payload = error.response?.data;
  if (!payload || typeof payload !== 'object' || !('message' in payload)) {
    return false;
  }

  const message = String(payload.message ?? '').toLowerCase();
  return (
    message.includes('codigo de barras') ||
    message.includes('código de barras') ||
    message.includes('barcode') ||
    message.includes('referencia') ||
    message.includes('reference') ||
    message.includes('already exists') ||
    message.includes('ja existe')
  );
};

const formatCurrency = (value?: number | null) => {
  if (value === null || value === undefined) {
    return 'R$ --';
  }

  return value.toLocaleString('pt-BR', {
    style: 'currency',
    currency: 'BRL',
  });
};

const isLowStock = (product: LocalProduct) => {
  const quantidadeAtual = product.quantidadeAtual ?? 0;
  const quantidadeMinima = product.quantidadeMinima ?? 0;

  if (quantidadeMinima <= 0) {
    return quantidadeAtual <= 0;
  }

  return quantidadeAtual <= quantidadeMinima;
};

const fallbackUuid = () =>
  'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (char) => {
    const random = Math.floor(Math.random() * 16);
    const value = char === 'x' ? random : (random & 0x3) | 0x8;
    return value.toString(16);
  });

const generateProductId = () => {
  const webCrypto = globalThis.crypto;
  if (webCrypto && typeof webCrypto.randomUUID === 'function') {
    return webCrypto.randomUUID();
  }
  return fallbackUuid();
};

const toUploadPayloadObject = (payload: UploadImagePayload) =>
  typeof payload === 'string'
    ? { uri: payload, mimeType: null as string | null, fileName: null as string | null }
    : {
        uri: payload.uri,
        mimeType: payload.mimeType ?? null,
        fileName: payload.fileName ?? null,
      };

export default function HomeScreen() {
  const user = useAuthStore((state) => state.user);
  const token = useAuthStore((state) => state.token);
  const logout = useAuthStore((state) => state.logout);

  const [selectedImageUri, setSelectedImageUri] = useState<string | null>(null);
  const [draftImageUri, setDraftImageUri] = useState<string | null>(null);
  const [draftProductId, setDraftProductId] = useState<string | null>(null);
  const [scanResult, setScanResult] = useState<ProductResponseDTO | null>(null);
  const [isReviewModalVisible, setIsReviewModalVisible] = useState(false);
  const [search, setSearch] = useState('');
  const [syncFeedback, setSyncFeedback] = useState<string | null>(null);
  const [cardImageFallbackIndex, setCardImageFallbackIndex] = useState<Record<string, number>>({});
  const createSubmitLockRef = useRef(false);

  const normalizedSearch = useMemo(() => search.trim().toLowerCase(), [search]);

  const productsQuery = useQuery({
    queryKey: PRODUCTS_QUERY_KEY,
    queryFn: () => productRepository.getAll(),
    initialData: [],
  });

  const filteredProducts = useMemo(() => {
    const data = productsQuery.data ?? [];
    if (!normalizedSearch) {
      return data;
    }

    return data.filter((product) => {
      const descricao = product.descricao?.toLowerCase() ?? '';
      const referencia = product.referencia?.toLowerCase() ?? '';
      const ean = product.codigoBarras?.toLowerCase() ?? '';
      return (
        descricao.includes(normalizedSearch) ||
        referencia.includes(normalizedSearch) ||
        ean.includes(normalizedSearch)
      );
    });
  }, [normalizedSearch, productsQuery.data]);

  useEffect(() => {
    setCardImageFallbackIndex({});
  }, [productsQuery.data?.length]);

  const syncMutation = useMutation({
    mutationFn: async ({ silent }: { silent: boolean }) => {
      const result = await pullProducts();
      return { result, silent };
    },
    onSuccess: ({ result, silent }) => {
      if (silent) {
        return;
      }

      if (result.status === 'offline') {
        setSyncFeedback(result.message);
        return;
      }

      setSyncFeedback(`Sincronizacao concluida: ${result.syncedCount} produto(s).`);
    },
    onError: (error, variables) => {
      if (variables.silent) {
        return;
      }

      setSyncFeedback(null);
      Alert.alert('Falha na sincronizacao', extractApiMessage(error, 'Erro ao sincronizar.'));
    },
  });

  const scanMutation = useMutation({
    mutationFn: uploadImage,
    onSuccess: (data) => {
      setScanResult(data);
      setDraftProductId(generateProductId());
      setIsReviewModalVisible(true);
    },
    onError: (error) => {
      Alert.alert('Falha no scan', extractScanErrorMessage(error), [
        { text: 'Fechar', style: 'cancel' },
        {
          text: 'Cadastrar manualmente',
          onPress: () => {
            setScanResult(null);
            setDraftProductId(generateProductId());
            setIsReviewModalVisible(true);
          },
        },
      ]);
    },
  });

  const resetDraftState = () => {
    setIsReviewModalVisible(false);
    setSelectedImageUri(null);
    setDraftImageUri(null);
    setDraftProductId(null);
    setScanResult(null);
  };

  const invalidateProductQueries = async () => {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: PRODUCTS_QUERY_KEY }),
      queryClient.invalidateQueries({ queryKey: PRODUCT_DETAIL_QUERY_KEY }),
      queryClient.invalidateQueries({ queryKey: PRODUCT_IMAGES_QUERY_KEY }),
    ]);
  };

  const findLocalProductForConflict = async (payload: ProductCreateDTO) => {
    if (payload.id) {
      const byId = await productRepository.getById(payload.id);
      if (byId) {
        return byId;
      }
    }

    const normalizedBarcode = payload.codigoBarras?.trim();
    if (normalizedBarcode) {
      const byBarcode = await productRepository.getAll(normalizedBarcode);
      const matchedBarcode =
        byBarcode.find(
          (candidate) =>
            (candidate.codigoBarras?.trim().toLowerCase() ?? '') ===
            normalizedBarcode.toLowerCase(),
        ) ?? null;
      if (matchedBarcode) {
        return matchedBarcode;
      }
    }

    const normalizedReferencia = payload.referencia?.trim();
    if (normalizedReferencia) {
      const byReferencia = await productRepository.getAll(normalizedReferencia);
      const matchedReferencia =
        byReferencia.find(
          (candidate) =>
            (candidate.referencia?.trim().toLowerCase() ?? '') ===
            normalizedReferencia.toLowerCase(),
        ) ?? null;
      if (matchedReferencia) {
        return matchedReferencia;
      }
    }

    return null;
  };

  const attachDraftImageToProduct = async (
    productId: string,
    sourceType: 'SCAN' | 'MANUAL',
  ): Promise<string | null> => {
    if (!draftImageUri) {
      return null;
    }

    let localImageId: string | null = null;

    try {
      await productRepository.setImageUrl(productId, draftImageUri);
    } catch (error) {
      if (__DEV__) {
        console.warn('[create-product] Failed to store local preview URI on product.', {
          productId,
          draftImageUri,
          error,
        });
      }
    }

    try {
      const preparedImage = await prepareImageForUpload({
        uri: draftImageUri,
        mimeType: null,
        fileName: null,
      });

      const uploadPayloadObject = toUploadPayloadObject(preparedImage.uploadPayload);
      localImageId = await productImageRepository.saveDraftImage({
        productId,
        localUri: preparedImage.localUri,
        isPrimary: true,
        sourceType,
        syncStatus: 'PENDENTE',
      });

      const queuePayload = {
        productId,
        localImageId,
        localUri: preparedImage.localUri,
        mimeType: uploadPayloadObject.mimeType,
        fileName: uploadPayloadObject.fileName,
        isPrimary: true,
      } as const;

      try {
        const imageResponse = await uploadProductImage(productId, preparedImage.uploadPayload, true);
        if (imageResponse.status === 'UPDATED') {
          const remoteResourceId = imageResponse.resourceId?.trim() || null;
          await productImageRepository.markSynced(
            localImageId,
            remoteResourceId,
            remoteResourceId
              ? buildProductImageContentUrl(productId, remoteResourceId)
              : null,
          );

          if (remoteResourceId) {
            try {
              const cached = await cacheRemoteProductImageContent(productId, remoteResourceId);
              await productImageRepository.updateCache(localImageId, {
                cachedUri: cached.cachedUri,
                status: 'SYNCED',
                error: null,
              });
            } catch (cacheError) {
              await productImageRepository.updateCache(localImageId, {
                cachedUri: null,
                status: 'ERROR',
                error:
                  cacheError instanceof Error
                    ? cacheError.message
                    : 'Falha ao cachear imagem remota',
              });
            }

            await productRepository.setImageUrl(
              productId,
              buildProductImageContentUrl(productId, remoteResourceId),
            );
          }

          return null;
        }

        return 'Produto salvo. A imagem foi enviada para aprovacao do gerente.';
      } catch (error) {
        if (isNetworkIssue(error)) {
          try {
            await syncQueueRepository.enqueue('IMAGE_ADD', queuePayload);
            return 'Produto salvo localmente. A imagem sera sincronizada quando houver internet.';
          } catch (queueError) {
            if (__DEV__) {
              console.warn('[create-product] Failed to queue image upload for retry.', {
                productId,
                localImageId,
                queueError,
              });
            }
          }
        }

        if (localImageId) {
          try {
            await productImageRepository.markError(localImageId);
          } catch (markErrorFailure) {
            if (__DEV__) {
              console.warn('[create-product] Failed to mark local image as error.', {
                productId,
                localImageId,
                markErrorFailure,
              });
            }
          }
        }
        return 'Produto salvo, mas nao foi possivel anexar a imagem agora.';
      }
    } catch (error) {
      if (__DEV__) {
        console.warn('[create-product] Failed to process image after product save.', {
          productId,
          draftImageUri,
          localImageId,
          error,
        });
      }
      return 'Produto salvo, mas houve falha ao processar a imagem localmente.';
    }
  };

  const reconcileRecoveredProduct = async (
    recoveredProductId: string,
    payload: ProductCreateDTO,
    sourceType: 'SCAN' | 'MANUAL',
    shouldAdjustStock: boolean,
  ) => {
    const messageParts = ['O produto ja estava salvo no servidor.'];

    if (shouldAdjustStock && payload.quantidadeInicial > 0) {
      try {
        const response = await adjustStock(recoveredProductId, {
          quantidadeDelta: payload.quantidadeInicial,
          motivo: 'Entrada por cadastro duplicado (auto-reconciliacao)',
        });
        if (response.status === 'PENDING_APPROVAL') {
          messageParts.push('A soma de estoque foi enviada para aprovacao.');
        } else {
          messageParts.push('Estoque ajustado com sucesso.');
        }
      } catch (error) {
        messageParts.push(extractApiMessage(error, 'Falha ao ajustar o estoque automaticamente.'));
      }
    }

    if (draftImageUri) {
      const imageMessage = await attachDraftImageToProduct(recoveredProductId, sourceType);
      if (imageMessage) {
        messageParts.push(imageMessage);
      }
    }

    await invalidateProductQueries();
    resetDraftState();
    Alert.alert('Produto recuperado', messageParts.join(' '));
  };

  const createProductMutation = useMutation({
    mutationFn: createProduct,
    onSuccess: async (product, payload) => {
      let postSaveMessage = 'Produto cadastrado com sucesso.';
      let shouldForcePull = false;
      const sourceType: 'SCAN' | 'MANUAL' = scanResult ? 'SCAN' : 'MANUAL';
      const productId = product.id?.trim() || payload.id?.trim() || null;

      try {
        await productRepository.saveOne(product);
      } catch (error) {
        shouldForcePull = true;
        if (__DEV__) {
          console.warn('[create-product] Failed to persist product in SQLite after API success.', {
            productId,
            error,
          });
        }
      }

      if (productId && draftImageUri) {
        const imageMessage = await attachDraftImageToProduct(productId, sourceType);
        if (imageMessage) {
          postSaveMessage = imageMessage;
        }
      }

      try {
        await invalidateProductQueries();
      } catch (error) {
        shouldForcePull = true;
        if (__DEV__) {
          console.warn('[create-product] Failed to invalidate product queries.', {
            productId,
            error,
          });
        }
      }

      if (shouldForcePull) {
        try {
          await pullProducts();
        } catch (error) {
          if (__DEV__) {
            console.warn('[create-product] Forced pull failed after API success.', {
              productId,
              error,
            });
          }
        }
      }

      resetDraftState();
      Alert.alert('Produto salvo', postSaveMessage);
    },
    onError: async (error, payload) => {
      const fallbackMessage = extractApiMessage(error, 'Erro ao criar produto.');
      const sourceType: 'SCAN' | 'MANUAL' = scanResult ? 'SCAN' : 'MANUAL';

      if (isDuplicateProductConflictError(error)) {
        try {
          await pullProducts();

          const recoveredProduct = await findLocalProductForConflict(payload);

          if (recoveredProduct) {
            Alert.alert(
              'Produto ja existente',
              `Ja existe um produto com os mesmos dados. Deseja somar ${payload.quantidadeInicial} unidade(s) ao estoque do produto existente?`,
              [
                {
                  text: 'Somente recuperar',
                  style: 'cancel',
                  onPress: () => {
                    void reconcileRecoveredProduct(
                      recoveredProduct.id,
                      payload,
                      sourceType,
                      false,
                    );
                  },
                },
                {
                  text: 'Somar estoque',
                  onPress: () => {
                    void reconcileRecoveredProduct(
                      recoveredProduct.id,
                      payload,
                      sourceType,
                      true,
                    );
                  },
                },
              ],
            );
            return;
          }
        } catch (recoveryError) {
          if (__DEV__) {
            console.warn('[create-product] Duplicate barcode recovery failed.', {
              payload,
              recoveryError,
            });
          }
        }
      }

      Alert.alert('Falha ao salvar', fallbackMessage);
    },
    onSettled: () => {
      createSubmitLockRef.current = false;
    },
  });

  useEffect(() => {
    syncMutation.mutate({ silent: true });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

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

  const handleImageSelected = async (asset: ImagePickerAsset) => {
    const prepared = await prepareImageForUpload({
      uri: asset.uri,
      mimeType: asset.mimeType ?? null,
      fileName: asset.fileName ?? null,
    });
    setSelectedImageUri(prepared.localUri);
    setDraftImageUri(prepared.localUri);
    scanMutation.mutate(prepared.uploadPayload);
  };

  const pickImageFromCamera = async (): Promise<ImagePickerAsset | null> => {
    const permission = await ImagePicker.requestCameraPermissionsAsync();
    if (!permission.granted) {
      Alert.alert(
        'Permissao da camera',
        'Permita acesso a camera para escanear etiquetas.',
      );
      return null;
    }

    const result = await ImagePicker.launchCameraAsync({
      mediaTypes: 'images',
      quality: 0.9,
      allowsEditing: false,
    });

    if (!result.canceled && result.assets.length > 0) {
      return result.assets[0];
    }

    return null;
  };

  const pickImageFromGallery = async (): Promise<ImagePickerAsset | null> => {
    const permission = await ImagePicker.requestMediaLibraryPermissionsAsync();
    if (!permission.granted) {
      Alert.alert(
        'Permissao da galeria',
        'Permita acesso a galeria para selecionar etiquetas.',
      );
      return null;
    }

    const result = await ImagePicker.launchImageLibraryAsync({
      mediaTypes: 'images',
      quality: 0.9,
      allowsEditing: false,
    });

    if (!result.canceled && result.assets.length > 0) {
      return result.assets[0];
    }

    return null;
  };

  const handleCameraPick = async () => {
    const asset = await pickImageFromCamera();
    if (asset) {
      await handleImageSelected(asset);
    }
  };

  const handleGalleryPick = async () => {
    const asset = await pickImageFromGallery();
    if (asset) {
      await handleImageSelected(asset);
    }
  };

  const handleDraftCameraPick = async () => {
    const asset = await pickImageFromCamera();
    if (asset) {
      const prepared = await prepareImageForUpload({
        uri: asset.uri,
        mimeType: asset.mimeType ?? null,
        fileName: asset.fileName ?? null,
      });
      setDraftImageUri(prepared.localUri);
    }
  };

  const handleDraftGalleryPick = async () => {
    const asset = await pickImageFromGallery();
    if (asset) {
      const prepared = await prepareImageForUpload({
        uri: asset.uri,
        mimeType: asset.mimeType ?? null,
        fileName: asset.fileName ?? null,
      });
      setDraftImageUri(prepared.localUri);
    }
  };

  const closeReviewModal = () => {
    if (createProductMutation.isPending) {
      return;
    }

    setIsReviewModalVisible(false);
    setScanResult(null);
    setDraftImageUri(null);
    setDraftProductId(null);
  };

  const openManualCreateModal = () => {
    setScanResult(null);
    setDraftImageUri(null);
    setDraftProductId(generateProductId());
    setIsReviewModalVisible(true);
  };

  const handleCreateProduct = (values: ProductFormValues) => {
    if (createSubmitLockRef.current || createProductMutation.isPending) {
      return;
    }

    const precoVenda = toPriceNumber(values.precoVenda);
    if (precoVenda === null) {
      Alert.alert('Preco obrigatorio', 'Informe um preco de venda valido para salvar.');
      return;
    }

    const quantidadeInicial = toIntegerNumber(values.quantidadeInicial);
    if (quantidadeInicial === null) {
      Alert.alert(
        'Quantidade inicial invalida',
        'Informe uma quantidade inicial inteira e maior ou igual a zero.',
      );
      return;
    }

    const quantidadeMinima = toIntegerNumber(values.quantidadeMinima);
    if (values.quantidadeMinima.trim().length > 0 && quantidadeMinima === null) {
      Alert.alert(
        'Quantidade minima invalida',
        'Informe uma quantidade minima inteira e maior ou igual a zero.',
      );
      return;
    }

    const precoCusto = toPriceNumber(values.precoCusto);
    if (values.precoCusto.trim().length > 0 && precoCusto === null) {
      Alert.alert('Preco de compra invalido', 'Informe um valor de compra valido.');
      return;
    }

    const stableDraftProductId = draftProductId ?? generateProductId();
    if (!draftProductId) {
      setDraftProductId(stableDraftProductId);
    }

    const payload: ProductCreateDTO = {
      id: stableDraftProductId,
      referencia:
        toOptionalString(values.referencia) ?? toOptionalString(scanResult?.referencia),
      descricao: toOptionalString(values.descricao),
      tamanho: toOptionalString(values.tamanho),
      cor: toOptionalString(values.cor),
      marca: toOptionalString(values.marca),
      codigoBarras:
        toOptionalString(values.codigoBarras) ??
        toOptionalString(scanResult?.codigoBarras),
      precoCusto: precoCusto ?? undefined,
      precoVenda,
      quantidadeInicial,
      quantidadeMinima: quantidadeMinima ?? undefined,
    };

    createSubmitLockRef.current = true;
    createProductMutation.mutate(payload);
  };

  const handleManualSync = () => {
    if (syncMutation.isPending) {
      return;
    }

    setSyncFeedback(null);
    syncMutation.mutate({ silent: false });
  };

  const handleOpenProductDetails = (id: string) => {
    router.push({
      pathname: '/product/[id]',
      params: { id },
    });
  };

  const renderProductCard = ({ item }: { item: LocalProduct }) => {
    const lowStock = isLowStock(item);
    const imageCandidates = resolveProductImageCandidates({
      apiBaseUrl: API_BASE_URL,
      productImageUrl: item.imagemUrl ?? null,
      fallbackImageUrl: item.serverImagemUrl ?? null,
      syncStatus: item.syncStatus,
    });
    const fallbackIndex = Math.min(
      cardImageFallbackIndex[item.id] ?? 0,
      Math.max(imageCandidates.length - 1, 0),
    );
    const activeUri = imageCandidates[fallbackIndex] ?? null;
    const activeSource = buildProductImageSource(activeUri, token);

    const handleCardImageError = () => {
      if (__DEV__) {
        console.warn('[image-render] Home card image load failed', {
          productId: item.id,
          attemptedUri: activeUri,
          syncStatus: item.syncStatus,
          fallbackIndex,
          candidates: imageCandidates,
        });
      }

      setCardImageFallbackIndex((previous) => {
        const current = previous[item.id] ?? 0;
        const next = current + 1;
        if (next >= imageCandidates.length) {
          return previous;
        }
        return { ...previous, [item.id]: next };
      });
    };

    return (
      <Pressable
        className={`mb-3 rounded-2xl border p-4 ${
          lowStock
            ? 'border-amber-600 bg-amber-950/20'
            : 'border-slate-800 bg-slate-900'
        }`}
        onPress={() => handleOpenProductDetails(item.id)}
      >
        <View className="flex-row gap-3">
          {activeSource ? (
            <Image
              className="h-20 w-20 rounded-xl"
              onError={handleCardImageError}
              resizeMode="cover"
              source={activeSource}
            />
          ) : (
            <View className="h-20 w-20 items-center justify-center rounded-xl bg-slate-800">
              <Ionicons color="#94a3b8" name="image-outline" size={24} />
            </View>
          )}

          <View className="flex-1 gap-1">
            <Text className="text-base font-semibold text-slate-100">
              {item.descricao || 'Sem descricao'}
            </Text>
            <Text className="text-sm text-slate-300">{formatCurrency(item.precoVenda)}</Text>
            <Text className="text-xs text-slate-400">
              Qtd: {item.quantidadeAtual} | Min: {item.quantidadeMinima}
            </Text>
            <View className="mt-1 flex-row flex-wrap gap-2">
              <View className="rounded-full bg-slate-800 px-2 py-1">
                <Text className="text-[11px] text-slate-300">
                  {item.statusValidacao ?? 'SEM_STATUS'}
                </Text>
              </View>
              <View className="rounded-full bg-slate-800 px-2 py-1">
                <Text className="text-[11px] text-slate-300">
                  {item.syncStatus ?? 'LOCAL'}
                </Text>
              </View>
              {lowStock ? (
                <View className="rounded-full bg-amber-600/20 px-2 py-1">
                  <Text className="text-[11px] text-amber-300">Estoque baixo</Text>
                </View>
              ) : null}
            </View>
          </View>
        </View>
      </Pressable>
    );
  };

  return (
    <SafeAreaView className="flex-1 bg-slate-950">
      <FlatList
        className="flex-1"
        contentContainerStyle={{ paddingHorizontal: 24, paddingTop: 24, paddingBottom: 20 }}
        data={filteredProducts}
        keyExtractor={(item) => item.id}
        ListEmptyComponent={
          <View className="mt-2 rounded-2xl border border-dashed border-slate-700 bg-slate-900/60 p-4">
            <Text className="text-center text-sm text-slate-300">
              {normalizedSearch
                ? 'Nenhum produto encontrado para o filtro digitado.'
                : 'Nenhum produto no banco local ainda.'}
            </Text>
            <Text className="mt-1 text-center text-xs text-slate-400">
              {normalizedSearch
                ? 'A busca filtra em tempo real por descricao, referencia e EAN.'
                : 'Use o scan/cadastro e sincronize para atualizar esta lista.'}
            </Text>
          </View>
        }
        ListFooterComponent={
          <View className="mt-4">
            <AppButton label="Sair" onPress={() => void logout()} />
          </View>
        }
        ListHeaderComponent={
          <View>
            <Text className="text-3xl font-bold text-emerald-400">VisionStock</Text>
            <Text className="mt-2 text-base text-slate-300">
              Escaneie a etiqueta, revise os dados e salve o produto.
            </Text>

            <View className="mt-5 rounded-2xl border border-slate-800 bg-slate-900 p-4">
              <Text className="text-slate-200">Usuario: {user?.email ?? '-'}</Text>
              <Text className="mt-1 text-slate-200">Role: {user?.role ?? '-'}</Text>
              <Text className="mt-1 text-xs text-slate-400">ID: {user?.userId ?? '-'}</Text>
            </View>

            <View className="mt-6">
              <ImagePickerButton
                disabled={scanMutation.isPending || createProductMutation.isPending}
                loading={scanMutation.isPending}
                onPickFromCamera={() => void handleCameraPick()}
                onPickFromGallery={() => void handleGalleryPick()}
              />
            </View>

            <AppButton
              className="mt-3 bg-slate-800"
              disabled={scanMutation.isPending || createProductMutation.isPending}
              label="Cadastrar manualmente"
              onPress={openManualCreateModal}
              textClassName="text-slate-100"
            />

            {selectedImageUri ? (
              <View className="mt-5 rounded-2xl border border-slate-800 bg-slate-900 p-3">
                <Image
                  className="h-44 w-full rounded-xl"
                  resizeMode="cover"
                  source={{ uri: selectedImageUri }}
                />
                <Text className="mt-2 text-xs text-slate-400">
                  Ultima imagem enviada para analise.
                </Text>
              </View>
            ) : null}

            <View className="mt-6 rounded-2xl border border-slate-800 bg-slate-900 p-4">
              <View className="mb-3 flex-row items-center justify-between">
                <Text className="text-lg font-semibold text-slate-100">Produtos Locais</Text>
                <Pressable
                  className={`h-10 flex-row items-center justify-center gap-2 rounded-xl px-3 ${
                    syncMutation.isPending ? 'bg-slate-700' : 'bg-emerald-500/20'
                  }`}
                  disabled={syncMutation.isPending}
                  onPress={handleManualSync}
                >
                  {syncMutation.isPending ? (
                    <ActivityIndicator color="#34d399" size="small" />
                  ) : (
                    <Ionicons color="#34d399" name="refresh" size={18} />
                  )}
                  <Text className="text-xs font-semibold text-emerald-300">Sincronizar</Text>
                </Pressable>
              </View>

              <View className="flex-row items-center gap-2">
                <View className="flex-1">
                  <AppInput
                    placeholder="Buscar por descricao, referencia ou EAN"
                    value={search}
                    onChangeText={setSearch}
                  />
                </View>
              </View>

              {syncFeedback ? (
                <Text className="mt-2 text-xs text-slate-400">{syncFeedback}</Text>
              ) : null}
            </View>

            <Text className="mb-3 mt-4 text-xs uppercase tracking-widest text-slate-400">
              Toque no card para ver detalhes
            </Text>
          </View>
        }
        onRefresh={handleManualSync}
        refreshing={syncMutation.isPending}
        renderItem={renderProductCard}
        showsVerticalScrollIndicator={false}
      />

      <Modal
        animationType="slide"
        onRequestClose={closeReviewModal}
        transparent
        visible={isReviewModalVisible}
      >
        <View className="flex-1 justify-end bg-black/50">
          <View className="h-[85%] rounded-t-3xl border border-slate-800 bg-slate-950 p-5">
            <View className="mb-4 flex-row items-start justify-between gap-3">
              <View className="flex-1">
                <Text className="text-xl font-bold text-slate-100">Rascunho de Produto</Text>
                <Text className="mt-1 text-xs text-amber-300">
                  Nada foi salvo ainda. O cadastro so ocorre ao tocar em "Salvar Produto".
                </Text>
              </View>
              <Pressable
                className="h-8 w-8 items-center justify-center rounded-full bg-slate-800"
                onPress={closeReviewModal}
              >
                <Text className="text-base font-semibold text-slate-200">X</Text>
              </Pressable>
            </View>

            <View className="mb-4">
              <Text className="mt-1 text-sm text-slate-400">
                {scanResult
                  ? 'Revise os dados sugeridos pela IA antes de salvar.'
                  : 'Preencha os dados manualmente para cadastrar o produto.'}
              </Text>
            </View>

            <ScrollView
              className="flex-1"
              contentContainerStyle={{ paddingBottom: 16 }}
              showsVerticalScrollIndicator={false}
            >
              <View className="mb-4 rounded-2xl border border-slate-800 bg-slate-900 p-3">
                {draftImageUri ? (
                  <Image
                    className="h-40 w-full rounded-xl"
                    resizeMode="cover"
                    source={{ uri: draftImageUri }}
                  />
                ) : (
                  <View className="h-40 w-full items-center justify-center rounded-xl bg-slate-800">
                    <Ionicons color="#94a3b8" name="image-outline" size={36} />
                    <Text className="mt-2 text-xs text-slate-400">
                      Nenhuma imagem anexada no rascunho.
                    </Text>
                  </View>
                )}

                <View className="mt-3 gap-2">
                  <AppButton
                    className="bg-slate-800"
                    disabled={createProductMutation.isPending}
                    label={draftImageUri ? 'Trocar foto (camera)' : 'Anexar foto (camera)'}
                    onPress={() => void handleDraftCameraPick()}
                    textClassName="text-slate-100"
                  />
                  <AppButton
                    className="bg-slate-800"
                    disabled={createProductMutation.isPending}
                    label={draftImageUri ? 'Trocar foto (galeria)' : 'Anexar foto (galeria)'}
                    onPress={() => void handleDraftGalleryPick()}
                    textClassName="text-slate-100"
                  />
                  {draftImageUri ? (
                    <AppButton
                      className="bg-rose-500/20"
                      disabled={createProductMutation.isPending}
                      label="Remover imagem"
                      onPress={() => setDraftImageUri(null)}
                      textClassName="text-rose-300"
                    />
                  ) : null}
                </View>
              </View>

              <ProductForm
                initialData={scanResult}
                isSubmitting={createProductMutation.isPending}
                onCancel={closeReviewModal}
                onSubmit={handleCreateProduct}
              />
            </ScrollView>
          </View>
        </View>
      </Modal>

      {(scanMutation.isPending || createProductMutation.isPending) && !isReviewModalVisible ? (
        <View className="absolute bottom-8 left-0 right-0 items-center">
          <View className="flex-row items-center gap-2 rounded-full border border-slate-700 bg-slate-900 px-4 py-2">
            <ActivityIndicator color="#34d399" />
            <Text className="text-sm text-slate-100">
              {scanMutation.isPending ? 'Consultando IA...' : 'Salvando produto...'}
            </Text>
          </View>
        </View>
      ) : null}
    </SafeAreaView>
  );
}
