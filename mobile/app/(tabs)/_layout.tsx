import { Tabs } from 'expo-router';

export default function TabsLayout() {
  return (
    <Tabs
      screenOptions={{
        headerShown: false,
        tabBarActiveTintColor: '#34d399',
        tabBarStyle: {
          backgroundColor: '#020617',
          borderTopColor: '#1e293b',
        },
      }}
    >
      <Tabs.Screen
        name="index"
        options={{
          title: 'Inicio',
        }}
      />
    </Tabs>
  );
}
