import { Text, View } from 'react-native';

import { AppButton } from '../../src/components/ui/AppButton';
import { useAuthStore } from '../../src/store/authStore';

export default function HomeScreen() {
  const user = useAuthStore((state) => state.user);
  const logout = useAuthStore((state) => state.logout);

  return (
    <View className="flex-1 bg-slate-950 px-6 pt-16">
      <Text className="text-3xl font-bold text-emerald-400">VisionStock</Text>
      <Text className="mt-2 text-base text-slate-300">Bem-vindo ao app.</Text>

      <View className="mt-6 rounded-2xl border border-slate-800 bg-slate-900 p-4">
        <Text className="text-slate-200">Usuario: {user?.email ?? '-'}</Text>
        <Text className="mt-1 text-slate-200">Role: {user?.role ?? '-'}</Text>
        <Text className="mt-1 text-slate-400">ID: {user?.userId ?? '-'}</Text>
      </View>

      <AppButton className="mt-8" label="Sair" onPress={() => void logout()} />
    </View>
  );
}
