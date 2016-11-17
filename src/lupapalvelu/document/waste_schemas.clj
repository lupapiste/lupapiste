(ns lupapalvelu.document.waste-schemas
  (:require [lupapalvelu.document.tools :refer [body] :as tools]
            [lupapalvelu.document.schemas :refer [defschemas]]
            [lupapalvelu.states :as states]))

(def basic-construction-waste-plan-name "rakennusjatesuunnitelma")
(def basic-construction-waste-report-name "rakennusjateselvitys")
(def extended-construction-waste-report-name "laajennettuRakennusjateselvitys")

(def construction-waste-report-schemas #{basic-construction-waste-report-name extended-construction-waste-report-name})

(defn construction-waste-plan-for-organization [{:keys [extended-construction-waste-report-enabled] :as org}]
  (if extended-construction-waste-report-enabled
    extended-construction-waste-report-name
    basic-construction-waste-plan-name))

(def jatetyyppi {:name "jatetyyppi"
                 :type :select
                 :css [:dropdown]
                 :i18nkey "jatetyyppi"
                 :body [{:name "betoni"}
                        {:name "kipsi"}
                        {:name "puu"}
                        {:name "metalli"}
                        {:name "lasi"}
                        {:name "muovi"}
                        {:name "paperi"}
                        {:name "maa"}]})

(def vaarallinenainetyyppi {:name "vaarallinenainetyyppi"
                            :type :select
                            :css [:dropdown]
                            :i18nkey "vaarallinenainetyyppi"
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

(def jateyksikko {:name "yksikko" :i18nkey "jateyksikko"
                  :type :select
                  :css [:dropdown]
                  :body [{:name "kg"}
                         {:name "tonni"}
                         {:name "m2"}
                         {:name "m3"}]})

(def rakennusjatemaara {:name "maara"
                        :type :string :subtype :decimal
                        :uicomponent :docgen-input
                        :inputType :string
                        :css [:grid-style-input]
                        :min 0 :max 9999999 :size :s})

(def rakennusjatesuunnitelmaRow [(assoc rakennusjatemaara :name "suunniteltuMaara")
                                 jateyksikko
                                 (assoc rakennusjatemaara :name "painoT")])

(def rakennusjateselvitysUusiRow [(assoc rakennusjatemaara :name "toteutunutMaara")
                                  jateyksikko
                                  (assoc rakennusjatemaara :name "painoT")
                                  {:name "jatteenToimituspaikka"
                                   :type :string
                                   :css [:grid-style-input]
                                   :max-len 50}])

(def rakennusjateselvitysRow [(assoc rakennusjatemaara :name "suunniteltuMaara" :readonly true)
                              (assoc rakennusjatemaara :name "toteutunutMaara")
                              (assoc jateyksikko :readonly true)
                              (assoc rakennusjatemaara :name "painoT")
                              {:name "jatteenToimituspaikka"
                               :type :string
                               :css [:grid-style-input]
                               :max-len 50}])

(def availableMaterialsRow [{:name "aines"
                             :type :string
                             :css [:grid-style-input]}
                           rakennusjatemaara
                           jateyksikko
                            {:name "saatavilla"
                             :type :date
                             }
                            {:name "kuvaus"
                             :type :string
                             :css [:grid-style-input]}])

(def contact {:name "contact"
              :i18nkey "available-materials.contact"
              :type :group
              :uicomponent :docgenGroup
              :approvable false
              :exclude-from-pdf true
              :template "form-grid-docgen-group-template"
              :rows [{:h3 "available-materials.contact"}
                     {:p "contact.help"}
                     ["name" "phone" "email"]]
              :group-help "contact.help"
              :body [{:name "name" :type :string}
                     {:name "phone" :type :string :subtype :tel}
                     {:name "email" :type :string :subtype :email}
                     ]})

(def available-materials {:name "availableMaterials"
                          :i18nkey "available-materials"
                          :type :table
                          :uicomponent :docgenTable
                          :exclude-from-pdf true
                          :css [:form-table :form-table--waste]
                          :repeating true
                          :body (tools/body availableMaterialsRow)})


(def rakennusjatesuunnitelma [{:name "rakennusJaPurkujate"
                               :i18nkey "rakennusJaPurkujate"
                               :type :table
                               :uicomponent :docgenTable
                               :css [:form-table :form-table--waste]
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
                           contact
                           available-materials])

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
                         {:row ["oljysailioidenLukumaara" "sailioilleSuunnitellutToimenpiteet/toimenpide"
                                "sailioilleSuunnitellutToimenpiteet/muuToimenpide"]
                          :css [:label-x-2]}
                         {:row ["etaisyysPohjavesialueesta" "tarkeallaPohjavesialueella::2"]
                          :css [:label-x-2]}
                         {:row ["mineraalisenPurkujatteenKasittely/kasittelytapa"
                                "mineraalisenPurkujatteenKasittely/muuKasittelytapa::2"]
                          :css [:label-x-2]}
                         ["polynLeviamisenEsto::4"]
                         {:row ["ilmoitusHairitsevastaMelusta/ilmoitusTehty"
                                "ilmoitusHairitsevastaMelusta/pvm"
                                "ilmoitusHairitsevastaMelusta/lisattyLiitteisiin"
                                "ilmoitusHairitsevastaMelusta/syy::2"]
                          :css [:label-x-3]}]
                 :template "form-grid-docgen-group-template"
                 :body [{:name "purettavaKerrosala" :type :string :subtype :decimal}
                        {:name "rakennusvuosi" :type :string :subtype :number}
                        {:name "oljysailioidenLukumaara" :type :string :subtype :number}
                        {:name "sailioilleSuunnitellutToimenpiteet"
                         :type :group
                         :body [{:name "toimenpide" :type :select :css [:form-input :dropdown]
                                 :body (map #(hash-map :name %) ["jaavatKayttoon" "poistetaan" "muu"])}
                                {:name "muuToimenpide" :show-when {:path "toimenpide" :values #{"muu"}} :type :string}]}
                        {:name "etaisyysPohjavesialueesta" :type :string}
                        {:name "tarkeallaPohjavesialueella" :type :checkbox :inputType :checkbox-wrapper}
                        {:name "mineraalisenPurkujatteenKasittely"
                         :type :group
                         :body [{:name "kasittelytapa" :type :select :css [:form-input :dropdown]
                                 :body (map #(hash-map :name %) ["murskaus" "pulverointi" "muu"])}
                                {:name "muuKasittelytapa" :show-when {:path "kasittelytapa" :values #{"muu"}} :type :string}]}
                        {:name "polynLeviamisenEsto" :type :text :max-len 4000}
                        {:name "ilmoitusHairitsevastaMelusta"
                         :type :group
                         :body [{:name "ilmoitusTehty" :type :select :css [:dropdown]
                                 :body (map #(hash-map :name %) ["tehty" "tehdaan" "eiTehda"])}
                                {:name "pvm" :show-when {:path "ilmoitusTehty" :values #{"tehty" "tehdaan"}} :type :date}
                                {:name "lisattyLiitteisiin" :show-when {:path "ilmoitusTehty" :values #{"tehty"}}
                                 :type :checkbox :inputType :checkbox-wrapper}
                                {:name "syy" :show-when {:path "ilmoitusTehty" :values #{"eiTehda"}} :type :string}]}]})

(def vaaralliset-aineet {:name "vaarallisetAineet"
                         :type :group
                         :uicomponent :docgenGroup
                         :approvable false
                         :rows [{:h3 "laajennettuRakennusjateselvitys.vaarallisetAineet"}
                                ["eiVaarallisiaAineita"]
                                {:row ["kartoitusVaarallisistaAineista/ilmoitusTehty"
                                       "kartoitusVaarallisistaAineista/pvm"
                                       "kartoitusVaarallisistaAineista/lisattyLiitteisiin"
                                       "kartoitusVaarallisistaAineista/syy::2"]
                                 :css [:label-x-2]}]
                         :template "form-grid-docgen-group-template"
                         :body [{:name "eiVaarallisiaAineita" :type :checkbox :inputType :checkbox-wrapper}
                                {:name "kartoitusVaarallisistaAineista"
                                 :hide-when {:path "/vaarallisetAineet/eiVaarallisiaAineita"
                                             :values #{true}}
                                 :type :group
                                 :body [{:name "ilmoitusTehty" :type :select :css [:form-input :dropdown]
                                         :body (map #(hash-map :name %) ["tehty" "tehdaan" "eiTehda"])}
                                        {:name "pvm" :show-when {:path "ilmoitusTehty" :values #{"tehty" "tehdaan"}} :type :date}
                                        {:name "lisattyLiitteisiin" :show-when {:path "ilmoitusTehty" :values #{"tehty"}}
                                         :type :checkbox :inputType :checkbox-wrapper}
                                        {:name "syy" :show-when {:path "ilmoitusTehty" :values #{"eiTehda"}} :type :string}]}]})

(def rakennus-ja-purkujate {:name "rakennusJaPurkujate"
                            :type :group
                            :uicomponent :docgenGroup
                            :rows [{:h3 "laajennettuRakennusjateselvitys.rakennusJaPurkujate"}
                                   ["vaarallisetJatteet::4"]
                                   ["muuJate::4"]]
                            :template "form-grid-docgen-group-template"
                            :body [{:name "vaarallisetJatteet"
                                    :hide-when {:path "/vaarallisetAineet/eiVaarallisiaAineita"
                                                :values #{true}}
                                    :type :table
                                    :css [:form-table :form-table--waste]
                                    :repeating true
                                    :approvable false
                                    :footer-sums [{:amount "maara" :unit "yksikko"}]
                                    :body [{:name "jate" :type :select :css [:dropdown]
                                            :body (map #(hash-map :name %) ["kreosiittiJate" "pcbJate" "asbestiJate" "kyllastettyPuu"])}
                                           {:name "maara" :type :string :subtype :number :size :s}
                                           {:name "yksikko" :type :select :css [:dropdown]
                                            :body (map #(hash-map :name % :i18nkey (str "unit." %)) ["kg" "tonnia"])}
                                           {:name "sijoituspaikka" :type :string}]}
                                   {:name "muuJate"
                                    :type :table
                                    :css [:form-table :form-table--waste]
                                    :repeating true
                                    :approvable false
                                    :footer-sums [{:amount "maara" :unitKey :t}]
                                    :body [{:name "jate" :type :select :css [:dropdown]
                                            :body (map #(hash-map :name %) ["betoni" "tiilet" "pinnoittamatonPuu" "pinnoitettuPuu" "sekajate"])}
                                           {:name "maara" :type :string :subtype :number}
                                           {:name "sijoituspaikka" :type :string}]}]})

(def pilaantuneet-maat {:name "pilaantuneetMaat"
                        :type :group
                        :uicomponent :docgenGroup
                        :approvable false
                        :rows [{:h3 "laajennettuRakennusjateselvitys.pilaantuneetMaat"}
                               {:row ["tutkimusPilaantuneistaMaista/tutkimusTehty"
                                      "tutkimusPilaantuneistaMaista/pvm"
                                      "tutkimusPilaantuneistaMaista/lisattyLiitteisiin"
                                      "tutkimusPilaantuneistaMaista/syy::2"]
                                :css [:label-x-2]}
                               {:row ["ilmoitusPuhdistuksesta/ilmoitusTehty::2"
                                      "ilmoitusPuhdistuksesta/pvm"
                                      "ilmoitusPuhdistuksesta/lisattyLiitteisiin"]
                                :css [:row-tight]}
                               {:row ["poistettavatAinekset" "sijoituspaikka::3"]
                                :css [:label-x-2]}]
                        :template "form-grid-docgen-group-template"
                        :body [{:name "tutkimusPilaantuneistaMaista"
                                :type :group
                                :body [{:name "tutkimusTehty" :type :select :css [:form-input :dropdown]
                                        :body (map #(hash-map :name %) ["tehty" "tehdaan" "eiTehda"])}
                                       {:name "pvm" :show-when {:path "tutkimusTehty" :values #{"tehty" "tehdaan"}} :type :date}
                                       {:name "lisattyLiitteisiin" :show-when {:path "tutkimusTehty" :values #{"tehty"}}
                                        :type :checkbox :inputType :checkbox-wrapper}
                                       {:name "syy" :show-when {:path "tutkimusTehty" :values #{"eiTehda"}} :type :string}]}
                                {:name "ilmoitusPuhdistuksesta"
                                 :type :group
                                 :body [{:name "ilmoitusTehty" :type :checkbox :inputType :checkbox-wrapper}
                                        {:name "pvm" :show-when {:path "ilmoitusTehty" :values #{true}} :type :date}
                                        {:name "lisattyLiitteisiin" :show-when {:path "ilmoitusTehty" :values #{true}}
                                         :type :checkbox :inputType :checkbox-wrapper}]}
                               {:name "poistettavatAinekset" :type :string :subtype :decimal :unit :tonnia}
                               {:name "sijoituspaikka" :type :string}]})

(defn ainekset-table [ainekset]
  {:name "ainekset"
   :type :table
   :repeating true
   :css [:form-table :form-table--waste]
   :approvable false
   :body [{:name "aines-group"
           :type :group
           :i18nkey "waste"
           :approvable false
           :template "simple-docgen-group-template"
           :body [{:name "aines" :type :select :label false
                   :css [:dropdown]
                   :body (conj (mapv #(hash-map :name %) ainekset)
                               {:name "muu" :i18nkey "select-other"})
                   :hide-when {:path "aines" :values #{"muu"}}}
                  {:name "muu" :type :string :label false
                   :show-when {:path "aines" :values #{"muu"}}}]}
          {:name "hyodynnetaan" :type :string :subtype :number}
          {:name "poisajettavia" :type :string :subtype :number}
          {:name "yhteensa" :type :calculation
           :columns ["hyodynnetaan" "poisajettavia"]}
          {:name "sijoituspaikka" :type :string}]
   :footer-sums [{:amount"hyodynnetaan" :unitKey :t}
                 {:amount "poisajettavia" :unitKey :t} "yhteensa"]})

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
                                     ["kaivettavienMassojenSelvitys/selvitystapa::2" "kaivettavienMassojenSelvitys/muuSelvitystapa::2"]]
                              :template "form-grid-docgen-group-template"
                              :body [(ainekset-table ["betoni" "asfalttijate" "betoniAsfalttiMaaAines" "stabiloituSavi" "kevytsoraVaahtolasimurske" "maatuhka"])
                                     {:name "kaivettavienMassojenSelvitys"
                                      :type :group
                                      :body [{:name "selvitystapa" :type :select :css [:form-input :dropdown]
                                              :body (map #(hash-map :name %) ["koekuopilla" "vanhoistaSuunnitelmista" "eiSelvitetty" "muu"])}
                                             {:name "muuSelvitystapa" :show-when {:path "selvitystapa" :values #{"muu"}} :type :string}]}]})

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
                  :body [{:name "vieraslajit" :type :select :css [:dropdown] :label false
                          :body (map #(hash-map :name %) ["on" "ei" "eiTiedossa"])}
                         {:name "selvitysVieraslajeista" :type :text :max-len 4000}]})


(def laajennettu-rakennusjateselvitys [toteutus
                                       purkaminen
                                       vaaralliset-aineet
                                       rakennus-ja-purkujate
                                       pilaantuneet-maat
                                       kaivettava-maa
                                       muut-kaivettavat-massat
                                       orgaaninen-aines
                                       vieraslajit
                                       contact
                                       available-materials])

(defschemas 1
  [{:info {:name basic-construction-waste-plan-name ; "rakennusjatesuunnitelma"
           :order 200
           :section-help "rakennusjate.help"}
    :body (body rakennusjatesuunnitelma)}
   {:info {:name basic-construction-waste-report-name ; "rakennusjateselvitys"
           :order 201
           :editable-in-states states/post-verdict-states
           :section-help "rakennusjate.help"}
    :body (body rakennusjateselvitys)}

   {:info {:name extended-construction-waste-report-name ; "laajennettuRakennusjateselvitys"
           :order 200
           :editable-in-states (states/all-application-states-but states/terminal-states)
           :section-help "rakennusjateLaajennettu.help"}
    :body (body laajennettu-rakennusjateselvitys)}])
