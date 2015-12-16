# Kehitystyö

## Ympäristön pystytys

Tarvitset kehitysympäristöön seuraavat työkalut:
- [JDK 8](http://www.oracle.com/technetwork/java/javase/downloads/index.html)
- [Leiningen](https://github.com/technomancy/leiningen) 2.5+
- [MongoDB](https://www.mongodb.org/downloads) (testattu 2.6 ja 3.0 versioilla)
- Ruby ja compass scss-tyylitiedostojen kääntämistä varten
  - `gem install compass`
- Python 2.x ja Robot Framework selaintestausta varten
  - `pip install robotframework`
  - `pip install robotframework-selenium2library`
  - IE:llä ajettavia testejä varten ajuri osoitteesta  http://selenium-release.storage.googleapis.com/index.html
  - Chromella ajettavia testejä varten ajuri osoitteesta http://chromedriver.storage.googleapis.com/index.html
- [pdftk](https://www.pdflabs.com/tools/pdftk-server/)
  PDF-tiedostojen kääntämistä ja korjaamista varten
- Valinnaisesti: [pdf2pdf](https://www.pdf-tools.com/pdf/Products-Shop/Evaluate.aspx?p=CNV&back=%2fpdf%2fpdf-to-pdfa-converter-signature.aspx)
  PDF/A-konversioita varten

## Kehitysympäristön konfigurointi

### user.properties

Luo projektin juureen (sovelluksen kehitysaikaiseen ajohakemistoon) user.properties
tiedosto. Tiedostossa voit määritellä mm. tietokantayhteyden:

    mongodb.servers.0.host  localhost
    mongodb.servers.0.port  27017
    mongodb.dbname          lupapiste
    mongodb.credentials.username     lupapiste-kannan-user
    mongodb.credentials.password     lupapiste-kannan-salasana

Jos haluat pdf2pdf työkalun käyttöön, määrittele lisenssiavain lisäämällä tiedostoon

    pdf2pdf.license-key    AVAIMESI


### Master password

Sovellus vaatii käynnistyäkseen master-salasanan asettamisen. Salasanan voi
asettaa
- tallentamalla se kotihakemistosi application_master_password.txt tiedostoon
- `APPLICATION_MASTER_PASSWORD` ympäristömuuttujassa tai
-  `-Dapplication.masterpassword=xxxx` käynnistysparametrilla
  (kun sovellus käynnistetään java -jar komennolla).

Salasanaa käytetään asetuksissa olevien salaisuuksien avaamiseen (ja kryptaamiseen).
Properties-tiedostoissa voi käyttää Jasyptilla kryptattuja arvoja, ks. ohje
test/lupapalvelu/nested.properties tiedostossa.

### SSL-avain
Kehitysmoodissa Lupapiste-sovelluksen sisäänrakennettu sovelluspalvelin kuuntelee
HTTPS-liikennettä portissa 8443. Generoi tätä varten projektin juureen
SSL/TLS-avain keystore -nimiseen tiedostoon. Salasanaa ei tule käyttää.
Ks. http://www.eclipse.org/jetty/documentation/current/configuring-ssl.html

Vaihtoehtoisesti voit lisätä user.properties tiedostoon rivin

    feature.ssl    false

Tällöin sovelluspalvelin kuuntelee ainoastaan HTTP-liikennettä.

### Kartat ja paikkatietoaineisto

Karttojen ja paikkatietoaineiston käyttö vaatii käyttäjätunnukset Maanmittauslaitokselta.

## Palvelun käynnistys

Kun työkalut on asennettu ja lisätty polkuun sekä MongoDB käynnistetty,
Lupapiste käynnistyy komennolla:

    lein run

Sovellus on ajossa osoitteessa http://localhost:8000.

Klikkaa oikean reunan Development palkissa "Apply minimal" linkkiä.
Tämä alustaa tietokantaan muutamia käyttäjiä ja organisaatioita. Voit kirjautua
sisään esimerkiksi hakijatunnuksella pena/pena tai viranomaistunnuksella sonja/sonja.


## Tyylikäytännöt

Lähdekoodissa käytetään aina unix-rivinvaihtoja (`\n`).

Sisennys on kaksi välilyöntiä. Merkkijonojen ympärillä käytetään lainausmerkkejä
myös JavaScript-koodissa.

JavaScript-koodi tulee tarkastaa JSHint-työkalulla, jonka asetukset ovat projektin juuressa.

## Testaus

### Backend

 - `lein nitpicker` tekee lähdekooditiedostoille laittomien merkkien tarkastuksen
 - `lein midje` ajaa (pelkät) yksikkötestit
 - `lein integration` ajaa integraatiotestit. Integraatiotestit olettavat,
    että palvelin on käynnissä oletusportissa 8000 ja siitä on yhteys MML:n rajapintoihin.
 - `lein stest` ajaa systeemitestit, jotka käyttävät myös muita ulkoisia integraatioita.
 - `lein verify` ajaa kaikki edellä mainitut.

### Frontend end-to-end testit

 - `local.sh` / `local.bat` ajaa Robot Frameworkilla paikalliset testit.
   Näissä oletetaan, että  palvelin on käynnissä oletusportissa.
 - `local-integration.sh` / `local-integration.bat`
   ajaa Robot Frameworkilla testit, jotka käyttävät ulkoisia palveluita kuten
   VETUMA-kirjautumispalvelun testijärjestelmää.

# Korkean tason domain-kuvaus

TODO

## Tietomalli
## Roolit

# Arkkitehtuuri yleiskuvaus

TODO

front+back
fyysinen pino: front, app, mongodb, geoserver, sftp jne


# Frontend arkkitehtuuri
## Yleiskuvaus
  SPA intial startup
  Kommunikointi backendiin
    Query and Command

## Globaalit objektit
Ajax
Hub
User feedback on success and error event
Localizations
Lupapiste Map
lupapisteApp
- models
- services
LUPAPISTE
- config
Docgen, viittaus skeemojen määrittelyyn

## KnockoutJS käyttö
- component, model, template
- services

## Compass + SASS

CSS-tyylit kirjoitetaan Sass-tiedostoihin `resources/private/common-html/sass` kansiossa. Sass-tiedostot käännetään compass gemillä (kirjoitushetkellä versio 1.0.3). Compassin voi asettaa kuuntelemaan muutoksia `compass.sh` scriptillä projektin juuresta. Vaihtoehtoisesti Sass-tiedostot voi kääntää käsin `compass compile resources/private/common-html`.

Compassin konfigurointi on tiedostossa `resources/private/common-html/config.rb`, sisältää mm. CSS splitterin, joka jakaa **main.css** tiedoston pienempiin osiin (IE9 css rule limit). CSS tiedostot generoidaan `resources/public/lp-static/css/` kansioon.

Oletuksena CSS-tiedostot minimoidaan, tätä voidaan säätää compassin _environment_ tai _output-style_ konfiguroinnilla (config.rb). Esimerkiksi käsin generoitu ei-minifioitu CSS: saa aikaiseksi seuraavalla komennolla (development-mode): `compass compile -e development resources/private/common-html`


## Oskari Map
  The hub between Lupapiste and Oskari Map

## Robot Framework

  Muista 2 välilyöntiä.

# Backend arkkitehtuuri
## Yleiskuvaus

TODO

## Action pipeline
Routes /api/command/:name, /api/query/:name, /api/datatables/:name, /data-api/json/:name and /api/raw/:name are defined in **web.clj**.

Commands, queries (incl. datatables requests), exports (/data-api) and raw actions are then executed by the framework in **action.clj**.

Use _defcommand_, _defquery_, _defraw_ and _defexport_ macros to define new actions. Action definitions are in the following form:

    (def<action-type>
      { ; metadata map
      }

      [command] ; action argument map, supports destruction
      ; action body
    )

Actions must return a map. Commands, queries and exports return a map that contain :ok key with a boolean success value. Use sade.core/ok, fail and fail! to generate these responses.

**Note:** coding convention: define actions only in _api.clj namespaces. Remember to require the api-namespace in server.clj or web.clj, so the action is registered and the route is available on server startup.

Supported action metadata-map keys (see ActionMetadata definition in action.clj):

Key | Description
--- | -----------
user-roles | Required. Set of user role keywords. Action pipeline checks that the action is allowed for the current user before executing the action.
parameters | Vector of parameters. Parameters can be keywords or symbols. Symbols will be available in the action body. If a parameter is missing from request, an error will be raised.
optional-parameters | Vector of parameters. Same as 'parameters', but error is not raised if missing from request.
user-authz-roles | Set of application context role keywords. Action pipeline checks that the current application is accessible by the current user before executing the action.
org-authz-roles | Set of application organization context keywords. User is authorized to use action only if user has valid role in application's organization.
description | Documentation string.
notified | Boolean. Documents that the action will be sending (email) notifications.
pre-checks | Vector of functions. Functions are called with 2 parameters: action argument map (command) and current application (if any). Function must return nil if everything is OK, or an error map. Use sade.core/fail to generate the error.
input-validators | Vector of functions. Functions are called with 1 parameter, the action argument map (command). Function must return nil if everything is OK, or an error map. Use sade.core/fail to generate the error.
states | Vector of application state keywords. Action pipeline checks that the current application is in correct state before executing the action.
on-complete | Function or vector of functions. Functions are called with 2 parameters: action argument map (command) and the action execution return value map.
on-success | Function or vector of functions. Functions are called only if the action return map contains :ok true. See :on-complete.
on-fail | Function or vector of functions. Functions are called only if the action return map contains :ok false. See :on-complete.
feature | Keyword: feature flag name. Action is run only if the feature flag is true. If you have feature.some-feature properties file, use :feature :some-feature in action meta data.

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
Returns JSON to frontend: `{:ok true :data [{:id "123" :comments []}]}`


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
TODO Kuinka lähetän sähköpostia

## Integrations
TODO
* WFS (KTJ, maastotietokanta, naapurit)
* WMTS/WMS
* KRYSP (miten keskustellaan taustajärjestelmien kanssa)
* Asianhallinta

## Tilat ja tilakone

## VETUMA

## Database

TODO tietomalli (collectionit)

## Schemat

    {
    (with-doc ""
      :avain) (schema/Str)
    }

## Koodauskäytännöt
- Käytetään omia apu-namespaceja (ss, mongo jne)


# Laajennuspisteet

## Uuden hakemustyypin lisääminen

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
   tiedostoon ja tiedosto käännetty compassilla

## UI komponentit (ui_components.clj, auto-skannatut tiedostot ui-components hakemistossa)
