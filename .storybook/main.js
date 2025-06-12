import fs from 'fs';

// Our defmeta & defstory macros store metadata to Cljs compiler state
// Shadow-cljs build-hook writes that to per namespace .stories.json file,
// which already contains the stories array as-is.
const jsonStoriesIndexer = {
  test: /stories\.json$/,
  createIndex: async (fileName) => {
    return JSON.parse(fs.readFileSync(fileName));
  },
};

export default {
  framework: {
    name: '@storybook/react-vite',
    options: {},
  },
  staticDirs: ['../resources/public'],
  docs: {
    autodocs: true
  },
  stories: [
    // '../target/ui-doc/js/**/*stories.js',
    '../target/ui-doc/js/**/*stories.json'
  ],
  core: {
    disableTelemetry: true
  },
  addons: [
    '@storybook/addon-docs',
    '@storybook/addon-a11y',
    '@storybook/addon-mcp'
  ],
  experimental_indexers: async (existingIndexers) => [jsonStoriesIndexer, ...existingIndexers],
};
