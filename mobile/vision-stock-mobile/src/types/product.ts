export interface ProductResponseDTO {
  id?: string | null;
  referencia?: string | null;
  codigoBarras?: string | null;
  imagemUrl?: string | null;
  descricao?: string | null;
  tamanho?: string | null;
  cor?: string | null;
  marca?: string | null;
  categoryId?: string | null;
  precoVenda?: number | null;
  quantidadeAtual?: number | null;
  quantidadeMinima?: number | null;
  statusIa?: string | null;
  statusValidacao?: string | null;
  syncStatus?: string | null;
  createdAt?: string | null;
  updatedAt?: string | null;
}

export interface ProductSyncDTO extends ProductResponseDTO {
  precoCusto?: number | null;
  markupPercentual?: number | null;
  versao?: number | null;
  createdBy?: string | null;
  updatedBy?: string | null;
}

export interface LocalProduct {
  id: string;
  referencia?: string | null;
  codigoBarras?: string | null;
  imagemUrl?: string | null;
  serverImagemUrl?: string | null;
  descricao: string;
  tamanho?: string | null;
  cor?: string | null;
  marca?: string | null;
  categoryId?: string | null;
  precoCusto?: number | null;
  precoVenda?: number | null;
  markupPercentual?: number | null;
  quantidadeAtual: number;
  quantidadeMinima: number;
  statusIa?: string | null;
  statusValidacao?: string | null;
  syncStatus?: string | null;
  createdAt?: string | null;
  updatedAt?: string | null;
  localPendingChanges?: number;
  lastPendingValidationId?: string | null;
}

export interface ProductCreateDTO {
  id: string;
  referencia?: string;
  descricao?: string;
  tamanho?: string;
  cor?: string;
  marca?: string;
  codigoBarras?: string;
  precoCusto?: number;
  precoVenda?: number | null;
  quantidadeInicial: number;
  quantidadeMinima?: number;
}

export interface ProductUpdateDTO {
  referencia?: string;
  codigoBarras?: string;
  descricao?: string;
  tamanho?: string;
  cor?: string;
  marca?: string;
  precoCusto?: number | null;
  precoVenda?: number | null;
  quantidadeMinima?: number;
  nota?: string;
}

export interface ProductUpdateResponseDTO {
  success: boolean;
  status: 'UPDATED' | 'PENDING_APPROVAL';
  message: string;
  validationRequestId?: string | null;
  product?: ProductResponseDTO | null;
}

export interface StockAdjustmentDTO {
  quantidadeDelta: number;
  motivo?: string;
}

export interface ActionResponseDTO {
  success: boolean;
  status: 'UPDATED' | 'PENDING_APPROVAL';
  message: string;
  validationRequestId?: string | null;
  resourceId?: string | null;
}

export interface ProductImageMetadataDTO {
  id: string;
  productId: string;
  fileName?: string | null;
  contentType?: string | null;
  fileSize?: number | null;
  sha256?: string | null;
  width?: number | null;
  height?: number | null;
  isPrimary?: boolean | null;
  sortOrder?: number | null;
  createdAt?: string | null;
  updatedAt?: string | null;
}

export type LocalImageSyncStatus = 'PENDENTE' | 'SYNCED' | 'ERRO';
export type LocalImageCacheStatus = 'NONE' | 'SYNCED' | 'ERROR';

export interface LocalProductImage {
  id: string;
  productId: string;
  remoteImageId?: string | null;
  localUri?: string | null;
  cachedUri?: string | null;
  cacheStatus?: LocalImageCacheStatus | null;
  cacheUpdatedAt?: string | null;
  cacheError?: string | null;
  mimeType?: string | null;
  fileSize?: number | null;
  width?: number | null;
  height?: number | null;
  sha256?: string | null;
  isPrimary: boolean;
  sourceType: 'SCAN' | 'MANUAL' | 'POST_EDIT';
  syncStatus: LocalImageSyncStatus;
  createdAt: string;
  updatedAt: string;
  deletedAt?: string | null;
}

export type SyncQueueOperationType =
  | 'PRODUCT_UPDATE'
  | 'STOCK_ADJUSTMENT'
  | 'IMAGE_ADD'
  | 'IMAGE_DELETE'
  | 'IMAGE_SET_PRIMARY';

export interface ValidationRequestDTO {
  id: string;
  productId?: string | null;
  status?: string | null;
  changeType?: string | null;
}
