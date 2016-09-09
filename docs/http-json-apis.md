# Lupapisteen HTTP/JSON-rajapinnat

## Autentikaatio

Rajapintoja voi käyttää joko istuntokohtaisen kirjautumisen avulla tai API-keyn avulla.

Istunto aloitetaan lähettämällä HTTP POST -pyyntönä username- ja password-parametrit /api/login osoitteeseen.
Vastauksessa saatavia cookieita kuljetetaan mukana tätä seuraavissa rajapintakutsuissa.
Cookieiden arvot saattavat muuttua istunnon aikana.

Istunnossa on lisäksi välitettävä `anti-csrf-token` -niminen cookie ja
`x-anti-forgery-token` -niminen header. Cookien ja headerin arvojen on vastattava
toisiaan.

API-key autentikaatio tapahtuu lähettämällä jokaisen HTTP-otsikkotiedoissa
OAuth 2.0 mukainen Authorization-header. Arvo on muotoa "Bearer salainen-avain",
avain sisältää ascii-kirjanmerkkejä, numeroita, viivoja ja pisteitä ja voi päättyä
yhtääsuuruunmerkkeihin. Avainta ei enkoodata. Esimerkiksi:

    Authorization: Bearer minun-avaimeni

Tarkempi määritys: https://tools.ietf.org/html/rfc6750#page-5

API-keyta käytettessä csrf-tokenia ei tarvita.

## Yleisperiaatteet tietojen muodosta

Merkistönä käytetään UTF-8:aa,

Koordinaatit esitetään ETRS-TM35FIN -tasokoordinaatistossa, jollei toisin ole ilmoitettu.

Ajanhetket esitetään 64bit kokonaislukuna eli aikaleimana UTC-aikavyöhykkeellä.
Aikaleima esitetään millisekuntien tarkkuudella.

Rajapintojen määrittely koodiin on kuvattu [kehitysohjeessa](developerguide.md).

### Pyynnöt

Kyselyjen (/api/query/<nimi>) parametrit välitetään HTTP GET-pyynnön parametreina.

Komentojen (/api/command/<nimi>) parametrit välitetään HTTP POST-pyynnön rungossa JSON-muodossa.

#### id-parametri

id-niminen parametri käsitellään erityisesti: se viittaa aina hakemuksen asiointitunnukseen.
Jos käyttäjällä ei ole oikeutta hakemukseen, tai jos hakemusta ei löydy tietokannasta,
palautuu virhe.

### Vastaussanomat

JSON-vastaussanoma sisältää päätasolla aina yhden objektin, jonka rakenne on seuraava:

Kenttä        | Tietotyyppi     | Kuvaus
--------------|-----------------|--------------------------------------------------------------------------
ok            | boolean         | true / false sen mukaan menikö rajapintakutsun käsittely läpi virheettä
(text)        | String          | Jos ok=false, sisältää virhekoodin.
(muut kentät) | (yleensä Array) | Rajapintakohtaisesti määritellyt varsinaiset data-kentät

Esimerkkivastaukset:

```JavaScript
{"ok" : true,
 "data" : [{"id":1, "name": "foo"},
           {"id":2, "name": "bar"}]}

{"ok" : false,
 "text" : "error.unauthorized"}
```
