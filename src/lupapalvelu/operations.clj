(ns lupapalvelu.operations
  (:require [taoensso.timbre :as timbre :refer (trace debug info warn error fatal)]
            [lupapalvelu.document.schemas :as schemas]
            [lupapalvelu.document.poikkeamis-schemas :as poischemas]
            [lupapalvelu.document.ymparisto-schemas :as ympschemas]
            [lupapalvelu.document.yleiset-alueet-schemas :as yleiset-alueet]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.core :refer :all]
            [sade.util :refer :all]
            [sade.env :as env]))

(def default-description "operations.tree.default-description")

(def ^:private operation-tree-for-R
  {:permit-type permit/R
   :tree ["Rakentaminen ja purkaminen"
          [["Uuden rakennuksen rakentaminen"
            [["Asuinrakennus" :asuinrakennus]
             ["Vapaa-ajan asuinrakennus" :vapaa-ajan-asuinrakennus]
             ["Varasto, sauna, autotalli tai muu talousrakennus" :varasto-tms]
             ["Julkinen rakennus" :julkinen-rakennus]
             ["Muun rakennuksen rakentaminen" :muu-uusi-rakentaminen]]]
           ["Rakennuksen korjaaminen tai muuttaminen"
            [["Rakennuksen laajentaminen tai korjaaminen" :laajentaminen]
             ["Perustusten tai kantavien rakenteiden muuttaminen tai korjaaminen" :perus-tai-kant-rak-muutos]
             ["Kayttotarkoituksen muutos" :kayttotark-muutos]
             ["Rakennuksen julkisivun tai katon muuttaminen" :julkisivu-muutos]
             ["Asuinhuoneiston jakaminen tai yhdistaminen" :jakaminen-tai-yhdistaminen]
             ["Markatilan laajentaminen" :markatilan-laajentaminen]
             ["Takan ja savuhormin rakentaminen" :takka-tai-hormi]
             ["Parvekkeen tai terassin lasittaminen" :parveke-tai-terassi]
             ["Muu rakennuksen muutostyo" :muu-laajentaminen]]]
           ["Rakennelman rakentaminen"
            [["Auto- tai grillikatos, vaja, kioski tai vastaava" :auto-katos]
             ["Masto, piippu, sailio, laituri tai vastaava" :masto-tms]
             ["Mainoslaite" :mainoslaite]
             ["Aita" :aita]
             ["Maalampokaivon poraaminen tai lammonkeruuputkiston asentaminen" :maalampo]
             ["Rakennuksen jatevesijarjestelman uusiminen" :jatevesi]
             ["Muun rakennelman rakentaminen" :muu-rakentaminen]]]
           ["Rakennuksen purkaminen" :purkaminen]]
          ["Elinympariston muuttaminen"
           [["Maisemaa muutava toimenpide"
             [["Kaivaminen, louhiminen tai maan tayttaminen" :kaivuu]
              ["Puun kaataminen" :puun-kaataminen]
              ["Muu maisemaa muuttava toimenpide" :muu-maisema-toimenpide]]]
            ["Tontti tai korttelialueen jarjestelymuutos"
             [["Tontin ajoliittyman muutos" :tontin-ajoliittyman-muutos]
              ["Paikoitusjarjestelyihin liittyvat muutokset" :paikoutysjarjestus-muutos]
              ["Korttelin yhteisiin alueisiin liittyva muutos" :kortteli-yht-alue-muutos]
              ["Muu-tontti-tai-korttelialueen-jarjestelymuutos" :muu-tontti-tai-kort-muutos]]]]]]})

(def ^:private operation-tree-for-YA
  {:permit-type permit/YA
   :tree ["yleisten-alueiden-luvat"
          [["kaivuulupa" :yleiset-alueet-kaivuulupa]
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
            ]]]})

(def ^:private operation-tree-for-P
  {:permit-type permit/P
   :tree ["Poikkeusluvat ja suunnittelutarveratkaisut" :poikkeamis]})

(def ^:private operation-tree-for-Y
  {:permit-type permit/Y
   :tree ["Ymp\u00e4rist\u00f6luvat"
          [["Meluilmoitus" :meluilmoitus]
           ["Pima" :pima]
           ["maa-ainesten_ottaminen" :maa-aineslupa]]]})

(def ^:private operation-tree
  (vector operation-tree-for-R
    (when (env/feature? :poikkari) operation-tree-for-P)
    (when (env/feature? :ymparisto) operation-tree-for-Y)
    (when (env/feature? :yleiset-alueet) operation-tree-for-YA)))

(defn all-operations []
  (->> operation-tree (keep :tree)))

(defn operations-for-permit-type [permit-type]
  (->> operation-tree
    (filter (fn->> :permit-type (= permit-type))) (keep :tree)))

;; TODO: implement
(defn municipality-operations [municipality] (all-operations))

; Operations must be the same as in the tree structure above.
; Mappings to schemas and attachments are currently random.

(def ^:private common-schemas ["hankkeen-kuvaus" "maksaja" "rakennuspaikka" "lisatiedot" "paasuunnittelija" "suunnittelija"])


(def ^:private common-ymp-schemas ["ymp-ilm-kesto"])


(def ^:private yleiset-alueet-common-schemas ["yleiset-alueet-maksaja"])


(def ^:private uuden_rakennuksen_liitteet [:paapiirustus
                                           [:asemapiirros
                                            :pohjapiirros
                                            :julkisivupiirros
                                            :leikkauspiirros]
                                           :rakennuspaikka
                                           [:selvitys_rakennuspaikan_perustamis_ja_pohjaolosuhteista]])

(def ^:private rakennuksen_muutos_liitteet [:paapiirustus
                                            [:pohjapiirros
                                             :julkisivupiirros]])

(def ^:private rakennuksen_laajennuksen_liitteet [:paapiirustus
                                                  [:asemapiirros
                                                   :pohjapiirros
                                                   :julkisivupiirros
                                                   :leikkauspiirros]])

(def ^:private kaupunkikuva_toimenpide_liitteet [:paapiirustus
                                                 [:asemapiirros
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
   :poikkeamis                  {:schema "rakennushanke"
                                 :permit-type "P"
                                 :required  (conj common-schemas "suunnittelutarveratkaisun-lisaosa")
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
                                 :required (conj yleiset-alueet-common-schemas "yleiset-alueet-hankkeen-kuvaus-kaivulupa" "tyoaika")
                                 :attachments []} ;; TODO: Mita attachmentteihin?
   :yleiset-alueet-kayttolupa   {:schema "tyoaika"
                                 :permit-type "YA"
                                 :required (conj yleiset-alueet-common-schemas "yleiset-alueet-hankkeen-kuvaus-kaivulupa")
                                 :attachments []} ;; TODO: Mita attachmentteihin?
   :mainostus-ja-viitoituslupa  {:schema "mainosten-tai-viitoitusten-sijoittaminen"
                                 :permit-type "YA"
                                 :required yleiset-alueet-common-schemas
                                 :attachments []} ;; TODO: Mita attachmentteihin?
   :yleiset-alueet-sijoituslupa {:schema "yleiset-alueet-hankkeen-kuvaus-sijoituslupa"
                                 :permit-type "YA"
                                 :schema-data [[["_selected" :value] "yritys"]]
                                 :required ["sijoituslupa-sijoituksen-tarkoitus"]
                                 :attachments []} ;; TODO: Mita attachmentteihin?

;   :yleiset-alueet-liikennetta-haittaavan-tyon-lupa   {:schema "tyoaika" ;; Mika nimi tassa kuuluu olla?
;                                                       :required (conj yleiset-alueet-common-schemas [])}
   })

(defn permit-type-of-operation [operation]
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
;; Actions
;;

(defquery "operations"
  {:description "returns operations: without parameters all, with permitType-parameter just those operations"}
  [{{:keys [permitType]} :data}]
  (if permitType
    (ok :operations (operations-for-permit-type permitType))
    (ok :operations (all-operations))))
