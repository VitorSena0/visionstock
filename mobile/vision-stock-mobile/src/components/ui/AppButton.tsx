import type { PressableProps } from 'react-native';
import { ActivityIndicator, Pressable, Text } from 'react-native';

type AppButtonProps = PressableProps & {
  label: string;
  loading?: boolean;
  className?: string;
  textClassName?: string;
};

export function AppButton({
  label,
  loading = false,
  disabled = false,
  className,
  textClassName,
  ...props
}: AppButtonProps) {
  const isDisabled = disabled || loading;

  return (
    <Pressable
      className={`h-12 items-center justify-center rounded-xl bg-emerald-400 ${isDisabled ? 'opacity-60' : ''} ${className ?? ''}`}
      disabled={isDisabled}
      {...props}
    >
      {loading ? (
        <ActivityIndicator color="#022c22" />
      ) : (
        <Text className={`text-base font-semibold text-emerald-950 ${textClassName ?? ''}`}>
          {label}
        </Text>
      )}
    </Pressable>
  );
}
