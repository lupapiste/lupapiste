(ns lupapalvelu.operations
  (:use [clojure.tools.logging])
  (:require [lupapalvelu.document.schemas :as schemas]))

(def default-description "operations.default-description")

(def ^:private operations-tree
  [["Rakentaminen ja purkaminen" [["Uuden rakennuksen rakentaminen" [["Asuinrakennus" {:op :asuinrakennus :text default-description}]
                                                                     ["Vapaa-ajan asuinrakennus" {:op :vapaa-ajan-asuinrakennus :text default-description}]
                                                                     ["Varasto, sauna, autotalli tai muu talousrakennus" {:op :varasto-tms :text default-description}]
                                                                     ["Julkinen rakennus" {:op :julkinen-rakennus :text default-description}]
                                                                     ["Muun rakennuksen rakentaminen" {:op :muu-uusi-rakentaminen :text default-description}]]]
                                  ["Rakennuksen korjaaminen tai muuttaminen" [["Rakennuksen laajentaminen tai korjaaminen" {:op :laajentaminen :text default-description}]
                                                                              ["Perustusten tai kantavien rakenteiden muuttaminen tai korjaaminen" {:op :perus-tai-kant-rak-muutos :text default-description}]
                                                                              ["Kayttotarkoituksen muutos" {:op :kayttotark-muutos :text default-description}]
                                                                              ["Rakennuksen julkisivun tai katon muuttaminen" {:op :julkisivu-muutos :text default-description}]
                                                                              ["Asuinhuoneiston jakaminen tai yhdistaminen" {:op :jakaminen-tai-yhdistaminen :text default-description}]
                                                                              ["Markatilan laajentaminen" {:op :markatilan-laajentaminen :text default-description}]
                                                                              ["Takan ja savuhormin rakentaminen" {:op :takka-tai-hormi :text default-description}]
                                                                              ["Parvekkeen tai terassin lasittaminen" {:op :parveke-tai-terassi :text default-description}]
                                                                              ["Muu rakennuksen muutostyo" {:op :muu-laajentaminen :text default-description}]]]
                                  ["Rakennelman rakentaminen" [["Auto- tai grillikatos, vaja, kioski tai vastaava" {:op :auto-katos :text default-description}]
                                                               ["Masto, piippu, sailio, laituri tai vastaava" {:op :masto-tms :text default-description}]
                                                               ["Mainoslaite" {:op :mainoslaite :text default-description}]
                                                               ["Aita" {:op :aita :text default-description}]
                                                               ["Maalampokaivon poraaminen tai lammonkeruuputkiston asentaminen" {:op :maalampo :text default-description}]
                                                               ["Rakennuksen jatevesijarjestelman uusiminen" {:op :jatevesi :text default-description}]
                                                               ["Muun rakennelman rakentaminen" {:op :muu-rakentaminen :text default-description}]]]
                                  ["Rakennuksen purkaminen" {:op :purkaminen :text default-description}]]]
   ["Elinympariston muuttaminen" [["Maisemaa muutava toimenpide" [["Kaivaminen, louhiminen tai maan tayttaminen" {:op :kaivuu :text default-description}]
                                                                  ["Puun kaataminen" {:op :puun-kaataminen :text default-description}]
                                                                  ["Muu maisemaa muuttava toimenpide" {:op :muu-maisema-toimenpide :text default-description}]]]
                                  ["Tontti tai korttelialueen jarjestelymuutos" [["Tontin ajoliittyman muutos" {:op :tontin-ajoliittyman-muutos :text default-description}]
                                                                  ["Paikoitusjarjestelyihin liittyvat muutokset" {:op :paikoutysjarjestus-muutos :text default-description}]
                                                                  ["Korttelin yhteisiin alueisiin liittyva muutos" {:op :kortteli-yht-alue-muutos :text default-description}]
                                                                  ["Muu-tontti-tai-korttelialueen-jarjestelymuutos" {:op :muu-tontti-tai-kort-muutos :text default-description}]]]]]])

(defn municipality-operations [municipality]
  ; Same data for all municipalities for now.
  operations-tree)

; Operations must be the same as in the tree structure above.
; Mappings to schemas and attachments are currently random.

(def ^:private common-schemas ["hankkeen-kuvaus" "maksaja" "rakennuspaikka" "lisatiedot" "paasuunnittelija" "suunnittelija"])

(def ^:private rakennuksen_liitteet [:hakija [:valtakirja]
                                     :rakennuspaikka [:ote_alueen_peruskartasta]
                                     :paapiirustus [:asemapiirros
                                                    :pohjapiirros
                                                    :julkisivupiirros
                                                    :leikkauspiirros]
                                     :ennakkoluvat_ja_lausunnot [:naapurien_suostumukset]])

(def operations
  {:asuinrakennus               {:schema "uusiRakennus"
                                 :schema-data [[["kaytto" "kayttotarkoitus"] schemas/yhden-asunnon-talot]]
                                 :required common-schemas
                                 :attachments rakennuksen_liitteet}
   :vapaa-ajan-asuinrakennus    {:schema "uusiRakennus"
                                 :schema-data [[["kaytto" "kayttotarkoitus"] schemas/vapaa-ajan-asuinrakennus]]
                                 :required common-schemas
                                 :attachments rakennuksen_liitteet}
   :varasto-tms                 {:schema "uusiRakennus"
                                 :schema-data [[["kaytto" "kayttotarkoitus"] schemas/talousrakennus]]
                                 :required common-schemas
                                 :attachments rakennuksen_liitteet}
   :julkinen-rakennus           {:schema "uusiRakennus"
                                 :required common-schemas
                                 :attachments rakennuksen_liitteet}
   :muu-uusi-rakentaminen       {:schema "uusiRakennus"
                                 :required common-schemas
                                 :attachments rakennuksen_liitteet}
   :laajentaminen               {:schema "rakennuksen-muuttaminen"
                                 :schema-data [[["muutostyolaji"] schemas/muumuutostyo]]
                                 :required common-schemas
                                 :attachments rakennuksen_liitteet}
   :perus-tai-kant-rak-muutos   {:schema "rakennuksen-muuttaminen"
                                 :schema-data [[["muutostyolaji"] schemas/perustusten-korjaus]]
                                 :required common-schemas
                                 :attachments rakennuksen_liitteet}
   :kayttotark-muutos           {:schema "rakennuksen-muuttaminen"
                                 :schema-data [[["muutostyolaji"] schemas/kayttotarkotuksen-muutos]]
                                 :required common-schemas
                                 :attachments rakennuksen_liitteet}
   :julkisivu-muutos            {:schema "rakennuksen-muuttaminen"
                                 :schema-data [[["muutostyolaji"] schemas/muumuutostyo]]
                                 :required common-schemas
                                 :attachments rakennuksen_liitteet}
   :jakaminen-tai-yhdistaminen  {:schema "rakennuksen-muuttaminen"
                                 :schema-data [[["muutostyolaji"] schemas/muumuutostyo]]
                                 :required common-schemas
                                 :attachments rakennuksen_liitteet}
   :markatilan-laajentaminen    {:schema "rakennuksen-muuttaminen"
                                 :schema-data [[["muutostyolaji"] schemas/muumuutostyo]]
                                 :required common-schemas
                                 :attachments rakennuksen_liitteet}
   :takka-tai-hormi             {:schema "rakennuksen-muuttaminen"
                                 :schema-data [[["muutostyolaji"] schemas/muumuutostyo]]
                                 :required common-schemas
                                 :attachments rakennuksen_liitteet}
   :parveke-tai-terassi         {:schema "rakennuksen-muuttaminen"
                                 :schema-data [[["muutostyolaji"] schemas/muumuutostyo]]
                                 :required common-schemas
                                 :attachments rakennuksen_liitteet}
   :muu-laajentaminen           {:schema "rakennuksen-muuttaminen"
                                 :schema-data [[["muutostyolaji"] schemas/muumuutostyo]]
                                 :required common-schemas
                                 :attachments rakennuksen_liitteet}
   :auto-katos                  {:schema "kaupunkikuvatoimenpide"
                                 :required common-schemas
                                 :attachments rakennuksen_liitteet}
   :masto-tms                   {:schema "kaupunkikuvatoimenpide"
                                 :required common-schemas
                                 :attachments rakennuksen_liitteet}
   :mainoslaite                 {:schema "kaupunkikuvatoimenpide"
                                 :required common-schemas
                                 :attachments rakennuksen_liitteet}
   :aita                        {:schema "kaupunkikuvatoimenpide"
                                 :required common-schemas
                                 :attachments rakennuksen_liitteet}
   :maalampo                    {:schema "kaupunkikuvatoimenpide"
                                 :required common-schemas
                                 :attachments rakennuksen_liitteet}
   :jatevesi                    {:schema "kaupunkikuvatoimenpide"
                                 :required common-schemas
                                 :attachments rakennuksen_liitteet}
   :muu-rakentaminen            {:schema "kaupunkikuvatoimenpide"
                                 :required common-schemas
                                 :attachments rakennuksen_liitteet}
   :purkaminen                  {:schema "purku"
                                 :required common-schemas
                                 :attachments []}
   :kaivuu                      {:schema "maisematyo"
                                 :required common-schemas
                                 :attachments []}
   :puun-kaataminen             {:schema "maisematyo"
                                 :required common-schemas
                                 :attachments []}
   :muu-maisema-toimenpide      {:schema "maisematyo"
                                 :required  common-schemas
                                 :attachments []}
   :tontin-ajoliittyman-muutos  {:schema "maisematyo"
                                 :required  common-schemas
                                 :attachments []}
   :paikoutysjarjestus-muutos   {:schema "maisematyo"
                                 :required  common-schemas
                                 :attachments []}
   :kortteli-yht-alue-muutos    {:schema "maisematyo"
                                 :required  common-schemas
                                 :attachments []}
   :muu-tontti-tai-kort-muutos {:schema "maisematyo"
                                 :required  common-schemas
                                 :attachments []}})


; Sanity checks:

(doseq [[op info] operations
        schema (cons (:schema info) (:required info))]
  (if-not (schemas/schemas schema) (throw (Exception. (format "Operation '%s' refers to missing schema '%s'" op schema)))))

