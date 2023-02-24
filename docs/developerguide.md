# Kehitystyö

## Ympäristön pystytys

Tarvitset kehitysympäristöön minimissään seuraavat työkalut:

- [JDK >= 11](https://adoptopenjdk.net/)
- [Leiningen](https://github.com/technomancy/leiningen) 2.5+
- [nodejs (npm to be precise)](https://nodejs.org/en/) 14.0+
- Docker (eg. [Docker Desktop for Mac](https://docs.docker.com/docker-for-mac/install/))
   * MongoDB on kontitettu. `cd docker && docker-compose up -d`

Lisäksi seuraavat tarvittaessa (esim Robot Framework testien ajoon):
- [MongoDB](https://www.mongodb.org/downloads) 5.0.x (jos et käytä Dockeria)
    - Mac: `brew install mongodb` (`brew tap homebrew/services` + `brew services start mongodb`)
    - Tarkista, että mongon kantakansiolla ja lokikansiolla on asetettu permissionit
    - Macillä saattaa tulla avoimien tiedostojen raja vastaan: [How to persist ulimit settings in osx](http://unix.stackexchange.com/questions/108174/how-to-persist-ulimit-settings-in-osx-mavericks)
- [React Dev tools](https://www.google.com/search?q=react+developer+tools) selaimeen
- Python 3.x ja [Robot Framework](http://robotframework.org/) selaintestausta varten (yleensä ei tarpeen)
  - `pip3 install robotframework`
  - `pip3 install robotframework-seleniumlibrary`
  - `pip3 install robotframework-debuglibrary`
  - Chromella ajettavia testejä varten [ChromeDriver](https://sites.google.com/a/chromium.org/chromedriver/) -ajuri (löytyy myös Homebrewstä).
  - Firefoxilla ajettavia testejä varten [geckodriver](https://github.com/mozilla/geckodriver) -ajuri (löytyy myös Homebrewstä).
  - IE:llä ajettavia testejä varten ajuri osoitteesta  http://selenium-release.storage.googleapis.com/index.html
- [KnockoutJS context debugger](https://chrome.google.com/webstore/detail/knockoutjs-context-debugg/oddcpmchholgcjgjdnfjmildmlielhof) Chrome plugin, joka helpottaa DOM-elementtien binding kontekstien hahmottamista

## Kehitysympäristön konfigurointi

Kun olet asentanut minimissään Javan, leiningenin, noden ja Dockerin, saat kehitysympäristön käyntiin kun luet alla
olevat seuraavat kappaleet: docker-compose, npm, user.properties, master password, sessionkey sekä SSL-avain.

### docker-compose

docker hakemistossa on [docker-compose.yml](../docker/docker-compose.yml), jolla saat MongoDB:n tulille:
```shell
cd docker
# -d on 'detach' : docker siirtyy taustalle ajoon
docker-compose up -d
```

### npm

`npm install`, jonka jälkeen `lein front` käynnistää ClojureScript ja SASS watcherit. Olet valmiina devaaman fronttia!
Seuraavaksi backendin konfigurointia:

### user.properties

**Jos työskentelet Cloudpermit-yhtiössä**, käytä CRED-8-tiketillä olevaa user.properties tiedostoa!

Luo projektin juureen (sovelluksen kehitysaikaiseen ajohakemistoon)
user.properties -tiedosto. Tiedostossa voit määritellä
mm. tietokantayhteyden:

    mongodb.servers.0.host  localhost
    mongodb.servers.0.port  27017
    mongodb.dbname          lupapiste
    mongodb.credentials.username     lupapiste-kannan-user
    mongodb.credentials.password     lupapiste-kannan-salasana

Määrittele viestijonojen nimille jokin oma tunniste, esim. käyttäjänimesi
```
integration-message-queue-user-prefix your-username
```

### Ympäristömuuttujat ja privaatti Maven-repositorio

Esimerkiksi `lupapiste-pubsub`-kirjasto ladataan privaatista Githubin Maven-reposta. Tätä varten tarvitaan tunnukset,
jotka Leiningen lukee ympäristömuuttujista. Generoi GitHub-käyttäjällesi token tai käytä olemassa olevaa ja aseta
esim. zshrc:ssä tai muulla haluamallasi tavalla seuraavat ympäristömuuttujat:

```
export CLOUDPERMIT_GITHUB_USERNAME=[github käyttäjätunnus]
export CLOUDPERMIT_GITHUB_TOKEN=[generoitu token]
```

### Googlen palvelut

Lupapiste käyttää Google Cloud Storagea tiedostojen tallennukseen ja Pub/Sub -palvelua viestijonona. Näiden sijaan
on mahdollista käyttää myös MongoDB:n GridFS-storagea ja ActiveMQ Artemis JMS-jonoa. Tuotantoa vastaavassa konfiguraatiossa
on kuitenkin syytä käyttää Googlen palveluja. Käytännössä siis user.properties-tiedostossa konfiguroidaan nämä paikalleen:

```
# Google Cloud Storage
# requires GOOGLE_APPLICATION_CREDENTIALS=/path/to/creds.json in environment OR gcs.service-account-file property
# when set to false, will use local MongoDB GridFS as file storage
feature.gcs              true
gcs.service-account-file /path/to/creds.json

# Google Pub/Sub (uses same project and credentials as Cloud Storage)
feature.pubsub true
# For local emulator connection
#gcp.pubsub.endpoint  127.0.0.1:8085
```

Ohjeita service accountin luomiseen löytyy
[Confluencesta](https://cloudpermit.atlassian.net/wiki/spaces/LUPAP/pages/3193241601/GCP+credentials+and+configuration).


### Master password

Sovellus vaatii käynnistyäkseen master-salasanan asettamisen. Salasanan voi
asettaa
- tallentamalla se kotihakemistosi application_master_password.txt tiedostoon
- `APPLICATION_MASTER_PASSWORD` ympäristömuuttujassa tai
-  `-Dapplication.masterpassword=xxxx` käynnistysparametrilla
  (kun sovellus käynnistetään java -jar komennolla).

Salasana löytyy Lupapiste projektissa työskenteleville CRED-7 tiketiltä.

Salasanaa käytetään asetuksissa olevien salaisuuksien avaamiseen (ja kryptaamiseen).
Properties-tiedostoissa voi käyttää Jasyptilla kryptattuja arvoja, ks. ohje
test/lupapalvelu/nested.properties tiedostossa.


### Sessionkey

Session kryptaamiseen sovellus lukee projektin juuresta `sessionkey` nimisen tiedoston.

Tätä ei ole pakko tehdä, mutta jos sessionkeyta ei ole, sessiot invalidoituvat jokaisen restartin välillä.

Generoi avain `sessionkey` tiedosto vaikkapa seuraavalla shell komennolla: `LC_ALL=C < /dev/urandom tr -cd '[:alnum:]' | head -c20 > sessionkey`

Myös Lupapisteen "spinoff" projektit kuten lupadoku voivat käyttää samaa sessionkeytä, jolloin sama kirjautuminen
toimii sovellusten välillä.

### SSL-avain

Kehitysmoodissa Lupapiste-sovelluksen sisäänrakennettu sovelluspalvelin kuuntelee
HTTPS-liikennettä portissa 8443. Generoi tätä varten projektin juureen
SSL/TLS-avain keystore -nimiseen tiedostoon. Keystoren salasanan oletetaan
olevan "lupapiste". Portin, keystore-tiedoston nimen  ja salasanan voi
ylikirjoittaa user.properties tiedostossa.

Vaihtoehtoina on joko 1) luoda SSL avain 2) asettaa SSL:n pois päältä, alla kerrotaan molemmista.

1. Avaimen voi generoida esimerkiksi JDK:n mukana tulevalla keytool työkalulla seuraavasti:

    $ keytool -keystore keystore -alias jetty -genkey -keyalg RSA -sigalg SHA256withRSA

    Enter keystore password:  lupapiste
    Re-enter new password: lupapiste
    What is your first and last name?
      [Unknown]:  localhost
    What is the name of your organizational unit?
      [Unknown]:  kehitys
    What is the name of your organization?
      [Unknown]:  lupapiste
    What is the name of your City or Locality?
      [Unknown]:  Tampere
    What is the name of your State or Province?
      [Unknown]:  Finland
    What is the two-letter country code for this unit?
      [Unknown]:  fi
    Is CN=localhost, OU=kehitys, O=lupapiste, L=Tampere, ST=Finland, C=fi correct?
      [no]:  yes
    Enter key password for <jetty>
            (RETURN if same as keystore password):

2. Vaihtoehtoisesti voit lisätä user.properties tiedostoon rivin

    ssl.enabled    false

Tällöin sovelluspalvelin kuuntelee ainoastaan HTTP-liikennettä.

Jos olet asettanut jo Lupapiste JIRAssa olevan user.properties tiedoston ja master passwordin paikalleen,
kehitysympäristön konfigurointi on nyt **valmis**.

Katso miten saat softan käyntiin kohtasta [palvelun käynnistäminen](./developerguide.md#palvelun-käynnistys).

**HUOM! Eli alla olevaa kartta yms asioita ei tarvitse konfiugroida, jos JIRAssa oleva user.properties on käytössä**

### Kartat ja paikkatietoaineisto

Karttojen ja paikkatietoaineiston käyttö vaatii käyttäjätunnukset Maanmittauslaitokselta.

Lupapiste dev tiimillä nämä ovat valmiina CRED-8 tiketillä.

Kartan saa testaustapauksissa toimimaan käyttämällä Maanmittauslaitoksen avointa [palvelualustaa](http://www.maanmittauslaitos.fi/aineistot-palvelut/rajapintapalvelut/paikkatiedon-palvelualustan-pilotti).
Testikäyttö onnistuu asettamalla user.properties tiedostoon seuraava rivi:

    maps.open-nls-wmts   http://avoindata.maanmittauslaitos.fi/mapcache/wmts

Tällöin selainpään karttakutsut lähetetään ko. karttapalvelimelle.

[Maanmittauslaitoksen avoimen datan lisenssi (CC 4.0)](http://www.maanmittauslaitos.fi/avoimen-tietoaineiston-cc-40-lisenssi)

Palvelun käyttämiä kiinteistö- ja osoitetietorajapintoja ei ole saatavina avoimina palveluina, lisätietoa rajapinnoista [Maanmittauslaitoksen](http://www.maanmittauslaitos.fi/aineistot-palvelut/rajapintapalvelut) sivuilta.

## MongoDB-kannan ja tunnusten luominen (ei tarpeen jos Docker)

Käynnistä `mongo`-shell

    mongo

Luo halutessasi admin-tunnukset. Siirry ensin admin-kantaan:

    > use admin

Luo uusi käyttäjätunnus:

    > db.createUser(
          {
              user: "tähän-haluamasi-admin-tunnus",
              pwd: "tähän-adminin-salasana",
              roles: ["userAdminAnyDatabase"]
          });

Siirry nyt `lupapiste`-kantaan:

    > use lupapiste

Luo tunnukset lupapisteelle (käytä samaa kannan nimeä, käyttäjän nimeä ja käyttäjän salasanaa kuin `user.properties`-tiedostossa):

    > db.createUser(
        {
            user: "lupapiste-user",
            pwd: "lupapiste-password",
            roles: [ { role: "readWrite", db: "lupapiste" } ]
        });


## Palvelun käynnistys

Kun konfiguraatiot ovat paikallaan, sekä MongoDB käynnistetty
Lupapisteen voi käynnistää kahdella eri tavalla:

1. `(go)`: Käynnistä REPL. REPL on oletuksena [user](/dev-src/user.clj) namespacessa, josta löytyy funktio `go`. Kutsu siis `(go)` ja softa käynnistyy.
2. Komentoriviltä Lupapisteen saa kutsumalla `lein run`.

Sovellus on ajossa osoitteessa http://localhost:8000.

Navigoi [Rekisteröidy palveluun](http://localhost:8000/app/fi/welcome#!/register) -sivulle, jossa sivun oikeaan laitaan avautuu Development-palkki. Valitse Development-palkista "Apply minimal", joka alustaa tietokantaan muutamia käyttäjiä ja organisaatioita (kts. [minimal.clj](/src/lupapalvelu/fixture/minimal.clj)). Voit kirjautua
sisään esimerkiksi hakijatunnuksella **pena/pena** tai viranomaistunnuksella **sonja/sonja**.

Kirjautumisen jälkeen voit luoda hakemuksia ja neuvontapyyntöjä Tee hakemus- ja
Kysy neuvoa -painikkeilla. Voit myös käyttää Development-palkin linkkejä luodaksesi
hakemuksia yhdellä klikkauksella.

Palvelun käyttäjäohje löytyy osoitteessa https://www.lupapiste.fi/ohjeet/.

## Frontin devaus

Ihan kärkeen aja `npm install`.

_Nopeasti_ liikkeelle: `lein front`, joka käynnistää SASS ja ClojureScript kääntäjät+watcherit.

Frontti jakautuu kahtia:

1. "legacy" plain Javascript puoleen joka koostuu KnockoutJS kirjastoa hyödytävistä
HTML- ja JavaScript-tiedostoista.
2. Tuoreempi ClojureScript puoli: shadow-cljs työkalu kääntelee ja live reloadaa ClojureScriptit selaimeen.

## Lähdekoodin hakemistorakenne

Koodi on jaoteltu seuraavasti:

| Hakemisto                               | Selitys                                                                           |
|-----------------------------------------|-----------------------------------------------------------------------------------|
| [src](/src)                             | Palvelinpään sovelluskoodi                                                        |
| [src/lupapalvelu](/src/lupapalvelu)     | Erityisesti Lupapisteeseen liittyvä palvelinpään sovelluskoodi                    |
| [src/sade](/src/sade)                   | Palvelinpään sovelluskoodi, jota on hyödynnetty muissa SADe-hankkeen projekteissa |
| [src-cljc](/src-cljc)                   | CLJC tiedostot, jotka jaetaan Clojuressa sekä ClojureScriptissä                   |
| [src-cljs](/src-cljs)                   | ClojureScript tiedostot (Rum)                                                     |
| [resources](/resources)                 | Staattiset resurssit                                                              |
| [resources/public](/resources/public)   | Resurssit, jotka palvellaan automaattisesti palvelimen juuripolussa               |
| [resources/private](/resources/private) | Selainpään sovelluskoodi                                                          |
| [dev-resources](/dev-resources)         | Kehitys- ja testausaikaiset aputiedostot                                          |
| [dev-src](/dev-src)                     | Kehitysaikainen apulähdekoodi                                                     |
| [test](/test)                           | Palvelinpään yksikkötestit                                                        |
| [itest](/itest)                         | Palvelinpään integraatiotestit                                                    |
| [stest](/stest)                         | Palvelinpään systeemitestit                                                       |
| [test-utils](/test-utils)               | Palvelinpään testien jaettu koodi                                                 |
| [robot](/robot)                         | Selainpään end-to-end-testi                                                       |

Palvelinpään päätiedosto, josta ohjelmiston suoritus käynnistyy, on
[src/lupapalvelu/main.clj](/src/lupapalvelu/main.clj).

## Tyylikäytännöt

Lähdekoodissa käytetään aina unix-rivinvaihtoja (`\n`).

Sisennys on kaksi välilyöntiä. Merkkijonojen ympärillä käytetään lainausmerkkejä
myös JavaScript-koodissa.

JavaScript-koodi tulee tarkastaa JSHint-työkalulla, jonka asetukset ovat projektin juuressa.

Clojure-koodissa käytetään seuraavia aliaksia nimiavaruuksille:

| namespace                                                       | alias    |
|-----------------------------------------------------------------|----------|
| [lupapalvelu.action](/src/lupapalvelu/action.clj)               | `action` |
| [lupapalvelu.application](/src/lupapalvelu/application.clj)     | `app`    |
| [lupapalvelu.attachment](/src/lupapalvelu/attachment.clj)       | `att`    |
| [lupapalvelu.authorization](/src/lupapalvelu/authorization.clj) | `auth`   |
| [lupapalvelu.company](/src/lupapalvelu/company.clj)             | `com`    |
| [lupapalvelu.domain](/src/lupapalvelu/domain.clj)               | `domain` |
| [lupapalvelu.operations](/src/lupapalvelu/operations.clj)       | `op`     |
| [lupapalvelu.organization](/src/lupapalvelu/organization.clj)   | `org`    |
| [lupapalvelu.user](/src/lupapalvelu/user.clj)                   | `usr`    |
| [sade.env](/src/sade/env.clj)                                   | `env`    |
| [sade.strings](/src/sade/strings.clj)                           | `ss`     |
| [sade.util](/src/sade/util.clj)                                 | `util`   |

Nimiavaruuksiin viitataan aina :require tyylillä (:use avainsanaa ei käytetä).
Koko nimiavaruuden sisällyttämistä (`:require [lupapalvelu.namespace :refer :all]`)
tulee välttää. Poikkeustapauksia ovat mm. `sade.core` ja `monger.operators`.

## Versionhallinta

Kehitys tehdään develop-haaraan git flow -mallin mukaisesti. Tuotannossa on master-haara.

## Testaus

### Backend

 - `lein nitpicker` tekee lähdekooditiedostoille laittomien merkkien tarkastuksen
 - `lein midje` ajaa (pelkät) yksikkötestit
 - `lein integration` ajaa integraatiotestit. Integraatiotestit olettavat,
    että palvelin on käynnissä oletusportissa 8000 ja siitä on yhteys MML:n rajapintoihin.
 - `lein stest` ajaa systeemitestit, jotka käyttävät myös muita ulkoisia integraatioita.
 - `lein verify` ajaa kaikki edellä mainitut.

### Frontend end-to-end testit

Katso [browser-testing.md](browser-testing.md).

 - `local.sh` / `local.bat` ajaa Robot Frameworkilla paikalliset testit.
   Näissä oletetaan, että  palvelin on käynnissä oletusportissa.
 - `local-integration.sh` / `local-integration.bat`
   ajaa Robot Frameworkilla testit, jotka käyttävät ulkoisia palveluita kuten
   VETUMA-kirjautumispalvelun testijärjestelmää.

Muista käyttää kahta välilyöntiä .robot-tiedostoissa erottamaan avainsanaa ja parametreja!

# Yleiset virhetilanteet / poikkeukset

## CompilerException java.lang.UnsupportedClassVersionError

Esim tälläinen:

```
CompilerException java.lang.UnsupportedClassVersionError: lupapalvelu/tiedonohjaus/CaseFile has been compiled by a more recent version of the Java Runtime (class file version 55.0), this version of the Java Runtime only recognizes class file versions up to 52.0, compiling:(lupapalvelu/tiedonohjaus.clj:1:1)
```

Tarkoittaa että tiedostot on käännetty eri Java versiolla. Saattaa tulla ongelmaksi jos
olet asentanut kaksi eri java versiota, joista oletuksena toinen on käytössä, mutta projekti
käyttää vanhempaa. Riippuen setupistasi eri työkalut (esim terminaali vs IDE) saattavat käyttää huomaamatta
eri Java versioita jolloin kyseinen tilanne pääsee tapahtumaan.

# Korkean tason domain-kuvaus

Ks. [tietomalli](information-architecture.md)

# Arkkitehtuuri yleiskuvaus

Asiointisovellus on toteutettu HTML5 Single-page application-pohjaisesti.
Käyttöliittymäkerros kutsuu taustapalvelua, joka edelleen lukee ja muokkaa
tietokannan tietoja. Järjestelmä tietosisältö muodostuu hakemuksista, niiden
lomaketiedoista ja liitetiedostoista sekä käyttäjistä. Rakenteisen tiedon osalta
pääroolissa ovat hakemuksen lomaketiedot, joten sovelluksen käyttöön on valittu
dokumenttitietokanta, johon monimuotoiset lomakkeet on helppo mallintaa.

Sovellus on toteutettu Command Query Responsibility Segregation periaatteiden
mukaisesti. Commandeja käytetään komentojen suorittamiseen (tiedon muokkaamiseen) ja
Queryjä käytetään tiedon kyselemiseen. Frontendistä kutsutaan backendin tarjoamia
JSON rajapintoja (*/api/command/<nimi>* (POST metodi) ja */api/query/<nimi>*
(GET metodi)).

## Keskeisimmät teknologiat

Frontend:
- [KnockoutJS](http://knockoutjs.com/documentation/introduction.html)
- [jQuery](http://api.jquery.com/)
- [Lodash](http://lodash.com/)
- [ClojureScript](https://clojurescript.org/)
- [Rum](https://github.com/tonsky/rum)

Backend:
- [Clojure](http://clojure.org/)
- [MongoDB](http://docs.mongodb.org/)

# Frontend arkkitehtuuri

## React

Luit oikein. Vaikkakin ohjelmiston peruskivi on valettu KnockoutJS:lle on mahdollisuus ujuttaa mukaan React komponentteja
kahdella eri tavalla:

1. ClojureScript komponentin upotus käyttämällä <cljs-*> Knockout custom elementtiä. Esimerkki:

       <cljs-pate_verdict-templates params="orgId: organization.organizationId()"></cljs-pate_verdict-templates>
   joka kutsuu `lupapalvelu.ui.pate.verdict-templates/start` funktiota.
   Katso [cljs-component](../resources/private/cljs-component), jos olet kiinnostunut toteutusyksityiskohdista.

2. `react:` Knockout [binding handlerilla](https://knockoutjs.com/documentation/custom-bindings.html). Esimerkki:

       <div data-bind="react: {component: SunKomponentti, props: {mun: "propsi"}}"><div>

    Toteutus on kyseiselle elementille `ReactDOM.render` siten, että viitteenä annettu komponentti luodaan
    kutsumalla `React.createElement`:iä komponentilla ja annetuilla propsuilla.
    Katso toteutus [ko.init.js](../resources/private/common/ko.init.js) tiedoston lopussa.

## ClojureScript-vinkkejä

Mystisissä ongelmissa (esim. toimii kehitysympäristössä muttei QA:lla)
juurisyynä voi olla puutteelliset externit (ks. `lupapiste-externs.js`).

Tämä johtuu ClojureScriptin mukana tulevasta Closure-kirjaston `:advanced` -tason käännöksestä, jonka minimoi koodia
mukaan lukien funktiokutsut.

### Päivitys 2021 - shadow-cljs

Vaihdettu käyttöön shadow-cljs, joka yhdessä ClojureScriptiin tulleen
:infer-externs option kanssa osaa luoda externit automaattisesti.
Saattaa olla että jotain pitää silti manuaalisesti externata, kts [externs](../externs/) kansio.

## Single Page kompositio

[web.clj](../src/lupapalvelu/web.clj)-tiedostossa määritellään rajapinnat,
jotka tarjoilevat kullekin [käyttäroolille](information-architecture.md#käyttäjä)
omat HTML-sovellussivut ja niihin liittyvät yhteenpaketoidut JavaScript- ja
CSS-resurssit. lupapalvelu.web nimiavaruudessa määritellään kullekin resurssille
pääsyrajaus, eli mikä käyttäjärooli vaaditaan.

Resurssien kompositio määritellään [ui_components.clj](../src/lupapalvelu/components/ui_components.clj)
-tiedostossa. Kutakin käyttäjien perusroolia vastaa oma komponentti,
jonka `:depends` vektoriin määritellään komponentit, joista tämä paketoitava resurssi koostuu.

Jokaista komponenttia vastaa alihakemisto [resources/private:ssa](../resources/private).
`:js`, `:css` ja `:html` avaimilla määritellään lista tiedostonimiä, joiden
tulee löytyä komponentin hakemistosta. Hakemiston nimen voi myös ylikirjoittaa
`:name` avaimen avulla.

HTML-tiedostoista poimitaan pelkät nav ja footer elementit sekä section class=page,
div class=notification ja script class=ko-template -elementit
(ks. [singlepage/parse-html-resource](../src/lupapalvelu/singlepage.clj)).

`ui-components`-niminen komponentti ja alihakemisto käsitellään erityisesti:
hakemiston kaikki tiedostot tulevat automaattisesti mukaan tähän komponenttiin.
_Huom:_ jotta uusi tiedosto tulee mukaan kehitysympäristössä,
lupapalvelu.components.ui-components nimiavaruus on ladattava uudelleen REPL:issä.
(Vaihtoehtoinen, raskaampi tapa saada muutokset voimaan on käynnistää palvelu uudelleen.)

Termi "ui-komponentti" voi viitata joko `ui_components.clj`:n määrityksiin,
`resource/private/ui-components` alla oleviin automaattisesti ladattaviin
komponentteihin tai joissain yhteyksissä KnockoutJS-komponentteihin.

## Näkymien reititys

Näkymä valitaan sen perusteella, mikä ankkuri eli niin sanottu hash-bang sovelluksen
osoitteessa on. Sovellus asettaa näkyville elementin, jonka ID:tä tämä vastaa.
Esim. http://localhost:8000/app/fi/authority#!/application/LP-753-2016-00001
osoiteessa näytetään nimeämiskäytännön mukaisesti
[application.html](../resources/private/application/application.html)
-tiedostossa oleva elementti:

    <section class="page" id="application">

Samassa application-hakemistossa oleva application.js sisältää logiikan,
joka suoritetaan, kun application-näkymä avataan:

    hub.onPageLoad("application", _.partial(initPage, "application"));

Näkymän avautuessa siis kutsutaan initPage-funktiota "application" -parametrilla sekä
hub.js:n välittämällä eventillä.

Vastaavasti koodissa voi kuunnella siirtymistä pois näkymästä
`hub.onPageUnload("sivun id", function(event){})` -koukun avulla.

## Kommunikointi selaimesta palvelinpäähän

Kaikki verkkopyynnöt tulee tehdä [ajax](../resources/private/init/ajax.js)-palvelun kautta.
Tämä keskittää virhekäsittelyä ja Cross Site Request Forgery -estomekanismin.

## Uusien näkymien toteutusarkkitehtuuri

Lupapiste on (kirjoitushetkellä) siirtymässä käyttämään [Flux-suunnittelumallin](https://facebook.github.io/flux/docs/overview.html) inspiroimaa frontend-arkkitehtuuria, jossa käyttöliittymä mallinnetaan knockout.js komponenteilla, viestinkuljetuksesta vastaa oma hub.js -komponentti ja sovelluksen tila sekä taustajärjestelmäkommunikaatio on service-komponenteissa.

![](fe-arkkitehtuuri.png)

### hub.js

Hub.js on yksinkertainen, itse toteutettu pub/sub -komponentti, joka tarjoaa mahdollisuuden kirjautua kuuntelemaan sekä lähettää signaaleja (eventtejä). Hub.js:n rooli arkkitehtuurissa on tehdä näkymien ja service-kerroksen välisestä kommunikoinnista löyhästi kytkettyä.

### Servicet

Serviceitä on useita, joista kukin käsittää jonkun tietomallin käsitteen tai toiminnallisen kokonaisuuden tilan ja tapahtumien hallinnoinnin (esim. FileUploadService, ApplicationBulletinsService). Servicen tehtävät ovat:

- Ylläpitää palvelimelta noudettua, tiettyyn käsitteeseen liittyvää tilaa selainpäässä
  - Tila on yleensä tallennettuna joukkoon [Knockout observable-objekteja](http://knockoutjs.com/documentation/observables.html)
- Kirjautua (subscribe) kuuntelemaan näkymäkomponenteilta tulevia signaaleja käyttäjän tekemistä toiminnoista
  - Signaalien käsittelyyn liittyy usein (muttei välttämättä aina) jonkun observable-objektin arvon muuttamista ja/tai AJAX-kutsuja palvelinpäähän
- Synkronoida muuttunut tila palvelinpäähän parhaaksi näkemällään logiikalla
  - Joissain tilanteissa on järkevää synkronoida tilaa viivästetysti palvelimelle/tietokantaan, joskus taas service välittää uuden tilan heti tilamuutoksen yhteydessä. Tämä on joka tapauksessa servicen sisäistä toteutusta, joista käyttöliittymäkomponenttien ei tarvitse tietää mitään

Tyypillinen service näyttää jotakuinkin tältä:

```javascript
LUPAPISTE.VetumaService = function() {
...
  // State
  self.authenticated = ko.observable(false);
  self.userInfo = ko.mapping.fromJS({
    firstName: undefined,
    lastName: undefined
  });

  // Subscriptions
  hub.subscribe("vetumaService::authenticateUser", function(params) {
    // Request authentication info from server
    // ...

    // Set state to new value
    self.authenticated(true);
    self.userInfo.firstName("John");
  });

  hub.subscribe("vetumaService::logoutRequested", function() {
    // Handle logout event
    ajax.command("logout-user", {userInfo: ko.mapping.toJS(self.userInfo)})
      .success(function() { self.authenticated(false); });
  });
};

```

### Näkymä (view)

Näkymä on toteutettu Knockout-komponenttina. Ymmärrämme Lupapisteessä knockout-komponentin käyttöliittymän osana, joka puhtaimmillaan tekee joko yhden asian tai koostaa muista komponenteista isomman kokonaisuuden. Komponentti koostuu ulkoasua kuvaavasta HTML-templaatista (*template*) ja JS-logiikasta (*model*).

Näkymäkomponentit saavat tyypillisesti parametreinaan valikoidun osan niiden serviceiden tila-observableista, joilla on relevanssia komponentin tai sen lapsikomponenttien esittämän tiedon esittämisessä. Yksinkertaistettu esimerkki:

application-bulletins-model.js
```javascript
LUPAPISTE.ApplicationBulletinsModel = function(params) {
  // ...
  ko.utils.extend(self, new LUPAPISTE.ComponentBaseModel(params));
  self.bulletins = params.bulletinService.bulletins;
  // ...
};
```

application-bulletins-template.html
```html
  <!-- ... -->
  <div data-bind="component: {name: 'application-bulletins-list',
                              params: {bulletins: bulletins}}"></div>
  <!-- ... -->
```

application-bulletins-list-model.js
```javascript
LUPAPISTE.ApplicationBulletinsListModel = function(params) {
  // ...
  ko.utils.extend(self, new LUPAPISTE.ComponentBaseModel(params));
  self.bulletins = params.bulletins;
  // ...
};
```

application-bulletins-list-template.html
```html
  <!-- ... -->
  <tbody data-bind="foreach: bulletins">
    <!-- ... -->
    <td data-bind="text: $data.bulletinState"></td>
    <!-- ... -->
  </tbody>
  <!-- ... -->
```

Yo. esimerkissä hierarkiassa korkeammalla oleva komponentti on siis saanut koko service-objektin parametrinaan, mutta välittää siitä ainoastaan yhden osan (bulletins) lapsikomponentille. Lapsikomponentti ei taas tiedä mitään servicestä tai muusta sen ylläpitämästä tilasta vaan piirtää itsensä ruudulle pelkästään käyttäen saamiaan tietoja. Toinen, samanlainen esimerkki piirroksena:

![](komponenttiarkkitehtuuri-esimerkki.png)

Komponentti toimii siis seuraavasti:
- Saa parametreina ne osat applikaation tilasta, jota tarvitaan komponentin esittämiseen oikein ruudulla
  - Koska välitetty tila on yleensä joukko service-kerroksen omistamia observable-objekteja, myöhemmät tilan muutokset servicessä propagoituvat komponenttihierarkian läpi komponentille.
- Välittää lapsikomponenteille ne osat saamastaan tilasta, jotka lapsikomponentti tarvitsee.
- Tarjoaa käyttöliittymässä yleensä joukon joitakin toimintoja käyttäjille, joiden käyttäminen vaikuttaa applikaation tilaan
- Signaloi hub.js:n avulla millaisia toimintoja käyttäjä on aktivoinut
  - Käyttäjän toiminnot signaloidaan aina eteenpäin, annettua tilaa ei koskaan muuteta suoraan (ks. alla)

### Konventiot

Arkkitehtuuriin liittyvät konventiot:

* **Lähettäessään eventtiä komponentti ei laita eventin parametreihin callback-funktiota datan palautukselle**
  * Callback-funktio rikkoo ajatuksen yksisuuntaisesta tiedon kulusta. Komponenttien lähettämien eventtien tulisi olla "fire-and-forget" -tyylisiä, eli komponentti vain kertoo käyttäjän triggeröimästä tapahtumasta eikä edes välitä käsitteleekö eventtiä kukaan
  * Käyttäjän triggeröimä tapahtuma voi olla kuitenkin merkityksellinen komponentin tilan kannalta. Tämä tilan muuttuminen toteutuu kun service käsittelee eventin ja muuttaa omaa sisäistä tilaansa tapahtuman mukaisesti, jonka johdosta tilamuutos välittyy myös komponenttihierarkian läpi eventin lähettäneelle komponentille
* **Komponentti ei koskaan muuta suoraan saamansa tilan (tyypillisesti kokoelma observable-objekteja) tilaa vaan ainoastaan signaloi käyttäjän tekemisistä hub.js:n kautta**
  * Myös tilan suora muuttaminen komponentissa rikkoo yksisuuntaisen arkkitehtuurin. Tilan muuttumiseen liittyy lähes aina jotain muutakin logiikkaa kuin uuden arvon asettamiset observable-objektiin. Tämän johdosta service-kerroksen tulisi aina vastata tilan muutoksesta ja mahdollisista sivuvaikutuksista.
* **Komponentin lähettämä signaali kuvaa käyttäjän pyytämää toimintoa, eikä esimerkiksi sitä miten komponentin mielestä tilaa tulisi muuttaa**
  * Komponentilla ei pitäisi sinänsä koskaan olla tietoa siitä, miten applikaation tila on järjestetty ja mitä käsitteitä tallennettuun tilaan liittyy. Komponentti tietää ainoastaan toiminnoista, joita se itse tarjoaa käyttäjälle itse määrittelemässään käyttöliittymässä.
  * Esimerkiksi, käyttäjän vaihtaessa hakemuslistan rivien järjestystä, oikea signaali service-kerrokselle olisi `"applicationListSortChanged"` ja parametreiksi uusi, pyydetty järjestys (esim. `{sortBy: "date", direction: "asc"}`). Esimerkki vääränlaisesta signaalista olisi `"fetchApplicationsOrderedBy"`, koska tämä ei kuvaa käyttäjän toimintaa vaan haluttua lopputulosta.
* **Komponentin tulee siivota tekemänsä subscriptionit hubiin sekä ns. long-living observable-objekteihin viittaavat computed-objektit**
  * Mikäli laajennetaan komponenttien ns. base-luokkaa `"ComponentBaseModel"` voidaan käyttää hub-viittauksiin sekä computedien luomiseen valmiita funktioita, jotka huolehtivat näiden siivoamisen komponentin elikaaren päässä. Long-living observable voi olla esimerkiksi globaali applikaatio-model, jonka jotain arvoa seurataan komponentin rungossa.
  ```javascript
  self.addEventListener("fileuploadService", "fileRemoved", function(event) {...});

  self.disposedComputed(function() {...});
  ```
## Ilmoitus toimintojen onnistumisesta tai epäonnistumisesta

Toiminnon lopuksi voi näyttää käyttäjälle vihreässä tai punaisessa palkissa
välähtävän viestin:

```javascript
hub.send("indicator", {style: "positive"});

hub.send("indicator", {style: "negative"});
```

Ajax-pyyntöjen callback-funktioina voi käyttää util.showSavedIndicator:ia:

```javascript
ajax.command("some-command")
  .success(util.showSavedIndicator)
  .error(util.showSavedIndicator)
  .call();
```

Tai oman callback-funktion sisällä:
```javascript
ajax.command("some-command")
  .success(function(resp){
    util.showSavedIndicator(resp);
    doOtherStuff();
  })
  .call();
```

Lomakkeen kentiin tapahtuvien automaattitallennusten jälkeen voidaan näyttää käyttäjälle huomaamattomampi ilmoitus ruudun alaosassa
```javascript
hub.send("indicator-icon", {style: "positive"});

hub.send("indicator-icon", {style: "negative"});
```


## Globaalit objektit
- Localizations
- Lupapiste Map
- lupapisteApp
  - models
  - services
- LUPAPISTE
  - config
- Docgen, viittaus skeemojen määrittelyyn

## Leiningen + SASS

Tyylimäärittelyt kirjoitetaan Sass-tiedostoihin [resources/private/common-html/sass](/resources/private/common-html/sass)-hakemistossa, ja ne generoidaan CSS:ksi hakemistoon [/resources/public/lp-static/css/](resources/public/lp-static/css/).

`lein front` käynnistää Sass-käännöksen ja jää vahtimaan muutoksia


## Oskari Map
  The hub between Lupapiste and Oskari Map

# Backend arkkitehtuuri
## Yleiskuvaus

TODO

## Action pipeline
Routes `/api/command/:name`, `/api/query/:name`, `/api/datatables/:name`, `/data-api/json/:name` and `/api/raw/:name` are defined in [web.clj](/src/lupapalvelu/web.clj).

Commands, queries (incl. datatables requests), exports (`/data-api`) and raw actions are then executed by the framework in [action.clj](/src/lupapalvelu/action.clj).

Use `defcommand`, `defquery`, `defraw` and `defexport` macros to define new actions. Action definitions are in the following form:
```clojure
(def<action-type> action-name
  { ; metadata map
  }

  [command] ; action argument map, supports destruction
  ; action body
)
```

Actions must return a map. Commands, queries and exports return a map that contain `:ok` key with a boolean success value. Use `sade.core/ok`, `fail` and `fail!` to generate these responses.

A function named `<action-type>-<action-name>` is generated from action body. That function can be called in REPL or integration tests. For example:
```clojure
    (lupapalvelu.comment-api/command-add-comment {:data {}, :application {}, :user {}})
```

**Note:** coding convention: define actions only in `_api.clj` namespaces. Remember to require the api-namespace in `server.clj` or `web.clj`, so the action is registered and the route is available on server startup.

Supported action metadata-map keys (see ActionMetadata definition in `action.clj`):

| Key                   | Description                                                                                                                                                                                                                                 |
|-----------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `user-roles`          | Required. Set of user role keywords. Action pipeline checks that the action is allowed for the current user before executing the action.                                                                                                    |
| `parameters`          | Vector of parameters. Parameters can be keywords or symbols. Symbols will be available in the action body. If a parameter is missing from request, an error will be raised.                                                                 |
| `optional-parameters` | Vector of parameters. Same as 'parameters', but error is not raised if missing from request.                                                                                                                                                |
| `user-authz-roles`    | Set of application context role keywords. Action pipeline checks that the current application is accessible by the current user before executing the action.                                                                                |
| `org-authz-roles`     | Set of application organization context keywords. User is authorized to use action only if user has valid role in application's organization.                                                                                               |
| `description`         | Documentation string.                                                                                                                                                                                                                       |
| `notified`            | Boolean. Documents that the action will be sending (email) notifications.                                                                                                                                                                   |
| `pre-checks`          | Vector of functions. Functions are called with 1 parameter: action argument map (command, includes the application if applicable). Function must return nil if everything is OK, or an error map. Use sade.core/fail to generate the error. |
| `input-validators`    | Vector of functions. Functions are called with 1 parameter, the action argument map (command). Function must return nil if everything is OK, or an error map. Use sade.core/fail to generate the error.                                     |
| `states`              | Vector of application state keywords. Action pipeline checks that the current application is in correct state before executing the action.                                                                                                  |
| `on-complete`         | Function or vector of functions. Functions are called with 2 parameters: action argument map (command) and the action execution return value map.                                                                                           |
| `on-success`          | Function or vector of functions. Functions are called only if the action return map contains :ok true. See :on-complete.                                                                                                                    |
| `on-fail`             | Function or vector of functions. Functions are called only if the action return map contains :ok false. See :on-complete.                                                                                                                   |
| `feature`             | Keyword: feature flag name. Action is run only if the feature flag is true. If you have feature.some-feature properties file, use :feature :some-feature in action meta data.                                                               |

Example query:
```clojure
(defquery comments
  {:parameters [id]
   :user-roles #{:applicant :authority :oirAuthority}
   :user-authz-roles action/all-authz-writer-roles
   :org-authz-roles action/commenter-org-authz-roles
   :states states/all-states}
  [{application :application}]
  (ok (select-keys application [:id :comments])))
```
Returns JSON to frontend:
```clojure
{:ok true :data [{:id "123" :comments []}]}
```

## Session
Backend delegates session handling to Ring. Semantics:

- If action return value map (see above) contains :session key, value is copied to response
- If response map contains :session key, session will be replaced to contain this new value. If value is nil, session will be deleted.
- If response map does not contain the :session key, session is left as is.

Components that wish to change the session can use sade.session namespace to merge changes to session.

Actions get the session as a part of the parameter map, under **:session** key.

Normally session contains the following keys:

- id (string): unique session id
- user (map): Current user contains keys
-- id (string): mongo-id reference to users collection
-- username (string)
-- firstName (string)
-- lastName (string)
-- role (string): "applicant", "authority", "oirAuthority", "authorityAdmin", "admin" or "trusted-etl"
-- email (string)
-- orgAuthz (map), keys are mongo-id referenced to organizations collection as Keywords, values are a set of Keywords: :authority, :tos-editor, :tos-publisher, :authorityAdmin
-- company (map):
--- id (string): mongo-id reference to companies collection
--- role (string): "user" or "admin"
-- architect (boolean)
- redirect-after-login (string): URL where browser should be redirected after login
- expires (long): timestamp, when the session expires (4h inactivity timeout)

Session cookien encryption key is read from sessionkey file (in working directory). If the file is missing, a random key will be used.

## Notifications
When a user needs to be informed about an action that has been taken place, a good practice is to send an email notification.

### Email template
An email template is used to define the body of the message. A new Template needs be created in ```resources/email-templates```-folder with a proper file name that adheres the following syntax: ```lang-template-name.md``` where lang is replaced with a two letter language code (e.g. ```fi-notify-authority-added.md```). A new file has to be created and the content translated for all supported languages. There are scripts in place to scan for missing translations so if you don't feel comfortable with some of the languages their templates can be left out (i.e. don't create files for them) and a professional translator will do the job for you.

Templates are created using Markdown markup language and they can contain variables that are filled in by the backend. These variables are writen inside double curly brackets like ```{{this}}```.

### Title and other translations
Define the title of the email (or other localizations related to emails) in the file ```resources/i18n/email.txt``` using the following syntax: ```"variable-name" "lang" "text"``` where lang is replaced with the two letter language code (e.g. ```"email.title.inforequest-invite" "en" "Inforequest at Lupapiste"```). Translate the title to all the supported languages you feel comfortable with.

### Define the email in the backend
Define the email useing defemail -function in the notifications -namespace. Email must conform the Email -spec that is also defined in the ```notifications``` -namespace.

### Define the command that sends the email
Define the command that sends the email after a successful execution using ```defcommand``` -macro. Provide the values for the variables used in the email template under ```:parameters```-keyword. Set ```:notified```to ```true``` and under ```on-success``` -keyword provide a function(s) that are called after a successful execution. To send an email declare a function that takes the command as its first (and only) parameter and calls ```notifications/notify!``` with previously defined email as its first, and the command as its second parameter.

## Integrations
TODO
* WFS (KTJ, maastotietokanta, naapurit)
* WMTS/WMS
* KRYSP (miten keskustellaan taustajärjestelmien kanssa)
* Asianhallinta

## Tietokanta

Ks. [database.md](database.md)

## Schemat

    {
    (with-doc ""
      :avain) (schema/Str)
    }

## Koodauskäytännöt
- Käytetään omia apu-namespaceja (ss, mongo jne)
- Actionit määritellään "-api" päätteisiin nimiavaruuksiin (tästä on käännösaikainen assertio actionin rekisteröinnissä)


# Laajennuspisteet

## Uuden hakemustyypin lisääminen

1. Luo uusi hakemustyyppi `lupapalvelu.permit/defpermit` -makrolla.
2. Määritä hakemustyypissä käytettävät liitteet (ks. `lupapalvelu.attachment.types/attachment-types-by-permit-type`). Varsinainen liitteiden määritys tehdään [lupapiste-commons](https://github.com/lupapiste/commons) projektiin (*attachment_types.cljc*).
3. Lisää hakemustyypille tarvittavat toimenpiteet ja luo hakemustyypin toimenpidepuu (operation tree) `lupapalvelu.operations` -nimiavaruuteen.
4. Jos hakemustyyppiin tulee KRYSP integraatio (Lupapisteestä ulospäin)
  1. Tee mapping halutusta XML formaatista. Mapping-funktio tulee luoda XML tiedosto ja kirjoittaa se levylle. Toteuta hakemustyypille multimetodi `lupapalvelu.permit/application-krysp-mapper`, joka kutsuu mapping-funktiota. Esimerkkejä: `lupapalvelu.backing-system.krysp.*-mapping`.
  2. Toteuta funktiot muunnokseen hakemus->kanoninenXML (esimerkkiä `lupapalvelu.document.vesihuolto_canonical` -nimiavaruudesta). Kanonisesta mallista luodaan mappingin perusteella XML esitys.
  3. Katso mallia KRYSP putkesta, joka alkaa `lupapalvelu.integrations_api` -nimiavaruuden **approve-application** commandista. Tarkempi kuvaus TODO.
5. Jos hakemustyyppiin tulee KRYSP integraatio (Lupapisteeseen luku)
  1. Toteuta hakemustyypille multimetodit (esimerkkejä: `lupapalvelu.backing-system.krysp.reader`):
     * sanoman nouto: `lupapalvelu.permit/fetch-xml-from-krysp`
     * päätösten luku: `lupapalvelu.permit/read-verdict-xml`
     * päätösten validointi: `lupapalvelu.permit/validate-verdict-xml`
  2. Katso mallia päätösten lukemisesta `lupapalvelu.verdict/do-check-for-verdict` -funktiosta, joka hakee annetulle hakemukselle päätöksen kunnan taustajärjestelmästä.
5. Jos hakemustyyppiin tulee asianhallinta integraatio
  1. Määritä `lupapalvelu.xml.validator` -nimiavaruuteen skeema validaattori(t) (_schema-validator_) uudelle lupatyypille.
  2. Tarkista että halutuilla toimenpiteillä on asianhallinta konfiguraatiossa arvo 'true' (_operations.clj_)


## Uudet toimenpidetyypin lisääminen
### metatiedot, puu, näiden lokalisaatiot

## Uuden skeeman lisääminen

## Uuden liitetyypin lisääminen

## Uuden tilan lisääminen

Lisää tila lupapalvelu/states.clj:ssä tai lupapiste-commons/states.cljc:ssä
sopivaan tilagraafiin.

Tarkastuslista:
 - Uusi tila vaikuttaa vain haluttuihin hakemus- ja toimenpidetyyppeihin
 - Tilan nimeä vastaava lokalisaatioavain ja vastaava title-avain
   ("tila", "tila.title") on lokalisoitu
 - Tilan nimi ja lokalisaatio on viety DW:n lataustiedostoon
 - Tyylit lisätty resources/private/common-html/sass/views/_application.scss
   tiedostoon ja tiedosto käännetty css:ksi
 - Tila lisätty haluttuun kohtaan järjestystä TOJ:n editorissa (lupapiste-toj.components.editor/state-presentation-order-map)

## UI komponentit (ui_components.clj, auto-skannatut tiedostot ui-components hakemistossa)
