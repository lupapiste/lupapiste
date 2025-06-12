# Malli schema generointi Ryhti OpenAPI perusteella

- **Kirjoittaja**: Juho Teperi
- **Päivä**: 2026-01-30
- **Osallistujat**:
- **Tila**:

## Context

- Ryhti API tarjoaa OpenAPI kuvaukset
- Ryhti tarjoaa tietojen validointia varten myös erillisen rajapinnan,
  joka tekee myös tarkastuksia joita ei ole kuvattu OpenAPI/JSONSchema tasolla.
- Lupapiste tulee käyttämään Ryhti tietomalleja pitkälti myös Asiointi API kanssa

## Oletukset

- Lupapiste tulee myös käyttämään Ryhti validaatio rajapintaa
- Koska Lupapiste tarjoaa Asiointi API ulkopuolelle, todennäköisesti
  haluamme tarjota sille itsekin OpenAPI kuvauksen. Tämä onnistuu
  esimerkiksi Reitit+Malli käyttämällä.

## Vaihtoehdot

- Tehdään OpenAPI kuvauksen perusteella Lupapisteen sisäiset skeemat jotain
  Clojure kirjastoa käyttäen (esim. Malli), käsin tai automaattisesti. Näihin
  voidaan tarvittaessa tehdä muutoksia.
- Käytetään jotain kirjastoa (clj/jvm?) joka validoi dataa suoraan
  JSONSchema vasten?
- Ryhti OpenAPI/JSONSchema osien käyttäminen "sinällään" Asiointi API kuvaukseen?

## Väittämä/todistus/perustelu

- JVM kirjastojen käyttä Clj datan validointiin ei ole kovin kätevää
- Ryhti JSONSchema käyttö suoraan ei mahdollistaisi kovin helposti tehdä muutoksia
  tietomalleihin, koska Asiointi API tuskin tulee olemaan 100% sama.

## Ratkaisuehdotus

- REPL namespace jossa on funktio jolla voidaan generoida Clojure
  namespace jossa Malli schemat kaikista OpenAPI käyttämistä tietomalleista.
- Generoitu namespace tallennetaan git repoon
- Skeemojen välisiin viittauksiin käytetään Ref-skeemaa
- Generoitu namespace julkaisee skeema rekisterin, jossa on kaikki
  tarjotut skeemat

## Vaikutukset

- Kun generoitua namespace päivitetään, git diff perusteella huomataan
  myös (tietomalli) muutokset Ryhti rajapinnassa
- Koska skeemojen väliset viittaukset on toteutettu ref-skeemoina,
  voidaan laajentaa generoidun skeema-rekisterin päälle uusi rekisteri,
  jossa on muutoksia esim. vain yhteen skeemaan, ja kaikki tähän viittaukset
  menevät rekisterin kautta muutettuuun versioon.
    - Olettaen että Asiointi API toteutetaan samoilla tietomalleilla, tämä
      mahdolliset muutokset generoidun päälle on todennäköisesti hyödyllinen
      ominaisuus.
