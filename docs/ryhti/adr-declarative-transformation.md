# Datapohjainen kuvaus Lupapiste->Ryhti datamuunnokseen

- **Kirjoittaja**: Juho Teperi
- **Päivä**: 2026-01-30
- **Osallistujat**:
- **Tila**:

## Context

- KuntaGML kanssa vastaava toteutus `canonocal-data`
    - Välimuoto ennen XML sanoman rakentamista, Clj datana vastaa XML rakenteita
    - Rakennetaan Lupapiste application pohjalta "normaaleilla" funktioilla
    - Päätasolla sisään tulee `application` ja ulos KuntaGML XML sanomaa vastaava
      clj tietorakenne
    - Ongelmia? Syvemmällä funktiokutsupuuta ei ole pääsyä enään
      laajempaan osaan `application`, jolloin muutamissa paikoissa tulee
      ongelmia että tarvittavaa tietoa ei ole enää saatavilla funktiossa.
      Refaktorointi vaikeaa koska tarvisi lisätä useisiin funktioihin uusia
      parametrejä että saa vietyä tiedon "syvälle". Ei siistiä.
- Ryhti API kuvaa rajapinnan jolla tulevaisuudessa Kunnat tulevat
  välittämänä tiedon Ryhtiin.
- Tulevaisuudessa Lupapiste tulee juttelemaan uuden Asiointi API kautta
  kunnille, esim. välittämään Lupapisteessa luodun rakennuslupa-asian
  kunnan järjestelmään.

## Oletukset

- Oletettavasti Asiointi API:n tietomallit tullaan tekemään
  hyvin tai täysin samannäköisiksi kuin Ryhti API:n tietomallit.
    - Mahdolliset poikkeukset olettavasti liittyisivät esim. tapauksiin
      että Ryhti API välitetään vain lupa-asia johon on tehty päätös,
      kun taas Asiointi API:ssa käsitellään myös avoimia asioita?

## Vaihtoehdot

- Tehdään vaan ihan perus funktioita jotka ottavat sisään ylätasolla Lupapiste application
  ja palauttaa lopulta BuildingPermitIssue.
    - Voidaan silti ottaa canonical-data rajoitteet huomioon, esim.
      syvemmällä kutsuttavilla funktiolla pääsy koko application.

## Väittämä/todistus/perustelu

- Voisiko olla kuvaus joka kuvaa suhteet kaikkien Lupapiste ja Ryhti kenttien
  välillä, niin että samalla kuvauksella voidaan tehdä muunnos molempiin suuntiin?
- "Data pohjaisella" kuvauksella selkeämpi rakenne (koodille)? Ohjaa listaamaan kentät tietyllä tavalla.
    - Vastaavasti myös "rajoittavampi", voi olla erikoistilanteita joita ei voi esittää?
    - Muuttuuko monimutkaiseksi kun tarvitsee lisätä enemmän sääntöjä?
      (Varmasti tarvii laajentaa myöhemmin odottamattomilla ominaisuuksilla)

## Ratkaisuehdotus

- Kuvataan per Ryhti objekti/skeema lista sääntöjä miten kenttien arvot
  muodostetaan
- Kuvauksessa Ryhti ja Lupapiste polku kentät arvoon
- Ryhti polku on nyt muodostettavan objektin kentän nimi
    - Useimmiten polku on vain yhden avaimen mittainen
- "Konteksti"
    - Lupapiste polku viittaa nykyiseen kontekstiin
    - Konteksti on arvo (map) jossa on koko application ja muuta tarivittavaa
      tietoa
    - Kun siirrytään syvemmälle rakenteessa, voidaan kontekstiin lisätä
      uusia kenttiä, esim. kun kutsutaan Suunnittelija mäppästä,
      kontekstiin lisätään käsiteltävä Lupapiste dokumentti.
- Lisäksi kuvauksessa on funktio jolla tarvittaessa Lupapiste
  arvo muutetaan Ryhti sopivaksi
    - Timestamp merkkijonoksi
    - Koodistoarvot
    - Voi olla uudelleenkäytettäviä funktioita

## Vaikutukset

- Kuvauksessa kentät voidaan kirjoittaa samaan järjestykseen kuin Ryhti tietomallit.pdf
- Kuvauksen selite kenttään voidaan merkitä tietomallit dokumentit suomenkielinen kenttä (-> helpottaa hakua) ja
  muita tietoja.
    - Toki voisi olla ihan koodikommentissakin

## Avoimet huomiot, kysymykset

- Itse "apply-mapping" toteutus on erittäin yksinkertainen
    - Isoin työ käydä läpi rajapintoja ja tietomallidokumenttia ja päätellä
      mitkä kentät vastaavat toisiaan. Mikäli toteutsta tarvitsisi muuttaa,
      tämä työ ei luultavasti ole turhaa ja koodi voidaan muokata toiseen toteutukseen.
- Toiseen suuntaan muutos on haastava. Jos kuvaukset kirjoitetaan esitetyllä tavalla,
  kuvaukset ovat edelleen riippuvaisia siitä mistä kohtaa "apply-mapping" on
  kutsuttu ja tämä ei oikein toimi samalla tavalla toiseen suuntaan.
    - Tässä Ryhti polut ovat suhteessa "nykyiseen sijaintiin"
        - Kahdensuuntaisessa ratkaisussa molempien polkujen pitäisi olla riippumattomia
          "nykyisesti sijainnista"
- Kun nyt `:->ryhti` funktiota käytetään kaikkien ali-mapping kutsumiseen,
  voisi myös tästä osasta tehdä enemmän deklaratiivisen, jotta
  kuvauksista syntyy oikeasti puu dataa.
    - Esim, `:children` parametri joka kertoo käytettävän `:mapping`
      ja miten sitä käytetään.
    - Vektori -> vektori, yksi arvo -> yksi arvo, yksi arvo -> vektori jossa yksi lapsi.
    - `:child` parametri yksi -> yksi tapaukselle? yksi -> yhden item vektori voisi olla target pathin kautta, `[:foo 0]`?
    - "Nykyisen value" lisääminen ali-mapping contextille.
    - Mahdollista että tarvii tukea "useampaa nykyistä value", katso: building-site-mapping.
