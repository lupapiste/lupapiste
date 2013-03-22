(ns lupapalvelu.operations
  (:use [clojure.tools.logging])
  (:require [lupapalvelu.document.schemas :as schemas]))

(def default-description "operations.tree.default-description")

(def ^:private operations-tree
  [["Rakentaminen ja purkaminen" [["Uuden rakennuksen rakentaminen" [["Asuinrakennus" :asuinrakennus]
                                                                     ["Vapaa-ajan asuinrakennus" :vapaa-ajan-asuinrakennus]
                                                                     ["Varasto, sauna, autotalli tai muu talousrakennus" :varasto-tms]
                                                                     ["Julkinen rakennus" :julkinen-rakennus]
                                                                     ["Muun rakennuksen rakentaminen" :muu-uusi-rakentaminen]]]
                                  ["Rakennuksen korjaaminen tai muuttaminen" [["Rakennuksen laajentaminen tai korjaaminen" :laajentaminen]
                                                                              ["Perustusten tai kantavien rakenteiden muuttaminen tai korjaaminen" :perus-tai-kant-rak-muutos]
                                                                              ["Kayttotarkoituksen muutos" :kayttotark-muutos]
                                                                              ["Rakennuksen julkisivun tai katon muuttaminen" :julkisivu-muutos]
                                                                              ["Asuinhuoneiston jakaminen tai yhdistaminen" :jakaminen-tai-yhdistaminen]
                                                                              ["Markatilan laajentaminen" :markatilan-laajentaminen]
                                                                              ["Takan ja savuhormin rakentaminen" :takka-tai-hormi]
                                                                              ["Parvekkeen tai terassin lasittaminen" :parveke-tai-terassi]
                                                                              ["Muu rakennuksen muutostyo" :muu-laajentaminen]]]
                                  ["Rakennelman rakentaminen" [["Auto- tai grillikatos, vaja, kioski tai vastaava" :auto-katos]
                                                               ["Masto, piippu, sailio, laituri tai vastaava" :masto-tms]
                                                               ["Mainoslaite" :mainoslaite]
                                                               ["Aita" :aita]
                                                               ["Maalampokaivon poraaminen tai lammonkeruuputkiston asentaminen" :maalampo]
                                                               ["Rakennuksen jatevesijarjestelman uusiminen" :jatevesi]
                                                               ["Muun rakennelman rakentaminen" :muu-rakentaminen]]]
                                  ["Rakennuksen purkaminen" :purkaminen]]]
   ["Elinympariston muuttaminen" [["Maisemaa muutava toimenpide" [["Kaivaminen, louhiminen tai maan tayttaminen" :kaivuu]
                                                                  ["Puun kaataminen" :puun-kaataminen]
                                                                  ["Muu maisemaa muuttava toimenpide" :muu-maisema-toimenpide]]]
                                  ["Tontti tai korttelialueen jarjestelymuutos" [["Tontin ajoliittyman muutos" :tontin-ajoliittyman-muutos]
                                                                  ["Paikoitusjarjestelyihin liittyvat muutokset" :paikoutysjarjestus-muutos]
                                                                  ["Korttelin yhteisiin alueisiin liittyva muutos" :kortteli-yht-alue-muutos]
                                                                  ["Muu-tontti-tai-korttelialueen-jarjestelymuutos" :muu-tontti-tai-kort-muutos]]]]]])

(defn municipality-operations [municipality]
  ; Same data for all municipalities for now.
  operations-tree)

; Operations must be the same as in the tree structure above.
; Mappings to schemas and attachments are currently random.

(def ^:private common-schemas ["hankkeen-kuvaus" "maksaja" "rakennuspaikka" "lisatiedot" "paasuunnittelija" "suunnittelija"])

(def ^:private uuden_rakennuksen_liitteet [:paapiirustus [:asemapiirros
                                                          :pohjapiirros
                                                          :julkisivupiirros
                                                          :leikkauspiirros]
                                           :rakennuspaikka [:selvitys_rakennuspaikan_perustamis_ja_pohjaolosuhteista]])

(def ^:private rakennuksen_muutos_liitteet [:paapiirustus [:pohjapiirros
                                                          :julkisivupiirros]])

(def ^:private rakennuksen_laajennuksen_liitteet [:paapiirustus [:asemapiirros
                                                          :pohjapiirros
                                                          :julkisivupiirros
                                                          :leikkauspiirros]])

(def ^:private kaupunkikuva_toimenpide_liitteet [:paapiirustus [:asemapiirros
                                                                :julkisivupiirros]])

(def operations
  {:asuinrakennus               {:schema "uusiRakennus"
                                 :schema-data [[["kaytto" "kayttotarkoitus"] schemas/yhden-asunnon-talot]]
                                 :required common-schemas
                                 :attachments uuden_rakennuksen_liitteet}
   :vapaa-ajan-asuinrakennus    {:schema "uusiRakennus"
                                 :schema-data [[["kaytto" "kayttotarkoitus"] schemas/vapaa-ajan-asuinrakennus]]
                                 :required common-schemas
                                 :attachments uuden_rakennuksen_liitteet}
   :varasto-tms                 {:schema "uusiRakennus"
                                 :schema-data [[["kaytto" "kayttotarkoitus"] schemas/talousrakennus]]
                                 :required common-schemas
                                 :attachments uuden_rakennuksen_liitteet}
   :julkinen-rakennus           {:schema "uusiRakennus"
                                 :required common-schemas
                                 :attachments uuden_rakennuksen_liitteet}
   :muu-uusi-rakentaminen       {:schema "uusiRakennus"
                                 :required common-schemas
                                 :attachments uuden_rakennuksen_liitteet}
   :laajentaminen               {:schema "rakennuksen-laajentaminen"
                                 :schema-data [[["muutostyolaji"] schemas/muumuutostyo]]
                                 :required common-schemas
                                 :attachments rakennuksen_laajennuksen_liitteet}
   :perus-tai-kant-rak-muutos   {:schema "rakennuksen-muuttaminen"
                                 :schema-data [[["muutostyolaji"] schemas/perustusten-korjaus]]
                                 :required common-schemas
                                 :attachments rakennuksen_laajennuksen_liitteet}
   :kayttotark-muutos           {:schema "rakennuksen-muuttaminen"
                                 :schema-data [[["muutostyolaji"] schemas/kayttotarkotuksen-muutos]]
                                 :required common-schemas
                                 :attachments rakennuksen_muutos_liitteet}
   :julkisivu-muutos            {:schema "rakennuksen-muuttaminen"
                                 :schema-data [[["muutostyolaji"] schemas/muumuutostyo]]
                                 :required common-schemas
                                 :attachments rakennuksen_laajennuksen_liitteet}
   :jakaminen-tai-yhdistaminen  {:schema "rakennuksen-muuttaminen"
                                 :schema-data [[["muutostyolaji"] schemas/muumuutostyo]]
                                 :required common-schemas
                                 :attachments rakennuksen_laajennuksen_liitteet}
   :markatilan-laajentaminen    {:schema "rakennuksen-muuttaminen"
                                 :schema-data [[["muutostyolaji"] schemas/muumuutostyo]]
                                 :required common-schemas
                                 :attachments rakennuksen_laajennuksen_liitteet}
   :takka-tai-hormi             {:schema "rakennuksen-muuttaminen"
                                 :schema-data [[["muutostyolaji"] schemas/muumuutostyo]]
                                 :required common-schemas
                                 :attachments rakennuksen_laajennuksen_liitteet}
   :parveke-tai-terassi         {:schema "rakennuksen-muuttaminen"
                                 :schema-data [[["muutostyolaji"] schemas/muumuutostyo]]
                                 :required common-schemas
                                 :attachments rakennuksen_laajennuksen_liitteet}
   :muu-laajentaminen           {:schema "rakennuksen-muuttaminen"
                                 :schema-data [[["muutostyolaji"] schemas/muumuutostyo]]
                                 :required common-schemas
                                 :attachments rakennuksen_laajennuksen_liitteet}
   :auto-katos                  {:schema "kaupunkikuvatoimenpide"
                                 :required common-schemas
                                 :attachments kaupunkikuva_toimenpide_liitteet}
   :masto-tms                   {:schema "kaupunkikuvatoimenpide"
                                 :required common-schemas
                                 :attachments kaupunkikuva_toimenpide_liitteet}
   :mainoslaite                 {:schema "kaupunkikuvatoimenpide"
                                 :required common-schemas
                                 :attachments kaupunkikuva_toimenpide_liitteet}
   :aita                        {:schema "kaupunkikuvatoimenpide"
                                 :required common-schemas
                                 :attachments kaupunkikuva_toimenpide_liitteet}
   :maalampo                    {:schema "kaupunkikuvatoimenpide"
                                 :required common-schemas
                                 :attachments kaupunkikuva_toimenpide_liitteet}
   :jatevesi                    {:schema "kaupunkikuvatoimenpide"
                                 :required common-schemas
                                 :attachments kaupunkikuva_toimenpide_liitteet}
   :muu-rakentaminen            {:schema "kaupunkikuvatoimenpide"
                                 :required common-schemas
                                 :attachments kaupunkikuva_toimenpide_liitteet}
   :purkaminen                  {:schema "purku"
                                 :required common-schemas
                                 :attachments [:muut [:selvitys_rakennusjatteen_maarasta_laadusta_ja_lajittelusta
                                                      :selvitys_purettavasta_rakennusmateriaalista_ja_hyvaksikaytosta]]}
   :kaivuu                      {:schema "maisematyo"
                                 :required common-schemas
                                 :attachments [:paapiirustus [:asemapiirros]]}
   :puun-kaataminen             {:schema "maisematyo"
                                 :required common-schemas
                                 :attachments [:paapiirustus [:asemapiirros]]}
   :muu-maisema-toimenpide      {:schema "maisematyo"
                                 :required  common-schemas
                                 :attachments [:paapiirustus [:asemapiirros]]}
   :tontin-ajoliittyman-muutos  {:schema "maisematyo"
                                 :required  common-schemas
                                 :attachments [:paapiirustus [:asemapiirros]]}
   :paikoutysjarjestus-muutos   {:schema "maisematyo"
                                 :required  common-schemas
                                 :attachments [:paapiirustus [:asemapiirros]]}
   :kortteli-yht-alue-muutos    {:schema "maisematyo"
                                 :required  common-schemas
                                 :attachments [:paapiirustus [:asemapiirros]]}
   :muu-tontti-tai-kort-muutos {:schema "maisematyo"
                                 :required  common-schemas
                                 :attachments [:paapiirustus [:asemapiirros]]}})


; Sanity checks:

(doseq [[op info] operations
        schema (cons (:schema info) (:required info))]
  (if-not (schemas/schemas schema) (throw (Exception. (format "Operation '%s' refers to missing schema '%s'" op schema)))))

