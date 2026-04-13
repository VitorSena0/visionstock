import * as SecureStore from 'expo-secure-store';
import { Platform } from 'react-native';

const isWeb = Platform.OS === 'web';

const hasLocalStorage = () => {
  return (
    isWeb &&
    typeof window !== 'undefined' &&
    typeof window.localStorage !== 'undefined'
  );
};

export const getStoredItem = async (key: string): Promise<string | null> => {
  if (hasLocalStorage()) {
    try {
      return window.localStorage.getItem(key);
    } catch {
      return null;
    }
  }

  return SecureStore.getItemAsync(key);
};

export const setStoredItem = async (key: string, value: string): Promise<void> => {
  if (hasLocalStorage()) {
    try {
      window.localStorage.setItem(key, value);
    } catch {
      // Ignore storage failures on web to keep app responsive.
    }
    return;
  }

  await SecureStore.setItemAsync(key, value);
};

export const deleteStoredItem = async (key: string): Promise<void> => {
  if (hasLocalStorage()) {
    try {
      window.localStorage.removeItem(key);
    } catch {
      // Ignore storage failures on web to keep app responsive.
    }
    return;
  }

  await SecureStore.deleteItemAsync(key);
};
