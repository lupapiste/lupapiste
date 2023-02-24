# OpenLayers Map React component

Karttakomponentin on kehittänyt Ubigu Oy.

Koodit ovat omassa repossaan [map-component](https://github.com/cloudpermit/map-component).

"Tavallinen" käyttö ei vaadi repon kloonausta, katso alta otsikko `Usage`.

Komponenttia käytetään sekä Lupapisteessä että Vaahterassa.

Käytetään NPM:n kautta (`npm install`), paketti julkaistaan [Githubiin](https://github.com/orgs/cloudpermit/packages?repo_name=map-component) CI:n toimesta.

# NPM private registry

Jotta `npm install` osaa hakea GitHub registrystä paketin, pitää `~/.npmrc` tiedostoon lisätä access token.

Luo GitHub tililläsi personal access token ja aseta se [GH:n ohjeen](https://docs.github.com/en/packages/working-with-a-github-packages-registry/working-with-the-npm-registry#authenticating-with-a-personal-access-token) mukaan em. tiedostoon.

# Usage

`npm install` first and foremost.

Currently the JavaScript file is not automatically picked up as resource, so there is small annoyance you need to do (most likely only once, unless you delete the link/file or want to develop locally):

1. Symlink the map from node_modules to Lupapiste static files. There is a helper script for this, run `npm run symlink-map`.
2. ...or copy file the file directly from node_modules: `cp node_modules/@cloudpermit/map-component/dist/lupapiste_map.js resources/public/lp-static/js/map.js`

# Local development in Lupapiste with `npm link`

Clone [map-component](https://github.com/cloudpermit/map-component) somewhere and see its README for setting it
up, e.g.:
```
npm install
./lupapiste.sh
```

Ensure Lupapiste node_modules are up to date with `npm install`

Use npm link to for linking the package through local filesystem in map-component and Lupapiste dirs:
```
map-component> npm link
lupapiste> npm link '@cloudpermit/map-component'
```

**Note: running npm install will always remove the link from Lupapiste and you have to link again**
