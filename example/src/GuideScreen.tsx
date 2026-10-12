import { Image, Pressable, StyleSheet, Text, View } from 'react-native';
import { BackdropBlurView } from 'react-native-backdrop-blur';

const GREEN = '#3E5B3C';

type Props = {
  onBack: () => void;
  onOpenCamera: () => void;
};

export default function GuideScreen({ onBack, onOpenCamera }: Props) {
  return (
    <View style={styles.container}>
      <Image
        source={require('./assets/guide-bg.jpg')}
        style={StyleSheet.absoluteFill}
        resizeMode="cover"
      />

      <View style={styles.center}>
        {/* Same as the design's CSS:
            border-radius: 25.6px;
            border: 0.64px solid rgba(255,255,255,.4);
            background: rgba(255,255,255,.35);
            box-shadow: 0 5.12px 20.48px 0 rgba(0,0,0,.1);
            backdrop-filter: blur(3.84px);
            The shadow sits on an outer view, since the inner one has to clip
            the blur to the rounded corners. Tint and border go on a layer
            above the blur rather than on the card, so the blur doesn't pick
            them up as backdrop on Android. */}
        <View style={styles.cardShadow}>
          <View style={styles.card}>
            <BackdropBlurView
              style={StyleSheet.absoluteFill}
              blurRadius={3.84}
            />
            <View style={[StyleSheet.absoluteFill, styles.cardTint]} />

            <View style={styles.iconCircle}>
              <View style={styles.arrowStem} />
              <View style={styles.arrowHead} />
              <View style={styles.arrowBase} />
            </View>
            <Text style={styles.title}>Offline Pack</Text>
            <View style={styles.progressTrack}>
              <View style={styles.progressFill} />
            </View>
            <View style={styles.divider} />
            <View style={styles.statusRow}>
              <View style={styles.checkCircle}>
                <View style={styles.checkMark} />
              </View>
              <Text style={styles.status}>Ready Offline</Text>
            </View>
          </View>
        </View>
      </View>

      <Pressable
        onPress={onBack}
        style={[styles.topButton, styles.back]}
        hitSlop={8}
      >
        <Text style={styles.topButtonText}>‹ 返回</Text>
      </Pressable>

      <Pressable
        onPress={onOpenCamera}
        style={[styles.topButton, styles.camera]}
        hitSlop={8}
      >
        <Text style={styles.topButtonText}>相机 ›</Text>
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
  },
  cardShadow: {
    borderRadius: 25.6,
    boxShadow: '0px 5.12px 20.48px 0px rgba(0, 0, 0, 0.10)',
  },
  card: {
    width: 200,
    paddingTop: 20,
    paddingBottom: 14,
    paddingHorizontal: 20,
    alignItems: 'center',
    borderRadius: 25.6,
    overflow: 'hidden',
  },
  cardTint: {
    borderRadius: 25.6,
    borderWidth: 0.64,
    borderColor: 'rgba(255, 255, 255, 0.40)',
    backgroundColor: 'rgba(255, 255, 255, 0.35)',
  },
  iconCircle: {
    width: 52,
    height: 52,
    borderRadius: 26,
    backgroundColor: GREEN,
    alignItems: 'center',
    justifyContent: 'center',
  },
  arrowStem: {
    position: 'absolute',
    top: 14,
    width: 2.5,
    height: 15,
    borderRadius: 1.25,
    backgroundColor: '#FFF',
  },
  arrowHead: {
    position: 'absolute',
    top: 21,
    width: 10,
    height: 10,
    borderRightWidth: 2.5,
    borderBottomWidth: 2.5,
    borderColor: '#FFF',
    transform: [{ rotate: '45deg' }],
  },
  arrowBase: {
    position: 'absolute',
    bottom: 13,
    width: 18,
    height: 2.5,
    borderRadius: 1.25,
    backgroundColor: '#FFF',
  },
  title: {
    marginTop: 12,
    color: '#1C241B',
    fontSize: 15,
    fontWeight: '700',
  },
  progressTrack: {
    marginTop: 12,
    alignSelf: 'stretch',
    height: 7,
    borderRadius: 3.5,
    backgroundColor: 'rgba(255, 255, 255, 0.55)',
    overflow: 'hidden',
  },
  progressFill: {
    width: '72%',
    height: '100%',
    borderRadius: 3.5,
    backgroundColor: GREEN,
  },
  divider: {
    marginTop: 14,
    alignSelf: 'stretch',
    height: StyleSheet.hairlineWidth,
    backgroundColor: 'rgba(255, 255, 255, 0.5)',
  },
  statusRow: {
    marginTop: 12,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
  },
  checkCircle: {
    width: 18,
    height: 18,
    borderRadius: 9,
    backgroundColor: GREEN,
    alignItems: 'center',
    justifyContent: 'center',
  },
  checkMark: {
    width: 5,
    height: 9,
    marginTop: -2,
    borderRightWidth: 2,
    borderBottomWidth: 2,
    borderColor: '#FFF',
    transform: [{ rotate: '45deg' }],
  },
  status: {
    color: '#1C241B',
    fontSize: 13,
    fontWeight: '500',
  },
  topButton: {
    position: 'absolute',
    top: 56,
    height: 36,
    paddingHorizontal: 14,
    justifyContent: 'center',
    borderRadius: 18,
    backgroundColor: 'rgba(0, 0, 0, 0.35)',
  },
  back: {
    left: 16,
  },
  camera: {
    right: 16,
  },
  topButtonText: {
    color: '#FFF',
    fontSize: 15,
    fontWeight: '500',
  },
});
