import { useCallback, useEffect, useState } from 'react';
import { BackHandler, Pressable, StyleSheet, Text, View } from 'react-native';
import { BackdropBlurView } from 'react-native-backdrop-blur';
import GuideScreen from './GuideScreen';
import {
  Camera,
  useCameraDevice,
  useCameraPermission,
} from 'react-native-vision-camera';

const RADII = [0, 5, 10, 20, 40];

type Screen = 'camera' | 'guide';

export default function App() {
  // A minimal back stack: the last entry is the screen on display.
  const [stack, setStack] = useState<Screen[]>(['camera']);
  const screen = stack[stack.length - 1];
  const canGoBack = stack.length > 1;

  const push = (next: Screen) => setStack((s) => [...s, next]);
  const goBack = useCallback(
    () => setStack((s) => (s.length > 1 ? s.slice(0, -1) : s)),
    []
  );

  useEffect(() => {
    if (!canGoBack) return;
    const sub = BackHandler.addEventListener('hardwareBackPress', () => {
      goBack();
      return true;
    });
    return () => sub.remove();
  }, [canGoBack, goBack]);

  if (screen === 'guide') {
    return <GuideScreen onBack={goBack} onOpenCamera={() => push('camera')} />;
  }
  return (
    <CameraScreen
      onBack={canGoBack ? goBack : undefined}
      onOpenGuide={() => push('guide')}
    />
  );
}

type CameraScreenProps = {
  /** Undefined when there is nothing to go back to. */
  onBack?: () => void;
  onOpenGuide: () => void;
};

function CameraScreen({ onBack, onOpenGuide }: CameraScreenProps) {
  const { hasPermission, requestPermission } = useCameraPermission();
  const device = useCameraDevice('back');
  const [blurRadius, setBlurRadius] = useState(10);

  useEffect(() => {
    if (!hasPermission) requestPermission();
  }, [hasPermission, requestPermission]);

  if (!hasPermission || device == null) {
    return (
      <View style={[styles.container, styles.center]}>
        <Text style={styles.text}>
          {hasPermission ? 'No camera found' : 'Waiting for camera permission'}
        </Text>
      </View>
    );
  }

  return (
    <View style={styles.container}>
      <Camera style={StyleSheet.absoluteFill} device={device} isActive />

      <View style={styles.center} pointerEvents="none">
        {/* blur + tint + border, like CSS:
            background: rgba(115,115,115,.4);
            border: 1px solid rgba(255,255,255,.3);
            backdrop-filter: blur(<blurRadius>px); */}
        <View style={styles.pill}>
          <BackdropBlurView
            style={StyleSheet.absoluteFill}
            blurRadius={blurRadius}
          />
          <View style={[StyleSheet.absoluteFill, styles.tint]} />
          <Text style={styles.text}>blur({blurRadius}px)</Text>
        </View>

        <View style={styles.card}>
          <BackdropBlurView
            style={StyleSheet.absoluteFill}
            blurRadius={blurRadius}
          />
          <View style={[StyleSheet.absoluteFill, styles.tint]} />
        </View>
      </View>

      <View style={styles.buttons}>
        {RADII.map((r) => (
          <Pressable
            key={r}
            onPress={() => setBlurRadius(r)}
            style={[styles.button, r === blurRadius && styles.buttonActive]}
          >
            <Text style={styles.text}>{r}</Text>
          </Pressable>
        ))}
      </View>

      {onBack && (
        <Pressable onPress={onBack} style={[styles.topButton, styles.back]}>
          <Text style={styles.text}>‹ 返回</Text>
        </Pressable>
      )}

      <Pressable onPress={onOpenGuide} style={[styles.topButton, styles.guide]}>
        <Text style={styles.text}>图片背景 ›</Text>
      </Pressable>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#000',
  },
  center: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    gap: 24,
  },
  pill: {
    height: 40,
    paddingHorizontal: 16,
    justifyContent: 'center',
    borderRadius: 16,
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.30)',
    overflow: 'hidden',
  },
  card: {
    width: 240,
    height: 160,
    borderRadius: 24,
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.30)',
    overflow: 'hidden',
  },
  tint: {
    backgroundColor: 'rgba(115,115,115,0.40)',
  },
  text: {
    color: '#FFF',
    fontSize: 16,
    fontWeight: '500',
  },
  buttons: {
    position: 'absolute',
    bottom: 48,
    left: 0,
    right: 0,
    flexDirection: 'row',
    justifyContent: 'center',
    gap: 12,
  },
  button: {
    minWidth: 44,
    height: 36,
    paddingHorizontal: 12,
    alignItems: 'center',
    justifyContent: 'center',
    borderRadius: 18,
    backgroundColor: 'rgba(0,0,0,0.5)',
  },
  buttonActive: {
    backgroundColor: 'rgba(255,255,255,0.35)',
  },
  topButton: {
    position: 'absolute',
    top: 56,
    height: 36,
    paddingHorizontal: 14,
    justifyContent: 'center',
    borderRadius: 18,
    backgroundColor: 'rgba(0,0,0,0.5)',
  },
  back: {
    left: 16,
  },
  guide: {
    right: 16,
  },
});
