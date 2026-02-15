import { router } from 'expo-router';
import { useState } from 'react';
import {
  Alert,
  KeyboardAvoidingView,
  Platform,
  SafeAreaView,
  Text,
  View,
} from 'react-native';

import { AppButton } from '../../src/components/ui/AppButton';
import { AppInput } from '../../src/components/ui/AppInput';
import { API_BASE_URL, IS_API_URL_FROM_ENV } from '../../src/services/api';
import { useAuthStore } from '../../src/store/authStore';

export default function LoginScreen() {
  const login = useAuthStore((state) => state.login);
  const isLoading = useAuthStore((state) => state.isLoading);
  const error = useAuthStore((state) => state.error);

  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');

  const handleSubmit = async () => {
    if (!email.trim() || !password.trim()) {
      Alert.alert('Campos obrigatorios', 'Informe e-mail e senha para continuar.');
      return;
    }

    try {
      await login(email, password);
    } catch {
      // Feedback de erro vem do estado global (authStore.error)
    }
  };

  return (
    <SafeAreaView className="flex-1 bg-slate-950">
      <KeyboardAvoidingView
        className="flex-1 justify-center px-6"
        behavior={Platform.OS === 'ios' ? 'padding' : undefined}
      >
        <View>
          <Text className="text-4xl font-bold text-emerald-400">VisionStock</Text>
          <Text className="mt-2 text-base text-slate-300">
            Controle de estoque offline-first com sincronizacao segura.
          </Text>
        </View>

        <View className="mt-8">
          <Text className="mb-2 text-sm font-medium text-slate-300">E-mail</Text>
          <AppInput
            autoCapitalize="none"
            autoCorrect={false}
            keyboardType="email-address"
            placeholder="voce@empresa.com"
            value={email}
            onChangeText={setEmail}
          />

          <Text className="mb-2 mt-4 text-sm font-medium text-slate-300">Senha</Text>
          <AppInput
            placeholder="Digite sua senha"
            secureTextEntry
            value={password}
            onChangeText={setPassword}
          />

          {error ? <Text className="mt-3 text-sm text-rose-400">{error}</Text> : null}

          <AppButton
            className="mt-6"
            label={isLoading ? 'Entrando...' : 'Entrar'}
            loading={isLoading}
            onPress={handleSubmit}
          />

          <AppButton
            className="mt-3 bg-slate-800"
            textClassName="text-slate-100"
            label="Voltar"
            onPress={() => router.replace('/')}
          />

          <Text className="mt-4 text-xs text-slate-400">API: {API_BASE_URL}</Text>
          {!IS_API_URL_FROM_ENV ? (
            <Text className="mt-1 text-xs text-amber-300">
              EXPO_PUBLIC_API_URL nao foi carregada do .env.
            </Text>
          ) : null}
        </View>
      </KeyboardAvoidingView>
    </SafeAreaView>
  );
}
