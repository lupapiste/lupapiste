(ns lupapalvelu.document.schemas)

(defn to-map-by-name
  "Take list of schema maps, return a map of schemas keyed by :name under :info"
  [docs]
  (reduce (fn [docs doc] (assoc docs (get-in doc [:info :name]) doc))
          {}
          docs))

(def simple-osoite-body [{:name "katu" :type :string}
                         {:name "postinumero" :type :string :size "s"}
                         {:name "postitoimipaikka" :type :string :size "m"}])

(def full-osoite-body [{:name "kunta" :type :string}
                       {:name "lahiosoite" :type :string}
                       {:name "osoitenumero" :type :string}
                       {:name "osoitenumero2" :type :string}
                       {:name "jakokirjain" :type :string :size "s"}
                       {:name "jakokirjain2" :type :string :size "s"}
                       {:name "porras" :type :string :size "s"}
                       {:name "huoneisto" :type :string :size "s"}
                       {:name "postinumero" :type :string :size "s"}
                       {:name "postitoimipaikka" :type :string :size "m"}
                       {:name "pistesijanti" :type :string}])

(def yhteystiedot-body [{:name "puhelin" :type :string :subtype :tel}
                        {:name "email" :type :string :subtype :email}
                        {:name "fax" :type :string :subtype :tel}])

(def henkilotiedot-body [{:name "etunimi" :type :string}
                         {:name "sukunimi" :type :string}
                         {:name "hetu" :type :string}])

(def henkilo-body [{:name "henkilotiedot" :type :group :body henkilotiedot-body}
                   {:name "osoite" :type :group :body simple-osoite-body}
                   {:name "yhteystiedot" :type :group :body yhteystiedot-body}])

(def yritys-body [{:name "yritysnimi" :type :string}
                   {:name "liikeJaYhteisoTunnus" :type :string}
                   {:name "osoite" :type :group :body simple-osoite-body}
                   {:name "yhteystiedot" :type :group :body yhteystiedot-body}])

(def party-body [{:name "henkilo" :type :group :body henkilo-body}
                 {:name "yritys" :type :group :body yritys-body}])

(def patevyys [{:name "koulutus" :type :string}
               {:name "patevyysluokka" :type :select
                :body [{:name "AA"}
                       {:name "A"}
                       {:name "B"}
                       {:name "C"}
                       {:name "ei tiedossa"}
                       ]}])

(def paasuunnittelija-body (conj
                         party-body
                         {:name "patevyys" :type :group :body patevyys}))

(def suunnittelija-body (conj
                         party-body
                         {:name "patevyys" :type :group
                          :body
                          (cons {:name "kuntaRoolikoodi" :type :select
                                  :body [{:name "GEO-suunnittelija"}
                                         {:name "LVI-suunnittelija"}
                                         {:name "RAK-rakennesuunnittelija"}
                                         {:name "ARK-rakennussuunnittelija"}
                                         {:name "ei tiedossa"}]
                                  } patevyys)
                            })) ; TODO miten liitteet hanskataan

(def schemas
  (to-map-by-name
    [{:info {:name "uusiRakennus"}
      :body [
             {:name "rakennuksenOmistajat"
              :type :group
              :body party-body}
             {:name "kuvaus" 
              :type :group
              :body [{:name "kuvaus" :type :string :size "l"}
                     {:name "poikkeamiset" :type :string :size "l"}]}
             {:name "kaytto" 
              :type :group
              :body [{:name "rakentajaTyyppi" :type "select"
                      :body [{:name "liiketaloudellinen"}
                             {:name "muu"}
                             {:name "ei tiedossa"}]}
                     {:name "kayttotarkoitus" :type :select
                      :body [{:name "999 muualla luokittelemattomat rakennukset"}
                             {:name "941 talousrakennukset"}
                             {:name "931 saunarakennukset"}
                             {:name "899 muut maa-, metsä- ja kalatalouden rakennukset"}
                             {:name "893 turkistarhat"}
                             {:name "892 kasvihuoneet"}
                             {:name "891 viljankuivaamot ja viljan säilytysrakennukset"}
                             {:name "819 eläinsuojat, ravihevostallit, maneesit yms"}
                             {:name "811 navetat, sikalat, kanalat yms"}
                             {:name "729 muut palo- ja pelastustoimen rakennukset"}
                             {:name "722 väestönsuojat"}
                             {:name "721 paloasemat"}
                             {:name "719 muut varastorakennukset"}
                             {:name "712 kauppavarastot"}
                             {:name "711 teollisuusvarastot"}
                             {:name "699 muut teollisuuden tuotantorakennukset"}
                             {:name "692 teollisuus- ja pienteollisuustalot"}
                             {:name "691 teollisuushallit"}
                             {:name "613 yhdyskuntatekniikan rakennukset"}
                             {:name "611 voimalaitosrakennukset"}
                             {:name "549 muualla luokittelemattomat opetusrakennukset"}
                             {:name "541 järjestöjen, liittojen, työnantajien yms opetusrakennukset"}
                             {:name "532 tutkimuslaitosrakennukset"}
                             {:name "531 korkeakoulurakennukset"}
                             {:name "521 ammatillisten oppilaitosten rakennukset"}
                             {:name "511 yleissivistävien oppilaitosten rakennukset"}
                             {:name "369 muut kokoontumisrakennukset"}
                             {:name "359 muut urheilu- ja kuntoilurakennukset"}
                             {:name "354 monitoimihallit ja muut urheiluhallit"}
                             {:name "353 tennis-, squash- ja sulkapallohallit"}
                             {:name "352 uimahallit"}
                             {:name "351 jäähallit"}
                             {:name "349 muut uskonnollisten yhteisöjen rakennukset"}
                             {:name "342 seurakuntatalot"}
                             {:name "341 kirkot, kappelit, luostarit ja rukoushuoneet"}
                             {:name "331 seura- ja kerhorakennukset yms"}
                             {:name "324 näyttelyhallit"}
                             {:name "323 museot ja taidegalleriat"}
                             {:name "322 kirjastot ja arkistot"}
                             {:name "312 elokuvateatterit"}
                             {:name "311 teatterit, ooppera-, konsertti- ja kongressitalot"}
                             {:name "241 vankilat"}
                             {:name "239 muualla luokittelemattomat sosiaalitoimen rakennukset"}
                             {:name "231 lasten päiväkodit"}
                             {:name "229 muut huoltolaitosrakennukset"}
                             {:name "223 kehitysvammaisten hoitolaitokset"}
                             {:name "222 lasten- ja koulukodit"}
                             {:name "221 vanhainkodit"}
                             {:name "219 muut terveydenhuoltorakennukset"}
                             {:name "215 terveydenhuollon erityislaitokset"}
                             {:name "214 terveyskeskukset"}
                             {:name "213 muut sairaalat"}
                             {:name "211 keskussairaalat"}
                             {:name "169 muut liikenteen rakennukset"}
                             {:name "164 tietoliikenteen rakennukset"}
                             {:name "163 pysäköintitalot"}
                             {:name "162 kulkuneuvojen suoja- ja huoltorakennukset"}
                             {:name "161 rautatie- ja linja-autoasemat, lento- ja satamaterminaalit"}
                             {:name "151 toimistorakennukset"}
                             {:name "141 ravintolat yms"}
                             {:name "139 muut asuntolarakennukset"}
                             {:name "131 asuntolat yms"}
                             {:name "129 muut majoitusliikerakennukset"}
                             {:name "124 vuokrattavat lomamökit ja -osakkeet"}
                             {:name "123 loma-, lepo- ja virkistyskodit"}
                             {:name "121 hotellit yms"}
                             {:name "119 muut myymälärakennukset"}
                             {:name "112 liike- ja tavaratalot, kauppakeskukset"}
                             {:name "111 myymälähallit"}
                             {:name "041 vapaa-ajan asuinrakennukset"}
                             {:name "039 muut asuinkerrostalot"}
                             {:name "032 luhtitalot"}
                             {:name "022 ketjutalot"}
                             {:name "021 rivitalot"}
                             {:name "013 muut erilliset talot"}
                             {:name "012 kahden asunnon talot"}
                             {:name "011 yhden asunnon talot"}
                             {:name "ei tiedossa"}]}]}
             {:name "mitat" 
              :type :group
              :body [{:name "tilavuus" :type :string :size "s" :unit "m3" :subtype :number}
                     {:name "kokonaisala" :type :string :size "s" :unit "m2" :subtype :number}
                     {:name "kellarinpinta-ala" :type :string :size "s" :unit "m2" :subtype :number}
                     {:name "kerrosluku" :type :string :size "s" :subtype :number}
                     {:name "kerrosala" :type :string :size "s" :unit "m2" :subtype :number}]}
             {:name "rakenne" 
              :type :group
              :body [{:name "rakentamistapa" :type :select
                      :body [{:name "elementti" :type :checkbox}
                             {:name "paikalla" :type :checkbox}
                             {:name "ei tiedossa" :type :checkbox}]}
                     {:name "kantavaRakennusaine" :type :select
                      :body [{:name "betoni" :type :checkbox}
                             {:name "tiili" :type :checkbox}
                             {:name "teras" :type :checkbox}
                             {:name "puu" :type :checkbox}
                             {:name "muurakennusaine" :type :string :size "s"}
                             {:name "ei tiedossa" :type :checkbox}]}
                     {:name "julkisivu" :type :select
                      :body [{:name "betoni" :type :checkbox}
                             {:name "tiili" :type :checkbox}
                             {:name "metallilevy" :type :checkbox}
                             {:name "kivi" :type :checkbox}
                             {:name "puu" :type :checkbox}
                             {:name "lasi" :type :checkbox}
                             {:name "muumateriaali" :type :string :size "s"}
                             {:name "ei tiedossa" :type :checkbox}]}]}
             {:name "lammitys" 
              :type :group
              :body [{:name "lammitystapa" :type :select
                      :body [{:name "vesikeskus" :type :checkbox}
                             {:name "ilmakeskus" :type :checkbox}
                             {:name "suorasahko" :type :checkbox}
                             {:name "uuni" :type :checkbox}
                             {:name "eiLammitysta" :type :checkbox}
                             {:name "ei tiedossa" :type :checkbox}]}
                     {:name "lammonlahde" :type :select
                      :body [{:name "kauko tai aluel\u00e4mp\u00f6" :type :checkbox}
                             {:name "kevyt poltto\u00f6ljy" :type :checkbox}
                             {:name "raskas poltto\u00f6ljy" :type :checkbox}
                             {:name "s\u00e4hk\u00f6" :type :checkbox}
                             {:name "kaasu" :type :checkbox}
                             {:name "kiviihiili koksi tms." :type :checkbox}
                             {:name "turve" :type :checkbox}
                             {:name "maal\u00e4mp\u00f6" :type :checkbox}
                             {:name "puu" :type :checkbox}
                             {:name "muu" :type :string :size "s"}
                             {:name "ei tiedossa" :type :checkbox}]}]}
             {:name "verkostoliittymat" :type :choice
                      :body [{:name "viemariKytkin" :type :checkbox}
                             {:name "vesijohtoKytkin" :type :checkbox}
                             {:name "sahkoKytkin" :type :checkbox}
                             {:name "maakaasuKytkin" :type :checkbox}
                             {:name "kaapeliKytkin" :type :checkbox}]}
             {:name "varusteet" :type :choice
              :body [{:name "sahkoKytkin" :type :checkbox}
                     {:name "kaasuKytkin" :type :checkbox}
                     {:name "viemariKytkin" :type :checkbox}
                     {:name "vesijohtoKytkin" :type :checkbox}
                     {:name "hissiKytkin" :type :checkbox}
                     {:name "koneellinenilmastointiKytkin" :type :checkbox}
                     {:name "lamminvesiKytkin" :type :checkbox}
                     {:name "aurinkopaneeliKytkin" :type :checkbox}
                     {:name "saunoja" :type :string :subtype :number}
                     {:name "vaestonsuoja" :type :string :subtype :number}]}
             {:name "luokitus" 
              :type :group
              :body [{:name "energialuokka" :type :string :size "s"}
                     {:name "paloluokka" :type :string :size "s"}]}]}

     {:info {:name "huoneisto"}
      :body [{:name "huoneistoTunnus" :type :group
              :body [{:name "porras" :type :string :subtype :letter :max-len 1 :size "s"}
                     {:name "huoneistonumero" :type :string :subtype :number :min-len 1 :max-len 3 :size "s"}
                     {:name "jakokirjain" :type :string :subtype :letter :max-len 1 :size "s"}]}
             {:name "huoneistonTyyppi" 
              :type :group
              :body [{:name "huoneistoTyyppi" :type :select
                      :body [{:name "asuinhuoneisto"}
                             {:name "toimitila"}
                             {:name "ei tiedossa"}]}
                      {:name "huoneistoala" :type :string :unit "m2" :subtype :number :size "s"}
                      {:name "huoneluku" :type :string :subtype :number :size "m"}]}
             {:name "keittionTyyppi" :type :select
              :body [{:name "keittio"}
                     {:name "keittokomero"}
                     {:name "keittotila"}
                     {:name "tupakeittio"}
                     {:name "ei tiedossa"}]}
             {:name "varusteet" :type :choice
              :body [{:name "wc" :type :checkbox}
                     {:name "ammeTaiSuihku" :type :checkbox}
                     {:name "sauna" :type :checkbox}
                     {:name "parvekeTaiTerassi" :type :checkbox}
                     {:name "lamminvesi" :type :checkbox}]}]}

     {:info {:name "hakija"}
      :body party-body}

     {:info {:name "paasuunnittelija"}
      :body paasuunnittelija-body}

     {:info {:name "suunnittelija"}
      :body suunnittelija-body}

     {:info {:name "maksaja"}
      :body party-body}

     {:info {:name "rakennuspaikka"} ; TODO sijainti(kios?/ jo kartalta osositettu)
      :body [{:name "huoneistonTyyppi" 
              :type :group
              :body[{:name "kiinteistotunnus" :type :string :subtype :kiinteistotunnus}
                    {:name "kokotilaKytkin" :type :checkbox}
                    {:name "maaraalaTunnus" :type :string}]}
             {:name "kylaNimi" :type :string}
             {:name "tilanNimi" :type :string}
             {:name "hallintaperuste" :type :select
              :body [{:name "oma"}
                     {:name "vuokra"}
                     {:name "ei tiedossa"}]}
             {:name "kaavanaste" :type "select"
              :body [{:name "asema"}
                     {:name "ranta"}
                     {:name "rakennus"}
                     {:name "yleis"}
                     {:name "eiKaavaa"}
                     {:name "ei tiedossa"}]}]}

     {:info {:name "osoite"}
      :body full-osoite-body}

     {:info {:name "lisatiedot"}
      :body [{:name "suoramarkkinointikielto" :type :checkbox}]}]))
