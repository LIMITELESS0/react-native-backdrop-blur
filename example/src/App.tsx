import { useCallback, useEffect, useState } from 'react';
import { BackHandler } from 'react-native';
import CameraScreen from './CameraScreen';
import GuideScreen from './GuideScreen';

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
