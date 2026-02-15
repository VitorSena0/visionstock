import { Redirect } from 'expo-router';
import { ActivityIndicator, View } from 'react-native';

import { useAuthStore } from '../src/store/authStore';

export default function IndexScreen() {
  const token = useAuthStore((state) => state.token);
  const isHydrated = useAuthStore((state) => state.isHydrated);

  if (!isHydrated) {
    return (
      <View className="flex-1 items-center justify-center bg-slate-950">
        <ActivityIndicator color="#34d399" />
      </View>
    );
  }

  return <Redirect href={token ? '/(tabs)' : '/login'} />;
}
