export type NormalizedUploadImage = {
  uri: string;
  mimeType: string;
  fileName: string;
  wasConvertedToJpeg: boolean;
};

const buildJpegName = () => `img-${Date.now()}.jpg`;
const buildOriginalName = (extension?: string) =>
  `img-${Date.now()}${extension ? `.${extension}` : ''}`;

const inferMimeTypeFromUri = (uri: string) => {
  const sanitizedUri = uri.split('?')[0]?.toLowerCase() ?? uri.toLowerCase();
  const extension = sanitizedUri.split('.').pop();

  if (extension === 'png') {
    return 'image/png';
  }
  if (extension === 'webp') {
    return 'image/webp';
  }
  if (extension === 'heic') {
    return 'image/heic';
  }
  if (extension === 'heif') {
    return 'image/heif';
  }
  return 'image/jpeg';
};

const extensionFromMimeType = (mimeType: string) => {
  const normalizedMimeType = mimeType.trim().toLowerCase();
  if (normalizedMimeType === 'image/png') {
    return 'png';
  }
  if (normalizedMimeType === 'image/webp') {
    return 'webp';
  }
  if (normalizedMimeType === 'image/heic') {
    return 'heic';
  }
  if (normalizedMimeType === 'image/heif') {
    return 'heif';
  }
  return 'jpg';
};

type ImageManipulatorSaveFormat = {
  JPEG: unknown;
};

type ExpoImageManipulatorModule = {
  manipulateAsync: (
    uri: string,
    actions: unknown[],
    options: { compress?: number; format?: unknown },
  ) => Promise<{ uri: string }>;
  SaveFormat?: ImageManipulatorSaveFormat;
};

const loadImageManipulatorModule = async (): Promise<ExpoImageManipulatorModule | null> => {
  try {
    const dynamicImport = new Function(
      'modulePath',
      'return import(modulePath);',
    ) as (modulePath: string) => Promise<unknown>;
    const module = (await dynamicImport('expo-image-manipulator')) as Partial<ExpoImageManipulatorModule>;
    if (typeof module.manipulateAsync !== 'function') {
      return null;
    }
    return module as ExpoImageManipulatorModule;
  } catch {
    return null;
  }
};

export const normalizeImageForUpload = async (
  input: {
    uri: string;
    mimeType?: string | null;
    fileName?: string | null;
    forceJpeg?: boolean;
  },
): Promise<NormalizedUploadImage> => {
  const normalizedMimeType =
    input.mimeType?.trim().toLowerCase() || inferMimeTypeFromUri(input.uri);
  const normalizedFileName =
    input.fileName?.trim() || buildOriginalName(extensionFromMimeType(normalizedMimeType));

  const imageManipulator = await loadImageManipulatorModule();
  const shouldForceJpeg = input.forceJpeg ?? true;
  if (!shouldForceJpeg || !imageManipulator || !imageManipulator.SaveFormat?.JPEG) {
    return {
      uri: input.uri,
      mimeType: normalizedMimeType,
      fileName: normalizedFileName,
      wasConvertedToJpeg: false,
    };
  }

  try {
    const result = await imageManipulator.manipulateAsync(input.uri, [], {
      compress: 0.82,
      format: imageManipulator.SaveFormat.JPEG,
    });

    return {
      uri: result.uri,
      mimeType: 'image/jpeg',
      fileName: buildJpegName(),
      wasConvertedToJpeg: true,
    };
  } catch {
    return {
      uri: input.uri,
      mimeType: normalizedMimeType,
      fileName: normalizedFileName,
      wasConvertedToJpeg: false,
    };
  }
};
