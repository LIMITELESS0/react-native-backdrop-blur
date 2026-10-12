import {
  codegenNativeComponent,
  type CodegenTypes,
  type ViewProps,
} from 'react-native';

export interface NativeProps extends ViewProps {
  /**
   * Blur radius in dp / pt, the same number you would put in CSS
   * `backdrop-filter: blur(<n>px)`. Defaults to 10.
   */
  blurRadius?: CodegenTypes.WithDefault<CodegenTypes.Float, 10>;
}

export default codegenNativeComponent<NativeProps>('BackdropBlurView');
