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
            [lupapalvelu.document.vesihuolto-schemas]
            [lupapalvelu.permit :as permit]))

(def default-description "operations.tree.default-description")

(def ^:private operation-tree-for-R
  ["Rakentaminen ja purkaminen"
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
                  ["Rakennuksen purkaminen" :purkaminen]
                  ["Tyonjohtaja" :tyonjohtajan-nimeaminen]
                  ["Suunnittelija" :suunnittelijan-nimeaminen]
                  ["Jatkoaika" :jatkoaika]
    ["Aloitusoikeus" :aloitusoikeus]]])

(def ^:private operation-tree-for-environment-R
  ["Elinympariston muuttaminen"
          [["Maisemaa muutava toimenpide"
            [["Kaivaminen, louhiminen tai maan tayttaminen" :kaivuu]
             ["Puun kaataminen" :puun-kaataminen]
             ["Muu maisemaa muuttava toimenpide" :muu-maisema-toimenpide]]]
           ["Tontti tai korttelialueen jarjestelymuutos"
            [["Tontin ajoliittyman muutos" :tontin-ajoliittyman-muutos]
             ["Paikoitusjarjestelyihin liittyvat muutokset" :paikoutysjarjestus-muutos]
             ["Korttelin yhteisiin alueisiin liittyva muutos" :kortteli-yht-alue-muutos]
      ["Muu-tontti-tai-korttelialueen-jarjestelymuutos" :muu-tontti-tai-kort-muutos]]]]])

(def ^:private operation-tree-for-YA
  ["yleisten-alueiden-luvat"
          [["sijoituslupa"
            [["pysyvien-maanalaisten-rakenteiden-sijoittaminen"
              [["vesi-ja-viemarijohtojen-sijoittaminen" :ya-sijoituslupa-vesi-ja-viemarijohtojen-sijoittaminen]
               ["maalampoputkien-sijoittaminen" :ya-sijoituslupa-maalampoputkien-sijoittaminen]
               ["kaukolampoputkien-sijoittaminen" :ya-sijoituslupa-kaukolampoputkien-sijoittaminen]
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
               ["maalampotyot" :ya-katulupa-maalampotyot]
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
   ["jatkoaika" :ya-jatkoaika]]])

(def ^:private operation-tree-for-P
  ["Poikkeusluvat ja suunnittelutarveratkaisut" :poikkeamis])

(def ^:private operation-tree-for-Y
  ["Ymp\u00e4rist\u00f6luvat"
   (filterv identity ; TODO remove filter after pima featura is in production
     [; permit/YI
      ["Meluilmoitus" :meluilmoitus]

      ; at the moment permit/R
      (when (env/feature? :pima) ["Pima" :pima])

      ; permit/MAL
      ["maa-ainesten_ottaminen" :maa-aineslupa]

      ; permit/YL
      ["ympariston-pilaantumisen-vaara"
       [["uusi-toiminta" :yl-uusi-toiminta]
        ["olemassa-oleva-toiminta" :yl-olemassa-oleva-toiminta]
        ["toiminnan-muutos" :yl-toiminnan-muutos]]]

      ; permit/VVVL
      ["vapautus-vesijohdosta-ja-viemariin-liitymisvelvollisuudeseta"
       [["vesijohdosta" :vvvl-vesijohdosta]
        ["viemarista" :vvvl-viemarista]
        ["vesijohdosta-ja-viemarista" :vvvl-vesijohdosta-ja-viemarista]
        ["hulevesiviemarista" :vvvl-hulevesiviemarista]]]])])


(def operation-tree
  (filterv identity
    [operation-tree-for-R
    operation-tree-for-environment-R
    operation-tree-for-P
    (when (env/feature? :ymparisto) operation-tree-for-Y)
    operation-tree-for-YA]))

;; TODO: implement
(defn municipality-operations [municipality] operation-tree)

(def schema-data-yritys-selected [[["_selected" :value] "yritys"]])

; Operations must be the same as in the tree structure above.
; Mappings to schemas and attachments are currently random.

(def ^:private common-schemas ["hankkeen-kuvaus" "maksaja" "rakennuspaikka" "paasuunnittelija" "suunnittelija" "tyonjohtaja"])

(def ^:private common-poikkeamis-schemas ["hankkeen-kuvaus" "maksaja" "poikkeusasian-rakennuspaikka"])

(def ^:private common-yleiset-alueet-schemas ["yleiset-alueet-maksaja"])

(def ^:private common-ymparistolupa-schemas ["ymp-maksaja" "rakennuspaikka"])

(def ^:private common-vvvl-schemas ["hankkeen-kuvaus-vesihuolto" "vesihuolto-kiinteisto"])


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
                                    :permit-type permit/YA
                                    :schema-data schema-data-yritys-selected
                                    :required (conj common-yleiset-alueet-schemas
                                                "yleiset-alueet-hankkeen-kuvaus-kaivulupa"
                                                "tyoaika")
                                    :attachments []
                                    :add-operation-allowed false
                                    :link-permit-required false})

(def ^:private ya-kayttolupa-general {:schema "tyoaika"
                                      :permit-type permit/YA
                                      :required (conj common-yleiset-alueet-schemas
                                                  "yleiset-alueet-hankkeen-kuvaus-kayttolupa")
                                      :attachments []
                                      :add-operation-allowed false
                                      :link-permit-required false})

(def ^:private ya-kayttolupa-with-tyomaastavastaava
  (update-in ya-kayttolupa-general [:required] conj "tyomaastaVastaava"))

(def ^:private ya-sijoituslupa-general {:schema "yleiset-alueet-hankkeen-kuvaus-sijoituslupa"
                                        :permit-type permit/YA
                                        :required common-yleiset-alueet-schemas
                                        :attachments []
                                        :add-operation-allowed false
                                        :link-permit-required false})

(def ya-operations
  {:ya-kayttolupa-tapahtumat                                          ya-kayttolupa-general
   :ya-kayttolupa-harrastustoiminnan-jarjestaminen                    ya-kayttolupa-general
   :ya-kayttolupa-metsastys                                           ya-kayttolupa-general
   :ya-kayttolupa-vesistoluvat                                        ya-kayttolupa-general
   :ya-kayttolupa-terassit                                            ya-kayttolupa-general
   :ya-kayttolupa-kioskit                                             ya-kayttolupa-general
   :ya-kayttolupa-muu-kayttolupa                                      ya-kayttolupa-general
   :ya-kayttolupa-mainostus-ja-viitoitus  {:schema "mainosten-tai-viitoitusten-sijoittaminen"
                                           :permit-type permit/YA
                                           :required common-yleiset-alueet-schemas
                                           :attachments []
                                           :add-operation-allowed false
                                           :link-permit-required false}
   :ya-kayttolupa-nostotyot                                           ya-kayttolupa-with-tyomaastavastaava
   :ya-kayttolupa-vaihtolavat                                         ya-kayttolupa-with-tyomaastavastaava
   :ya-kayttolupa-kattolumien-pudotustyot                             ya-kayttolupa-with-tyomaastavastaava
   :ya-kayttolupa-muu-liikennealuetyo                                 ya-kayttolupa-with-tyomaastavastaava
   :ya-kayttolupa-talon-julkisivutyot                                 ya-kayttolupa-with-tyomaastavastaava
   :ya-kayttolupa-talon-rakennustyot                                  ya-kayttolupa-with-tyomaastavastaava
   :ya-kayttolupa-muu-tyomaakaytto                                    ya-kayttolupa-with-tyomaastavastaava
   :ya-katulupa-vesi-ja-viemarityot                                   ya-katulupa-general
   :ya-katulupa-maalampotyot                                          ya-katulupa-general
   :ya-katulupa-kaukolampotyot                                        ya-katulupa-general
   :ya-katulupa-kaapelityot                                           ya-katulupa-general
   :ya-katulupa-kiinteiston-johto-kaapeli-ja-putkiliitynnat           ya-katulupa-general
   :ya-sijoituslupa-vesi-ja-viemarijohtojen-sijoittaminen             ya-sijoituslupa-general
   :ya-sijoituslupa-maalampoputkien-sijoittaminen                     ya-sijoituslupa-general
   :ya-sijoituslupa-kaukolampoputkien-sijoittaminen                   ya-sijoituslupa-general
   :ya-sijoituslupa-sahko-data-ja-muiden-kaapelien-sijoittaminen      ya-sijoituslupa-general
   :ya-sijoituslupa-ilmajohtojen-sijoittaminen                        ya-sijoituslupa-general
   :ya-sijoituslupa-muuntamoiden-sijoittaminen                        ya-sijoituslupa-general
   :ya-sijoituslupa-jatekatoksien-sijoittaminen                       ya-sijoituslupa-general
   :ya-sijoituslupa-leikkipaikan-tai-koiratarhan-sijoittaminen        ya-sijoituslupa-general
   :ya-sijoituslupa-muu-sijoituslupa                                  ya-sijoituslupa-general
;  :ya-liikennetta-haittaavan-tyon-lupa   {:schema "tyoaika"
;                                          :permit-type permit/YA
;                                          :required common-yleiset-alueet-schemas
;                                          :attachments []}
   :ya-jatkoaika                          {:schema "hankkeen-kuvaus-jatkoaika"
                                           :permit-type permit/YA
                                           :required (conj common-yleiset-alueet-schemas
                                                       "tyo-aika-for-jatkoaika")
                                           :attachments []
                                           :add-operation-allowed false
                                           :link-permit-required true}})

(def ^:private ymparistolupa-attachments []) ; TODO
(def ^:private ymparistolupa-operation
  {:schema "yl-hankkeen-kuvaus"
   :permit-type permit/YL
   :schema-data []
   :required common-ymparistolupa-schemas
   :attachments ymparistolupa-attachments
   :add-operation-allowed false
   :link-permit-required false})

(def yl-operations
  {:yl-uusi-toiminta ymparistolupa-operation
   :yl-olemassa-oleva-toiminta ymparistolupa-operation
   :yl-toiminnan-muutos ymparistolupa-operation})

(def operations
  (merge
    {:asuinrakennus               {:schema "uusiRakennus"
                                   :permit-type permit/R
                                   :schema-data [[["kaytto" "kayttotarkoitus"] schemas/yhden-asunnon-talot]
                                                 [["huoneistot" "0" "huoneistoTunnus" "huoneistonumero"] "000"]] ;FIXME Aftre krysp update change to 000
                                   :required common-schemas
                                   :attachments uuden_rakennuksen_liitteet
                                   :add-operation-allowed true
                                   :link-permit-required false}
     :vapaa-ajan-asuinrakennus    {:schema "uusi-rakennus-ei-huoneistoa"
                                   :permit-type permit/R
                                   :schema-data [[["kaytto" "kayttotarkoitus"] schemas/vapaa-ajan-asuinrakennus]]
                                   :required common-schemas
                                   :attachments uuden_rakennuksen_liitteet
                                   :add-operation-allowed true
                                   :link-permit-required false}
     :varasto-tms                 {:schema "uusi-rakennus-ei-huoneistoa"
                                   :permit-type permit/R
                                   :schema-data [[["kaytto" "kayttotarkoitus"] schemas/talousrakennus]]
                                   :required common-schemas
                                   :attachments uuden_rakennuksen_liitteet
                                   :add-operation-allowed true
                                   :link-permit-required false}
     :julkinen-rakennus           {:schema "uusiRakennus"
                                   :permit-type permit/R
                                   :required common-schemas
                                   :attachments uuden_rakennuksen_liitteet
                                   :add-operation-allowed true
                                   :link-permit-required false}
     :muu-uusi-rakentaminen       {:schema "uusiRakennus"
                                   :permit-type permit/R
                                   :required common-schemas
                                   :attachments uuden_rakennuksen_liitteet
                                   :add-operation-allowed true
                                   :link-permit-required false}
     :laajentaminen               {:schema "rakennuksen-laajentaminen"
                                   :permit-type permit/R
                                   :required common-schemas
                                   :attachments rakennuksen_laajennuksen_liitteet
                                   :add-operation-allowed true
                                   :link-permit-required false}
     :perus-tai-kant-rak-muutos   {:schema "rakennuksen-muuttaminen-ei-huoneistoja"
                                   :permit-type permit/R
                                   :schema-data [[["muutostyolaji"] schemas/perustusten-korjaus]]
                                   :required common-schemas
                                   :attachments rakennuksen_laajennuksen_liitteet
                                   :add-operation-allowed true
                                   :link-permit-required false}
     :kayttotark-muutos           {:schema "rakennuksen-muuttaminen"
                                   :permit-type permit/R
                                   :schema-data [[["muutostyolaji"] schemas/kayttotarkotuksen-muutos]]
                                   :required common-schemas
                                   :attachments rakennuksen_muutos_liitteet
                                   :add-operation-allowed true
                                   :link-permit-required false}
     :julkisivu-muutos            {:schema "rakennuksen-muuttaminen-ei-huoneistoja-ei-ominaisuuksia"
                                   :permit-type permit/R
                                   :schema-data [[["muutostyolaji"] schemas/muumuutostyo]]
                                   :required common-schemas
                                   :attachments rakennuksen_laajennuksen_liitteet
                                   :add-operation-allowed true
                                   :link-permit-required false}
     :jakaminen-tai-yhdistaminen  {:schema "rakennuksen-muuttaminen"
                                   :permit-type permit/R
                                   :schema-data [[["muutostyolaji"] schemas/muumuutostyo]]
                                   :required common-schemas
                                   :attachments rakennuksen_laajennuksen_liitteet
                                   :add-operation-allowed true
                                   :link-permit-required false}
     :markatilan-laajentaminen    {:schema "rakennuksen-muuttaminen-ei-huoneistoja-ei-ominaisuuksia"
                                   :permit-type permit/R
                                   :schema-data [[["muutostyolaji"] schemas/muumuutostyo]]
                                   :required common-schemas
                                   :attachments rakennuksen_laajennuksen_liitteet
                                   :add-operation-allowed true
                                   :link-permit-required false}
     :takka-tai-hormi             {:schema "rakennuksen-muuttaminen-ei-huoneistoja-ei-ominaisuuksia"
                                   :permit-type permit/R
                                   :schema-data [[["muutostyolaji"] schemas/muumuutostyo]]
                                   :required common-schemas
                                   :attachments rakennuksen_laajennuksen_liitteet
                                   :add-operation-allowed true
                                   :link-permit-required false}
     :parveke-tai-terassi         {:schema "rakennuksen-muuttaminen-ei-huoneistoja-ei-ominaisuuksia"
                                   :permit-type permit/R
                                   :schema-data [[["muutostyolaji"] schemas/muumuutostyo]]
                                   :required common-schemas
                                   :attachments rakennuksen_laajennuksen_liitteet
                                   :add-operation-allowed true
                                   :link-permit-required false}
     :muu-laajentaminen           {:schema "rakennuksen-muuttaminen"
                                   :permit-type permit/R
                                   :schema-data [[["muutostyolaji"] schemas/muumuutostyo]]
                                   :required common-schemas
                                   :attachments rakennuksen_laajennuksen_liitteet
                                   :add-operation-allowed true
                                   :link-permit-required false}
     :auto-katos                  {:schema "kaupunkikuvatoimenpide"
                                   :permit-type permit/R
                                   :required common-schemas
                                   :attachments kaupunkikuva_toimenpide_liitteet
                                   :add-operation-allowed true
                                   :link-permit-required false}
     :masto-tms                   {:schema "kaupunkikuvatoimenpide"
                                   :permit-type permit/R
                                   :required common-schemas
                                   :attachments kaupunkikuva_toimenpide_liitteet
                                   :add-operation-allowed true
                                   :link-permit-required false}
     :mainoslaite                 {:schema "kaupunkikuvatoimenpide"
                                   :permit-type permit/R
                                   :required common-schemas
                                   :attachments kaupunkikuva_toimenpide_liitteet
                                   :add-operation-allowed true
                                   :link-permit-required false}
     :aita                        {:schema "kaupunkikuvatoimenpide"
                                   :permit-type permit/R
                                   :required common-schemas
                                   :attachments kaupunkikuva_toimenpide_liitteet
                                   :add-operation-allowed true
                                   :link-permit-required false}
     :maalampo                    {:schema "kaupunkikuvatoimenpide"
                                   :permit-type permit/R
                                   :required common-schemas
                                   :attachments kaupunkikuva_toimenpide_liitteet
                                   :add-operation-allowed true
                                   :link-permit-required false}
     :jatevesi                    {:schema "kaupunkikuvatoimenpide"
                                   :permit-type permit/R
                                   :required common-schemas
                                   :attachments kaupunkikuva_toimenpide_liitteet
                                   :add-operation-allowed true
                                   :link-permit-required false}
     :muu-rakentaminen            {:schema "kaupunkikuvatoimenpide"
                                   :permit-type permit/R
                                   :required common-schemas
                                   :attachments kaupunkikuva_toimenpide_liitteet
                                   :add-operation-allowed true
                                   :link-permit-required false}
     :purkaminen                  {:schema "purkaminen"
                                   :permit-type permit/R
                                   :required common-schemas
                                   :attachments [:muut [:selvitys_rakennusjatteen_maarasta_laadusta_ja_lajittelusta
                                                        :selvitys_purettavasta_rakennusmateriaalista_ja_hyvaksikaytosta]]
                                   :add-operation-allowed true
                                   :link-permit-required false}
     :kaivuu                      {:schema "maisematyo"
                                   :permit-type permit/R
                                   :required common-schemas
                                   :attachments [:paapiirustus [:asemapiirros]]
                                   :add-operation-allowed true
                                   :link-permit-required false}
     :puun-kaataminen             {:schema "maisematyo"
                                   :permit-type permit/R
                                   :required common-schemas
                                   :attachments [:paapiirustus [:asemapiirros]]
                                   :add-operation-allowed true
                                   :link-permit-required false}
     :muu-maisema-toimenpide      {:schema "maisematyo"
                                   :permit-type permit/R
                                   :required  common-schemas
                                   :attachments [:paapiirustus [:asemapiirros]]
                                   :add-operation-allowed true
                                   :link-permit-required false}
     :tontin-ajoliittyman-muutos  {:schema "maisematyo"
                                   :permit-type permit/R
                                   :required  common-schemas
                                   :attachments [:paapiirustus [:asemapiirros]]
                                   :add-operation-allowed true
                                   :link-permit-required false}
     :paikoutysjarjestus-muutos   {:schema "maisematyo"
                                   :permit-type permit/R
                                   :required  common-schemas
                                   :attachments [:paapiirustus [:asemapiirros]]
                                   :add-operation-allowed true
                                   :link-permit-required false}
     :kortteli-yht-alue-muutos    {:schema "maisematyo"
                                   :permit-type permit/R
                                   :required  common-schemas
                                   :attachments [:paapiirustus [:asemapiirros]]
                                   :add-operation-allowed true
                                   :link-permit-required false}
     :muu-tontti-tai-kort-muutos  {:schema "maisematyo"
                                   :permit-type permit/R
                                   :required  common-schemas
                                   :attachments [:paapiirustus [:asemapiirros]]
                                   :add-operation-allowed true
                                   :link-permit-required false}
     :poikkeamis                  {:schema "rakennushanke"
                                   :permit-type "P"
                                   :required  (conj common-poikkeamis-schemas "suunnittelutarveratkaisun-lisaosa")
                                   :attachments [:paapiirustus [:asemapiirros]]
                                   :add-operation-allowed false
                                   :link-permit-required false}
     :meluilmoitus                {:schema "meluilmoitus"
                                   :permit-type permit/YI
                                   :required ["ymp-ilm-kesto"]
                                   :attachments [:kartat [:kartta-melun-ja-tarinan-leviamisesta]]
                                   :add-operation-allowed false
                                   :link-permit-required false}
     :pima                        {:schema "pima"
                                   :permit-type permit/R ; TODO
                                   :required ["ymp-ilm-kesto-mini"]
                                   :attachments []
                                   :add-operation-allowed true
                                   :link-permit-required false}
     :maa-aineslupa               {:schema "maa-aineslupa-kuvaus"
                                   :permit-type permit/MAL
                                   :required ["ymp-maksaja" "rakennuspaikka"]
                                   :attachments []
                                   :link-permit-required false}
     :vvvl-vesijohdosta           {:schema "talousvedet"
                                   :permit-type permit/VVVL
                                   :required common-vvvl-schemas
                                   :attachments [:kartat [:kartta-melun-ja-tarinan-leviamisesta]]
                                   :add-operation-allowed false
                                   :link-permit-required false}
     :vvvl-viemarista             {:schema "jatevedet"
                                   :permit-type permit/VVVL
                                   :required common-vvvl-schemas
                                   :attachments [:kartat [:kartta-melun-ja-tarinan-leviamisesta]]
                                   :add-operation-allowed false
                                   :link-permit-required false}
     :vvvl-vesijohdosta-ja-viemarista {:schema "talousvedet"
                                   :permit-type permit/VVVL
                                   :required (conj common-vvvl-schemas "jatevedet")
                                   :attachments [:kartat [:kartta-melun-ja-tarinan-leviamisesta]]
                                   :add-operation-allowed false
                                   :link-permit-required false}
     :vvvl-hulevesiviemarista    {:schema "hulevedet"
                                   :permit-type permit/VVVL
                                   :required common-vvvl-schemas
                                   :attachments [:kartat [:kartta-melun-ja-tarinan-leviamisesta]]
                                   :add-operation-allowed false
                                   :link-permit-required false}

     :tyonjohtajan-nimeaminen     {:schema "hankkeen-kuvaus-minimum"
                                   :permit-type permit/R
                                   :required ["tyonjohtaja" "maksaja"]
                                   :attachments []
                                   :add-operation-allowed false
                                   :link-permit-required true}

     :suunnittelijan-nimeaminen   {:schema "hankkeen-kuvaus-minimum"
                                   :permit-type permit/R
                                   :required ["suunnittelija" "maksaja"]
                                   :attachments []
                                   :add-operation-allowed false
                                   :link-permit-required true}

     :jatkoaika                   {:schema "hankkeen-kuvaus-minimum"
                                   :permit-type permit/R
                                   :required ["maksaja"]
                                   :attachments []
                                   :add-operation-allowed false
                                   :link-permit-required true}

     :aloitusoikeus               {:schema "aloitusoikeus"
                                   :permit-type permit/R
                                   :required ["maksaja"]
                                   :attachments []
                                   :add-operation-allowed false
                                   :link-permit-required true}}
    ya-operations
    yl-operations))

(defn permit-type-of-operation [operation]
  (:permit-type (operations (keyword operation))))

(defn operations-for-permit-type [permit-type]
  (clojure.walk/postwalk
    (fn [node]
      (if (keyword? node)
        (when (= (name permit-type) (permit-type-of-operation node))
          ; Return operation keyword if permit type matches, or nil
          node)
        (if (string? node)
          ; A step in a path is returned as is
          node
          ; Not a keyword or string, must be a sequence. Take only paths that have operations.
          (let [filtered (filter identity node)]
            (when (or (> (count filtered) 1) (sequential? (first filtered)))
              filtered)))))
    operation-tree))

(doseq [[op {:keys [permit-type]}] operations]
  (when-not permit-type
    (throw (Exception. (format "Operation %s does not have permit-type set." op)))))

(def link-permit-required-operations
  (reduce (fn [result [operation metadata]]
            (if (:link-permit-required metadata)
              (conj result operation)
              result)) #{} operations))

;;
;; Actions
;;

(defquery "operations"
  {:description "returns operations: without parameters all, with permitType-parameter just those operations"}
  [{{:keys [permitType]} :data}]
  (if permitType
    (ok :operations (operations-for-permit-type permitType))
    (ok :operations operation-tree)))




