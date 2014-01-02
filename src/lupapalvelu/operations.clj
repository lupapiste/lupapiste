(ns lupapalvelu.operations
  (:require [taoensso.timbre :as timbre :refer [trace debug info warn error fatal]]
            [sade.util :refer :all]
            [sade.env :as env]
            [lupapalvelu.core :refer [ok]]
            [lupapalvelu.action :refer [defquery]]
            [lupapalvelu.attachment :as attachment]
            [lupapalvelu.document.schemas :as schemas]
            [lupapalvelu.document.poikkeamis-schemas]
            [lupapalvelu.document.ymparisto-schemas]
            [lupapalvelu.document.yleiset-alueet-schemas]
            [lupapalvelu.permit :as permit]))

(def default-description "operations.tree.default-description")

(def ^:private operation-tree-for-R
  (let [treepart [["Uuden rakennuksen rakentaminen"
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
        ]
    {:permit-type permit/R
     :tree ["Rakentaminen ja purkaminen"
            (let [treepart (if (env/feature? :rakentamisen-aikaiset-tyonjohtaja)
                             (conj treepart ["Tyonjohtaja" :tyonjohtajan-nimeaminen])
                             treepart)
                  treepart (if (env/feature? :rakentamisen-aikaiset-suunnittelija)
                             (conj treepart ["Suunnittelija" :suunnittelijan-nimeaminen])
                             treepart)
                  treepart (if (env/feature? :jatkoaika)
                             (conj treepart ["Jatkoaika" :jatkoaika])
                             treepart)
                  treepart (if (env/feature? :aloitusoikeus)
                             (conj treepart ["Aloitusoikeus" :aloitusoikeus])
                             treepart)]
              treepart)]}))

(def ^:private operation-tree-for-environment-R
  {:permit-type permit/R
   :tree ["Elinympariston muuttaminen"
          [["Maisemaa muutava toimenpide"
            [["Kaivaminen, louhiminen tai maan tayttaminen" :kaivuu]
             ["Puun kaataminen" :puun-kaataminen]
             ["Muu maisemaa muuttava toimenpide" :muu-maisema-toimenpide]]]
           ["Tontti tai korttelialueen jarjestelymuutos"
            [["Tontin ajoliittyman muutos" :tontin-ajoliittyman-muutos]
             ["Paikoitusjarjestelyihin liittyvat muutokset" :paikoutysjarjestus-muutos]
             ["Korttelin yhteisiin alueisiin liittyva muutos" :kortteli-yht-alue-muutos]
             ["Muu-tontti-tai-korttelialueen-jarjestelymuutos" :muu-tontti-tai-kort-muutos]]]]]})

(def ^:private operation-tree-for-YA
  {:permit-type permit/YA
   :tree ["yleisten-alueiden-luvat"
          [["sijoituslupa"
            [["pysyvien-maanalaisten-rakenteiden-sijoittaminen"
              [["vesi-ja-viemarijohtojen-sijoittaminen" :ya-sijoituslupa-vesi-ja-viemarijohtojen-sijoittaminen]
               ["maalampoputkien-sijoittaminen" :ya-sijoituslupa-maalampoputkien-sijoittaminen]
               ["sahko-data-ja-muiden-kaapelien-sijoittaminen" :ya-sijoituslupa-sahko-data-ja-muiden-kaapelien-sijoittaminen]]]
             ["pysyvien-maanpaallisten-rakenteiden-sijoittaminen"
              [["ilmajohtojen-sijoittaminen" :ya-sijoituslupa-ilmajohtojen-sijoittaminen]
               ["muuntamoiden-sijoittaminen" :ya-sijoituslupa-muuntamoiden-sijoittaminen]
               ["jatekatoksien-sijoittaminen" :ya-sijoituslupa-jatekatoksien-sijoittaminen]
               ["leikkipaikan-tai-koiratarhan-sijoittaminen" :ya-sijoituslupa-leikkipaikan-tai-koiratarhan-sijoittaminen]]]
            ["muu-sijoituslupa" :ya-sijoituslupa-muu-sijoituslupa]]]
           ["katulupa"
            [["kaivaminen-yleisilla-alueilla"
              [["vesi-ja-viemarityot" :ya-katulupa-vesi-ja-viemarityot]
               ["kaukolampotyot" :ya-katulupa-kaukolampotyot]
               ["kaapelityot" :ya-katulupa-kaapelityot]
               ["kiinteiston-johto-kaapeli-ja-putkiliitynnat" :ya-katulupa-kiinteiston-johto-kaapeli-ja-putkiliitynnat]]]
             ["liikennealueen-rajaaminen-tyokayttoon"
              [["nostotyot" :ya-kayttolupa-nostotyot]
               ["vaihtolavat" :ya-kayttolupa-vaihtolavat]
               ["kattolumien-pudotustyot" :ya-kayttolupa-kattolumien-pudotustyot]
               ["muu-liikennealuetyo" :ya-kayttolupa-muu-liikennealuetyo]]]
             ["yleisen-alueen-rajaaminen-tyomaakayttoon"
              [["talon-julkisivutyot" :ya-kayttolupa-talon-julkisivutyot]
               ["talon-rakennustyot" :ya-kayttolupa-talon-rakennustyot]
               ["muu-tyomaakaytto" :ya-kayttolupa-muu-tyomaakaytto]]]]]
           ["kayttolupa"
            [["tapahtumat" :ya-kayttolupa-tapahtumat]
             ["harrastustoiminnan-jarjestaminen" :ya-kayttolupa-harrastustoiminnan-jarjestaminen]
             ["mainokset" :ya-kayttolupa-mainostus-ja-viitoitus]
             ["metsastys" :ya-kayttolupa-metsastys]
             ["vesistoluvat" :ya-kayttolupa-vesistoluvat]
             ["terassit" :ya-kayttolupa-terassit]
             ["kioskit" :ya-kayttolupa-kioskit]
             ["muu-kayttolupa" :ya-kayttolupa-muu-kayttolupa]]]
          ["jatkoaika" :ya-jatkoaika]]]})

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
  (vector
    operation-tree-for-R
    operation-tree-for-environment-R
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

(def ^:private common-schemas (let [sc ["hankkeen-kuvaus" "maksaja" "rakennuspaikka" "lisatiedot" "paasuunnittelija" "suunnittelija"]]
                                (if (env/feature? :rakentamisen-aikaiset-tyonjohtaja-osapuoli)
                                  (conj sc "tyonjohtaja")
                                  sc)))

(def ^:private common-poikkeamis-schemas ["hankkeen-kuvaus" "maksaja" "poikkeusasian-rakennuspaikka" "lisatiedot"])


(def ^:private common-ymp-schemas ["ymp-ilm-kesto"])


(def ^:private common-yleiset-alueet-schemas ["yleiset-alueet-maksaja"])


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

(def ^:private ya-katulupa-general {:schema "tyomaastaVastaava"
                                    :permit-type "YA"
                                    :schema-data [[["_selected" :value] "yritys"]]
                                    :required (conj common-yleiset-alueet-schemas
                                                "yleiset-alueet-hankkeen-kuvaus-kaivulupa"
                                                "tyoaika")
                                    :attachments []})

(def ^:private ya-kayttolupa-general {:schema "tyoaika"
                                      :permit-type "YA"
                                      :required (conj common-yleiset-alueet-schemas
                                                  "yleiset-alueet-hankkeen-kuvaus-kayttolupa")
                                      :attachments []})

(def ^:private ya-kayttolupa-with-tyomaastavastaava
  (update-in ya-kayttolupa-general [:required] conj "tyomaastaVastaava"))

(def ^:private ya-sijoituslupa-general {:schema "yleiset-alueet-hankkeen-kuvaus-sijoituslupa"
                                        :permit-type "YA"
                                        :required (conj common-yleiset-alueet-schemas
                                                    "sijoituslupa-sijoituksen-tarkoitus")
                                        :attachments []})

(def ya-operations
  {:ya-kayttolupa-tapahtumat                                          ya-kayttolupa-general
   :ya-kayttolupa-harrastustoiminnan-jarjestaminen                    ya-kayttolupa-general
   :ya-kayttolupa-metsastys                                           ya-kayttolupa-general
   :ya-kayttolupa-vesistoluvat                                        ya-kayttolupa-general
   :ya-kayttolupa-terassit                                            ya-kayttolupa-general
   :ya-kayttolupa-kioskit                                             ya-kayttolupa-general
   :ya-kayttolupa-muu-kayttolupa                                      ya-kayttolupa-general
   :ya-kayttolupa-mainostus-ja-viitoitus  {:schema "mainosten-tai-viitoitusten-sijoittaminen"
                                           :permit-type "YA"
                                           :required common-yleiset-alueet-schemas
                                           :attachments []}
   :ya-kayttolupa-nostotyot                                           ya-kayttolupa-with-tyomaastavastaava
   :ya-kayttolupa-vaihtolavat                                         ya-kayttolupa-with-tyomaastavastaava
   :ya-kayttolupa-kattolumien-pudotustyot                             ya-kayttolupa-with-tyomaastavastaava
   :ya-kayttolupa-muu-liikennealuetyo                                 ya-kayttolupa-with-tyomaastavastaava
   :ya-kayttolupa-talon-julkisivutyot                                 ya-kayttolupa-with-tyomaastavastaava
   :ya-kayttolupa-talon-rakennustyot                                  ya-kayttolupa-with-tyomaastavastaava
   :ya-kayttolupa-muu-tyomaakaytto                                    ya-kayttolupa-with-tyomaastavastaava
   :ya-katulupa-vesi-ja-viemarityot                                   ya-katulupa-general
   :ya-katulupa-kaukolampotyot                                        ya-katulupa-general
   :ya-katulupa-kaapelityot                                           ya-katulupa-general
   :ya-katulupa-kiinteiston-johto-kaapeli-ja-putkiliitynnat           ya-katulupa-general
   :ya-sijoituslupa-vesi-ja-viemarijohtojen-sijoittaminen             ya-sijoituslupa-general
   :ya-sijoituslupa-maalampoputkien-sijoittaminen                     ya-sijoituslupa-general
   :ya-sijoituslupa-sahko-data-ja-muiden-kaapelien-sijoittaminen      ya-sijoituslupa-general
   :ya-sijoituslupa-ilmajohtojen-sijoittaminen                        ya-sijoituslupa-general
   :ya-sijoituslupa-muuntamoiden-sijoittaminen                        ya-sijoituslupa-general
   :ya-sijoituslupa-jatekatoksien-sijoittaminen                       ya-sijoituslupa-general
   :ya-sijoituslupa-leikkipaikan-tai-koiratarhan-sijoittaminen        ya-sijoituslupa-general
   :ya-sijoituslupa-muu-sijoituslupa                                  ya-sijoituslupa-general
;  :ya-liikennetta-haittaavan-tyon-lupa   {:schema "tyoaika"
;                                          :permit-type "YA"
;                                          :required common-yleiset-alueet-schemas
;                                          :attachments []}
   :ya-jatkoaika                          {:schema "hankkeen-kuvaus-jatkoaika"
                                           :permit-type "YA"
                                           :required (conj common-yleiset-alueet-schemas
                                                       "tyo-aika-for-jatkoaika")
                                           :attachments []}})

(def operations
  (merge
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
                                   :required  (conj common-poikkeamis-schemas "suunnittelutarveratkaisun-lisaosa")
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

     :tyonjohtajan-nimeaminen     {:schema "hankkeen-kuvaus-minimum"
                                   :permit-type "R"
                                   :required ["tyonjohtaja" "maksaja"]
                                   :attachments []}

     :suunnittelijan-nimeaminen   {:schema "hankkeen-kuvaus-minimum"
                                   :permit-type "R"
                                   :required ["suunnittelija" "maksaja"]
                                   :attachments []}

     :jatkoaika                   {:schema "hankkeen-kuvaus-minimum"
                                   :permit-type "R"
                                   :required ["maksaja"]
                                   :attachments []}

     :aloitusoikeus     {:schema "aloitusoikeus"
                         :permit-type "R"
                         :required ["maksaja"]
                         :attachments []}
     }
    ya-operations))

(defn permit-type-of-operation [operation]
  (:permit-type (operations (keyword operation))))

(doseq [[op {:keys [permit-type]}] operations]
  (when-not permit-type
    (throw (Exception. (format "Operation %s does not have permit-type set." op)))))

;;
;; Actions
;;

(defquery "operations"
  {:description "returns operations: without parameters all, with permitType-parameter just those operations"}
  [{{:keys [permitType]} :data}]
  (if permitType
    (ok :operations (operations-for-permit-type permitType))
    (ok :operations (all-operations))))
