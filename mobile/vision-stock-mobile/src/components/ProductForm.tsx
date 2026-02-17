import { zodResolver } from '@hookform/resolvers/zod';
import { Controller, useForm } from 'react-hook-form';
import { useEffect } from 'react';
import { Text, View } from 'react-native';
import { z } from 'zod';

import type { ProductResponseDTO } from '../types/product';
import { AppButton } from './ui/AppButton';
import { AppInput } from './ui/AppInput';

const decimalPattern = /^\d+(?:[.,]\d{1,2})?$/;
const integerPattern = /^\d+$/;

const productFormSchema = z.object({
  referencia: z.string().trim().max(100, 'Referencia muito longa.'),
  codigoBarras: z.string().trim().max(100, 'Codigo de barras muito longo.'),
  descricao: z.string().trim().min(1, 'Descricao obrigatoria.'),
  precoCusto: z
    .string()
    .trim()
    .refine(
      (value) => value.length === 0 || decimalPattern.test(value),
      'Preco de compra invalido. Use formato 59,90.',
    ),
  precoVenda: z
    .string()
    .trim()
    .min(1, 'Preco de venda obrigatorio.')
    .refine(
      (value) => decimalPattern.test(value),
      'Preco invalido. Use formato 99,90.',
    ),
  quantidadeInicial: z
    .string()
    .trim()
    .min(1, 'Quantidade inicial obrigatoria.')
    .refine((value) => integerPattern.test(value), 'Informe um numero inteiro.'),
  quantidadeMinima: z
    .string()
    .trim()
    .refine(
      (value) => value.length === 0 || integerPattern.test(value),
      'Informe um numero inteiro.',
    ),
  tamanho: z.string().trim(),
  cor: z.string().trim(),
  marca: z.string().trim(),
});

export type ProductFormValues = z.infer<typeof productFormSchema>;

type ProductFormProps = {
  initialData?: ProductResponseDTO | null;
  isSubmitting?: boolean;
  onCancel: () => void;
  onSubmit: (values: ProductFormValues) => void;
};

const toPriceInputValue = (price?: number | null) => {
  if (price === null || price === undefined) {
    return '';
  }
  return String(price).replace('.', ',');
};

const buildDefaultValues = (initialData?: ProductResponseDTO | null) => ({
  referencia: initialData?.referencia ?? '',
  codigoBarras: initialData?.codigoBarras ?? '',
  descricao: initialData?.descricao ?? '',
  precoCusto: '',
  precoVenda: toPriceInputValue(initialData?.precoVenda),
  quantidadeInicial: '0',
  quantidadeMinima:
    initialData?.quantidadeMinima !== null &&
    initialData?.quantidadeMinima !== undefined
      ? String(initialData.quantidadeMinima)
      : '0',
  tamanho: initialData?.tamanho ?? '',
  cor: initialData?.cor ?? '',
  marca: initialData?.marca ?? '',
});

export function ProductForm({
  initialData,
  isSubmitting = false,
  onCancel,
  onSubmit,
}: ProductFormProps) {
  const {
    control,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm<ProductFormValues>({
    resolver: zodResolver(productFormSchema),
    defaultValues: buildDefaultValues(initialData),
  });

  useEffect(() => {
    reset(buildDefaultValues(initialData));
  }, [initialData, reset]);

  return (
    <View className="gap-4">
      <View>
        <Text className="mb-2 text-sm font-medium text-slate-300">Referencia</Text>
        <Controller
          control={control}
          name="referencia"
          render={({ field: { onChange, onBlur, value } }) => (
            <AppInput
              autoCapitalize="characters"
              onBlur={onBlur}
              onChangeText={onChange}
              placeholder="Ex: CAM-POLO-001"
              value={value}
            />
          )}
        />
        {errors.referencia ? (
          <Text className="mt-1 text-xs text-rose-400">{errors.referencia.message}</Text>
        ) : null}
      </View>

      <View>
        <Text className="mb-2 text-sm font-medium text-slate-300">Codigo de Barras</Text>
        <Controller
          control={control}
          name="codigoBarras"
          render={({ field: { onChange, onBlur, value } }) => (
            <AppInput
              keyboardType="number-pad"
              onBlur={onBlur}
              onChangeText={onChange}
              placeholder="Ex: 7891234567890"
              value={value}
            />
          )}
        />
        {errors.codigoBarras ? (
          <Text className="mt-1 text-xs text-rose-400">{errors.codigoBarras.message}</Text>
        ) : null}
      </View>

      <View>
        <Text className="mb-2 text-sm font-medium text-slate-300">Descricao</Text>
        <Controller
          control={control}
          name="descricao"
          render={({ field: { onChange, onBlur, value } }) => (
            <AppInput
              onBlur={onBlur}
              onChangeText={onChange}
              placeholder="Ex: Camiseta Basica"
              returnKeyType="next"
              value={value}
            />
          )}
        />
        {errors.descricao ? (
          <Text className="mt-1 text-xs text-rose-400">{errors.descricao.message}</Text>
        ) : null}
      </View>

      <View>
        <Text className="mb-2 text-sm font-medium text-slate-300">Preco de Compra</Text>
        <Controller
          control={control}
          name="precoCusto"
          render={({ field: { onChange, onBlur, value } }) => (
            <AppInput
              keyboardType="decimal-pad"
              onBlur={onBlur}
              onChangeText={onChange}
              placeholder="Ex: 45,00"
              value={value}
            />
          )}
        />
        {errors.precoCusto ? (
          <Text className="mt-1 text-xs text-rose-400">{errors.precoCusto.message}</Text>
        ) : (
          <Text className="mt-1 text-xs text-slate-500">
            Opcional, mas recomendado para calculo de margem.
          </Text>
        )}
      </View>

      <View>
        <Text className="mb-2 text-sm font-medium text-slate-300">Preco de Venda</Text>
        <Controller
          control={control}
          name="precoVenda"
          render={({ field: { onChange, onBlur, value } }) => (
            <AppInput
              keyboardType="decimal-pad"
              onBlur={onBlur}
              onChangeText={onChange}
              placeholder="Ex: 89,90"
              value={value}
            />
          )}
        />
        {errors.precoVenda ? (
          <Text className="mt-1 text-xs text-rose-400">{errors.precoVenda.message}</Text>
        ) : (
          <Text className="mt-1 text-xs text-slate-500">Campo obrigatorio.</Text>
        )}
      </View>

      <View>
        <Text className="mb-2 text-sm font-medium text-slate-300">Quantidade Inicial</Text>
        <Controller
          control={control}
          name="quantidadeInicial"
          render={({ field: { onChange, onBlur, value } }) => (
            <AppInput
              keyboardType="number-pad"
              onBlur={onBlur}
              onChangeText={onChange}
              placeholder="Ex: 50"
              value={value}
            />
          )}
        />
        {errors.quantidadeInicial ? (
          <Text className="mt-1 text-xs text-rose-400">
            {errors.quantidadeInicial.message}
          </Text>
        ) : null}
      </View>

      <View>
        <Text className="mb-2 text-sm font-medium text-slate-300">Quantidade Minima</Text>
        <Controller
          control={control}
          name="quantidadeMinima"
          render={({ field: { onChange, onBlur, value } }) => (
            <AppInput
              keyboardType="number-pad"
              onBlur={onBlur}
              onChangeText={onChange}
              placeholder="Ex: 10"
              value={value}
            />
          )}
        />
        {errors.quantidadeMinima ? (
          <Text className="mt-1 text-xs text-rose-400">
            {errors.quantidadeMinima.message}
          </Text>
        ) : (
          <Text className="mt-1 text-xs text-slate-500">
            Usado para alertas de reposicao.
          </Text>
        )}
      </View>

      <View>
        <Text className="mb-2 text-sm font-medium text-slate-300">Tamanho</Text>
        <Controller
          control={control}
          name="tamanho"
          render={({ field: { onChange, onBlur, value } }) => (
            <AppInput
              autoCapitalize="characters"
              onBlur={onBlur}
              onChangeText={onChange}
              placeholder="Ex: M"
              value={value}
            />
          )}
        />
      </View>

      <View>
        <Text className="mb-2 text-sm font-medium text-slate-300">Cor</Text>
        <Controller
          control={control}
          name="cor"
          render={({ field: { onChange, onBlur, value } }) => (
            <AppInput
              onBlur={onBlur}
              onChangeText={onChange}
              placeholder="Ex: Azul Marinho"
              value={value}
            />
          )}
        />
      </View>

      <View>
        <Text className="mb-2 text-sm font-medium text-slate-300">Marca</Text>
        <Controller
          control={control}
          name="marca"
          render={({ field: { onChange, onBlur, value } }) => (
            <AppInput
              onBlur={onBlur}
              onChangeText={onChange}
              placeholder="Ex: Nike"
              value={value}
            />
          )}
        />
      </View>

      <View className="mt-2 gap-3">
        <AppButton
          label={isSubmitting ? 'Salvando...' : 'Salvar Produto'}
          loading={isSubmitting}
          onPress={handleSubmit(onSubmit)}
        />
        <AppButton
          className="bg-slate-800"
          disabled={isSubmitting}
          label="Cancelar"
          onPress={onCancel}
          textClassName="text-slate-100"
        />
      </View>
    </View>
  );
}
