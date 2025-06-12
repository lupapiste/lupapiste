# Domain käsitteet

Dokumentti pyrkii lyhyesti selittämään domainin keskeisimmät käsitteet.

## Organisaatio (organization)

Organisaatio, joka käsittelee hakemuksia.

Viranomaiset kuuluvat organisaatioon.

Esimerkiksi: 
 - Sipoon rakennusvalvonta
 - Rovaniemen yleiset alueet
 - Keski-Uudenmaan ympäristötoimi

## Lupatyypit (permit types)

Lupatyyppi määrittää hakemuksen tyypin (rakennuslupa vs ympäristölupa jne...).

```clojure
(->> (lupapalvelu.permit/permit-types)
     (keys)
     (map (partial lupapalvelu.i18n/localize :fi)))
 =>
 ("Kiinteistötoimitukset ja tonttijaot"
  "Kaavat ja kaavamuutokset"
  "Muut ympäristöluvat"
  "Ympäristölupa"
  "Vapautushakemus vesijohtoon ja viemäriverkostoon liittämisvelvollisuudesta"
  "Rakentamisen lupa"
  "Poikkeamisen hakeminen"
  "Maa-aineksen ottolupa"
  "Ympäristöilmoitus"
  "Yleisten alueiden lupa"
  "Digitoidun luvan arkistointi")
```

## Hakemus (application)

Hakemus on tärkein käsite. Hakemus itsessään muodostuu useista käsitteitä, jotka yhdessä muodostavat hakemuksen.

Hakemus (tai hanke) on pitkäkestoinen prosessi. Hakemus elää vaiheittain. Tietoja lisätään jopa useiden vuosien ajan.

### Lupatyyppi (permitType)

Määrittää minkätyyppisestä luvasta on kyse.

### Hakemuksen tila (state)

Hakemuksen tila, esim "Hakemus jätetty", "Päätös annettu", "Rakennustyöt aloitettu".
Katso esimerkkigraafi [täältä](../information-architecture.md##hakemuksen-tila).

### Kiinteistö (property)

Hakemuksen täytyy aina kohdistua yhteen kiinteistöön. 

Kiinteistötoimitus lupatyypille on toteutettu tuki useamman kiinteistön lisäämiselle.

### Toimenpiteet (operations)

Hakemuksella on aina yksi päätoimenpide (primaryOperation), esimerkiksi "Uuden rakennuksen rakentaminen".

Lisäksi hakemus voi sisältää muita toimenpiteitä (secondaryOperations), esim "Autotallin rakentaminen".

Jokaisesta toimenpiteestä muodostetaan hakemukselle lomake (document), joka sisältää toimenpiteeseen vaadittavat tiedot.

### Osapuolet (parties)

Hakemuksen osapuolia on esimerkiksi hakija, suunnittelija, maksaja, työnjohtaja.

Osapuolia varten hakemukselle muodostetaan lomakkeet (document).

Lisäksi hankkeelle kutsutut (valtuutetut) osapuolet listataan "Osapuolet" välilehdellä.

### Lomakkeet (documents)

Edellä mainitut toimenpiteet ja osapuolet esitetään lomakkeina, joihin kerätään niihin liittyvä data.

Hakemuksella on myös muita lomakkeita, jotka eivät liity em. käsitteisiin. Esimerkiksi rakennusjäteselvitys, kiinteistö.

### Valtuutukset (auths)

Liittyy osapuoliin. Jotta käyttäjällä on pääsy hakemukselle, hänellä täytyy olla valtuutus (auth). 

Esimerkiksi hakemuksen luoja voi kutsua käyttäjiä sähköpostilla hankkeelle.

Valtuutettu voi saada joko luku (reader), kommentointi (commenter) tai kirjoitusoikeudet (writer) hankkeelle. 

### Liitteet (attachments)

Liitteet ovat tyypitettyjä tiedostoja. Esimerkiksi suunnitelmia rakennuksesta (pohjapiirustus) tai vaikka osapuolen CV.

Viranomaiset tarkastavat ja hyväksyvät hankkeelle lisätyt liitteet.

### Lausunnot (statements)

Viranomaiset voivat pyytää lausuntoa hakemuksesta esimerkiksi ELY-keskukselta tai paloviranomaisilta.

Lausunnonantaja saa tiedon pyynnöstä sähköpostiin. Lausunnonantaja saa valtuutuksen hakemukselle.

### Päätökset / sopimukset ((pate-)verdicts)

Hakemus saa aina päätöksen (tai yleisillä alueilla mahdollisesti sopimusehdotuksen).

Päätöksen jälkeen alkaa niin sanottu "rakentamisen aikainen asiointi". 
Ennen päätöstä puhutaan "hakemisen aikaisesta asioinnista".

### Katselmukset (reviews)

Päätöksellä määrätään usein vaaditut katselmukset, jotka täytyy hoitaa ennen kuin rakennuksen voi esimerkiksi ottaa käyttöön.

Esimerkkejä katselmuksista: aloituskokous, rakennekatselmus, sijaintikatselmus.

### Työnjohtajat (foremen)

Rakennuslupahankkeilla täytyy olla nimetty ja hyväksytty työnjohtaja.

Työnjohtaja nimetään erillisellä työnjohtaja hakemuksella (tai ilmoituksella), jonka viranomainen hyväksyy.

### Tehtävät (assignments)

Tehtävä on ns "TODO"-item, jonka viranomainen kuittaa tehdyksi.

Organisaatio voi määrittää automaattitehtäviä. 
Esimerkiksi: hakemukselle lisätään ilmanvaihtosuunnitelma liite -> hakemuksen "IV-käsittelijälle" luodaan tehtävä
"Tarkista liite". 

### Rakennukset (buildings)

Rakennusvalvonnan toimenpiteet kohdistuvat usein olemassa olevaan rakennukseen.

Toimenpiteesen lomakkeelta valitaan rakennus, johon toimenpide kohdistetaan.

Kun luodaan päätös, hankkeelle luodaan "buildings" taulukko johon päätöksessä mainittujen rakennusten tiedot tallennetaan.

Rakennuksiin viitataan katselmuksissa, sillä katselmus usein liittyy tiettyyn rakennukseen. 

## Viitelupa (link permit)

Hakemusten välille voidaan luoda linkkejä (viitteitä). 
Esimerkiksi työnjohtaja hakemuksella on aina viite siihen liittyvään rakennuslupaan. 

## Jatkoaikahakemus (continuation permit)

Päätöksessä luvalle myönnetään usein voimassaoloaika. Jos tämä on täyttynyt, täytyy lupaan hakea jatkoa erillisellä
jatkoaikahakemuksella.

## Muutoslupa (change permit)

Jos päätöksenannon jälkeen rakentamisen aikana alkuperäisiin suunnitelmiin tuleekin merkittäviä muutoksia, 
hakijan täytyy hakea muutoslupaa (uusi hakemus). 
Viranomaiset tarkastavat muuttuneet suunnitelmat ja antavat uuden päätöksen.
