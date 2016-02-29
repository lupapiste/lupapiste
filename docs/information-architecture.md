# Tietomalli

Käsite | Selite
------ | ---
Lupatyyppi (permit type) | Lupatyyppi määrittää millaisesta lupa-asioinnista on kyse. Esimerkiksi rakennusvalvonta, yleiset alueet ja ympäristötoimi ovat omia lupatyyppejään.
Hakemus (application) | Hakija täyttää palvelussa hakemuksen, joka sisältää mm. lomaketietoja ja liitteitä. Hakemuksella on aina tila (state), joka kuvaa hakemuksen tilaa lupa- tai ilmoitusprosessissa. Viranomainen tarkastaa ja käsittelee palveluun jätetyn hakemuksen.
Toimenpide (operation) | Toimenpiteet määrittävät hakemuksen tyypin eli millaisia tietoja hakemukseen täytyy täyttää. Toimenpide kuuluu aina tiettyyn lupatyyppiin. Esimerkiksi toimenpide "Aidan rakentaminen" kuuluu rakennusvalvonnan lupatyyppiin. Toimenpiteellä on skeema (schema), joka määrittää hakemuksella täytettävät lomaketiedot.
Organisaatio (organization) | Viranomainen kuuluu aina yhteen tai useampaan organisaatioon. Viranomaisella on oikeus nähdä ja käsitellä omaan organisaatioonsa jäteyt hakemukset. Organisaatio on useissa tapauksessa kunnan tietty viranomaisorganisaatio (esimerkiksi rakennusvalvonta). Tietomalli mahdollistaa helposti ylikunnallisen lupakäsittelyn, sillä yksi organisaatio on konfiguroitavissa usean kunnan käyttöön.
Neuvontapyyntö (info request) | Hakemuksen esiversio. Neuvontapyynnön avulla hakija voi pyytää  viranomaiselta neuvoa jo ennen varsinaisen hakemuksen tekoa. Neuvontapyyntö voidaan muuntaa hakemukseksi, jolloin asioinnin valmistelun voi aloittaa suoraan neuvontapyynnön pohjalta.

## Käyttäjät

Käyttäjällä on yksiselitteinen perusrooli. Rooli määrittää peruskäyttöoikeustason,
ja roolin mukaan käyttäjälle tarjotaan palvelussa tietty näkymä (oma Single Page Application).

Rooli | Selite
----- | ---
Pääkäyttäjä (admin) | Palvelun hallinnointi
Organisaation pääkäyttäjä (authorityAdmin) | Organisaation pääkäyttäjä hallitsee organisaation tietoja ja konfiguraatioita
Viranomainen (authority) | Viranomainen kuuluu yhteen tai useampaan organisaatioon. Viranomainen voi käsitellä organisaatioon tulleita hakemuksia. Viranomaisrooleja organisaatioihin hallinnoi organisaation pääkäyttäjä
Hakija (applicant) | Vahvasti tunnistautunut hakija. Hakijat voivat luoda hakemuksia palveluun. Hakija voi myös saada valtuutuksia muiden hakijoiden tekemiin hakemuksiin, jolloin samaa hakemusta voi valmistella useampi henkilö
Avoimen neuvontapyynnön viranomaiskäyttäjä (oirAuthority) | Käyttäjä saa ilmoituksia avoimista neuvontapyynnöistä. Käyttäjä voi antaa vastauksen hakijan avoimeen neuvontapyyntön. Käytössä organisaatioissa, jotka eivät vielä ole ottaneet varsinaista asiointia käyttöön.
Dummy (dummy) | Dummy käyttäjä, joka ei ole vielä rekisteröitynyt ja vahvasti tunnistautunut palveluun. Dummy käyttäjä syntyy esimerkiksi kun hakemukselle valtuutetaan käyttäjä, jonka sähköpostiosoite ei ole vielä rekisteröitynyt palvelun käyttäjäksi.

## Organisaatiot

![](organisaatiot.png)

Yllä olevassa kuvassa on hahmoteltu reaalimaailman mallia Lupapisteen käyttöön
ottavista viranomaisorganisaatioista. Lupapisteen kannalta asiakas on jokin kunnan
organisaatio, ei kunta itse. Näiden organisaatioiden nimet vaihtelevat kunnittain,
mutta suurin piirtein vastuualueiden jako on seuraava:

- Rakennusvalvonta, joka vastaa rakentamisen luvista
- Ympäristötoimi, joka vastaa Ympäristöluvista ja maankäytön luvista
- Tekninen toimi, joka vastaa sijoitus- ja kaivuluvista sekä yleisen alueen käytön luvista

Joskus kunnat järjestävät nämä palvelunsa alueellisina. Esimerkiksi Säkylällä,
Euralla ja Köylilöllä yhteinen rakennusvalvonta ja Keski-Uudenmaan ympäristökeskuksella
monen kunnan ympäristötoimi.

Lupapisteen viranomaiskäyttäjät kuuluvat  kunnan tiettyyn organisaatioon tai
alueelliseen organisaatioon. Käytännössä on tilanteita, että sama henkilö kuuluu
useampaan organisaatioon. (Esim kesän ajan tuuraa naapurikunnan viranomaista,
tai joissakin kunnissa, esim Järvenpäässä yhteispalvelun henkilöstö, joka ottaa
vastaan kaikkien organisaatioiden hakemuksia).

Lupahakemukset osoitetaan aina yhdelle organisaatiolle. Näin voidaan hallita
organisaatiokohtaisia työjonoja. Esim. ympäristölupia ei haluta näkymään
rakennusvalvonnan jonossa. Organisaatiota ei voi vaihtaa hakemuksen luomisen jälkeen.

Tietyn kunnan alueelle kohdistuvia tietyn lupatyypin hakemuksia voi käsitellä
vain yksi organisaatio.

## Käyttäjien oikeudet ja hakemukset

Kun käyttäjä kirjautuu sisään Lupapisteeseen, niin mitkä hakemukset
hän näkee? Hakemuksen näkyvyys määräytyy hakemuksen organization- ja
auth-kenttien perusteella seuraavasti:

Jos käyttäjä on viranomainen samassa organisaatiossa kuin hakemuskin,
niin hakemus näkyy. Tietokannassa tämä tarkoittaa, että käyttäjän
(eli users-collectionin alkion) orgAuthz-kenttä sisältää
ko. organisaation.

Jos hakemuksen auth-kenttä sisältää käyttäjän tiedot, niin ko. käyttäjä näkee
hakemuksen.

Jos käyttäjä on ns. yrityskäyttäjä, niin hän näkee hakemukset joiden
auth-kentässä on yrityksen tiedot.

Mitä käyttäjä sitten voi hakemukselle tehdä riippuu puolestaan käyttäjälle
(joko auth- tai orgAuthz-kentässä) määritellystä roolista.

Hienosäätöä:

- Viranomainen voi kuulua useampaan organisaatioon ja hänellä voi olla samassa organisaatiossa useampi rooli.
- Kaikki viranomaiset eivät "orgAuthz-mielessä" kuulu
  organisaatioon. Esimerkiksi lausunnonantajat ja
  vierailijaviranomaiset (nämä ovat viranomaisia, joille "oikeat"
  viranomaiset voivat antaa lukuoikeuden yksittäiselle hankkeelle)
  listataan erikseen organisaation statementGivers-
  guestAuthorities-kentissä. Huomaa, että viranomainen voi olla
  toisessa organisaatiossa esim. lausunnonantaja ja toisessa
  organisaatiossa "oikea" viranomainen. Toisaalta,
  vierailijaviranomaisen ei tarvitse välttämättä olla viranomainen
  (authority) lainkaan.
