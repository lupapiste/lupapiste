# UI2

TODO: Keksitäänkö parempi nimi.

## Design system teema

Figma design: "Cloudpermit_Design_System_2026".

Figma variable perusteella on generoitu CSS file, josta löytyy CSS variablet
jotka vastaavat Figma tokeneita: `resources/private/common-html/lp-theme.css`.

Tämän päivitys onnistuu AI:lla, käyttäen `.eca/skills/update-lupapiste-theme-css/SKILL.md`
ohjetta.

### Setup

- Figma Desktop TAI [figma-linux](github.com/IliyaBrook/figma-linux) (epävirallinen)
- [figma-console-mcp](https://github.com/southleft/figma-console-mcp)
  1. Tarvitset personal access tokenin (mahdollisesti tarvii maksetun dev seat?)
  2. Lisää esim. `.eca/config.json` alle uusi mcpServer
  3. Desktop Bridge Plugin - step 3 npx ohjeiden alla (tärkeä)
  4. (Juhon testi) en käyttänyt uusinta figma-console-mcp, vaan versiota 1.20.0, joko johtuen stop 3 virheestä tai
     ehkä Linux desktop versiossa ei ole uusinta mcp tukea?

## Cljs komponentit

`src-cljs/lupapalvelu/ui2/` hakemiston alla.

Ideana on määrittää komponenttien variantit [CVA](https://cva.style/docs)
inpiroidulla tyylillä Cljs datana. Tätä dataa varten on funktio, joka ottaa
komponentit propsit ja kasaa niiden perusteella className kokoelman.

Erona CVA on, että Cljs versiossa voisi samalla määritellä jokaiselle variantille
eri elementtien tyylejä. Näin jos jokin komponentti koostuu useammasta elementistä
joita tarvii tyylitellä, ei ole tarpeen tehdä useampaa kuvausta,
vaan molempien elementtien tyylit tulee dataan "viereikkäin".

### Arkkitehtuurihuomioita

- Kaikki komponentit ottavat React komponentti tyyliin parametrit yhdessä `props`
  parametrissä. Muina parametreinä on sallittuna vain lapset, eli `children`.
  - TODO: Sallitaan että `props` ei ole pakollinen.
- Komponentit voivat käyttää `(into [:el ...] children)` patternia laittamaan
  lapset pääelementin sisälle.

### Uuden komponentin lisäys
