## Ryhti integration notes

### ConstructionAction - Rakentamistoimenpide
* statusOfConstructionAction* pakollinen kenttä constructionActioniltä voidaan päättellä hankkeen tilasta.

### BuildingObjectIssue
* buildingObjectIssueKey generoidaan ConstructionActionKeyn rinnalle

### BuildingObjectChange
* Lasketaan hankkeen tiedoista. Ei vielä päätetty miten buildingObjectChangeKey hoidetaan.

### Structure, Building, AreaToBeBuiltForSpecificActivities
* Nämä kolme on lupapisteessä "Rakennuskohde"
* Osa kentistä on vain tietynlaisella rakennuskohteella mutta valtaosa on samoja
* Maalämpökaivon lokalisoinneissa on siivottavaa; ks. alempana BuildingObjectOwner

### Address
* Osoitejärjetysnumero          - Tieto voidaan päätellä `osoite` kentästä
* osoitenumeronNumeroOsa        - Tieto voidaan päätellä `osoite` kentästä
* osoitenumeronNumeroOsa2       - Tieto voidaan päätellä `osoite` kentästä
* osoitenumeronJakokirjain      - Tieto voidaan päätellä `osoite` kentästä
* osoitenumeronJakokirjain2     - Tieto voidaan päätellä `osoite` kentästä

### ContactAddress
* Tietolähde                    - Lupapisteessä täytetyn datan tietolähde on aina "02 - Kunta" koodisto: "http://uri.suomi.fi/codelist/rytj/tietolahde/code/02"
* ulkomainenLähiosoiteLisärivi  - Sama kuin `osoiteenLisämääre` kenttä
* huoneistotunnuksenKirjainosa  - Tieto saadaan huoneisto dokumentista
* huoneistotunnuksenNumeroOsa   - Tieto saadaan huoneisto dokumentista
* huoneistotunnuksenJakokirjain - Tieto saadaan huoneisto dokumentista
* postilokeroOsoite             - Jos tätä tarvitaan? Niin voidaan jonain määrin päätellä `osoite` kentästä onko postilokero vai ei, jos osoite alkaa "PL", "PB", "Postilokero" etc.
* Postiosoite                   - "sijaintipaikan osoittavien tietojen kokonaisuus" voidaan koostaa muista osoite-kentistä
* sijainti                      - Jos tarvitaan voidaan hakea tietoa esim. MML:ltä
* YhteysosoitteenLaji           - Me tarvitaan vaan Kotimainen/Ulkomainen postiosoite/yhteysosoite, mikä on postiosoite ja yhteysosositteen ero?

### Planner
* `plannerRole`:
  * Lupapisteessä lomakkeet erikseen pääsuunnittelijalle ja suunnittelijalle => Pääsuunnittelija-rooli voidaan päätellä lomakkeen skeemasta
  * Suunnittelijalla on ennestään "Suunnittelurooli" (`kuntaRoolikoodi`), jossa saman**kaltainen** setti valintoja. Ei kai voida tässä vaiheessa koskea, koska vaikuttaa kuntaGML:ään.
* `PlannerCompetence` ja `requiredCompetence` 
  * voidaan mapata olemassa olevista kentistä `patevyysluokka` ja `suunnittelutehtavanVaativuusluokka`, joissa käytetään kirjainluokkia `AA`-`C`.
  * "Ei tiedossa" voinee koitua ongelmaksi
* `BuildingReference`, `StructureReference` ja `SpecificAreaReference`
  * Suunnittelijalle voi poimia viittauksia rakennuskohteisiin `rakennuskohdeSelector`-docgen komponentilla. 
  * Komponentti tallentaa taulukon joka sisältää `uusiRakennuskohde`-lomakkeiden `id`:itä.
  * Tallennetun `id`:n perusteella voidaan hankkeelta kaivaa rakennuskohde, mistä saadaan sen tyyppi (`BuildingReference`, `StructureReference` tai `SpecificAreaReference`) ja ryhti-id.
  * `rakennuskohdeSelector`-komponentti ei takaa, että tallennettuja id:itä vastaavat rakennuskohteet välttämättä löytyvät hankkeelta. Toimenpide on esimerkisi voitu poistaa, jolloin siihen tallennettu viittaus ei ole validi.
  * `rakennuskohdeSelector` käytössä myös työnjohtajan rakennuskohteen valitsimena

### Location
* Löytyy rakennuskohteen (Structure/Building/AreaToBeBuiltForSpecificActivities) alta
* Lisättävä myöhemmin kyky valita sijaintipiste kartalta: LPK-8020

### BuildingPermitIssue
* Pysyvä lupatunnus, huoneistotunnus, rakennustunnus, ja rakennelmatunnus täytyy hakea Ryhtistä, mutta sen hakemisessa kestää kun organisaatiolla pitää olla RYHTI API tunnukset.
  * Lisätään näiden päivitys AsiointiAPI:n käyttöönoton yhteydessä
* `dateOfInitiation` eli vireilletulopäivä on pääteltävissä käsittelyajanlaskennasta LPK-7182 sitten kun valmistuu
  * Samoin `lifeCycleState`

### BuildingPermitApplication
* `applicationContent` on hankkeen kuvaus - kentän arvo (joka on kaikilla toimenpiteillä)
* `buildingPermitApplicationType` on mäpättävissä hankkeen (pää)toimenpiteestä
* `permitApplicationType` [sic] on mäpättävissä siitä, onko hanke sijoittamis-/toteuttamislupa
* `dateOfReception` on hankkeen submitted aikaleima
* `lifeCycleState` on mäpättävissä hankkeen tilasta

### ConstructionProject
* Ei pakollinen hankkeella, mutta sisältää työnjohtajat ja suunnittelijat niin tuettava
* Ei sisältänyt muita pakollisia kenttiä kuin `constructionProjectKey`

### Huoneistot
* `muutoksen-laji` on lisätty skeemaan, mutta sarake ei ole lisätty `huoneistot/columns.cljs` tiedostoon. Mutta toimii nyt oletettavasti samalla tavalla kuin KuntaGML:n `muutostapa`

### BuildingSite
* Kenttää spatialPlan ei voida toteuttaa koska tietoa ei ole vielä saatavissa Ryhdiltä
* Kenttää statusOfPlan ei muuteta vielä koska Ryhti-koodisto on laajempi kuin VTJ:n vastaava

### BuildingObjectOwner
* Eli rakennuksen omistaja
* Omistajalajikoodisto on laajempi Ryhtissä joten päivitettävä siirtymän yhteydessä
* Lokalisaatioissa on siivottavan varaa sitten kun docgen tukee i18nprefix:iä tai vastaavaa

### Decision
* Eli verdict eli päätös
* Käytetään `buildingPermitDecisionKey`:tä kaikkien päätöslajien avaimille (demolitionPermitDecisionKey, landscapeWorkPermitDecisionKey jne.)
  * Toisin kuin rakennuskohteella, päätöksen tyyppi ei voi muuttua (riippuu hankkeen tyypistä)
* `decisionMakerType` mäppäytyy päätöksen lautakunta/viranhaltija - valintaan
* Säädösviitteet eivät ole pakollisia joten jätetään toistaiseksi pois
* `relatedApplication` on hankkeen `buildingPermitApplicationKey` oletettavasti
* `lifeCycleState` on pääteltävissä päätöksestä (mm. `verdict-code-negative?` funktio)
* Päivämääräkentät tulevat pakolliseksi joten ei sallita päätöspohjasta luonnoksen luomista mikäli se ei sisällä niitä:
  * `publicNoticeDate` on kuulutus/julkipanopäivä (tulee pakolliseksi R ja P hankkeille)
  * `decisionInForceUntil` on tulkittu "Lupa voimassa" päiväksi (tulee pakolliseksi P hankkeille)
  * Pääkäyttäjän täytyy muokata/luoda uusi päätöspohja jos vanha ei kelpaa
* Lupaehdon katselmuksena voi olla KuntaGML:ssä "lämpö-, vesi- ja ilmanvaihtolaitteiden katselmus" kun Ryhdissä kukin on erillinen koodiston arvo
  * Muuten katselmuslista on subset Ryhtin listasta
  * Muilla lupaehdoilla ei ollut aiemmin koodistoa niin sekin on yhteensopiva
