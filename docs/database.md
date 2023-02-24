# Tietokanta

Palvelun tiedot on tallennettu MongoDB tietokantaan.

## Collections

Collection                    | Kuvaus
----------------------------- | -----------------------------
activation                    | Käyttäjätunnusten aktivointitiedot
app-links                     | Viitelupatiedot (linkit)
application-bulletin-comments | Julkipantujen hakemusten kommentit
application-bulletins         | Julkipannut hakemukset
applications                  | Hakemukset. Skeema osittain määritelty eri nimiavaruuksissa, avaimet esitelty lupapalvelu.domain/application-skeleton tietorakenteessa.
companies                     | Yritystilit. Skeema määritelty lupapalvelu.company nimiavaruudessa.
logins                        | Epäonnistuneet kirjautumiset
migrations                    | Tietokantakonversioiden kirjanpito
open-inforequest-token        | Avointen neuvontapyyntöjen sähköpostiavaimet
organizations                 | Organisaatiot
perf-mon-timing               | Suorituskykymittaukset
poi                           | Point of Interest maastotietokanta
propertyCache                 | Välimuisti kiinteistöjen sijaintitiedoille
screenmessages                | Käyttäjille näytettävät ilmoitukset
sequences                     | Hakemusten asiointitunnusten juoksevien numeroiden tila
sign-processes                | Onnistuu.fi-istunnot
statistics                    | Tilasto PDF/A konversioista
submitted-applications        | Hakemuksesta tallennetaan tänne kopio kun se jätetään (ensimmäisen kerran)
token                         | Eri kertakäyttölinkit, ks. token.clj
users                         | Käyttäjät. Skeema määritelty lupapalvelu.user nimiavaruudessa.
vetuma                        | VETUMA-istunnot


Indeksit on määriteltu lupapalvelu.mongo nimiavaruudessa.

## Sudenkuoppia

- Arraysta voi päivittää vain yhtä alkiota kerrallaan, kun päivitykset kohde haetaan $elemMatch haulla
  - `db.myCollection.update({arr: {$elemMatch: {key: {$gt: 0}}}}, {$inc: {"arr.$.key": 1}})`
    päivittää arr arraysta vain ensimmäisen alkion, jossa key > 0, vaikka ehto täsmäisi useampaan
  - Jos haluaa päivittää useita, on määriteltävä päivitettävien kohtien indeksit
  - ks. lupapalvelu.mongo/generate-array-updates
- Samassa updatessa samaan arrayhin voi kohdistaa vain joko $push tai $pull -operaatioita
  - Jos tarvetta molemmille, joutuu tekemään kaksi updatea
- Jos hakuehdossa vertaa avainta arvoon `null`, palautuu myös ne joilta koko avain puuttuu!
  - Käytettävä lisäksi $exists-ehtoa, esim.
    `db.myCollection.find({$and: [{key: null}, {key: {$exists: true}}]})`
- [MongoChef](http://3t.io/mongochef/)-pommi: jos projektiossa käytetään $elemMatchia, niin kentän päivitys ei välttämättä kohdistu näkyvään kenttään. Esim. rajataan auth-arraysta näkyviin vain yksi valtuutettu ja muutetaan käyttäjätunnus. Todellisuudessa muuttuukin auth-arrayn ensimmäisen alkion käyttäjätunnus!

Lue myös [Top 5 syntactic weirdnesses to be aware of in MongoDB](http://devblog.me/wtf-mongo)
1. Keys order in a hash object
2. undefined, null and undefined
3. Soft limits, hard limits and no limits
4. Special treatment for arrays
5. $near geo-location operator
