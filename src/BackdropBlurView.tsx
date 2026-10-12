import { View, type ViewProps } from 'react-native';

export type BackdropBlurViewProps = ViewProps & {
  blurRadius?: number;
};

// Web: the browser already has backdrop-filter, so just pass it through.
export function BackdropBlurView({
  blurRadius = 10,
  style,
  ...rest
}: BackdropBlurViewProps) {
  const filter = `blur(${blurRadius}px)`;
  return (
    <View
      {...rest}
      style={[
        style,
        // react-native-web forwards these to the DOM; RN's style types don't
        // know about them.
        { backdropFilter: filter, WebkitBackdropFilter: filter } as object,
      ]}
    />
  );
}
