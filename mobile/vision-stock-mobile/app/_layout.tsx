import { Stack, router } from 'expo-router';
import { useEffect, useState } from 'react';
import { ActivityIndicator, View } from 'react-native';
import '../global.css';

import { runMigrations } from '../src/database';
import { setUnauthorizedHandler } from '../src/services/api';
import { queryClient } from '../src/services/queryClient';
import { useAuthStore } from '../src/store/authStore';
import { QueryClientProvider } from '@tanstack/react-query';

export default function RootLayout() {
  const hydrate = useAuthStore((state) => state.hydrate);
  const [isBootstrapReady, setIsBootstrapReady] = useState(false);

  useEffect(() => {
    let isMounted = true;

    const bootstrap = async () => {
      try {
        await Promise.all([hydrate(), runMigrations()]);
      } finally {
        if (isMounted) {
          setIsBootstrapReady(true);
        }
      }
    };

    void bootstrap();

    setUnauthorizedHandler(async () => {
      await useAuthStore.getState().clearSession();
      router.replace('/login');
    });
    return () => {
      isMounted = false;
      setUnauthorizedHandler(null);
    };
  }, [hydrate]);

  if (!isBootstrapReady) {
    return (
      <View className="flex-1 items-center justify-center bg-slate-950">
        <ActivityIndicator color="#34d399" />
      </View>
    );
  }

  return (
    <QueryClientProvider client={queryClient}>
      <Stack screenOptions={{ headerShown: false }} />
    </QueryClientProvider>
  );
}
