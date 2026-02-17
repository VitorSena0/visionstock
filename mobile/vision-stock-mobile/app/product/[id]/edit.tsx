import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQuery } from '@tanstack/react-query';
import axios from 'axios';
import { router, useLocalSearchParams } from 'expo-router';
import { Controller, useForm } from 'react-hook-form';
import { useState } from 'react';
import { Alert, ScrollView, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { z } from 'zod';

import { AppButton } from '../../../src/components/ui/AppButton';
import { AppInput } from '../../../src/components/ui/AppInput';
import { productRepository } from '../../../src/database/productRepository';
import { syncQueueRepository } from '../../../src/database/syncQueueRepository';
import { adjustStock, updateProduct } from '../../../src/services/api';
import { queryClient } from '../../../src/services/queryClient';
import {
  PRODUCT_DETAIL_QUERY_KEY,
  PRODUCTS_QUERY_KEY,
} from '../../../src/services/syncService';
import type { ProductUpdateDTO } from '../../../src/types/product';

const decimalPattern = /^\d+(?:[.,]\d{1,2})?$/;
const integerPattern = /^\d+$/;
const signedIntegerPattern = /^-?\d+$/;

const updateSchema = z.object({
  referencia: z.string().trim().max(100, 'Referencia muito longa.'),
  codigoBarras: z.string().trim().max(100, 'Codigo de barras muito longo.'),
  descricao: z.string().trim().min(1, 'Descricao obrigatoria.'),
  tamanho: z.string().trim(),
  cor: z.string().trim(),
  marca: z.string().trim(),
  precoCusto: z
    .string()
    .trim()
    .refine((value) => !value || decimalPattern.test(value), 'Preco de custo invalido.'),
  precoVenda: z
    .string()
    .trim()
    .refine((value) => !value || decimalPattern.test(value), 'Preco de venda invalido.'),
  quantidadeMinima: z
    .string()
    .trim()
    .refine((value) => !value || integerPattern.test(value), 'Quantidade minima invalida.'),
  nota: z.string().trim().max(500, 'Nota muito longa.'),
});

type UpdateFormValues = z.infer<typeof updateSchema>;

const toPrice = (value: string): number | null => {
  const normalized = value.trim().replace(',', '.');
  if (!normalized) {
    return null;
  }
  const numeric = Number(normalized);
  return Number.isFinite(numeric) ? numeric : null;
};

const toInteger = (value: string): number | null => {
  const normalized = value.trim();
  if (!normalized) {
    return null;
  }
  const numeric = Number(normalized);
  if (!Number.isInteger(numeric)) {
    return null;
  }
  return numeric;
};

const isNetworkIssue = (error: unknown) =>
  axios.isAxiosError(error) && (!error.response || error.code === 'ERR_NETWORK');

export default function ProductEditScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const productId = Array.isArray(id) ? id[0] : id;
  const [deltaInput, setDeltaInput] = useState('');
  const [stockReason, setStockReason] = useState('');

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

  const form = useForm<UpdateFormValues>({
    resolver: zodResolver(updateSchema),
    values: {
      referencia: productQuery.data?.referencia ?? '',
      codigoBarras: productQuery.data?.codigoBarras ?? '',
      descricao: productQuery.data?.descricao ?? '',
      tamanho: productQuery.data?.tamanho ?? '',
      cor: productQuery.data?.cor ?? '',
      marca: productQuery.data?.marca ?? '',
      precoCusto:
        productQuery.data?.precoCusto !== null && productQuery.data?.precoCusto !== undefined
          ? String(productQuery.data.precoCusto).replace('.', ',')
          : '',
      precoVenda:
        productQuery.data?.precoVenda !== null && productQuery.data?.precoVenda !== undefined
          ? String(productQuery.data.precoVenda).replace('.', ',')
          : '',
      quantidadeMinima:
        productQuery.data?.quantidadeMinima !== null &&
        productQuery.data?.quantidadeMinima !== undefined
          ? String(productQuery.data.quantidadeMinima)
          : '',
      nota: '',
    },
  });

  const invalidate = async () => {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: PRODUCTS_QUERY_KEY }),
      queryClient.invalidateQueries({ queryKey: [...PRODUCT_DETAIL_QUERY_KEY, productId] }),
    ]);
  };

  const updateMutation = useMutation({
    mutationFn: async (payload: ProductUpdateDTO) => {
      if (!productId) {
        throw new Error('Produto invalido');
      }
      return updateProduct(productId, payload);
    },
    onSuccess: async (response, payload) => {
      if (!productId) {
        return;
      }
      if (response.status === 'UPDATED' && response.product) {
        await productRepository.saveOne(response.product);
      } else {
        await productRepository.applyLocalProductUpdate(
          productId,
          payload,
          response.validationRequestId ?? null,
        );
      }
      await invalidate();
      Alert.alert(
        'Produto',
        response.status === 'UPDATED'
          ? 'Produto atualizado com sucesso.'
          : 'Alteracao enviada para aprovacao do gerente.',
      );
      router.back();
    },
    onError: async (error, payload) => {
      if (!productId) {
        return;
      }

      if (isNetworkIssue(error)) {
        await productRepository.applyLocalProductUpdate(productId, payload, null);
        await syncQueueRepository.enqueue('PRODUCT_UPDATE', {
          productId,
          patch: payload,
        });
        await invalidate();
        Alert.alert(
          'Sem internet',
          'Edicao salva localmente e pendente de sincronizacao.',
        );
        router.back();
        return;
      }

      Alert.alert('Falha na edicao', 'Nao foi possivel atualizar o produto.');
    },
  });

  const stockMutation = useMutation({
    mutationFn: async (payload: { quantidadeDelta: number; motivo?: string }) => {
      if (!productId) {
        throw new Error('Produto invalido');
      }
      return adjustStock(productId, payload);
    },
    onSuccess: async (response, payload) => {
      if (!productId) {
        return;
      }
      await productRepository.applyLocalStockAdjustment(
        productId,
        payload.quantidadeDelta,
        payload.motivo,
        response.validationRequestId ?? null,
      );
      await invalidate();
      setDeltaInput('');
      setStockReason('');
      Alert.alert(
        'Estoque',
        response.status === 'UPDATED'
          ? 'Ajuste de estoque aplicado.'
          : 'Ajuste enviado para aprovacao do gerente.',
      );
    },
    onError: async (error, payload) => {
      if (!productId) {
        return;
      }
      if (isNetworkIssue(error)) {
        await productRepository.applyLocalStockAdjustment(
          productId,
          payload.quantidadeDelta,
          payload.motivo,
          null,
        );
        await syncQueueRepository.enqueue('STOCK_ADJUSTMENT', {
          productId,
          quantidadeDelta: payload.quantidadeDelta,
          motivo: payload.motivo,
        });
        await invalidate();
        setDeltaInput('');
        setStockReason('');
        Alert.alert(
          'Sem internet',
          'Ajuste salvo localmente e pendente de sincronizacao.',
        );
        return;
      }
      Alert.alert('Falha no ajuste', 'Nao foi possivel ajustar o estoque.');
    },
  });

  const handleSubmit = form.handleSubmit((values) => {
    const payload: ProductUpdateDTO = {
      referencia: values.referencia.trim() || undefined,
      codigoBarras: values.codigoBarras.trim() || undefined,
      descricao: values.descricao.trim() || undefined,
      tamanho: values.tamanho.trim() || undefined,
      cor: values.cor.trim() || undefined,
      marca: values.marca.trim() || undefined,
      precoCusto: toPrice(values.precoCusto),
      precoVenda: toPrice(values.precoVenda),
      quantidadeMinima: toInteger(values.quantidadeMinima) ?? undefined,
      nota: values.nota.trim() || undefined,
    };

    updateMutation.mutate(payload);
  });

  const handleAdjustStock = () => {
    const normalized = deltaInput.trim();
    if (!signedIntegerPattern.test(normalized)) {
      Alert.alert('Ajuste invalido', 'Informe um numero inteiro positivo ou negativo.');
      return;
    }

    const delta = Number(normalized);
    if (!Number.isInteger(delta) || delta === 0) {
      Alert.alert('Ajuste invalido', 'O ajuste deve ser diferente de zero.');
      return;
    }

    stockMutation.mutate({
      quantidadeDelta: delta,
      motivo: stockReason.trim() || undefined,
    });
  };

  if (productQuery.isLoading) {
    return (
      <SafeAreaView className="flex-1 items-center justify-center bg-slate-950">
        <Text className="text-slate-200">Carregando...</Text>
      </SafeAreaView>
    );
  }

  if (!productId || !productQuery.data) {
    return (
      <SafeAreaView className="flex-1 items-center justify-center bg-slate-950 px-6">
        <Text className="text-lg text-slate-100">Produto nao encontrado.</Text>
        <AppButton className="mt-4" label="Voltar" onPress={() => router.back()} />
      </SafeAreaView>
    );
  }

  return (
    <SafeAreaView className="flex-1 bg-slate-950">
      <ScrollView
        className="flex-1"
        contentContainerStyle={{ paddingHorizontal: 24, paddingTop: 24, paddingBottom: 28 }}
        showsVerticalScrollIndicator={false}
      >
        <Text className="text-3xl font-bold text-emerald-400">Editar Produto</Text>
        <Text className="mt-1 text-sm text-slate-400">
          Quantidade atual nao e editada diretamente. Use Ajustar estoque.
        </Text>

        <View className="mt-5 gap-4">
          <View>
            <Text className="mb-2 text-sm text-slate-300">Referencia</Text>
            <Controller
              control={form.control}
              name="referencia"
              render={({ field: { onChange, value } }) => (
                <AppInput value={value} onChangeText={onChange} />
              )}
            />
          </View>

          <View>
            <Text className="mb-2 text-sm text-slate-300">Codigo de Barras</Text>
            <Controller
              control={form.control}
              name="codigoBarras"
              render={({ field: { onChange, value } }) => (
                <AppInput value={value} onChangeText={onChange} keyboardType="number-pad" />
              )}
            />
          </View>

          <View>
            <Text className="mb-2 text-sm text-slate-300">Descricao</Text>
            <Controller
              control={form.control}
              name="descricao"
              render={({ field: { onChange, value } }) => (
                <AppInput value={value} onChangeText={onChange} />
              )}
            />
            {form.formState.errors.descricao ? (
              <Text className="mt-1 text-xs text-rose-400">
                {form.formState.errors.descricao.message}
              </Text>
            ) : null}
          </View>

          <View>
            <Text className="mb-2 text-sm text-slate-300">Tamanho</Text>
            <Controller
              control={form.control}
              name="tamanho"
              render={({ field: { onChange, value } }) => (
                <AppInput value={value} onChangeText={onChange} />
              )}
            />
          </View>

          <View>
            <Text className="mb-2 text-sm text-slate-300">Cor</Text>
            <Controller
              control={form.control}
              name="cor"
              render={({ field: { onChange, value } }) => (
                <AppInput value={value} onChangeText={onChange} />
              )}
            />
          </View>

          <View>
            <Text className="mb-2 text-sm text-slate-300">Marca</Text>
            <Controller
              control={form.control}
              name="marca"
              render={({ field: { onChange, value } }) => (
                <AppInput value={value} onChangeText={onChange} />
              )}
            />
          </View>

          <View>
            <Text className="mb-2 text-sm text-slate-300">Preco de Custo</Text>
            <Controller
              control={form.control}
              name="precoCusto"
              render={({ field: { onChange, value } }) => (
                <AppInput value={value} onChangeText={onChange} keyboardType="decimal-pad" />
              )}
            />
          </View>

          <View>
            <Text className="mb-2 text-sm text-slate-300">Preco de Venda</Text>
            <Controller
              control={form.control}
              name="precoVenda"
              render={({ field: { onChange, value } }) => (
                <AppInput value={value} onChangeText={onChange} keyboardType="decimal-pad" />
              )}
            />
          </View>

          <View>
            <Text className="mb-2 text-sm text-slate-300">Quantidade Minima</Text>
            <Controller
              control={form.control}
              name="quantidadeMinima"
              render={({ field: { onChange, value } }) => (
                <AppInput value={value} onChangeText={onChange} keyboardType="number-pad" />
              )}
            />
          </View>

          <View>
            <Text className="mb-2 text-sm text-slate-300">Nota</Text>
            <Controller
              control={form.control}
              name="nota"
              render={({ field: { onChange, value } }) => (
                <AppInput value={value} onChangeText={onChange} multiline />
              )}
            />
          </View>
        </View>

        <View className="mt-6 rounded-2xl border border-slate-800 bg-slate-900 p-4">
          <Text className="text-base font-semibold text-slate-100">Ajustar estoque</Text>
          <Text className="mt-1 text-xs text-slate-400">
            Use valor positivo para entrada e negativo para saida.
          </Text>

          <View className="mt-3 gap-3">
            <AppInput
              keyboardType="numbers-and-punctuation"
              placeholder="Ex: +5 ou -2"
              value={deltaInput}
              onChangeText={setDeltaInput}
            />
            <AppInput
              placeholder="Motivo do ajuste (opcional)"
              value={stockReason}
              onChangeText={setStockReason}
            />
            <AppButton
              label={stockMutation.isPending ? 'Aplicando...' : 'Aplicar ajuste'}
              loading={stockMutation.isPending}
              onPress={handleAdjustStock}
            />
          </View>
        </View>

        <View className="mt-6 gap-3">
          <AppButton
            label={updateMutation.isPending ? 'Salvando...' : 'Salvar alteracoes'}
            loading={updateMutation.isPending}
            onPress={handleSubmit}
          />
          <AppButton
            className="bg-slate-800"
            label="Cancelar"
            onPress={() => router.back()}
            textClassName="text-slate-100"
          />
        </View>
      </ScrollView>
    </SafeAreaView>
  );
}
