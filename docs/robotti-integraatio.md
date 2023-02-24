# Robotti-integraatio

Integraatio tarjoaa (ohjelmistorobotille) rajapinnan rakennusvalvonnan
Pate-päätösten sekä hankkeiden toimenpiteiden sijaintien
noutoon. Rajapinnan Swagger-dokumentaatio löytyy urlista
`https://<lupapiste>/rest/api-docs/index.html`, missä `<lupapiste>`
voi olla joko tuotannon (www.lupapiste.fi) tai QAn
(www-qa.lupapiste.fi) palvelin. Autentikaatiomekanismina on basic-auth
ja käytännössä kutsujalla pitää olla kohdeorganisaation
viranomaisoikeudet.

## GET `/rest/verdict-messages`

Kutsu palauttaa kahdentyyppisiä sanomia: päätössanomia tai päätöksen
poistosanomia. Jälkimmäiset tarvitaan sen takia, jos päätös poistetaan
Lupapisteestä sen jälkeen, kun robotti on jo vastaavan päätössanoman noutanut.

Parametrit

| Nimi  | Pakollinen  | Tyyppi  | Kuvaus  |
|:--|:--|:--|:--|
| `organization`  | Kyllä  | `string` (esim. 091-R)  | Organisaation tunnus  |
| `from`  | Kyllä  | `YYYY-MM-dd` (esim. 2020-01-20) | Aikaisin mahdollinen päätöksen julkaisupäivä (sisältyy)  |
| `until`  | Ei   |  `YYYY-MM-dd` (esim. 2020-01-20) | Päätökset julkaistu tätä päivää ennen (ei sisälly).  |
| `all`  | Ei   | `boolean` | Oletuksena ei palauteta sanomia, jotka on kuitattu (ks. `/rest/ack-verdict-messages` alempana). Jos `all` on `true`, niin palautetaan kaikki aikaväliin osuvat sanomat.  |

Vastauksena tuleva JSON on taulukko, joka sisältää päätös- ja
poistosanomia.


### Päätössanoma
```clj
{(optional-key :avainsanat) [Str],
 (optional-key :lausunnot)
   [{:lausunnonantaja Str,
     :lausuntotieto (enum "kielteinen"
                          "lausunto" "ei-lausuntoa"
                          "poydalle" "puollettu"
                          "ehdollinen" "ei-huomautettavaa"
                          "palautettu" "ei-puollettu"),
     :pvm (constrained Str "Date string YYYY-MM-dd")}],
 (optional-key :menettely) Str,
 (optional-key :naapurit)
   [{:kiinteistotunnus (constrained Str "Finnish property id"),
     :kuultu Bool,
     :pvm (constrained Str "Date string YYYY-MM-dd")}],
 :asiointitunnus (pred "Application ID"),
 :kiinteistotunnus (constrained Str "Finnish property id"),
 :osoite Str,
 :paatos
   {(optional-key :aloittamisoikeusVakuudella)
      {(optional-key :laji) Str,
       (optional-key :pvm) (constrained Str "Date string YYYY-MM-dd"),
       (optional-key :summa) Str},
    (optional-key :hankkeenVaativuus)
      {(optional-key :selite) Str,
       (optional-key :vaativuus) (enum "tavanomainen" "vaativa"
                                       "poikkeuksellisen vaativa"
                                         "vähäinen")},
    (optional-key :kaavanKayttotarkoitus) Str,
    (optional-key :kasittelija)
      {(optional-key :nimi) (constrained Str "Non-blank string"),
       (optional-key :nimike) (constrained Str "Non-blank string")},
    (optional-key :korvaaPaatoksen) (constrained Str
                                                 "Non-blank string"),
    (optional-key :lisaselvitykset) Str,
    (optional-key :muutLupaehdot) [Str],
    (optional-key :naapurienKuuleminen) Str,
    (optional-key :osoite) Str,
    (optional-key :paatoksentekija)
      {(optional-key :nimi) (constrained Str "Non-blank string"),
       (optional-key :nimike) (constrained Str "Non-blank string")},
    (optional-key :paatosteksti) Str,
    (optional-key :perustelut) Str,
    (optional-key :poikkeamiset) Str,
    (optional-key :pykala) Str,
    (optional-key :rakennukset)
      [{(optional-key :autopaikatYhteensa) Str,
        (optional-key :kiinteistonAutopaikat) Str,
        (optional-key :paloluokka) Str,
        (optional-key :rakennetutAutopaikat) Str,
        (optional-key :selite) Str,
        (optional-key :tunniste) Str,
        (optional-key :tunnus) (constrained Str
                                            "Finnish building id"),
        (optional-key :vssLuokka) Str}],
    (optional-key :rakennushanke) Str,
    (optional-key :rakennusoikeus) Str,
    (optional-key :sovelletutOikeusohjeet) Str,
    (optional-key :toimenpidetekstiJulkipanoon) Str,
    (optional-key :vaaditutErityissuunnitelmat) [Str],
    (optional-key :vaaditutKatselmukset)
      [{:laji (enum
                "pohjakatselmus"
                "loppukatselmus" "aloituskokous"
                "muu tarkastus" "rakennuksen paikan tarkastaminen"
                "ei tiedossa" "rakennuksen paikan merkitseminen"
                "muu katselmus" "osittainen loppukatselmus"
                "rakennekatselmus"
                  "lämpö-, vesi- ja ilmanvaihtolaitteiden katselmus"),
        :nimi Str,
        :tunnus Str}],
    (optional-key :vaaditutTyonjohtajat)
      [(enum "työnjohtaja"
             "erityisalojen työnjohtaja" "KVV-työnjohtaja"
             "IV-työnjohtaja" "vastaava työnjohtaja")],
    :kieli (enum "en" "fi" "sv"),
    :paatostieto
      (enum
        "suunnitelmat tarkastettu" "tehty uhkasakkopäätös"
        "tehty hallintopakkopäätös (ei velvoitetta)"
          "pysytti myönnettynä"
        "valituksesta on luovuttu (oikaisuvaatimus tai lupa pysyy puollettuna)"
          "siirretty maaoikeudelle"
        "myönnetty aloitusoikeudella" "osittain myönnetty"
        "työhön liittyy ehto" "hyväksytty"
        "asia palautettu uudelleen valmisteltavaksi" "ei tiedossa"
        "hallintopakon tai uhkasakkoasian käsittely lopetettu"
          "asia poistettu esityslistalta"
        "muutettu toimenpideluvaksi (konversio)"
          "asia pantu pöydälle kokouksessa"
        "ei lausuntoa" "ei puollettu"
        "muutti myönnetyksi" "myönnetty"
        "puollettu" "ehdollinen"
        "asiakirjat palautettu korjauskehotuksin"
          "pysytti määräyksen tai päätöksen"
        "pysytti osittain myönnettynä" "annettu lausunto"
        "valituksesta on luovuttu (oikaisuvaatimus tai lupa pysyy evättynä)"
          "määräys peruutettu"
        "ei tutkittu" "evätty"
        "ei tutkittu (oikaisuvaatimusvaatimus tai lupa pysyy puollettuna)"
          "ilmoitus merkitty tiedoksi"
        "muutti evätyksi" "pysytti evättynä"
        "tehty hallintopakkopäätös (asetettu velvoite)" "peruutettu"
        "muutti määräystä tai päätöstä"
          "ei tutkittu (oikaisuvaatimus tai lupa pysyy evättynä)"),
    :paatostyyppi (enum "lautakunta" "viranhaltija"),
    :paivamaarat {(optional-key :aloitettavaPvm)
                    (constrained Str "Date string YYYY-MM-dd"),
                  (optional-key :antoPvm)
                    (constrained Str "Date string YYYY-MM-dd"),
                  (optional-key :julkipanoPvm)
                    (constrained Str "Date string YYYY-MM-dd"),
                  (optional-key :lainvoimainenPvm)
                    (constrained Str "Date string YYYY-MM-dd"),
                  (optional-key :muutoksenhakuPvm)
                    (constrained Str "Date string YYYY-MM-dd"),
                  (optional-key :voimassaPvm)
                    (constrained Str "Date string YYYY-MM-dd"),
                  :paatosPvm (constrained Str
                                          "Date string YYYY-MM-dd")},
    :tunnus (constrained Str "Non-blank string")},
 :sanomatunnus (constrained Str "Non-blank string")
 :versio (eq 2)}
```

### Poistosanoma

```clj
{:asiointitunnus (pred "Application ID"),
 :kiinteistotunnus (constrained Str "Finnish property id"),
 :osoite Str,
 :poistettuPaatos (constrained Str "Non-blank string"),
 :sanomatunnus (constrained Str "Non-blank string")
 :versio (eq 2)}
```

## POST `/rest/ack-verdict-messages`

Sanomien kuittaaminen luetuksi perustuu (päätös- ja poistosanomien)
`sanomatunnus`-kenttiin, jotka ovat sanomakohtaisia ja
uniikkeja. Kuittausparametrit välitetään kutsun JSON-bodyssa:

| Nimi  | Pakollinen  | Tyyppi  | Kuvaus  |
|:--|:--|:--|:--|
| `organization`  | Kyllä  | `string` (esim. 091-R)  | Organisaation tunnus  |
| `ids`  | Kyllä  | `[string]` | Taulukko sanomatunnuksia |


## GET `/rest/operation-locations`

Kutsu palauttaa listan hankkeita ja niiden toimenpiteiden sijainteja
EPSG:3067-koordinaatistossa (eli ETRS89 eli TM35FIN). Parametreina
annettuja ajankohtia verrataan sijaintien
muokkausaikoihin. Muokkauksella tarkoitetaan tässä nimenomaan
käyttäjän käsin tekemää muokkausta. Hanke valikoituu mukaan, jos mikä
tahansa sen toimenpiteistä "osuu". Vastauksessa on mukana hankkeen
kaikkien sellaisten toimenpiteiden sijainnit, joita ei ole vielä kuitattu.

Parametrit

| Nimi  | Pakollinen  | Tyyppi  | Kuvaus  |
|:--|:--|:--|:--|
| `organization`  | Kyllä  | `string` (esim. 091-R)  | Organisaation tunnus  |
| `from`  | Kyllä  | `YYYY-MM-dd` (esim. 2020-01-20) | Aikaisin mahdollinen muokkauspäivä (sisältyy)  |
| `until`  | Ei   |  `YYYY-MM-dd` (esim. 2020-01-20) | Sijaintia muokattu julkaistu tätä päivää ennen (ei sisälly).  |
| `all`  | Ei   | `boolean` | Oletuksena ei palauteta sanomia, jotka on kuitattu (ks. `/rest/ack-operation-locations-messages` alempana). Jos `all` on `true`, niin palautetaan kaikki aikaväliin osuvien hankkeiden sijainnit riippumatta siitä onko ne jo kuitattu tai ei. |

### Sijaintisanoma

```clj
{:data
   [{:application-id (pred "Application ID"),
     :operations
       [{(optional-key :building-id) (constrained Str
                                                  "Non-blank string"),
         (optional-key :description) (constrained Str
                                                  "Non-blank string"),
         (optional-key :location)
           [(one (constrained Num "Invalid x coordinate") "X")
            (one (constrained Num "Invalid y coordinate") "Y")],
         (optional-key :tag) (constrained Str "Non-blank string"),
         :id (constrained Str "Non-blank string"),
         :operation (constrained Str "Non-blank string")}]}],
 :message-id (constrained Str "Non-blank string")}
```

Esimerkkisanoma, jossa yksi hanke:

```clj
{:message-id "5e68eddeda8e6b57a05951c1"
 :data       [{:application-id "LP-753-2020-90001"
               :operations     [{:building-id "199887766E"  ;; VTJ-PRT
                                 :description "Päärakennus"
                                 :location    [404439.054, 6693877.207]
                                 :tag         "A"
                                 ;; Lupapisteen sisäinen toimenpide-id
                                 :id          "5e68eddcda8e6b57a05951b8"
                                 ;; Toimenpidetyypin tunniste
                                 :operation   "pientalo"}]}]}
```

## POST `/rest/ack-operation-locations-message`

Sanomien kuittaaminen luetuksi ja käsitellyksi perustuu sanoman
`message-id` -kenttään. Kun sanoma on kuitattu, sen sisältämiä
sijainnit lähetetään uudestaan vain jos ne ovat muuttuneet.

Kuittausparametrit välitetään kutsun JSON-bodyssa:

| Nimi  | Pakollinen  | Tyyppi  | Kuvaus  |
|:--|:--|:--|:--|
| `organization`  | Kyllä  | `string` (esim. 091-R)  | Organisaation tunnus  |
| `message-id`  | Kyllä  | `string` | Sanoman id |
