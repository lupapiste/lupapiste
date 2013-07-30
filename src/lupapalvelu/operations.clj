(ns lupapalvelu.operations
  (:use [clojure.tools.logging])
  (:require [lupapalvelu.document.schemas :as schemas]
            [lupapalvelu.document.suunnittelutarveratkaisu-ja-poikeamis-schemas :as poischemas]
            [lupapalvelu.document.ymparisto-schemas :as ympschemas]
            [lupapalvelu.document.yleiset-alueet-schemas :as yleiset-alueet]
            [lupapalvelu.core :refer [fail]]
            [sade.env :as env]))

(def default-description "operations.tree.default-description")

(def ^:private operations-tree
  (concat [["Rakentaminen ja purkaminen" [["Uuden rakennuksen rakentaminen" [["Asuinrakennus" :asuinrakennus]
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
                                                                  ["Muu-tontti-tai-korttelialueen-jarjestelymuutos" :muu-tontti-tai-kort-muutos]]]]]]
          (when (env/feature? :poikkari) [["Poikkeusluvat ja suunnittelutarveratkaisut" [["Poikkeuslupa" :poikkeuslupa]
                                                                                        ["Suunnittelutarveratkaisu" :suunnittelutarveratkaisu]]]])
          (when (env/feature? :ymparisto) [["Ymp\u00e4rist\u00f6luvat" [["Meluilmoitus" :meluilmoitus]
                                                                        ["Pima" :pima]
                                                                        ["maa-ainesten_ottaminen" :maa-aineslupa]]]])
          (when (env/feature? :yleiset-alueet) [["yleisten-alueiden-luvat" [["kaivuulupa" :yleiset-alueet-kaivuulupa]
                                                                            ["kayttolupa"
                                                                             [["tyomaasuojat-ja-muut-rakennelmat" :yleiset-alueet-kayttolupa] ;; TODO
                                                                              ["mainoslaitteet-ja-opasteviitat" :mainostus-ja-viitoituslupa]
                                                                              ["muut-yleisten-alueiden-tilojen-kaytot" :yleiset-alueet-kayttolupa] ;; TODO
                                                                              ["messujen-ja-tapahtumien-alueiden-kaytot" :yleiset-alueet-kayttolupa] ;; TODO
                                                                              ["kadulta-tapahtuvat-nostot" :yleiset-alueet-kayttolupa] ;; TODO
                                                                              ["kiinteistojen-tyot-jotka-varaavat-yleisen-alueen-tyomaaksi" :yleiset-alueet-kayttolupa] ;; TODO
                                                                              ["rakennustelineet-kadulla" :yleiset-alueet-kayttolupa] ;; TODO
                                                                              ["muu-kayttolupa" :yleiset-alueet-kayttolupa]]] ;; TODO
                                                                            ["sijoituslupa"
                                                                             [["pysyvien-maanalaisten-rakenteiden-sijoittaminen" :yleiset-alueet-sijoituslupa] ;; TODO
                                                                              ["pysyvien-maanpaallisten-rakenteiden-sijoittaminen" :yleiset-alueet-sijoituslupa] ;; TODO
                                                                              ["muu-sijoituslupa" :yleiset-alueet-sijoituslupa]] ;; TODO
                                                                            #_["liikennetta-haittaavan-tyon-lupa" :liikennetta-haittaavan-tyon-lupa] ;; TODO
                                                                            ]]]])))


(defn municipality-operations [municipality]
  ; Same data for all municipalities for now.
  operations-tree)

; Operations must be the same as in the tree structure above.
; Mappings to schemas and attachments are currently random.

(def ^:private common-schemas ["hankkeen-kuvaus" "maksaja" "rakennuspaikka" "lisatiedot" "paasuunnittelija" "suunnittelija"])


(def ^:private common-ymp-schemas ["ymp-ilm-kesto"])


(def ^:private yleiset-alueet-common-schemas ["yleiset-alueet-maksaja"])


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
                                 :permit-type "R"
                                 :schema-data [[["kaytto" "kayttotarkoitus"] schemas/yhden-asunnon-talot]
                                               [["huoneistot" "0" "huoneistoTunnus" "huoneistonumero"] "001"]] ;FIXME Aftre krysp update change to 000
                                 :required common-schemas
                                 :attachments uuden_rakennuksen_liitteet}
   :vapaa-ajan-asuinrakennus    {:schema "uusiRakennus"
                                 :permit-type "R"
                                 :schema-data [[["kaytto" "kayttotarkoitus"] schemas/vapaa-ajan-asuinrakennus]]
                                 :required common-schemas
                                 :attachments uuden_rakennuksen_liitteet}
   :varasto-tms                 {:schema "uusiRakennus"
                                 :permit-type "R"
                                 :schema-data [[["kaytto" "kayttotarkoitus"] schemas/talousrakennus]]
                                 :required common-schemas
                                 :attachments uuden_rakennuksen_liitteet}
   :julkinen-rakennus           {:schema "uusiRakennus"
                                 :permit-type "R"
                                 :required common-schemas
                                 :attachments uuden_rakennuksen_liitteet}
   :muu-uusi-rakentaminen       {:schema "uusiRakennus"
                                 :permit-type "R"
                                 :required common-schemas
                                 :attachments uuden_rakennuksen_liitteet}
   :laajentaminen               {:schema "rakennuksen-laajentaminen"
                                 :permit-type "R"
                                 :required common-schemas
                                 :attachments rakennuksen_laajennuksen_liitteet}
   :perus-tai-kant-rak-muutos   {:schema "rakennuksen-muuttaminen"
                                 :permit-type "R"
                                 :schema-data [[["muutostyolaji"] schemas/perustusten-korjaus]]
                                 :required common-schemas
                                 :attachments rakennuksen_laajennuksen_liitteet}
   :kayttotark-muutos           {:schema "rakennuksen-muuttaminen"
                                 :permit-type "R"
                                 :schema-data [[["muutostyolaji"] schemas/kayttotarkotuksen-muutos]]
                                 :required common-schemas
                                 :attachments rakennuksen_muutos_liitteet}
   :julkisivu-muutos            {:schema "rakennuksen-muuttaminen"
                                 :permit-type "R"
                                 :schema-data [[["muutostyolaji"] schemas/muumuutostyo]]
                                 :required common-schemas
                                 :attachments rakennuksen_laajennuksen_liitteet}
   :jakaminen-tai-yhdistaminen  {:schema "rakennuksen-muuttaminen"
                                 :permit-type "R"
                                 :schema-data [[["muutostyolaji"] schemas/muumuutostyo]]
                                 :required common-schemas
                                 :attachments rakennuksen_laajennuksen_liitteet}
   :markatilan-laajentaminen    {:schema "rakennuksen-muuttaminen"
                                 :permit-type "R"
                                 :schema-data [[["muutostyolaji"] schemas/muumuutostyo]]
                                 :required common-schemas
                                 :attachments rakennuksen_laajennuksen_liitteet}
   :takka-tai-hormi             {:schema "rakennuksen-muuttaminen"
                                 :permit-type "R"
                                 :schema-data [[["muutostyolaji"] schemas/muumuutostyo]]
                                 :required common-schemas
                                 :attachments rakennuksen_laajennuksen_liitteet}
   :parveke-tai-terassi         {:schema "rakennuksen-muuttaminen"
                                 :permit-type "R"
                                 :schema-data [[["muutostyolaji"] schemas/muumuutostyo]]
                                 :required common-schemas
                                 :attachments rakennuksen_laajennuksen_liitteet}
   :muu-laajentaminen           {:schema "rakennuksen-muuttaminen"
                                 :permit-type "R"
                                 :schema-data [[["muutostyolaji"] schemas/muumuutostyo]]
                                 :required common-schemas
                                 :attachments rakennuksen_laajennuksen_liitteet}
   :auto-katos                  {:schema "kaupunkikuvatoimenpide"
                                 :permit-type "R"
                                 :required common-schemas
                                 :attachments kaupunkikuva_toimenpide_liitteet}
   :masto-tms                   {:schema "kaupunkikuvatoimenpide"
                                 :permit-type "R"
                                 :required common-schemas
                                 :attachments kaupunkikuva_toimenpide_liitteet}
   :mainoslaite                 {:schema "kaupunkikuvatoimenpide"
                                 :permit-type "R"
                                 :required common-schemas
                                 :attachments kaupunkikuva_toimenpide_liitteet}
   :aita                        {:schema "kaupunkikuvatoimenpide"
                                 :permit-type "R"
                                 :required common-schemas
                                 :attachments kaupunkikuva_toimenpide_liitteet}
   :maalampo                    {:schema "kaupunkikuvatoimenpide"
                                 :permit-type "R"
                                 :required common-schemas
                                 :attachments kaupunkikuva_toimenpide_liitteet}
   :jatevesi                    {:schema "kaupunkikuvatoimenpide"
                                 :permit-type "R"
                                 :required common-schemas
                                 :attachments kaupunkikuva_toimenpide_liitteet}
   :muu-rakentaminen            {:schema "kaupunkikuvatoimenpide"
                                 :permit-type "R"
                                 :required common-schemas
                                 :attachments kaupunkikuva_toimenpide_liitteet}
   :purkaminen                  {:schema "purku"
                                 :permit-type "R"
                                 :required common-schemas
                                 :attachments [:muut [:selvitys_rakennusjatteen_maarasta_laadusta_ja_lajittelusta
                                                      :selvitys_purettavasta_rakennusmateriaalista_ja_hyvaksikaytosta]]}
   :kaivuu                      {:schema "maisematyo"
                                 :permit-type "R"
                                 :required common-schemas
                                 :attachments [:paapiirustus [:asemapiirros]]}
   :puun-kaataminen             {:schema "maisematyo"
                                 :permit-type "R"
                                 :required common-schemas
                                 :attachments [:paapiirustus [:asemapiirros]]}
   :muu-maisema-toimenpide      {:schema "maisematyo"
                                 :permit-type "R"
                                 :required  common-schemas
                                 :attachments [:paapiirustus [:asemapiirros]]}
   :tontin-ajoliittyman-muutos  {:schema "maisematyo"
                                 :permit-type "R"
                                 :required  common-schemas
                                 :attachments [:paapiirustus [:asemapiirros]]}
   :paikoutysjarjestus-muutos   {:schema "maisematyo"
                                 :permit-type "R"
                                 :required  common-schemas
                                 :attachments [:paapiirustus [:asemapiirros]]}
   :kortteli-yht-alue-muutos    {:schema "maisematyo"
                                 :permit-type "R"
                                 :required  common-schemas
                                 :attachments [:paapiirustus [:asemapiirros]]}
   :muu-tontti-tai-kort-muutos  {:schema "maisematyo"
                                 :permit-type "R"
                                 :required  common-schemas
                                 :attachments [:paapiirustus [:asemapiirros]]}
   :suunnittelutarveratkaisu    {:schema "suunnittelutarveratkaisun-lisaosa"
                                 :permit-type "R"
                                 :required  (conj common-schemas "rakennushanke")
                                 :attachments [:paapiirustus [:asemapiirros]]}
   :poikkeuslupa                {:schema "poikkeamishakemuksen-lisaosa"
                                 :permit-type "R"
                                 :required  (conj common-schemas "rakennushanke")
                                 :attachments [:paapiirustus [:asemapiirros]]}
   :meluilmoitus                {:schema "meluilmoitus"
                                 :permit-type "R"
                                 :required common-ymp-schemas
                                 :attachments []}
   :pima                        {:schema "pima"
                                 :permit-type "R"
                                 :required ["ymp-ilm-kesto-mini"]
                                 :attachments []}
   :maa-aineslupa               {:schema "ottamismaara"
                                 :permit-type "R"
                                 :required ["maa-ainesluvan-omistaja" "paatoksen-toimitus" "maksaja"
                                            "ottamis-suunnitelman-laatija" "ottamis-suunnitelma"]
                                 :attachments []}
   :yleiset-alueet-kaivuulupa   {:schema "tyomaastaVastaava"
                                 :permit-type "YA"
                                 :schema-data [[["_selected" :value] "yritys"]]
                                 :operation-type :publicArea
                                 :required (conj yleiset-alueet-common-schemas "yleiset-alueet-hankkeen-kuvaus-kaivulupa" "tyoaika")
                                 :attachments []} ;; TODO: Mita attachmentteihin?
   :yleiset-alueet-kayttolupa   {:schema "tyoaika"
                                 :permit-type "YA"
                                 :operation-type :publicArea
                                 :required (conj yleiset-alueet-common-schemas "yleiset-alueet-hankkeen-kuvaus-kaivulupa")
                                 :attachments []} ;; TODO: Mita attachmentteihin?
   :mainostus-ja-viitoituslupa  {:schema "mainosten-tai-viitoitusten-sijoittaminen"
                                 :permit-type "YA"
                                 :operation-type :publicArea
                                 :required yleiset-alueet-common-schemas
                                 :attachments []} ;; TODO: Mita attachmentteihin?
   :yleiset-alueet-sijoituslupa {:schema "yleiset-alueet-hankkeen-kuvaus-sijoituslupa"
                                 :permit-type "YA"
                                 :schema-data [[["_selected" :value] "yritys"]]
                                 :operation-type :publicArea
                                 :required ["sijoituslupa-sijoituksen-tarkoitus"]
                                 :attachments []} ;; TODO: Mita attachmentteihin?

;   :yleiset-alueet-liikennetta-haittaavan-tyon-lupa   {:schema "tyoaika" ;; Mika nimi tassa kuuluu olla?
;                                                       :required (conj yleiset-alueet-common-schemas [])}
   })

(defn permit-type [operation]
  (:permit-type (operations (keyword operation))))

(doseq [[op {:keys [permit-type]}] operations]
  (when-not permit-type
    (throw (Exception. (format "Operation %s does not have permit-type set." op)))))

;;
;; Sanity scheck
;;

(doseq [[op {:keys [schema required]}] operations
        schema (cons schema required)]
  (if-not (schemas/get-schema schema)
    (throw (Exception. (format "Operation '%s' refers to missing schema '%s'" op schema)))))

;;
;; Validate
;;

(defn validate-permit-type-is-not [type]
  (fn [_ application]
    (let [application-permit-type (permit-type application)]
      (when (= (keyword application-permit-type) (keyword type))
        (fail :error.invalid-permit-type :permit-type type)))))

(def validate-permit-type-is-not-ya (validate-permit-type-is-not :YA))

