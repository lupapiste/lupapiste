# Elasticsearch

## Käyttöönotto

Varmista että joko `local.properties`- tai `user.properties`-konfiguraatiotiedostossa on asetettu

```
mongo-watcher.start-in-server-instance true
```

Uusien indeksien luominen on asetettu mongo-watcher -instassin tehtäväksi, joten kehitysympäristöt eivät 
käynnistyessään luo indeksejä ilman yllämainittua asetusta. Vaihtoehtoisesti voi ajaa rinnakkaista REPL:iä ja
käynnistää sovelluksen funktiolla `user/mongo-watcher-go`. Asetus tullaan asettamaan oletuksena päälle kunhan 
itestit saadaan tukemaan mongo-watcheria. 

## Uuden indeksin luominen

### 1. Indexin nimi ja versio

Aseta `lupapalvelu.elasticsearch.util/config`-funktion palauttamaan mappiin uuden indeksin nimi ja versio

```clojure
{:name    :elastic.index/applications
 :version {:major 1 :minor 0}}
```

Nimen tulee alkaa `:elastic.index/`. Jos indeksi on "suora vastine" mongon kokoelmalle (dokumenttien määrä ja id:t
ovat 1:1 mongon kanssa), voi olla suositeltavaa käyttää nimen loppuosana mongo-kokoelman nimeä. Muussa tapauksessa
jokin muu indeksin sisältöä tai tarkoitusta kuvaava nimi toiminee parhaiten.

Versionumerot toimivat siten, että `major`-versiota tulee kasvattaa silloin, kun indeksin mappingissä (ks. kohta 2)
jonkin kentän tyyppi muuttuu tai kenttä poistuu. Toisin sanoen indeksin mapping muuttuu siten, että aikaisemmin
indeksoitu data ei ole enää yhteensopivaa uuden mappingin kanssa.

Uusien kenttien lisääminen ei luo yhteensopivuusongelmaa vanhan datan kanssa, joten tällöin riittää `minor`-version
kasvattaminen. Myös tilanteessa, jossa johonkin kenttään indeksoitava sisältö muuttuu, voidaan `minor`-version
kasvattamisella laukaista uudelleen indeksointi sovelluksen käynnistyksen/päivityksen yhteydessä vaikka varsinaisia
uusia kenttiä indeksiin ei tulisikaan.

### 2. Mapping eli elastic-dokumentin skeema

Luo konfiguraatiotiedosto indeksin mappingille. Tiedosto tulee asettaa polkuun `resources/elasticsearch/` ja sen nimi
tulee olla muotoa `[indeksin nimen loppuosa]-v[major-versio].edn` eli yllä mainitun `:elastic.index/applications`
-indeksin tapauksessa `applications-v1.edn`. Mapping-tiedosto sisältää kyseiseen indeksiin vietävien kenttien
[tyypit](https://www.elastic.co/docs/reference/elasticsearch/mapping-reference/field-data-types). Lupapisteessä indeksit
luodaan ns. `strict`-modella eli määrittelemättömien kenttien kirjoittaminen indeksiin on estetty. Kaikki indeksiin
kirjoitettavat kentät tulee siis määritellä mapping-tiedostossa.

Vaikka kenttien nimien tulisi yleensä kuvata niiden tarkoitusta teknisen toteutuksen sijaan, analysoidun (`text`) kentän 
`keyword`-tyyppinen alikenttä nimetään konvention mukaan `keyword`. Tällöin esimerkiksi hakutulosten järjestämiseen 
voidaan käyttää kenttää `:address.keyword`.

Esim:
```clojure
:address {:type            "text"
          :analyzer        "prefix"
          :search_analyzer "prefix_search"
          :fields          {:keyword {:type "keyword"}}}
```

### 3. Datan synkronointi

Uutta indeksiä varten tulee implementoida indeksikohtainen metodi multimetodille
`lupapalvelu.elasticsearch.util/elastic-documents`.
Tätä metodia käytetään indeksointiin kun

1. Luodaan uusi indeksi
2. Indeksin versionumeroa päivitetään
3. Admin-käyttöliittymästä käynnistetään kaikkien dokumenttien indeksointi
4. Admin-käyttöliittymästä käynnistetään indeksin siivous.

```clojure
(defmethod es-util/elastic-documents :elastic.index/applications [_]
  (->> (mongo/select :applications {} (vec source-fields))
       (keep application->es-document)))
```

Metodin tulee palauttaa kaikki indeksiin vietävät elastic-dokumentit. Laiska sekvenssi on tässä tapauksessa hyvä muoto
palautusarvolle, sillä indeksoija käsittelee alkiot yksitellen.

Käytönaikaista datan synkronointia varten Lupapisteessä on MongoDB:n Change Stream -tekniikkaa hyödyntävä
`mongo-watcher`.
Mongo-kokoelman muutoksia voi rekisteröityä kuuntelemaan esimerkiksi seuraavalla tavalla:

```clojure
(mw-client/register-event-handler! :applications :application-elastic-updater application-elastic-updater)
```

missä `application-elastic-updater` on funktio, joka saa parametrikseen keskeisiltä osiltaan `clj`:ksi konvertoidun
[
`ChangeStreamDocument`in](https://mongodb.github.io/mongo-java-driver/3.8/javadoc/com/mongodb/client/model/changestream/ChangeStreamDocument.html)

Esimerkki Change Stream -dokumentista:

```clojure
{:full-document  {... koko päivitetty mongo-dokumentti ...}
 :id             "LP-753-2025-00001"
 :namespace      {:collection-name :applications :database-name "lupapiste"}
 :operation-type :update
 :removed-fields [:attachments.1]
 :resume-token   "826847E83D000000012B022C0100296E5A1004A231AC13F975476CAACB3F5BB6629DE4463C5F6964003C4C502D3039312D323032352D3030363731000004"
 :updates        {:address "New address"}}
```


