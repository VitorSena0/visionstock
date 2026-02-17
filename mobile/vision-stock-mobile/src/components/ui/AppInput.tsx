import type { TextInputProps } from 'react-native';
import { TextInput } from 'react-native';
import { twMerge } from 'tailwind-merge';

type AppInputProps = TextInputProps & {
  className?: string;
};

export function AppInput({ className, ...props }: AppInputProps) {
  return (
    <TextInput
      className={twMerge(
        'h-12 rounded-xl border border-slate-700 bg-slate-900 px-4 text-slate-100',
        className,
      )}
      placeholderTextColor="#94a3b8"
      {...props}
    />
  );
}
