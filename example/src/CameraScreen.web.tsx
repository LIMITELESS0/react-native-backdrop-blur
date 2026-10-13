import { Pressable, StyleSheet, Text, View } from 'react-native';
import type { CameraScreenProps } from './CameraScreen.types';

// react-native-vision-camera is native-only, so the web build gets a
// placeholder with the same navigation instead of the live preview.
export default function CameraScreen({
  onBack,
  onOpenGuide,
}: CameraScreenProps) {
  return (
    <View style={styles.container}>
      <Text style={styles.text}>
        The camera demo runs on iOS / Android only
      </Text>

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
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: '#000',
  },
  text: {
    color: '#FFF',
    fontSize: 16,
    fontWeight: '500',
  },
  topButton: {
    position: 'absolute',
    top: 56,
    height: 36,
    paddingHorizontal: 14,
    justifyContent: 'center',
    borderRadius: 18,
    backgroundColor: 'rgba(255,255,255,0.15)',
  },
  back: {
    left: 16,
  },
  guide: {
    right: 16,
  },
});
