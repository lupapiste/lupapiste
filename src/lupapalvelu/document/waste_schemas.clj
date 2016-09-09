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
