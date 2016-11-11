(ns lupapalvelu.document.waste-schemas
  (:require [lupapalvelu.document.tools :as tools]))

(def jatetyyppi {:name "jatetyyppi" :type :select :i18nkey "jatetyyppi"
                  :body [{:name "betoni"}
                         {:name "kipsi"}
                         {:name "puu"}
                         {:name "metalli"}
                         {:name "lasi"}
                         {:name "muovi"}
                         {:name "paperi"}
                         {:name "maa"}]})

(def vaarallinenainetyyppi {:name "vaarallinenainetyyppi" :type :select :i18nkey "vaarallinenainetyyppi"
                            :body [{:name "maalit-lakat-liimat-ja-liuottimet"}
                                   {:name "aerosolipullot"}
                                   {:name "painekyllastetty-puu"}
                                   {:name "elohopealamput-ja-paristot"}
                                   {:name "jateoljyt-ja-muut-oljyiset-jatteet"}
                                   {:name "asbesti"}
                                   {:name "kivihiilipiki-eli-kreosootti"}
                                   {:name "raskasmetallipitoiset-maalijatteet"}
                                   {:name "eristeiden-ja-tiivistemassojen-haitalliset-jatteet"}
                                   {:name "sahko-ja-elektroniikkaromu"}]})

(def jateyksikko {:name "yksikko" :i18nkey "jateyksikko" :type :select
                  :body [{:name "kg"}
                         {:name "tonni"}
                         {:name "m2"}
                         {:name "m3"}]})

(def rakennusjatemaara {:name "maara" :type :string :subtype :decimal :uicomponent :docgen-input :inputType :string :min 0 :max 9999999 :size :s})

(def rakennusjatesuunnitelmaRow [(assoc rakennusjatemaara :name "suunniteltuMaara")
                                 jateyksikko
                                 (assoc rakennusjatemaara :name "painoT")])

(def rakennusjateselvitysUusiRow [(assoc rakennusjatemaara :name "toteutunutMaara")
                                  jateyksikko
                                  (assoc rakennusjatemaara :name "painoT")
                                  {:name "jatteenToimituspaikka" :type :string :max-len 50}])

(def rakennusjateselvitysRow [(assoc rakennusjatemaara :name "suunniteltuMaara" :readonly true)
                              (assoc rakennusjatemaara :name "toteutunutMaara")
                              (assoc jateyksikko :readonly true)
                              (assoc rakennusjatemaara :name "painoT")
                              {:name "jatteenToimituspaikka" :type :string :max-len 50}])

(def availableMaterialsRow [{:name "aines" :type :string}
                           rakennusjatemaara
                           jateyksikko
                           {:name "saatavilla" :type :date}
                           {:name "kuvaus" :type :string}])

(def rakennusjatesuunnitelma [{:name "rakennusJaPurkujate"
                               :i18nkey "rakennusJaPurkujate"
                               :type :table
                               :uicomponent :docgenTable
                               :repeating true
                               :approvable false
                               :body (tools/body jatetyyppi rakennusjatesuunnitelmaRow)}
                              {:name "vaarallisetAineet"
                               :i18nkey "vaarallisetAineet"
                               :type :table
                               :uicomponent :docgenTable
                               :repeating true
                               :approvable false
                               :body (tools/body vaarallinenainetyyppi rakennusjatesuunnitelmaRow)}])

(def rakennusjateselvitys [{:name "rakennusJaPurkujate"
                            :i18nkey "rakennusJaPurkujate"
                            :type :group
                            :uicomponent :constructionWasteReport
                            :approvable false
                            :body [{:name "suunniteltuJate"
                                    :type :table
                                    :repeating true
                                    :repeating-init-empty true
                                    :body (tools/body (assoc jatetyyppi :readonly true) rakennusjateselvitysRow)}
                                   {:name "suunnittelematonJate"
                                    :type :table
                                    :repeating true
                                    :body (tools/body jatetyyppi rakennusjateselvitysUusiRow)}]}
                           {:name "vaarallisetAineet"
                            :i18nkey "vaarallisetAineet"
                            :type :group
                            :uicomponent :constructionWasteReport
                            :approvable false
                            :body [{:name "suunniteltuJate"
                                    :type :table
                                    :repeating true
                                    :repeating-init-empty true
                                    :body (tools/body (assoc vaarallinenainetyyppi :readonly true) rakennusjateselvitysRow)}
                                   {:name "suunnittelematonJate"
                                    :type :table
                                    :repeating true
                                    :body (tools/body vaarallinenainetyyppi rakennusjateselvitysUusiRow)}]}
                           {:name "contact"
                            :i18nkey "available-materials.contact"
                            :type :group
                            :group-help "contact.help"
                            :body [{:name "name" :type :string}
                                   {:name "phone" :type :string :subtype :tel}
                                   {:name "email" :type :string :subtype :email}]}
                           {:name "availableMaterials"
                            :i18nkey "available-materials"
                            :type :table
                            :uicomponent :docgenTable
                            :approvable false
                            :repeating true
                            :body (tools/body availableMaterialsRow)}])

(def toteutus {:name "toteutus"
               :type :group
               :uicomponent :docgenGroup
               :approvable false
               :rows [{:h3 "laajennettuRakennusjateselvitys.toteutus"}
                      ["purkuAlku" "purkuLoppu" "rakennusAlku" "rakennusLoppu"]
                      ["paaurakoitsijanYhteystiedot::4"]]
               :template "form-grid-docgen-group-template"
               :body [{:name "purkuAlku" :type :date}
                      {:name "purkuLoppu" :type :date}
                      {:name "rakennusAlku" :type :date}
                      {:name "rakennusLoppu" :type :date}
                      {:name "paaurakoitsijanYhteystiedot" :type :text :max-len 4000}]})

(def purkaminen {:name "purkaminen"
                 :type :group
                 :uicomponent :docgenGroup
                 :approvable false
                 :rows  [{:h3 "laajennettuRakennusjateselvitys.purkaminen"}
                         ["purettavaKerrosala" "rakennusvuosi"]
                         ["oljysailioidenLukumaara" "sailioilleSuunnitellutToimenpiteet/toimenpide" "sailioilleSuunnitellutToimenpiteet/muuToimenpide"]
                         ["etaisyysPohjavesialueesta" "tarkeallaPohjavesialueella"]
                         ["mineraalisenPurkujatteenKasittely/kasittelutapa" "mineraalisenPurkujatteenKasittely/muuKasittelutapa"]
                         ["polynLeviamisenEsto::4"]
                         ["ilmoitusHairitsevastaMelusta/ilmoitusTehty" "ilmoitusHairitsevastaMelusta/pvm" "ilmoitusHairitsevastaMelusta/lisattyLiitteisiin" "ilmoitusHairitsevastaMelusta/syy"]]
                 :template "form-grid-docgen-group-template"
                 :body [{:name "purettavaKerrosala" :type :string :subtype :decimal}
                        {:name "rakennusvuosi" :type :string :subtype :number}
                        {:name "oljysailioidenLukumaara" :type :string :subtype :number}
                        {:name "sailioilleSuunnitellutToimenpiteet"
                         :type :group
                         :body [{:name "toimenpide" :type :select :body (map #(hash-map :name %) ["jaavatKayttoon" "poistetaan" "muu"])}
                                {:name "muuToimenpide" :show-when {:path "toimenpide" :values ["muu"]} :type :string}]}
                        {:name "etaisyysPohjavesialueesta" :type :string}
                        {:name "tarkeallaPohjavesialueella" :type :checkbox}
                        {:name "mineraalisenPurkujatteenKasittely"
                         :type :group
                         :body [{:name "kasittelytapa" :type :select :body (map #(hash-map :name %) ["murskaus" "pulverointi" "muu"])}
                                {:name "muuKasittelytapa" :show-when {:path "kasittelytapa" :values ["muu"]} :type :string}]}
                        {:name "polynLeviamisenEsto" :type :text :max-len 4000}
                        {:name "ilmoitusHairitsevastaMelusta"
                         :type :group
                         :body [{:name "ilmoitusTehty" :type :select :body (map #(hash-map :name %) ["tehty" "tehdaan" "eiTehda"])}
                                {:name "pvm" :show-when {:path "ilmoitusTehty" :values ["tehty" "tehdaan"]} :type :date}
                                {:name "lisattyLiitteisiin" :show-when {:path "ilmoitusTehty" :values ["tehty"]} :type :checkbox}
                                {:name "syy" :show-when {:path "ilmoitusTehty" :values ["eiTehda"]} :type :string}]}]})

(def vaaralliset-aineet {:name "vaarallisetAineet"
                         :type :group
                         :uicomponent :docgenGroup
                         :approvable false
                         :rows [{:h3 "laajennettuRakennusjateselvitys.vaarallisetAineet"}
                                ["eiVaarallisiaAineita"]
                                ["kartoitusVaarallisistaAineista/ilmoitusTehty" "kartoitusVaarallisistaAineista/pvm" "kartoitusVaarallisistaAineista/lisattyLiitteisiin" "kartoitusVaarallisistaAineista/syy"]]
                         :template "form-grid-docgen-group-template"
                         :body [{:name "eiVaarallisiaAineita" :type :checkbox}
                                {:name "kartoitusVaarallisistaAineista"
                                 :hide-when {:path "/vaarallisetAineet/eiVaarallisiaAineita"
                                             :values [true]}
                                 :type :group
                                 :body [{:name "ilmoitusTehty" :type :select :body (map #(hash-map :name %) ["tehty" "tehdaan" "eiTehda"])}
                                        {:name "pvm" :show-when {:path "ilmoitusTehty" :values ["tehty" "tehdaan"]} :type :date}
                                        {:name "lisattyLiitteisiin" :show-when {:path "ilmoitusTehty" :values ["tehty"]} :type :checkbox}
                                        {:name "syy" :show-when {:path "ilmoitusTehty" :values ["eiTehda"]} :type :string}]}]})

(def rakennus-ja-purkujate {:name "rakennusJaPurkujate"
                            :type :group
                            :uicomponent :docgenGroup
                            :rows [{:h3 "laajennettuRakennusjateselvitys.rakennusJaPurkujate"}
                                   ["vaarallisetJatteet::4"]
                                   ["muuJate::4"]]
                            :template "form-grid-docgen-group-template"
                            :body [{:name "vaarallisetJatteet"
                                    :hide-when {:path "/vaarallisetAineet/eiVaarallisiaAineita"
                                                :values [true]}
                                    :type :table
                                    :repeating true
                                    :approvable false
                                    :body [{:name "jate" :type :select :body (map #(hash-map :name %) ["kreosiittiJate" "pcbJate" "asbestiJate" "kyllastettyPuu"])}
                                           {:name "maara" :type :string :subtype :number :size :s}
                                           {:name "yksikko" :type :select :body (map #(hash-map :name % :i18nkey (str "unit." %)) ["kg" "tonnia"])}
                                           {:name "sijoituspaikka" :type :string}]}
                                   {:name "muuJate"
                                    :type :table
                                    :repeating true
                                    :approvable false
                                    :body [{:name "jate" :type :select :body (map #(hash-map :name %) ["betoni" "tiilet" "pinnoittamatonPuu" "pinnoitettuPuu" "sekajate"])}
                                           {:name "maara" :type :string :subtype :number :unit :tonnia :size :s}
                                           {:name "sijoituspaikka" :type :string}]}]})

(def pilaantuneet-maat {:name "pilaantuneetMaat"
                        :type :group
                        :uicomponent :docgenGroup
                        :approvable false
                        :rows [{:h3 "laajennettuRakennusjateselvitys.pilaantuneetMaat"}
                               ["tutkimusPilaantuneistaMaista/tutkimusTehty" "tutkimusPilaantuneistaMaista/pvm" "tutkimusPilaantuneistaMaista/lisattyLiitteisiin" "tutkimusPilaantuneistaMaista/syy"]
                               ["ilmoitusPuhdistuksesta/ilmoitusTehty" "ilmoitusPuhdistuksesta/pvm" "ilmoitusPuhdistuksesta/lisattyLiitteisiin"]
                               ["poistettavatAinekset" "sijoituspaikka::3"]]
                        :template "form-grid-docgen-group-template"
                        :body [{:name "tutkimusPilaantuneistaMaista"
                                :type :group
                                :body [{:name "tutkimusTehty" :type :select :body (map #(hash-map :name %) ["tehty" "tehdaan" "eiTehda"])}
                                       {:name "pvm" :show-when {:path "tutkimusTehty" :values ["tehty" "tehdaan"]} :type :date}
                                       {:name "lisattyLiitteisiin" :show-when {:path "tutkimusTehty" :values ["tehty"]} :type :checkbox}
                                       {:name "syy" :show-when {:path "tutkimusTehty" :values ["eiTehda"]} :type :string}]}
                                {:name "ilmoitusPuhdistuksesta"
                                 :type :group
                                 :body [{:name "ilmoitusTehty" :type :checkbox}
                                        {:name "pvm" :show-when {:path "ilmoitusTehty" :values [true]} :type :date}
                                        {:name "lisattyLiitteisiin" :show-when {:path "ilmoitusTehty" :values [true]} :type :checkbox}]}
                               {:name "poistettavatAinekset" :type :string :subtype :decimal :unit :tonnia}
                               {:name "sijoituspaikka" :type :string}]})

(defn ainekset-table [ainekset]
  {:name "ainekset"
   :type :table
   :repeating true
   :approvable false
   :body [{:name "aines" :type :select :body (map #(hash-map :name %) ainekset)}
          {:name "hyodynnetaan" :type :string :subtype :number :unit :tonnia :size :s}
          {:name "poisajettavia" :type :string :subtype :number :unit :tonnia :size :s}
          {:name "yhteensa" :type :string :subtype :number :unit :tonnia :size :s}
          {:name "sijoituspaikka" :type :string}]})

(def kaivettava-maa {:name "kaivettavaMaa"
                     :type :group
                     :uicomponent :docgenGroup
                     :approvable false
                     :rows [["ainekset::4"]]
                     :template "form-grid-docgen-group-template"
                     :body [(ainekset-table ["louhe" "hiekkaSoraMurskeKivet" "moreeniSiltti" "jaykkaSavi" "pehmeaSavi" "liejuRuoppausmassa" "turve" "kasvualustaMulta"])]})

(def muut-kaivettavat-massat {:name "muutKaivettavatMassat"
                              :type :group
                              :uicomponent :docgenGroup
                              :approvable false
                              :rows [["ainekset::4"]
                                     ["kaivettavienMassojenSelvitys/selvitystapa" "kaivettavienMassojenSelvitys/muuSelvitystapa"]]
                              :template "form-grid-docgen-group-template"
                              :body [(ainekset-table ["betoni" "asfalttijate" "betoniAsfalttiMaaAines" "stabiloituSavi" "kevytsoraVaahtolasimurske" "maatuhka"])
                                     {:name "kaivettavienMassojenSelvitys"
                                      :type :group
                                      :body [{:name "selvitystapa" :type :select :body (map #(hash-map :name %) ["koekuopilla" "vanhoistaSuunnitelmista" "eiSelvitetty" "muu"])}
                                             {:name "muuSelvitystapa" :show-when {:path "selvitystapa" :values ["muu"]} :type :string}]}]})

(def orgaaninen-aines {:name "orgaaninenAines"
                       :type :group
                       :uicomponent :docgenGroup
                       :approvable false
                       :rows [["ainekset::4"]]
                       :template "form-grid-docgen-group-template"
                       :body [(ainekset-table ["risutHavutOksat" "juurakotKannot"])]})

(def vieraslajit {:name "vieraslajit"
                  :type :group
                  :uicomponent :docgenGroup
                  :approvable false
                  :rows [{:h3 "laajennettuRakennusjateselvitys.vieraslajit"}
                         ["vieraslajit"]
                         ["selvitysVieraslajeista::4"]]
                  :template "form-grid-docgen-group-template"
                  :body [{:name "vieraslajit" :type :select :body (map #(hash-map :name %) ["on" "ei" "eiTiedossa"])}
                         {:name "selvitysVieraslajeista" :type :text :max-len 4000}]})

(def laajennettu-rakennusjateselvitys [toteutus
                                       purkaminen
                                       vaaralliset-aineet
                                       rakennus-ja-purkujate
                                       pilaantuneet-maat
                                       kaivettava-maa
                                       muut-kaivettavat-massat
                                       orgaaninen-aines
                                       vieraslajit])
