(ns lupapalvelu.operations
  (:use [lupapalvelu.log])
  (:require [lupapalvelu.document.schemas :as schemas]
            [lupapalvelu.i18n :as i18n]))

;; Key in applications_[fi|sv].js
(def default-description "operations.default-description")

(def ^:private operations-tree
  ;; These keys as localized in applications_[fi|sv].js
  [["Rakentaminen ja purkaminen" [["Uuden rakennuksen rakentaminen" [["Asuinrakennus" {:op :asuinrakennus :text default-description}]
                                                                     ["Vapaa-ajan asuinrakennus" {:op :vapaa-ajan-asuinrakennus :text default-description}]
                                                                     ["Varasto, sauna, autotalli tai muu talousrakennus" {:op :varasto-tms :text default-description}]
                                                                     ["Julkinen rakennus" {:op :julkinen-rakennus :text default-description}]
                                                                     ["Muu" {:op :muu-uusi-rakentaminen :text default-description}]]]
                                  ["Rakennuksen laajentaminen tai muuttaminen" [["Rakennuksen laajentaminen tai korjaaminen" {:op :laajentaminen :text default-description}]
                                                                                ["Kayttotarkoituksen muutos" {:op :kayttotark-muutos :text default-description}]
                                                                                ["Rakennuksen julkisivun tai katon materiaalin, varin tai muodon muuttaminen" {:op :julkisivu-muutos :text default-description}]
                                                                                ["Asuinhuoneiston jakaminen tai yhdistaminen" {:op :jakaminen-tai-yhdistaminen :text default-description}]
                                                                                ["Markatilan laajentaminen" {:op :markatilan-laajentaminen :text default-description}]
                                                                                ["Takan ja savuhormin rakentaminen" {:op :takka-tai-hormi :text default-description}]
                                                                                ["Parvekkeen tai terassin lasittaminen" {:op :parveke-tai-terassi :text default-description}]
                                                                                ["Muu" {:op :muu-laajentaminen :text default-description}]]]
                                  ["Muu rakentaminen" [["Auto- tai grillikatos, vaja, kioski tai vastaava" {:op :auto-katos :text default-description}]
                                                       ["Masto, piippu, sailio, laituri tai vastaava" {:op :masto-tms :text default-description}]
                                                       ["Mainoslaite" {:op :mainoslaite :text default-description}]
                                                       ["Aita" {:op :aita :text default-description}]
                                                       ["Maalampokaivon poraaminen tai lammonkeruuputkiston asentaminen" {:op :maalampo :text default-description}]
                                                       ["Rakennuksen jatevesijarjestelman uusiminen" {:op :jatevesi :text default-description}]
                                                       ["Muu" {:op :muu-rakentaminen :text default-description}]]]
                                  ["Rakennuksen purkaminen" {:op :purkaminen :text default-description}]]]
   ["Maisemaa muuttava toimenpide" [["Kaivaminen, louhiminen tai maan tayttaminen" {:op :kaivuu :text default-description}]
                                    ["Puun kaataminen" {:op :puun-kaataminen :text default-description}]
                                    ["Muu" {:op :muu-maisema-toimenpide :text default-description}]]]])

(defn municipality-operations [municipality]
  ; Same data for all municipalities for now.
  operations-tree)

; Operations must be the same as in the tree structure above.
; Mappings to schemas and attachments are currently random.

(def ^:private common-schemas ["hankkeen-kuvaus" "maksaja" "rakennuspaikka" "lisatiedot" "paasuunnittelija" "suunnittelija"])

(def operations
  {:asuinrakennus               {:schema "uusiRakennus"
                                 :required common-schemas
                                 :attachments [:hakija [:valtakirja]
                                               :rakennuspaikka [:ote_alueen_peruskartasta]
                                               :paapiirustus [:asemapiirros
                                                              :pohjapiirros
                                                              :julkisivupiirros]
                                               :ennakkoluvat_ja_lausunnot [:naapurien_suostumukset]]}
   :vapaa-ajan-asuinrakennus    {:schema "vapaa-ajan-asuinrakennus"
                                 :required common-schemas
                                 :attachments []}
   :varasto-tms                 {:schema "varasto-tms"
                                 :required common-schemas
                                 :attachments []}
   :julkinen-rakennus           {:schema "julkinen-rakennus"
                                 :required common-schemas
                                 :attachments []}
   :muu-uusi-rakentaminen       {:schema "muu-uusi-rakentaminen"
                                 :required common-schemas
                                 :attachments []}
   :laajentaminen               {:schema "rakennuksen-muuttaminen"
                                 :required common-schemas
                                 :attachments []}
   :kayttotark-muutos           {:schema "kayttotark-muutos"
                                 :required common-schemas
                                 :attachments []}
   :julkisivu-muutos            {:schema "julkisivu-muutos"
                                 :required common-schemas
                                 :attachments []}
   :jakaminen-tai-yhdistaminen  {:schema "jakaminen-tai-yhdistaminen"
                                 :required common-schemas
                                 :attachments []}
   :markatilan-laajentaminen    {:schema "markatilan-laajentaminen"
                                 :required common-schemas
                                 :attachments []}
   :takka-tai-hormi             {:schema "takka-tai-hormi"
                                 :required common-schemas
                                 :attachments []}
   :parveke-tai-terassi         {:schema "parveke-tai-terassi"
                                 :required common-schemas
                                 :attachments []}
   :muu-laajentaminen           {:schema "muu-laajentaminen"
                                 :required common-schemas
                                 :attachments []}
   :auto-katos                  {:schema "auto-katos"
                                 :required common-schemas
                                 :attachments []}
   :masto-tms                   {:schema "masto-tms"
                                 :required common-schemas
                                 :attachments []}
   :mainoslaite                 {:schema "mainoslaite"
                                 :required common-schemas
                                 :attachments []}
   :aita                        {:schema "aita"
                                 :required common-schemas
                                 :attachments []}
   :maalampo                    {:schema "maalampo"
                                 :required common-schemas
                                 :attachments []}
   :jatevesi                    {:schema "jatevesi"
                                 :required common-schemas
                                 :attachments []}
   :muu-rakentaminen            {:schema "muu-rakentaminen"
                                 :required common-schemas
                                 :attachments []}
   :purkaminen                  {:schema "purku"
                                 :required common-schemas
                                 :attachments []}
   :kaivuu                      {:schema "kaivuu"
                                 :required common-schemas
                                 :attachments []}
   :puun-kaataminen             {:schema "puun-kaataminen"
                                 :required common-schemas
                                 :attachments []}
   :muu-maisema-toimenpide      {:schema "muu-maisema-toimenpide"
                                 :required  common-schemas
                                 :attachments []}})

; Sanity checks:

(doseq [[op info] operations
        schema (cons (:schema info) (:required info))]
  (if-not (schemas/schemas schema) (throw (Exception. (format "Operation '%s' refers to missing schema '%s'" op schema)))))

(doseq [[op] operations]
  (let [term (str "operations." (name op))]
    (if-not ((i18n/loc "fi") term) (warn "Missing localization: FI: '%s'" term))))
