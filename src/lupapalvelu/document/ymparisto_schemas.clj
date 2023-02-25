(ns lupapalvelu.document.ymparisto-schemas
  (:require [lupapalvelu.document.schemas :refer :all]
            [lupapalvelu.document.tools :refer :all]
            [lupapalvelu.document.yleinen-ilmoitus-schemas :as yleinen-ilmoitus-schemas]
            [lupapalvelu.document.rekisterointi-ilmoitus-schemas :as rekisterointi-ilmoitus-schemas]))

(def kesto (body {:name "kesto" :type :table :repeating true :uicomponent :docgenTable
                  :body [{:name "alku" :type :date}
                         {:name "loppu" :type :date}
                         {:name "arkiAlkuAika" :type :time}
                         {:name "arkiLoppuAika" :type :time}
                         {:name "lauantaiAlkuAika" :type :time}
                         {:name "lauantaiLoppuAika" :type :time}
                         {:name "sunnuntaiAlkuAika" :type :time}
                         {:name "sunnuntaiLoppuAika" :type :time}]}))

(def kesto-mini (body {:name "kesto" :type :group
                       :body [{:name "alku" :type :date}
                              {:name "loppu" :type :date}]}))

(def maatila (body {:name "omistaja"
                    :type :group
                    :body [{:name "etunimi" :type :string}
                           {:name "sukunimi" :type :string}]}
                   {:name "tilatiedot"
                    :type :group
                    :body [{:name "tilatunnus" :type :string}]}
                   (update-in simple-osoite [0 :body] (fn [field] (map #(dissoc % :required) field)))
                   (update-in yhteystiedot [0 :body] (fn [field] (map #(dissoc % :required) field)))))

(def maatila-suppea (body {:name "etunimi" :type :string}
                          {:name "sukunimi" :type :string}
                          {:name "tilatunnus" :type :string}))

(def ymparistolupa (body {:name "lupaviranomainen" :type :string :size :l}
                         {:name "lupapaatostiedot" :type :string :size :l}
                         {:name "voimassaoloaika"
                          :type :group
                          :body [{:name "alku" :type :date}
                                 {:name "loppu" :type :date}]}))

(def meluilmoitus (body
                    {:name "rakentaminen" :type :group
                     :body [{:name "melua-aihettava-toiminta" :type :select :sortBy :displayname
                             :body [{:name "louhinta"}
                                    {:name "murskaus"}
                                    {:name "paalutus"}]}
                            {:name "muu-rakentaminen" :type :string :size :m}
                            {:name "kuvaus" :type :text :max-len 4000}
                            {:name "koneet" :type :text :max-len 4000}]}

                    {:name "tapahtuma" :type :group
                     :body [{:name "nimi" :type :string :size :m}
                            {:name "ulkoilmakonsertti" :type :checkbox}
                            {:name "kuvaus" :type :text :max-len 4000}] }
                    {:name "melu" :type :group
                     :body [{:name "melu10mdBa" :type :string :size :s}
                            {:name "paivalla" :type :string :size :s}
                            {:name "yolla" :type :string :size :s}
                            {:name "mittaus" :type :string :size :m}]}
                    {:name "leviaminen"
                     :type :table
                     :repeating true
                     :uicomponent :docgenTable
                     :group-help "meluilmoitus.leviaminen.groupHelpText"
                     :body [{:name "kohde" :type :string}
                            {:name "etaisyys" :type :string :subtype :number :unit :m :size :s}
                            {:name "vaikutus" :type :string :subtype :number :unit :db :size :s}]}
                    {:name "torjunta-ja-seuranta"
                     :type :group
                     :body [{:name "torjuntatoimenpiteet" :type :text :max-len 4000}
                            {:name "seuranta" :type :text :max-len 4000}]}
                    {:name "tiedottaminen"
                     :type :group
                     :body [{:name "talokohtainen" :type :checkbox}
                            {:name "huoneistokohtainen" :type :checkbox}
                            {:name "porraskaytavakohtainen" :type :checkbox}
                            {:name "laajuus" :type :text :max-len 1000}
                            {:name "osoitteet"
                             :type :table
                             :repeating true
                             :uicomponent :docgenTable
                             :body [{:name "katu" :type :string :size :l}
                                    {:name "numero" :type :string :size :m}]}]}
                    {:name "lisatiedot"
                     :type :group
                     :group-help "meluilmoitus.lisatiedot.groupHelpText"
                     :body [{:name "arviointimenetelmat" :type :text :max-len 4000}]}))

(def pima (body {:name "kuvaus" :type :text :max-len 4000}))

(def ottamismaara (body
                    {:name "kokonaismaara" :type :string :unit :m3 :size :m}
                    {:name "vuotuinenOtto" :type :string :unit :m3 :size :m}
                    {:name "ottamisaika" :type :string :unit :y :size :m}))

(def ottamis-suunnitelma (body
                           {:name "selvitykset" :type :group
                            :body [{:name "toimenpiteet" :type :text :max-len 4000}
                                   {:name "tutkimukset" :type :text :max-len 4000}
                                   {:name "ainesLaatu" :type :text :max-len 4000}
                                   {:name "ainesMaara" :type :text :max-len 4000}]}
                           {:name "Luonnonolot" :type :group
                            :body [{:name "maisemakuva" :type :text :max-len 4000}
                                   {:name "kasvillisuusJaElaimmisto" :type :text :max-len 4000}
                                   {:name "kaavoitustilanne" :type :text :max-len 4000}]}
                           {:name "pohjavesiolot" :type :group
                            :body [{:name "luokitus" :type :text :max-len 4000}
                                   {:name "suojavyohykkeet" :type :text :max-len 4000}]}
                           {:name "vedenottamot" :type :text :max-len 4000}
                           {:name "vakuus" :type :select :sortBy :displayname
                            :body [{:name "EiAloiteta"}
                                   {:name "Rahaa"}
                                   {:name "Pankkitakaus"}]}))

(def jatteen-keraystoiminta-ilmoitus
  (body
    {:name "toiminnan-muoto"
     :type :select
     :required true
     :body [{:name "uusiToiminta"}
            {:name "muutosToimintaan"}
            {:name "olemassaOlevaToiminta"}]}
    {:name "keraystoiminnan-jarjestaja"
     :type :group
     :validator :some-checked
     :css [:checkbox-group]
     :body [{:name "kunnanKerays" :type :checkbox}
            {:name "tuottajanKerays" :type :checkbox}
            {:name "muuKerays" :type :checkbox}
            {:name "muuKeraysValue" :type :string}]}
    {:name "jatteen-vastuullinen"
     :type :group
     :validator :some-checked
     :css [:checkbox-group]
     :body [{:name "kunnanJate" :type :checkbox}
            {:name "tuottajanJate" :type :checkbox}
            {:name "muuJate" :type :checkbox}
            {:name "muuJateValue" :type :string}]}
    {:name "vastaanottopaikat-liitteena" :type :checkbox}))

(def luonnonmuistomerkin-rauhoittaminen
  (body
    {:name "muistomerkki-perustelut-rauhoitukselle" :type :group
     :group-help "muistomerkki-perustelut-rauhoitukselle.help"
     :body [{:name "kohteen-nimi" :type :string :size :l :required true}
            kuvaus
            {:name "muita-tietoja" :type :text :max-len 4000 :required false :layout :full-width}]}

   {:name "muistomerkki-kaytto-ja-hoito" :type :group
    :group-help "muistomerkki-kaytto-ja-hoito.help"
    :body [{:name "ei-nahtavyyskohde" :type :checkbox :layout :full-width}]}))

(def tiedot-sailiosta
  {:name "tiedot-sailiosta" :type :group
    :body [{:name "kaytosta-poistamisen-syy" :type :select :sortBy :displayname :required true
            :other-key "muu-syy"
            :body [{:name "lammistysmuodon-vaihtaminen"}
                   {:name "oljylammistyslaitteiston-uusiminen"}]}
           {:name "muu-syy" :type :string}
           {:name "kaytosta-poistamisen-ajankohta" :type :date :required true}
           {:name "kaytosta-poiston-jalkeen" :type :group :layout :horizontal
            :body [{:name "tyhjennetty" :type :checkbox}
                   {:name "puhdistettu" :type :checkbox}
                   {:name "tarkastettu" :type :checkbox}]}
           {:name "sailion-pienin-etaisyys-rakennuksesta" :type :string :subtype :number :size :s :unit :m :required true}
           {:name "sailion-pienin-etaisyys-rakennuksesta-mista-mitattu" :type :string :size :l :required true}
           {:name "koko" :type :string :subtype :number :size :s :unit :m3 :required true}
           {:name "materiaali" :type :select :sortBy :displayname :required true
            :other-key "muu-materiaali"
            :body [{:name "metalli"}
                   {:name "muovi-tai-lasikuitu"}]}
           {:name "muu-materiaali" :type :string}
           {:name "onko-sailion-pohja-alempana-kuin-rakennuksen-perusteet" :type :group :layout :horizontal
            :body [{:name "onko-sailion-pohja-alempana-kuin-rakennuksen-perusteet-kylla" :type :checkbox}
                   {:name "onko-sailion-pohja-alempana-kuin-rakennuksen-perusteet-paljonko" :type :string :subtype :number :size :s :unit :m}]}
           {:name "sailio-sijaitsee-bunkkerissa" :type :checkbox}
           {:name "sailio-sijaitsee-tarkealla-pohjavesialueella" :type :select :required true
            :body [{:name "kylla"}
                   {:name "ei"}
                   {:name "ei-tietoa"}]}
           {:name "sailion-kunto" :type :text :max-len 4000 :layout :full-width :required true}
           {:name "onko-tapahtunut-vuotoja" :type :group :layout :horizontal
            :body [{:name "on-tapahtunut-vuotoja" :type :checkbox}
                   {:name "vuotoja-tapahtunut-vuonna" :type :string :subtype :number :min-len 4 :max-len 4 :size :s}]}
           {:name "onko-tapahtunut-ylitayttoja" :type :group :layout :horizontal
            :body [{:name "on-tapahtunut-ylitayttoja" :type :checkbox}
                   {:name "ylitayttoja-tapahtunut-vuonna" :type :string :subtype :number :min-len 4 :max-len 4 :size :s}]}
           {:name "oljysailion-putkijarjestelma" :type :group
            :group-help "oljysailion-putkijarjestelma.help"
            :body [{:name "oljysailion-putkijarjestelma" :type :select :sortBy :displayname
                    :body [{:name "1-putkijarjestelma"}
                           {:name "2-putkijarjestelma"}]}]}]})

(def kaytostapoistetun-sailion-jattaminen-maaperaan
  (body
    {:name "tiedot-kiinteistosta" :type :group
     :body (body
             {:name "kiint-omistaja-jos-ei-hakija" :type :string :size :l})}
   {:name "maahan-jattamisen-perustelut" :type :group :layout :vertical
    :body [{:name "perustelut-liitteena" :type :checkbox}
           {:name "sailion-poistaminen-vahingoittaa-rakenteita" :type :checkbox}
           {:name "sailion-poistaminen-teknisesti-vaikeata" :type :checkbox}
           {:name "sailion-poistaminen-muut-perustelut" :type :checkbox}
           {:name "sailion-kunto" :type :text :max-len 4000}]}
   tiedot-sailiosta))

(def leirintaalueilmoitus
  (body
    {:name "tunnistetiedot"
     :type :group
     :body [{:name "leirintaalueen-nimi"   :type :string :size :l :required true}
            {:name "kyla-tai-kaupunginosa" :type :string :size :l :required true}
            {:name "yleisen-alueen-nro"    :type :string :size :l :required true}]}
    {:name "toiminnan-luonne"
     :type :group
     :body [{:name       "luonne-tyyppi"
             :type       :select
             :labelclass "hidden"
             :layout     :full-width
             :required   true
             :body [{:name "leirinta-alue-on-yleinen" }
                    {:name "leirinta-alue-on-rajattu" }]}
            {:name      "rajattu-mille"
             :type      :string
             :size      :xl
             :required  true
             :show-when {:path   "luonne-tyyppi"
                         :values #{"leirinta-alue-on-rajattu"}}}]}
    {:name "kaytto-kausi"
     :type :group
     :body [{:name "alku" :type :date :required true}
            {:name "loppu" :type :date :required true}]}
    {:name "alueen-koko"
     :type :group
     :body [{:name "pinta-ala"                    :type :string :subtype :number :unit :hehtaaria :min 0 :required true}
            {:name "leirintapaikkoja-yhteensa"    :type :string :subtype :number :unit :kpl :min 0 :required true}
            {:name "leirintapaikkoja-matkailuajoneuvoille" :type :string :subtype :number :unit :kpl :min 0 :required true}
            {:name "leirintapaikkoja-teltoille"   :type :string :subtype :number :unit :kpl :min 0 :required true}
            {:name "leirintamokkeja"              :type :string :subtype :number :unit :kpl :min 0 :required true}
            {:name "leirintamokkejen-vuodepaikat" :type :string :subtype :number :unit :kpl :min 0 :required true}]}
    {:name "selvitykset"
     :type :group
     :body [
            {:name     "talousvesi-selvitys"
             :type     :text
             :max-len  4000
             :required true}
             {:name     "jatevesi-selvitys"
              :type     :text
              :max-len  4000
              :required true}
             {:name     "kaymala-selvitys"
              :type     :text
              :max-len  4000
              :required true}
             {:name     "vaaralliset-kemikaalit-selvitys"
              :type     :text
              :max-len  4000
              :required true}
             {:name     "vaaralliset-jatteet-selvitys"
              :type     :text
              :max-len  4000
              :required true}
             {:name     "alkusammutus-ja-ensiapu-selvitys"
              :type     :text
              :max-len  4000
              :required true}
             {:name     "pysakointi-selvitys"
              :type     :text
              :max-len  4000
              :required true}
             {:name     "valvonta-ja-vartiointi-selvitys"
              :type     :text
              :max-len  4000
              :required true}]}))

(def koeluontoinen-toiminta
  (body
    {:name "kuvaus-toiminnosta"
     :type :text
     :required true
     :max-len 4000
     :placeholder "koeluontoinen-toiminta.kuvaus.placeholder"}
    {:name "raaka-aineet"
     :type :text
     :max-len 4000
     :placeholder "koeluontoinen-toiminta.kuvaus.placeholder"}
    {:name "paastot"
     :type :text
     :max-len 4000
     :placeholder "koeluontoinen-toiminta.kuvaus.placeholder"}
    {:name "ymparistonsuojeluselvitys"
     :type :text
     :max-len 4000
     :placeholder "koeluontoinen-toiminta.kuvaus.placeholder"}))

(def maa-ainesten-kotitarveotto
  (body
    {:name "kotitarveoton-kesto"
     :type :group
     :body [{:name "alkanut" :type :string :subtype :number :required true}
            {:name "jatkuu-vuoteen" :type :string :subtype :number :required true}]}
    {:name "kotitarveoton-maarat"
     :type :group
     :body [{:name "kokonaismaara" :type :string :size :m :subtype :decimal :unit :k-m3 :required true}
            {:name "maaran-jakautuminen"
             :type :group
             :body [{:name "kalliokivi" :type :string :size :s :subtype :decimal :unit :k-m3}
                    {:name "sora-ja-hiekka" :type :string :size :s :subtype :decimal :unit :k-m3}
                    {:name "siltti-ja-savi" :type :string :size :s :subtype :decimal :unit :k-m3}
                    {:name "moreeni" :type :string :size :s :subtype :decimal :unit :k-m3}
                    {:name "eloperainen-maalaji" :type :string :size :s :subtype :decimal :unit :k-m3}
                    {:name "muu" :type :string :size :m :unit :k-m3}]}]}))

(def ottamisalue [{:name        "kiinteisto"
                   :type        :group
                   :uicomponent :propertyGroup
                   :body        [{:name  "maaraalaTunnus"
                                  :type :maaraalaTunnus
                                  :uicomponent :maaraala-tunnus
                                  :size :s
                                  :transform :zero-pad-4}
                                 {:name "tilanNimi" :type :string :readonly true :uicomponent :docgen-input :inputType :string}
                                 {:name "rekisterointipvm" :type :string :readonly true :uicomponent :docgen-input :inputType :string}
                                 {:name "maapintaala" :type :string :readonly true :unit :hehtaaria :uicomponent :docgen-input :inputType :string}
                                 {:name "vesipintaala" :type :string :readonly true :unit :hehtaaria :uicomponent :docgen-input :inputType :string}
                                 {:name "rantaKytkin" :type :checkbox :uicomponent :docgen-input :inputType :checkbox}]}
                  {:name "hallintaperuste" :type :select :sortBy :displayname :required true
                   :body [{:name "oma"}
                          {:name "vuokra"}
                          ei-tiedossa]}
                  {:name "kaavanaste" :type :select :sortBy :displayname :hidden true
                   :body [{:name "asema"}
                          {:name "ranta"}
                          {:name "rakennus"}
                          {:name "yleis"}
                          {:name "ei kaavaa"}
                          ei-tiedossa]}
                  {:name "kaavatilanne" :type :select :sortBy :displayname
                   :body [{:name "maakuntakaava"}
                          {:name "oikeusvaikutteinen yleiskaava"}
                          {:name "oikeusvaikutukseton yleiskaava"}
                          {:name "asemakaava"}
                          {:name "ranta-asemakaava"}
                          {:name "ei kaavaa"}]}
                  {:name       "asiastaIlmoitettu" :type :group
                   :group-help "ottamisalue.asiastaIlmoitettu._groupHelpText"
                   :body       [{:name "hankkeestaIlmoitettuPvm" :type :date :i18nkey "date"}]}])



(def ilmoitus-poikkeuksellisesta-tilanteesta
  (body
    {:name "kuvaus"
     :type :text}
    {:name "tilanne"
     :type :select
     :size :l
     :other-key "muu-kertaluonteinen-tapaus"
     :body [{:name "onnettomuus"}
            {:name "tuotantohairio"}
            {:name "purkutyo"}]}
    {:name "muu-kertaluonteinen-tapaus" :type :string}
    {:name "luvanvarainen" :type :checkbox}
    {:name "paastot-ja-jatteet" :type :group
     :body [{:name "paaston-aiheuttama-vaara" :type :text :max-len 4000}
            {:name "jatteen-nimi-olomuoto-ominaisuudet" :type :text :max-len 4000}
            {:name "jatteen-maarat" :type :text :max-len 4000}
            {:name "muut-paastot-olomuoto-ominaisuudet" :type :text :max-len 4000}]}
    {:name "jatehuollon-jarjestaminen" :type :group
     :body [{:name "keraily-varastointi-kuljetus-kasittely" :type :text :max-len 4000}]}))

(def maastoliikennelaki-kilpailut-ja-harjoitukset
  (body
    {:name "toiminnan-laatu" :type :group
     :group-help "maastoliikennelaki-kilpailut-ja-harjoitukset.toiminnan-laatu.help"
     :body [kuvaus]}
    {:name "toiminnan-vaikutukset" :type :group
     :group-help "maastoliikennelaki-kilpailut-ja-harjoitukset.toiminnan-vaikutukset.help"
     :body [{:name "tiedot-vaikutuksista-luonnolle" :type :text :max-len 4000 :required true}]}))

(def vesiliikennelaki-kilpailut-ja-harjoitukset
  (body
    {:name "toiminnan-laatu" :type :group
     :group-help "vesiliikennelaki-kilpailut-ja-harjoitukset.toiminnan-laatu.help"
     :body [kuvaus]}
    {:name "toiminnan-vaikutukset" :type :group
     :group-help "vesiliikennelaki-kilpailut-ja-harjoitukset.toiminnan-vaikutukset.help"
     :body [{:name "tiedot-vaikutuksista-luonnolle" :type :text :max-len 4000 :required true}]}))

(def elainmaarat [{:name "ryhma"
                   :type :select
                   :label false
                   :body (map (partial hash-map :name)
                              ["lyspylehmat" "emolehmat" "hiehotLihanaudatSiitossonnit" "nuorkarja"
                               "emakotPorsaineen" "sateliittiemakotPorsaineen" "lihasiatSiitossiat"
                               "joutilaatEmakot" "vieroitetutPorsaat"
                               "hevoset" "ponit" "lampaatUuhetKaritsoineen" "vuohetKututKileineen"
                               "lattiakanatBroileremot" "hakkikanat" "kalkkunat" "broileritKananuorikot"
                               "ankatHanhet" "sorsat"])}
                  {:name "nykyinen" :i18nkey "nykyinen-elainmaara" :label false :type :string :unit :kpl :size :m}
                  {:name "maksimi" :i18nkey "maksimi-elainmaara" :label false :type :string :unit :kpl :size :m}])

(def lannan-varastointi-ilmoituksen-tilan-tiedot
  (body {:name "tilan-nimi" :type :string}
        {:name "kayntiosoite" :type :string :size :l}
        {:name "millainen-tila"
         :type :group
         :body [{:name "kasvinviljelytila" :type :checkbox}
                {:name      "kasvinviljelytila-tarkenne"
                 :type      :group
                 :show-when {:path "kasvinviljelytila"
                             :values #{true}}
                 :css       [:checkbox-group]
                 :body      [{:name "ottaa-vastaan-lantaa" :type :checkbox}
                             {:name "aumaa-orgaanisia-aineita" :type :checkbox}
                             {:name "sekoittaa-maa-aineksia-ja-lantaa" :type :checkbox}]}

                {:name "elaintila"
                 :type :checkbox}
                {:name      "elaintila-tarkenne"
                 :show-when {:path "elaintila"
                             :values #{true}}
                 :type      :group
                 :repeating true
                 :repeating-init-empty true
                 :body      [{:name  "elainluokka"
                              :type :select
                              :sortBy :displayname
                              :body  (->select-options ["lypsylehmia"
                                                        "emolehmia"
                                                        "hiehoja-lihanautoja-siitossonneja"
                                                        "nuorkarjaa"
                                                        "emakoita-porsaineen"
                                                        "satelliittiemakoita-porsaineen"
                                                        "joutilaita-emakoita"
                                                        "vieroitettuja-porsaita"
                                                        "lihasikoja"
                                                        "siitossikoja"
                                                        "hevosia"
                                                        "poneja"
                                                        "lampaita-uuhia-karitsoineen"
                                                        "vuohia-kuttuja-kileineen"
                                                        "lattiakanoja-broileriemoja"
                                                        "hakkikanoja"
                                                        "broilereita"
                                                        "ankkoja-hanhia"
                                                        "muita-mita"
                                                        ])}
                             {:name      "elainluokka-tarkenne"
                              :type      :string
                              :show-when {:path   "elainluokka"
                                          :values #{"muita-mita"}}}
                             {:name    "maara"
                              :type    :string
                              :subtype :number
                              :unit    :kpl}]}]}))

(def lannan-varastointi-v2
  (body {:name      "aumat"
         :type      :group
         :repeating true
         :body      [{:name "mita-aumataan"
                      :type :select
                      :size :l
                      :body (->select-options ["lanta"
                                               "orgaaniset-lannoitevalmisteet"
                                               "jalkikypsytettavaa-komposti"])}
                     {:name "lanta"
                      :type :group
                      :group-help "lannan-varastointi-v2.aumat.lanta._group_help"
                      :show-when {:path "mita-aumataan"
                                  :values #{"lanta"}}
                      :body [{:name "minka-elainlajin-lantaa"
                              :type :select
                              :size :l
                              :sortBy :displayname
                              :body (->select-options ["nauta"
                                                       "sika"
                                                       "siipikarja"
                                                       "hevoset"
                                                       "lampaat-ja-vuohet"
                                                       "turkiselaimet"
                                                       "muu"])}
                             {:name "minka-muun-elainlajin-lantaa"
                              :type :string
                              :show-when {:path   "minka-elainlajin-lantaa"
                                          :values #{"muu"}}
                              }
                             {:name "lantalaji"
                              :type :select
                              :sortBy :displayname
                              :body (->select-options ["kuivikelanta"
                                                       "kuivalanta"
                                                       "turpeeseen-imeytetty-lanta"
                                                       "kuivikepohjalanta"
                                                       ])}
                             {:name    "kuivalanta-pitoisuus"
                              :type    :string
                              :subtype :number}
                             {:name    "maara"
                              :type    :string
                              :subtype :number
                              :unit    :m3}
                             {:name "alkupera"
                              :type :select
                              :body (->select-options ["omasta-toiminnasta"
                                                       "muualta"])}
                             {:name      "muualta-mista"
                              :type      :string
                              :show-when {:path   "alkupera"
                                          :values #{"muualta"}}}]}
                     {:name "orgaaniset-lannoitevalmisteet"
                      :type :group
                      :group-help "lannan-varastointi-v2.aumat.orgaaniset-lannoitevalmisteet._group_help"
                      :show-when {:path "mita-aumataan"
                                  :values #{"orgaaniset-lannoitevalmisteet"}}
                      :body [{:name "kauppanimet-tai-tyyppinimet"
                              :type :string}
                             {:name    "maara"
                              :type    :string
                              :subtype :number
                              :unit    :m3}
                             {:name "alkupera"
                              :type :select
                              :body (->select-options ["omasta-toiminnasta"
                                                       "muualta"])}
                             {:name      "alkupera"
                              :type      :string
                              :show-when {:path   "alkupera"
                                          :values #{"muualta"}}}
                             {:name      "muualta-mista"
                              :type      :string
                              :show-when {:path   "alkupera"
                                          :values #{"muualta"}}}]}
                     {:name "jalkikypsytettavaa-komposti"
                      :type :group
                      :show-when {:path "mita-aumataan"
                                  :values #{"jalkikypsytettavaa-komposti"}}
                      :body [{:name "mita-komposti-sisaltaa"
                              :type :string}
                             {:name "kuiva-ainepitoisuus"
                              :type :string}
                             {:name "maara"
                              :type :string}]}
                     {:name "lannan-varastointi-poikkeustilanteessa"
                      :group-help "lannan-varastointi-v2.aumat.lannan-varastointi-poikkeustilanteessa._group_help"
                      :type :group
                      :show-when {:path "mita-aumataan"
                                  :values #{"lanta"}}
                      :body [{:name "millainen-poikkeuksellinen-syy"
                              :type :select
                              :body (->select-options ["tyotekninen"
                                                       "hygieeninen"])
                              }
                             {:name "mika-syy"
                              :type :string}]}]}))

(def ^:deprecated lannan-varastointi-ilmoitus
  (body {:name "toimenpide"
         :type :group
         :body [{:name     "tapa"
                 :type     :select
                 :required true
                 :layout   :full-width
                 :body     [{:name "poikkeaminenVarastointitilavuudesta"}
                            {:name "muuLannanKaukovarastointi"}]}]}

        {:name "elainmaarat" :group-help "lannan-varastointi.elainmaarat.group-help" :type :table :repeating true :body elainmaarat}
        {:name      "muutElaimet"
         :repeating true
         :type      :table
         :body      [{:name "kuvaus" :label false :type :string}
                     {:name "nykyinen" :i18nkey "nykyinen-elainmaara" :label false :type :string :unit :kpl :size :m}
                     {:name "maksimi" :i18nkey "maksimi-elainmaara" :label false :type :string :unit :kpl :size :m}]}

        {:name      "vastaanotettuLanta"
         :type      :group
         :repeating true
         :body      (body
                      {:name "kuvaus" :type :string}
                      {:name "maara" :type :string :unit :m3 :size :m}
                      (map #(assoc % :size :m) maatila-suppea))}

        {:name "lantajarjestelma"
         :type :group
         :body [{:name "lietelanta" :type :checkbox}
                {:name "kuivalantaJaVirtsa" :type :checkbox}
                {:name "kuivikelanta" :type :checkbox}
                {:name "kuivikepohja" :type :checkbox}
                {:name "tyhjennysvali" :type :string :unit :kuukautta :size :m}
                {:name "kaytettyKuivike" :type :string}]}

        {:name "omatVarastot"
         :type :group
         :body (body (map (partial hash-map :type :string :unit :m3 :size :m :name)
                          ["kuivalantala" "virtsasailio" "lietesailioTaiKuilu" "muuSailio"
                           "kuivikepohja" "kompostialusta"])
                     {:name "muutVarastot"
                      :type :group
                      :body [{:name "tyyppi" :type :string}
                             {:name "tilavuus" :type :string :unit :m3 :size :m}]}
                     {:name "suppeaJaloittelualue"
                      :type :group
                      :body [{:name "jaloittelualue" :type :string :unit :m2 :size :m}
                             {:name "varastointitilavuus" :type :string :unit :m3 :size :m}]})}
        {:name "yhteinenVarasto"
         :type :group
         :body [{:name "tyyppi" :type :string}
                {:name "tilavuus" :type :string :unit :m3 :size :m}
                {:name "kayttajat" :type :table :repeating true :body (map #(assoc % :label false) maatila-suppea)}]}

        {:name "selostusElaimienOleskelusta" :type :text :max-len 1000}
        {:name "poikkeamissuunnitelma" :type :text :max-len 4000}

        {:name       "poikkeamistapa"
         :type       :group
         :group-help "lannan-varastointi.poikkeamistapa.help"
         :body       [{:name                 "tapaA"
                       :type                 :group
                       :group-help           "lannan-varastointi.poikkeamistapa.tapa.help"
                       :repeating            true
                       :repeating-init-empty true
                       :body                 [{:name "hyodyntava-maatila" :type :group :body maatila}
                                              {:name "ymparistolupa" :type :group :body ymparistolupa}
                                              {:name "lantamaara" :type :string :unit :m3 :size :m}]}

                      {:name                 "tapaB"
                       :type                 :group
                       :group-help           "lannan-varastointi.poikkeamistapa.tapa.help"
                       :repeating            true
                       :repeating-init-empty true
                       :body                 [{:name "varastoiva-maatila" :type :group :body maatila}
                                              {:name "varastointitapa" :type :text :max-len 1000}
                                              {:name "lantamaara" :type :string :unit :m3 :size :m}]}

                      {:name                 "tapaC"
                       :type                 :group
                       :group-help           "lannan-varastointi.poikkeamistapa.tapa.help"
                       :repeating            true
                       :repeating-init-empty true
                       :body                 [{:name "hyodyntava-maatila" :type :group :body maatila}
                                              {:name "hyodyntamispaikka" :type :text :max-len 1000}
                                              {:name "lantamaara" :type :string :unit :m3 :size :m}]}

                      {:name                 "tapaD"
                       :type                 :group
                       :group-help           "lannan-varastointi.poikkeamistapa.tapaD.help"
                       :repeating            true
                       :repeating-init-empty true
                       :body                 [{:name "patterinSijaintipaikka" :type :group :body maatila}
                                              {:name      "peruslohko"
                                               :type      :table
                                               :repeating true
                                               :body      [{:name "nimi" :label false :type :string}
                                                           {:name "tunnus" :label false :type :string}
                                                           {:name "pintaala" :label false :type :string}]}
                                              {:name "perustamistapaJaPeittaminen" :type :text}
                                              {:name "lannanMaara" :type :string :unit :m3 :size :m}
                                              {:name "patterienLukumaara" :type :string :unit :kpl :size :m}
                                              {:name "etaisyydet"
                                               :type :group
                                               :body [{:name "etaisyysTalouskaivoon" :type :string :unit :m :size :m}
                                                      {:name "etaisyysValtaojaan" :type :string :unit :m :size :m}
                                                      {:name "etaisyysVesistoon" :type :string :unit :m :size :m}]}
                                              {:name "patterinLevitysaika" :type :group :body [{:name "alku" :type :date}
                                                                                               {:name "loppu" :type :date}]}]}]}))

(def jatteiden-hyodyntaminen-maarakentamisessa
  (let [syntypaikka=hankkeen-sijainti {:path   "hankkeen-sijainti-on-syntypaikka"
                                       :values #{true}}]
    (body
      {:name "mineraali-jatteen-alkupera"
       :type :group
       :body [{:name "hankkeen-sijainti-on-syntypaikka" :type :checkbox :css [:full-width-component] :default true}
              {:name "kunta-tai-kaupunki" :type :string :size :l :required true :hide-when syntypaikka=hankkeen-sijainti}
              {:name "kiinteistotunnus" :type :string :size :l :subtype :kiinteistotunnus :required true :hide-when syntypaikka=hankkeen-sijainti}
              {:name "jatteen-syntytapa" :type :string :size :l :required true}
              {:name "jatteen-maara" :type :string :size :l :subtype :number :required true :unit :tonnia}
              {:name "jatten-nimi-ja-olomuoto" :type :text :max-len 4000 :required true}]}
      {:name "mineraali-jatteen-sijoittaminen"
       :type :group
       :body [{:name "jatteen-hyodyntamistapa" :type :text :max-len 4000 :required true}
              {:name "pohja-veden-ylin-korkeus" :type :string :subtype :number :unit :m :required true}
              ]}
      {:name "toteutus-aika"
       :type :group
       :body [{:name "alku" :type :date :required true}
              {:name "loppu" :type :date :required true}]})))

(def jatteiden-hyodyntaminen-hyodyntamispaikka
  (let [hyodyntamispaikka=hankkeen-sijainti {:path   "hankkeen-sijainti-on-hyodyntamispaikka"
                                             :values #{true}}]
    (body
      {:name "hankkeen-sijainti-on-hyodyntamispaikka" :type :checkbox :css [:full-width-component]}
      {:name "kunta-tai-kaupunki" :type :string :size :l :required true :hide-when hyodyntamispaikka=hankkeen-sijainti}
      {:name "kiinteistotunnus" :type :string :size :l :subtype :kiinteistotunnus :required true :hide-when hyodyntamispaikka=hankkeen-sijainti}
      {:name "tontin-tai-tilan-pinta-ala" :type :string :subtype :number :unit :m2 :required true}
      {:name "rakennuspaikan-osoite" :type :string :size :l :required true :hide-when hyodyntamispaikka=hankkeen-sijainti})))

(def ylijaamamaiden-hyodyntaminen-hyodyntamispaikka
    (body
      {:name "kunta-tai-kaupunki" :type :string :size :l :required true }
      {:name "kiinteistotunnus" :type :string :size :l :subtype :kiinteistotunnus :required true }
      {:name "tontin-tai-tilan-pinta-ala" :type :string :subtype :number :unit :m2 :required true}
      {:name "rakennuspaikan-osoite" :type :string :size :l :required true }))

(def ylijaamamaiden-hyodyntaminen
  (body
    {:name "ylijaamamaan-alkupera"
     :type :group
     :body [{:name "kunta-tai-kaupunki" :type :string :size :l :required true }
            {:name "kiinteistotunnus" :type :string :subtype :kiinteistotunnus :size :l :required true}
            {:name "ylijaama-maan-maara" :type :string :subtype :number :unit :tonnia :size :l :required true}
            {:name "ylijaama-maan-laatu" :type :string :size :l :required true}]}
    {:name "ylijaamamaan-sijoittaminen"
     :type :group
     :body [{:name "kayttotarkoitus-kohteessa" :type :text :max-len 4000 :required true}
            {:name "puhtauden-varmistaminen" :type :text :max-len 4000 :required true}
            {:name "muut-muuttamis-toimet" :type :text :max-len 4000 :required true}]}
    {:name "toteutus-aika"
     :type :group
     :body [{:name "alku" :type :date :required true}
            {:name "loppu" :type :date :required true}]}))

(def ilmoitus-lannan-levittamisesta-poikkeustilanteessa-elainsuojantiedot
  (body {:name "elainsuojan-nimi" :type :string}
        {:name "kayntiosoite" :type :string}))

(def lannan-levittamisesta-poikkeustilanteessa
  (body {:name "elainsuojantiedot"
         :type :group
         :body  [{:name "elainsuojan-nimi" :type :string}
                 {:name "kayntiosoite" :type :string}
                 {:name "tilatunnus" :type :string :subtype :number}]}
        {:name "selvitys-saaolosuhteista"
         :type :group
         :body (build-body [with-tiedot-esitetty-liitteessa]
                           {:name "kuvaus" :type :text})}
        {:name        "tiedot-pellosta"
         :group-help  "lannan-levittamisesta-poikkeustilanteessa.tiedot-pellosta._group_help"
         :uicomponent :docgenTable
         :type        :table
         :repeating   true
         :footer-sums ["kasvulohkon-pinta-ala" "levitettava-lantamaara"]
         :body        [{:name "perus-ja-kasvulohkotunnus" :type :string :label false}
                       {:name "kasvulohkon-pinta-ala" :type :string :subtype :decimal :unit :hehtaaria :label false}
                       {:name "levitettava-lantamaara" :type :string :subtype :decimal :unit :m3 :label false}
                       {:name "levitettava-lantamaara-hehtaaria-kohden" :type :string :subtype :number :label false}]}
        {:name "levityksen-toteutus"
         :type :group
         :group-help "lannan-levittamisesta-poikkeustilanteessa.levityksen-toteutus.minka-elainlajin-lantaa._group_help"
         :body  (build-body [with-tiedot-esitetty-liitteessa]
                            {:name "minka-elainlajin-lantaa"
                             :type :select
                             :body (->select-options ["nauta"
                                                      "sika"
                                                      "siipikarja"
                                                      "hevoset"
                                                      "lampaat-ja-vuohet"
                                                      "turkiselaimet"])
                             :other-key "minka-elainlajin-lantaa-tarkenne"
                             :size :l}
                            {:name "minka-elainlajin-lantaa-tarkenne"
                             :type :string
                             :size :l}
                            {:name "lantalaji"
                             :type :select
                             :css [:full-width-component]
                             :size :l
                             :body (->select-options ["kuivikelanta"
                                                      "kuivalanta"
                                                      "turpeeseen-imeytetty-lietelanta"
                                                      "kuivikepohjalanta"
                                                      "lietelanta"
                                                      "virtsa"])}
                            {:name "levitysajankohta"
                             :type :date}
                            {:name "levitys-ja-multaustekniikka"
                             :type :text})}))

(def kirjallinen-vireillepano
  (body {:name "kuvaus"
         :type :text
         :required true}
        {:name "ajankohta"
         :group-help "kirjallinen-vireillepano.ajankohta._group-help"
         :type :group
         :body [{:name "alku" :type :date}
                {:name "loppu" :type :date}]}

        {:name "mita-vaikutuksia"
         :type :text
         :required true}
        {:name "vireille-panijan-vaatimukset"
         :type :text
         :required true}))

(defschemas
  1
  [{:info {:name "meluilmoitus"
           :approvable true
           :order 50}
    :body meluilmoitus}
   {:info {:name "yi-yleinen-ilmoitus"
           :approvable true
           :order 50}
    :body yleinen-ilmoitus-schemas/yleinen-ilmoitus}
   {:info {:name "rekisterointi-ilmoitus"
           :approvable true
           :order 50}
    :body rekisterointi-ilmoitus-schemas/rekisterointi-ilmoitus}
   {:info {:name "pima"
           :approvable true
           :order 51}
    :body pima}
   {:info {:name "ymp-ilm-kesto-mini"
           :approvable true
           :order 60}
    :body kesto-mini}
   {:info {:name "ymp-ilm-kesto"
           :approvable true
           :order 60}
    :body kesto}
   {:info {:name "ottamismaara"
           :approvable true
           :order 50}
    :body ottamismaara}
   {:info {:name "ottamis-suunnitelma"
           :approvable true
           :order 51}
    :body ottamis-suunnitelma}
   {:info {:name "maa-ainesluvan-omistaja"
           :approvable true
           :i18name "osapuoli"
           :order 3
           :type :party
           :accordion-fields hakija-accordion-paths}
    :body party}
   {:info {:name "ottamis-suunnitelman-laatija"
           :approvable true
           :i18name "osapuoli"
           :order 4
           :type :party
           :accordion-fields hakija-accordion-paths}
    :body party}
   {:info {:name "ymp-maksaja"
           :i18name "osapuoli"
           :repeating false
           :order 6
           :removable-by :all
           :approvable true
           :subtype :maksaja
           :type :party
           :accordion-fields hakija-accordion-paths}
     :body maksaja}
   {:info {:name "yl-hankkeen-kuvaus"
           :approvable true
           :order 1}
    :body [kuvaus
           {:name "peruste" :type :text :max-len 4000 :required true :layout :full-width}]}
   {:info {:name "yl-uusi-toiminta"
           :approvable true
           :order 1}
    :body [kuvaus
           {:name "peruste" :type :text :max-len 4000 :required true :layout :full-width}
           {:name "aloituslupa" :type :checkbox}]}
   {:info {:name "maa-aineslupa-kuvaus"
           :approvable true
           :order 1}
    :body [kuvaus
           {:name "aloituslupa" :type :checkbox}]}
   {:info {:name "maa-aineslupa-jatkoaika-kuvaus"
           :approvable true
           :order 1}
    :body [kuvaus]}
   {:info {:name "luonnonmuistomerkin-rauhoittaminen"
           :approvable true
           :order 1}
    :body luonnonmuistomerkin-rauhoittaminen}
   {:info {:name "kaytostapoistetun-sailion-jattaminen-maaperaan"
           :approvable true
           :order 100}
    :body kaytostapoistetun-sailion-jattaminen-maaperaan}
   {:info {:name "ilmoitus-poik-tilanteesta"
           :approvable true
           :order 1}
    :body ilmoitus-poikkeuksellisesta-tilanteesta}
   {:info {:name "jatteen-kerays"
           :approvable true}
    :body jatteen-keraystoiminta-ilmoitus}
   {:info {:name "koeluontoinen-toiminta"
           :approvable true}
    :body (body
            koeluontoinen-toiminta
            (update-in (vec kesto-mini) [0 :body] (fn [body] (map #(assoc % :required true) body))))}
   {:info {:name "leirintaalueilmoitus"
           :approvable true}
    :body leirintaalueilmoitus}
   {:info {:name "maastoliikennelaki-kilpailut-ja-harjoitukset"
           :approvable true
           :order 1}
    :body maastoliikennelaki-kilpailut-ja-harjoitukset}
   {:info {:name "vesiliikennelaki-kilpailut-ja-harjoitukset"
           :approvable true
           :order 2}
    :body vesiliikennelaki-kilpailut-ja-harjoitukset}
   {:info {:name "maa-ainesten-kotitarveotto"
           :approvable true}
    :body maa-ainesten-kotitarveotto}
   {:info {:name "ottamisalue"
           :approvable true}
    :body ottamisalue}
   {:info {:name "yl-maatalous-hankkeen-kuvaus"
           :approvable true
           :order 1}
    :body [kuvaus
           {:name "tilatunnus" :type :string}]}
   {:info {:name "lannan-varastointi"
           :section-help "lannan-varastointi-kuvaus.help"
           :approvable true}
    :body lannan-varastointi-ilmoitus}
   {:info {:name "lannan-varastointi-v2"
           :approvable true}
    :body lannan-varastointi-v2}

   {:info {:name "lannan-varastointi-ilmoituksen-tilan-tiedot"
           :approvable true
           :order 1}
    :body lannan-varastointi-ilmoituksen-tilan-tiedot}

   {:info {:name "jatteiden-hyodyntaminen-maarakentamisessa"
           :approvable true
           :order 1}
    :body jatteiden-hyodyntaminen-maarakentamisessa}
   {:info {:name "hyodyntamispaikka"
           :approvable true
           :order 2}
    :body jatteiden-hyodyntaminen-hyodyntamispaikka}
   {:info {:name "ylijaamamaiden-hyodyntaminen"
           :approvable true
           :order 1}
    :body ylijaamamaiden-hyodyntaminen}
   {:info {:name "ylijaamamaiden-hyodyntaminen-hyodyntamispaikka"
           :approvable true
           :order 2}
    :body ylijaamamaiden-hyodyntaminen-hyodyntamispaikka}
   {:info {:name "lannan-levittamisesta-poikkeustilanteessa"
           :approvable true
           :order 1}
    :body lannan-levittamisesta-poikkeustilanteessa}
   {:info {:name "kirjallinen-vireillepano"
           :approvable true
           :order 1}
    :body kirjallinen-vireillepano}
   {:info {:name "paatoksen-toimitus"
           :order 9999
           :copy-action :clear}
    :body [{:name "paatoksenToimittaminen" :type :select :sortBy :displayname
            :body [{:name "Noudetaan"}
                   {:name "Postitetaan"}]}]}])
