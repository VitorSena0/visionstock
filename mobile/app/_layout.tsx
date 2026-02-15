import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { Stack, router } from 'expo-router';
import { useEffect } from 'react';

import { setUnauthorizedHandler } from '../src/services/api';
import { useAuthStore } from '../src/store/authStore';

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 30_000,
      retry: 1,
      refetchOnReconnect: true,
    },
    mutations: {
      retry: 0,
    },
  },
});

export default function RootLayout() {
  const hydrate = useAuthStore((state) => state.hydrate);

  useEffect(() => {
    void hydrate();

    setUnauthorizedHandler(async () => {
      await useAuthStore.getState().clearSession();
      router.replace('/login');
    });

    return () => {
      setUnauthorizedHandler(null);
    };
  }, [hydrate]);

  return (
    <QueryClientProvider client={queryClient}>
      <Stack screenOptions={{ headerShown: false }} />
    </QueryClientProvider>
  );
}
