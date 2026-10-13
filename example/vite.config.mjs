import { existsSync } from 'node:fs';
import path from 'node:path';
import { defineConfig, mergeConfig } from 'vite';

import config from 'react-native-builder-bob/vite-config';
import pack from '../package.json' with { type: 'json' };

const IMAGE_EXT = /\.(png|jpe?g|gif|webp)$/;

/**
 * Metro resolves `require('./img.jpg')` to `img@3x.jpg` / `img@2x.jpg` when
 * only scaled variants exist; Vite doesn't, so do the same here.
 */
function scaledImages() {
  return {
    name: 'scaled-images',
    resolveId(source, importer) {
      if (!importer || !source.startsWith('.') || !IMAGE_EXT.test(source)) {
        return null;
      }
      const file = path.resolve(path.dirname(importer), source);
      if (existsSync(file)) return null;
      const ext = path.extname(file);
      const base = file.slice(0, -ext.length);
      for (const scale of ['@3x', '@2x', '@1x']) {
        if (existsSync(base + scale + ext)) return base + scale + ext;
      }
      return null;
    },
  };
}

export default defineConfig((env) =>
  mergeConfig(config(env), {
    plugins: [scaledImages()],
    resolve: {
      alias: {
        [pack.name]: new URL('..', import.meta.url),
      },
      conditions: ['react-native-backdrop-blur-source'],
      dedupe: Object.keys(pack.peerDependencies),
    },
  })
);
