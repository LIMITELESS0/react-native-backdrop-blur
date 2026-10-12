# react-native-backdrop-blur

CSS `backdrop-filter: blur()` for React Native — a live blur of whatever is
behind a view, built for overlays on top of a **camera or video preview**.

- **iOS**: the system material (`UIVisualEffectView`), with its strength
  scaled to `blurRadius`.
- **Android**: copies the `SurfaceView` behind the view with `PixelCopy` and
  runs a Gaussian-approximating blur on it (~20 fps). Works over camera
  previews (VisionCamera, CameraX `PreviewView`) and video players that render
  into a `SurfaceView` — the cases where view-snapshot blur libraries only show
  grey.
- **Web**: plain CSS `backdrop-filter`.

## Requirements

- React Native **0.76+** with the **New Architecture** (Fabric).
- Android minSdk 24.

## Installation

```sh
npm install react-native-backdrop-blur
cd ios && pod install
```

Then rebuild the native app.

## Usage

Layer it like CSS: blur at the bottom, tint above it, content on top.

```tsx
import { StyleSheet, Text, View } from 'react-native';
import { BackdropBlurView } from 'react-native-backdrop-blur';

// background: rgba(115,115,115,.4);
// border: 1px solid rgba(255,255,255,.3);
// border-radius: 16px;
// backdrop-filter: blur(10px);
<View
  style={{
    borderRadius: 16,
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.3)',
    overflow: 'hidden',
  }}
>
  <BackdropBlurView style={StyleSheet.absoluteFill} blurRadius={10} />
  <View
    style={[
      StyleSheet.absoluteFill,
      { backgroundColor: 'rgba(115,115,115,0.4)' },
    ]}
  />
  <Text style={{ color: '#fff' }}>Already scanned</Text>
</View>;
```

Put the tint on its own layer, not on the container's `backgroundColor`;
otherwise it is drawn underneath the blur and covered by it.

### Props

| Prop         | Type     | Default | Description                                         |
| ------------ | -------- | ------- | --------------------------------------------------- |
| `blurRadius` | `number` | `10`    | Blur radius in dp / pt, like `blur(<n>px)` in CSS.  |

Plus all `View` props.

## Limitations

- **Android only blurs `SurfaceView` content.** Over ordinary React Native
  views it draws nothing and only your tint layer shows. Over a
  `TextureView` (e.g. a video player configured to use one) it also draws
  nothing.
- **iOS and Android don't look identical.** Apple doesn't expose the material's
  blur radius, so iOS maps `blurRadius` against an estimated 30pt full
  intensity, and the material adds a slight tint / saturation boost. Android
  is a plain blur at the exact radius. Compare on both platforms and tune
  `blurRadius` per platform with `Platform.select` if needed.
- On Android the blur refreshes at ~20 fps, so it can trail fast camera motion
  by up to ~50 ms.

## Example

The `example/` app shows the blur over a live camera preview:

```sh
yarn
yarn example android
yarn example ios
```

## Contributing

- [Development workflow](CONTRIBUTING.md#development-workflow)
- [Sending a pull request](CONTRIBUTING.md#sending-a-pull-request)
- [Code of conduct](CODE_OF_CONDUCT.md)

## License

MIT

---

Made with [create-react-native-library](https://github.com/callstack/react-native-builder-bob)
