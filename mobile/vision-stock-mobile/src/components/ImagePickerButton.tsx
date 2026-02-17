import { MaterialIcons } from '@expo/vector-icons';
import { ActivityIndicator, Pressable, Text, View } from 'react-native';

type ImagePickerButtonProps = {
  onPickFromCamera: () => void;
  onPickFromGallery: () => void;
  loading?: boolean;
  disabled?: boolean;
};

type ActionButtonProps = {
  icon: keyof typeof MaterialIcons.glyphMap;
  label: string;
  onPress: () => void;
  disabled?: boolean;
};

function ActionButton({ icon, label, onPress, disabled }: ActionButtonProps) {
  return (
    <Pressable
      className={`h-14 flex-row items-center justify-center gap-2 rounded-xl border border-slate-700 bg-slate-950 ${disabled ? 'opacity-60' : ''}`}
      disabled={disabled}
      onPress={onPress}
    >
      <MaterialIcons color="#34d399" name={icon} size={22} />
      <Text className="text-base font-semibold text-slate-100">{label}</Text>
    </Pressable>
  );
}

export function ImagePickerButton({
  onPickFromCamera,
  onPickFromGallery,
  loading = false,
  disabled = false,
}: ImagePickerButtonProps) {
  const isDisabled = disabled || loading;

  return (
    <View className="rounded-3xl border border-slate-800 bg-slate-900 p-5">
      <View className="items-center gap-2">
        <View className="h-14 w-14 items-center justify-center rounded-full bg-emerald-500/20">
          <MaterialIcons color="#34d399" name="document-scanner" size={28} />
        </View>
        <Text className="text-center text-xl font-bold text-slate-100">
          Escanear Etiqueta
        </Text>
        <Text className="text-center text-sm text-slate-400">
          Tire uma foto ou selecione da galeria para obter sugestoes da IA.
        </Text>
      </View>

      <View className="mt-5 gap-3">
        <ActionButton
          disabled={isDisabled}
          icon="photo-camera"
          label="Usar camera"
          onPress={onPickFromCamera}
        />
        <ActionButton
          disabled={isDisabled}
          icon="photo-library"
          label="Escolher da galeria"
          onPress={onPickFromGallery}
        />
      </View>

      {loading ? (
        <View className="mt-4 flex-row items-center justify-center gap-2">
          <ActivityIndicator color="#34d399" />
          <Text className="text-sm text-emerald-300">Consultando IA...</Text>
        </View>
      ) : null}
    </View>
  );
}
