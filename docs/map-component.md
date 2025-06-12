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

# Local development in Lupapiste

Clone [map-component](https://github.com/cloudpermit/map-component) somewhere and see its README for setting it
up, e.g.:
```
npm install
./vaahtera.sh
```

Overwrite the node_modules contents in Lupapiste project for testing the local version of map-component code:
```
cp -r ../map-component/dist/* node_modules/@cloudpermit/map-component/dist
```

Why this and not `npm link`? Link (symlink) also includes full `node_modules` from
map-component folder, which includes its own copies of React etc, which wont work.
