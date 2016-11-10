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
               :approvable false
               :rows [["purkuAlku" "purkuLoppu" "rakennusAlku" "rakennusLoppu"]
                      ["paaurakoitsijanYhteystiedot"]]
               :template "form-grid-docgen-group-template"
               :body [{:name "purkuAlku" :type :date}
                      {:name "purkuLoppu" :type :date}
                      {:name "rakennusAlku" :type :date}
                      {:name "rakennusLoppu" :type :date}
                      {:name "paaurakoitsijanYhteystiedot" :type :text :max-len 4000 :layout :full-width}]})

(def purkaminen {:name "purkaminen"
                 :type :group
                 :approvable false
                 :rows  [["purettavaKerrosala" "rakennusvuosi"]
                         ["oljysailioidenLukumaara" "sailioilleSuunnitellutToimenpiteet/toimenpide" "sailioilleSuunnitellutToimenpiteet/muuToimenpide"]
                         ["etaisyysPohjavesialueesta" "tarkeallaPohjavesialueella"]
                         ["mineraalisenPurkujatteenKasittely/kasittelutapa" "mineraalisenPurkujatteenKasittely/muuKasittelutapa"]
                         ["polynLeviamisenEsto"]
                         ["ilmoitusHairitsevastaMelusta/ilmoitusTehty" "ilmoitusHairitsevastaMelusta/pvm" "ilmoitusHairitsevastaMelusta/lisattyLiitteisiin" "ilmoitusHairitsevastaMelusta/syy"]]
                 :template "form-grid-docgen-group-template"
                 :body [{:name "purettavaKerrosala" :type :string :subtype :decimal}
                        {:name "rakennusvuosi" :type :string :subtype :number}
                        {:name "oljysailioidenLukumaara" :type :string :subtype :number}
                        {:name "sailioilleSuunnitellutToimenpiteet"
                         :type :group ; TODO: :pre-selector
                         :body [{:name "toimenpide" :type :select :body (map #(hash-map :name %) ["jaavatKayttoon" "poistetaan" "muu"])}
                                {:name "muuToimenpide" :pre-values ["muu"] :type :string}]}
                        {:name "etaisyysPohjavesialueesta" :type :string}
                        {:name "tarkeallaPohjavesialueella" :type :checkbox}
                        {:name "mineraalisenPurkujatteenKasittely"
                         :type :group ; TODO: :pre-selector
                         :body [{:name "kasittelytapa" :type :select :body (map #(hash-map :name %) ["murskaus" "pulverointi" "muu"])}
                                {:name "muuKasittelytapa" :pre-values ["muu"] :type :string}]}
                        {:name "polynLeviamisenEsto" :type :text :max-len 4000 :layout :full-width}
                        {:name "ilmoitusHairitsevastaMelusta"
                         :type :group ; TODO: :pre-selector
                         :body [{:name "ilmoitusTehty" :type :select :body (map #(hash-map :name %) ["tehty" "tehdaan" "eiTehda"])}
                                {:name "pvm" :pre-values ["tehty" "tehdaan"] :type :date}
                                {:name "lisattyLiitteisiin" :pre-values ["tehty"] :type :checkbox}
                                {:name "syy" :pre-values ["eiTehda"] :type :string}]}]})

(def vaaralliset-aineet {:name "vaarallisetAineet"
                         :type :group
                         :approvable false
                         :rows [["eiVaarallisiaAineita"]
                                ["kartoitusVaarallisistaAineista/ilmoitusTehty" "kartoitusVaarallisistaAineista/pvm" "kartoitusVaarallisistaAineista/lisattyLiitteisiin" "kartoitusVaarallisistaAineista/syy"]]
                         :template "form-grid-docgen-group-template"
                         :body [{:name "eiVaarallisiaAineita" :type :checkbox}
                                {:name "kartoitusVaarallisistaAineista"
                                 :hide-when {:path "/vaarallisetAineet/eiVaarallisiaAineita"
                                             :values [true]}
                                 :type :group ; TODO: :pre-selector
                                 :body [{:name "ilmoitusTehty" :type :select :body (map #(hash-map :name %) ["tehty" "tehdaan" "eiTehda"])}
                                        {:name "pvm" :pre-values ["tehty" "tehdaan"] :type :date}
                                        {:name "lisattyLiitteisiin" :pre-values ["tehty"] :type :checkbox}
                                        {:name "syy" :pre-values ["eiTehda"] :type :string}]}]})

(def rakennus-ja-purkujate {:name "rakennusJaPurkujate"
                            :type :group
                            :body [{:name "vaarallisetJatteet"
                                    :hide-when {:path "/vaarallisetAineet/eiVaarallisiaAineita"
                                                :values [true]}
                                    :type :table
                                    :approvable false
                                    :body [{:name "jate" :type :select :body (map #(hash-map :name %) ["kreosiittiJate" "pcbJate" "asbestiJate" "kyllastettyPuu"])}
                                           {:name "maara" :type :string :subtype :number :size :s}
                                           {:name "yksikko" :type :select :body (map #(hash-map :name % :i18nkey (str "unit." %)) ["kg" "tonnia"])}
                                           {:name "sijoituspaikka" :type :string}]}
                                   {:name "muuJate"
                                    :type :table
                                    :approvable false
                                    :body [{:name "jate" :type :select :body (map #(hash-map :name %) ["betoni" "tiilet" "pinnoittamatonPuu" "pinnoitettuPuu" "sekajate"])}
                                           {:name "maara" :type :string :subtype :number :unit :tonnia :size :s}
                                           {:name "sijoituspaikka" :type :string}]}]})

(def pilaantuneet-maat {:name "pilaantuneetMaat"
                        :type :group
                        :approvable false
                        :rows [["tutkimusPilaantuneistaMaista/tutkimusTehty" "tutkimusPilaantuneistaMaista/pvm" "tutkimusPilaantuneistaMaista/lisattyLiitteisiin" "tutkimusPilaantuneistaMaista/syy"]
                               ["ilmoitusPuhdistuksesta/ilmoitusTehty" "ilmoitusPuhdistuksesta/pvm" "ilmoitusPuhdistuksesta/lisattyLiitteisiin"]
                               ["poitettavatAinekset" "sijoituspaikka"]]
                        :template "form-grid-docgen-group-template"
                        :body [{:name "tutkimusPilaantuneistaMaista"
                                :type :group ; TODO: :pre-selector
                                :body [{:name "tutkimusTehty" :type :select :body (map #(hash-map :name %) ["tehty" "tehdaan" "eiTehda"])}
                                       {:name "pvm" :pre-values ["tehty" "tehdaan"] :type :date}
                                       {:name "lisattyLiitteisiin" :pre-values ["tehty"] :type :checkbox}
                                       {:name "syy" :pre-values ["eiTehda"] :type :string}]}
                                {:name "ilmoitusPuhdistuksesta"
                                 :type :group ; TODO: :pre-selector
                                 :body [{:name "ilmoitusTehty" :type :checkbox}
                                        {:name "pvm" :pre-values [true] :type :date}
                                        {:name "lisattyLiitteisiin" :pre-values [true] :type :checkbox}]}
                               {:name "poistettavatAinekset" :type :string :subtype :decimal :unit :tonnia}
                               {:name "sijoituspaikka" :type :string :size :l}]})

(defn ainekset-table [ainekset]
  {:name "ainekset"
   :type :table
   :approvable false
   :body [{:name "aines" :type :select :body (map #(hash-map :name %) ainekset)}
          {:name "hyodynnetaan" :type :string :subtype :number :unit :tonnia :size :s}
          {:name "poisajettavia" :type :string :subtype :number :unit :tonnia :size :s}
          {:name "yhteensa" :type :string :subtype :number :unit :tonnia :size :s}
          {:name "sijoituspaikka" :type :string}]})

(def kaivettava-maa {:name "kaivettavaMaa"
                     :type :group
                     :approvable false
                     :body [(ainekset-table ["louhe" "hiekkaSoraMurskeKivet" "moreeniSiltti" "jaykkaSavi" "pehmeaSavi" "liejuRuoppausmassa" "turve" "kasvualustaMulta"])]})

(def muut-kaivettavat-massat {:name "muutKaivettavatMassat"
                              :type :group
                              :approvable false
                              :rows [["ainekset"]
                                     ["kaivettavienMassojenSelvitys/selvitystapa" "kaivettavienMassojenSelvitys/muuSelvitystapa"]]
                              :template "form-grid-docgen-group-template"
                              :body [(ainekset-table ["betoni" "asfalttijate" "betoniAsfalttiMaaAines" "stabiloituSavi" "kevytsoraVaahtolasimurske" "maatuhka"])
                                     {:name "kaivettavienMassojenSelvitys"
                                      :type :group ; TODO: :pre-selector
                                      :body [{:name "selvitystapa" :type :select :body (map #(hash-map :name %) ["koekuopilla" "vanhoistaSuunnitelmista" "eiSelvitetty" "muu"])}
                                             {:name "muuSelvitystapa" :pre-values ["muu"] :type :string}]}]})

(def orgaaninen-aines {:name "organinenAines"
                       :type :group
                       :approvable false
                       :body [(ainekset-table ["risutHavutOksat" "juurakotKannot"])]})

(def vieraslajit {:name "vieraslajit"
                  :type :group
                  :approvable false
                  :body [{:name "vieraslajit" :type :select :body (map #(hash-map :name %) ["on" "ei" "eiTiedossa"])}
                         {:name "selvitysVieraslajeista" :type :text :max-len 4000 :layout :full-width}]})

(def laajennettu-rakennusjateselvitys [toteutus
                                       purkaminen
                                       vaaralliset-aineet
                                       rakennus-ja-purkujate
                                       pilaantuneet-maat
                                       kaivettava-maa
                                       muut-kaivettavat-massat
                                       orgaaninen-aines
                                       vieraslajit])
