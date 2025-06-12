import React from 'react';

// HMR will handle css updates when imported here
// NOTE: Legacy css currently not loaded here
// Could be a good idea, to also ensure the legacy styles don't break new components.
import '../resources/public/lp-static/css/tailwind.css';

export const decorators = [
  (Story) => {
    document.body.classList.add('twp', 'nobase', 'lp-theme');
    return <Story />
  },
];

export const parameters = {
  backgrounds: {
    options: {
      // 'page': {
      //   name: 'Page background',
      //   value: 'rgb(237, 230, 245)',
      // },
    },
  },
};

export const initialGlobals = {
  // backgrounds: { value: 'section-modal' },
};

export const globalTypes = {
  locale: {
    name: 'Locale',
    description: 'Locale for components',
    defaultValue: 'fi',
    toolbar: {
      icon: 'globe',
      items: [
        {value: 'en', title: 'English'},
        {value: 'fi', title: 'Finnish'},
        {value: 'sv', title: 'Swedish'},
      ],
    },
  },
};

