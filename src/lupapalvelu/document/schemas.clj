(ns lupapalvelu.document.schemas)

;;
;; helpers
;;

(defn body
  "shallow merges stuff into vector"
  [& rest]
  (reduce
    (fn [a x]
      (let [v (if (sequential? x) x (vector x))]
        (concat a v)))
    [] rest))

(defn to-map-by-name
  "Take list of schema maps, return a map of schemas keyed by :name under :info"
  [docs]
  (reduce (fn [docs doc] (assoc docs (get-in doc [:info :name]) doc)) {} docs))

;;
;; schema sniplets
;;

(def henkilo-valitsin [{:name :userId :type :personSelector }
                        {:name "turvakieltoKytkin" :type :checkbox}])

(def rakennuksen-valitsin [{:name :rakennusnro :type :buildingSelector}])

(def simple-osoite [{:name "osoite"
                     :type :group
                     :body [{:name "katu" :type :string}
                            {:name "postinumero" :type :string :size "s"}
                            {:name "postitoimipaikannimi" :type :string :size "m"}]}])

(def full-osoite [{:name "osoite"
                   :type :group
                   :body [{:name "kunta" :type :string}
                          {:name "lahiosoite" :type :string}
                          {:name "osoitenumero" :type :string}
                          {:name "osoitenumero2" :type :string}
                          {:name "jakokirjain" :type :string :size "s"}
                          {:name "jakokirjain2" :type :string :size "s"}
                          {:name "porras" :type :string :size "s"}
                          {:name "huoneisto" :type :string :size "s"}
                          {:name "postinumero" :type :string :size "s"}
                          {:name "postitoimipaikannimi" :type :string :size "m"}
                          {:name "pistesijanti" :type :string}]}])

(def yhteystiedot [{:name "yhteystiedot"
                    :type :group
                    :body [{:name "puhelin" :type :string :subtype :tel}
                           {:name "email" :type :string :subtype :email}
                           {:name "fax" :type :string :subtype :tel}]}])

(def henkilotiedot-minimal [{:name "henkilotiedot"
                             :type :group
                             :body [{:name "etunimi" :type :string}
                                    {:name "sukunimi" :type :string}]}])

(def henkilotiedot-with-sotu [{:name "henkilotiedot"
                               :type :group
                               :body [{:name "etunimi" :type :string}
                                      {:name "sukunimi" :type :string}
                                      {:name "hetu" :type :string}]}])

(def henkilo (body
               henkilo-valitsin
               henkilotiedot-with-sotu
               simple-osoite
               yhteystiedot))

(def yritys-minimal [{:name "yritysnimi" :type :string}
                     {:name "liikeJaYhteisoTunnus" :type :string}])

(def yritys (body
              yritys-minimal
              simple-osoite
              {:name "yhteyshenkilo"
               :type :group
               :body (body
                       henkilotiedot-minimal
                       yhteystiedot)}))

(def party [{:name "_selected" :type :radioGroup :body [{:name "henkilo"} {:name "yritys"}]}
            {:name "henkilo" :type :group :body henkilo}
            {:name "yritys" :type :group :body yritys}])

(def patevyys [{:name "koulutus" :type :string}
               {:name "patevyysluokka" :type :select
                :body [{:name "AA"}
                       {:name "A"}
                       {:name "B"}
                       {:name "C"}
                       {:name "ei tiedossa"}]}])

(def designer-basic (body
                      henkilotiedot-minimal
                      {:name "yritys" :type :group :body yritys-minimal}
                      simple-osoite
                      yhteystiedot))

(def paasuunnittelija (body
                        henkilo-valitsin
                        designer-basic
                        {:name "patevyys" :type :group :body patevyys}))

(def kuntaroolikoodi [{:name "kuntaRoolikoodi" :type :select
                       :body [{:name "GEO-suunnittelija"}
                              {:name "LVI-suunnittelija"}
                              {:name "IV-suunnittelija"}
                              {:name "KVV-suunnittelija"}
                              {:name "RAK-rakennesuunnittelija"}
                              {:name "ARK-rakennussuunnittelija"}
                              {:name "Vaikeiden t\u00F6iden suunnittelija"}
                              {:name "ei tiedossa"}]}])

(def suunnittelija (body
                     henkilo-valitsin
                     designer-basic
                     {:name "patevyys" :type :group
                      :body (body
                              kuntaroolikoodi
                              patevyys)
                      }))

(def huoneisto [{:name "huoneistoTunnus" :type :group
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
                        {:name "huoneluku" :type :string :size "m"}]}
                {:name "keittionTyyppi" :type :select
                 :body [{:name "keittio"}
                        {:name "keittokomero"}
                        {:name "keittotila"}
                        {:name "tupakeittio"}
                        {:name "ei tiedossa"}]}
                {:name "varusteet"
                 :type :group
                 :layout :vertical
                 :body [{:name "WCKytkin" :type :checkbox}
                        {:name "ammeTaiSuihkuKytkin" :type :checkbox}
                        {:name "saunaKytkin" :type :checkbox}
                        {:name "parvekeTaiTerassiKytkin" :type :checkbox}
                        {:name "lamminvesiKytkin" :type :checkbox}]}])

(def yhden-asunnon-talot "011 yhden asunnon talot")
(def vapaa-ajan-asuinrakennus "041 vapaa-ajan asuinrakennukset")
(def talousrakennus "941 talousrakennukset")
(def rakennuksen-tiedot [{:name "kaytto"
                          :type :group
                          :body [{:name "rakentajaTyyppi" :type "select"
                                  :body [{:name "liiketaloudellinen"}
                                         {:name "muu"}
                                         {:name "ei tiedossa"}]}
                                 {:name "kayttotarkoitus" :type :select
                                  :body [{:name "999 muualla luokittelemattomat rakennukset"}
                                         {:name talousrakennus}
                                         {:name "931 saunarakennukset"}
                                         {:name "899 muut maa-, mets\u00e4- ja kalatalouden rakennukset"}
                                         {:name "893 turkistarhat"}
                                         {:name "892 kasvihuoneet"}
                                         {:name "891 viljankuivaamot ja viljan s\u00e4ilytysrakennukset"}
                                         {:name "819 el\u00e4insuojat, ravihevostallit, maneesit yms"}
                                         {:name "811 navetat, sikalat, kanalat yms"}
                                         {:name "729 muut palo- ja pelastustoimen rakennukset"}
                                         {:name "722 v\u00e4est\u00f6nsuojat"}
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
                                         {:name "541 j\u00e4rjest\u00f6jen, liittojen, ty\u00f6nantajien yms opetusrakennukset"}
                                         {:name "532 tutkimuslaitosrakennukset"}
                                         {:name "531 korkeakoulurakennukset"}
                                         {:name "521 ammatillisten oppilaitosten rakennukset"}
                                         {:name "511 yleissivist\u00e4vien oppilaitosten rakennukset"}
                                         {:name "369 muut kokoontumisrakennukset"}
                                         {:name "359 muut urheilu- ja kuntoilurakennukset"}
                                         {:name "354 monitoimihallit ja muut urheiluhallit"}
                                         {:name "353 tennis-, squash- ja sulkapallohallit"}
                                         {:name "352 uimahallit"}
                                         {:name "351 j\u00e4\u00e4hallit"}
                                         {:name "349 muut uskonnollisten yhteis\u00f6jen rakennukset"}
                                         {:name "342 seurakuntatalot"}
                                         {:name "341 kirkot, kappelit, luostarit ja rukoushuoneet"}
                                         {:name "331 seura- ja kerhorakennukset yms"}
                                         {:name "324 n\u00e4yttelyhallit"}
                                         {:name "323 museot ja taidegalleriat"}
                                         {:name "322 kirjastot ja arkistot"}
                                         {:name "312 elokuvateatterit"}
                                         {:name "311 teatterit, ooppera-, konsertti- ja kongressitalot"}
                                         {:name "241 vankilat"}
                                         {:name "239 muualla luokittelemattomat sosiaalitoimen rakennukset"}
                                         {:name "231 lasten p\u00e4iv\u00e4kodit"}
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
                                         {:name "163 pys\u00e4k\u00f6intitalot"}
                                         {:name "162 kulkuneuvojen suoja- ja huoltorakennukset"}
                                         {:name "161 rautatie- ja linja-autoasemat, lento- ja satamaterminaalit"}
                                         {:name "151 toimistorakennukset"}
                                         {:name "141 ravintolat yms"}
                                         {:name "139 muut asuntolarakennukset"}
                                         {:name "131 asuntolat yms"}
                                         {:name "129 muut majoitusliikerakennukset"}
                                         {:name "124 vuokrattavat lomam\u00f6kit ja -osakkeet"}
                                         {:name "123 loma-, lepo- ja virkistyskodit"}
                                         {:name "121 hotellit yms"}
                                         {:name "119 muut myym\u00e4l\u00e4rakennukset"}
                                         {:name "112 liike- ja tavaratalot, kauppakeskukset"}
                                         {:name "111 myym\u00e4l\u00e4hallit"}
                                         {:name vapaa-ajan-asuinrakennus}
                                         {:name "039 muut asuinkerrostalot"}
                                         {:name "032 luhtitalot"}
                                         {:name "022 ketjutalot"}
                                         {:name "021 rivitalot"}
                                         {:name "013 muut erilliset talot"}
                                         {:name "012 kahden asunnon talot"}
                                         {:name yhden-asunnon-talot}
                                         {:name "ei tiedossa"}]}]}
                         {:name "mitat"
                          :type :group
                          :body [{:name "tilavuus" :type :string :size "s" :unit "m3" :subtype :number}
                                 {:name "kokonaisala" :type :string :size "s" :unit "m2" :subtype :number}
                                 {:name "kellarinpinta-ala" :type :string :size "s" :unit "m2" :subtype :number}
                                 {:name "kerrosluku" :type :string :size "s"}
                                 {:name "kerrosala" :type :string :size "s" :unit "m2" :subtype :number}]}
                         {:name "rakenne"
                          :type :group
                          :body [{:name "rakentamistapa" :type :select
                                  :body [{:name "elementti"}
                                         {:name "paikalla"}
                                         {:name "ei tiedossa"}]}
                                 {:name "kantavaRakennusaine" :type :select
                                  :body [{:name "betoni"}
                                         {:name "tiili"}
                                         {:name "teras"}
                                         {:name "puu"}
                                         {:name "muurakennusaine" :type :string :size "s"}
                                         {:name "ei tiedossa"}]}
                                 {:name "julkisivu" :type :select
                                  :body [{:name "betoni"}
                                         {:name "tiili"}
                                         {:name "metallilevy"}
                                         {:name "kivi"}
                                         {:name "puu"}
                                         {:name "lasi"}
                                         {:name "muumateriaali" :type :string :size "s"}
                                         {:name "ei tiedossa"}]}]}
                         {:name "lammitys"
                          :type :group
                          :body [{:name "lammitystapa" :type :select
                                  :body [{:name "vesikeskus"}
                                         {:name "ilmakeskus"}
                                         {:name "suorasahko"}
                                         {:name "uuni"}
                                         {:name "eiLammitysta"}
                                         {:name "ei tiedossa"}]}
                                 {:name "lammonlahde" :type :select
                                  :body [{:name "kauko tai aluel\u00e4mp\u00f6"}
                                         {:name "kevyt poltto\u00f6ljy"}
                                         {:name "raskas poltto\u00f6ljy"}
                                         {:name "s\u00e4hk\u00f6"}
                                         {:name "kaasu"}
                                         {:name "kiviihiili koksi tms"}
                                         {:name "turve"}
                                         {:name "maal\u00e4mp\u00f6"}
                                         {:name "puu"}
                                         {:name "muu" :type :string :size "s"} ;TODO tukii tekstille
                                         {:name "ei tiedossa"}]}]}
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
                          :body [{:name "energialuokka" :type :select
                                  :body [{:name "A"}
                                         {:name "B"}
                                         {:name "C"}
                                         {:name "D"}
                                         {:name "E"}
                                         {:name "F"}
                                         {:name "G"}]}
                                 {:name "energiatehokkuusluku" :type :number}
                                 {:name "energiatehokkuusluvunYksikko" :type :select
                                  :body [{:name "kWh/m2"}
                                         {:name "kWh/brm2/vuosi"}]}
                                 {:name "paloluokka"
                                  :body [{:name "palonkest\u00e4v\u00e4"}
                                          {:name "paloapid\u00e4tt\u00e4v\u00e4"}
                                          {:name "paloahidastava"}
                                          {:name "l\u00e4hinn\u00e4 paloakest\u00e4v\u00e4"}
                                          {:name "l\u00e4hinn\u00e4 paloapid\u00e4tt\u00e4v\u00e4"}
                                          {:name "l\u00e4hinn\u00e4 paloahidastava"}
                                          {:name "P1"}
                                          {:name "P2"}
                                          {:name "P3"}
                                          {:name "P1/P2"}
                                          {:name "P1/P3"}
                                          {:name "P2/P3"}
                                          {:name "P1/P2/P3"}]}]}
                         {:name "huoneistot"
                          :type :group
                          :repeating true
                          :body huoneisto}])


(def rakennelma (body {:name "kuvaus" :type :text}))
(def maisematyo (body {:name "kuvaus" :type :text}))

(def rakennuksen-omistajat [{:name "rakennuksenOmistajat"
                             :type :group :repeating true
                             :body party}])

(def muumuutostyo "muut muutosty\u00f6t")
(def kayttotarkotuksen-muutos "rakennukse p\u00e4\u00e4asiallinen k\u00e4ytt\u00f6tarkoitusmuutos")

(def muutostyonlaji [{:name "perusparannuskytkin" :type :checkbox}
                     {:name "muutostyolaji" :type :select
                      :body
                      [{:name "perustusten ja kantavien rakenteiden muutos- ja korjausty\u00f6t"}
                       {:name kayttotarkotuksen-muutos}
                       {:name muumuutostyo}]}])

(def olemassaoleva-rakennus (body
                              rakennuksen-valitsin
                              rakennuksen-omistajat
                              full-osoite
                              rakennuksen-tiedot))

(def rakennuksen-muuttaminen (body
                               muutostyonlaji
                               olemassaoleva-rakennus))

(def purku (body
             {:name "poistumanSyy" :type :select
              :body [{:name "purettu uudisrakentamisen vuoksi"}
                     {:name "purettu muusta syyst\u00e4"}
                     {:name "tuhoutunut"}
                     {:name "r\u00e4nsitymisen vuoksi hyl\u00e4tty"}
                     {:name "poistaminen"}]}
             {:name "poistumanAjankohta" :type :string}
             olemassaoleva-rakennus))

;;
;; schemas
;;

(def schemas
  (to-map-by-name
    [{:info {:name "hankkeen-kuvaus"}
      :body [{:name "kuvaus" :type :text}
             {:name "poikkeamat" :type :text}]}

     {:info {:name "uusiRakennus"}
      :body (concat rakennuksen-omistajat rakennuksen-tiedot)}

     {:info {:name "rakennuksen-muuttaminen"}
      :body rakennuksen-muuttaminen}

     {:info {:name "purku"}
      :body purku}

     {:info {:name "kaupunkikuvatoimenpide"}
      :body rakennelma}

     {:info {:name "maisematyo"}
      :body maisematyo}

     {:info {:name "hakija" :repeating true}
      :body party}

     {:info {:name "paasuunnittelija"}
      :body paasuunnittelija}

     {:info {:name "suunnittelija" :repeating true}
      :body suunnittelija}

     {:info {:name "maksaja" :repeating true}
      :body party}

     {:info {:name "rakennuspaikka"} ; TODO sijainti(kios?/ jo kartalta osoitettu)
      :body [{:name "kiinteisto"
              :type :group
              :body [{:name "maaraalaTunnus" :type :string}
                     {:name "kokotilaKytkin" :type :checkbox}
                     {:name "kylaNimi" :type :string}
                     {:name "tilanNimi" :type :string}]}
             {:name "hallintaperuste" :type :select
              :body [{:name "oma"}
                     {:name "vuokra"}
                     {:name "ei tiedossa"}]}
             {:name "kaavanaste" :type :select
              :body [{:name "asema"}
                     {:name "ranta"}
                     {:name "rakennus"}
                     {:name "yleis"}
                     {:name "eiKaavaa"}
                     {:name "ei tiedossa"}]}]}

       {:info {:name "lisatiedot"}
      :body [{:name "suoramarkkinointikielto" :type :checkbox}
             {:name "toimitustapa" :type :select
              :body [{:name "s\u00e4hk\u00f6isesti"}
                     {:name "noudetaan"}
                     {:name "postitse"}]}]}]))
