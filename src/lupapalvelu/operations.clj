(ns lupapalvelu.operations
  (:require [taoensso.timbre :as timbre :refer [trace debug info warn error fatal]]
            [schema.core :as sc]
            [sade.env :as env]
            [sade.util :as util]
            [sade.core :refer :all]
            [lupapalvelu.action :refer [defquery]]
            [lupapalvelu.attachment :as attachment]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.document.schemas :as schemas]
            [lupapalvelu.document.poikkeamis-schemas]
            [lupapalvelu.document.ymparisto-schemas]
            [lupapalvelu.document.yleiset-alueet-schemas]
            [lupapalvelu.document.vesihuolto-schemas]
            [lupapalvelu.states :as states]
            [lupapiste-commons.usage-types :as usages]
            [monger.operators :refer :all]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.permit :as permit]))

(def default-description "operations.tree.default-description")

(def- operation-tree-for-R
  ["Rakentaminen ja purkaminen"
   [["Uuden rakennuksen rakentaminen"
     [["kerrostalo-rivitalo" :kerrostalo-rivitalo]
      ["pientalo" :pientalo]
      ["Vapaa-ajan asuinrakennus" :vapaa-ajan-asuinrakennus]
      ["Varasto, sauna, autotalli tai muu talousrakennus" :varasto-tms]
      ["teollisuusrakennus" :teollisuusrakennus]
      ["Muun rakennuksen rakentaminen" :muu-uusi-rakentaminen]]]
    ["Rakennuksen-laajentaminen"
     [["kerrostalo-rt-laaj" :kerrostalo-rt-laaj]
      ["pientalo-laaj" :pientalo-laaj]
      ["vapaa-ajan-rakennus-laaj" :vapaa-ajan-rakennus-laaj]
      ["talousrakennus-laaj" :talousrakennus-laaj]
      ["teollisuusrakennus-laaj" :teollisuusrakennus-laaj]
      ["muu-rakennus-laaj" :muu-rakennus-laaj]]]
    ["Rakennuksen korjaaminen tai muuttaminen"
     [["kayttotark-muutos" :kayttotark-muutos]
      ["sisatila-muutos" :sisatila-muutos]
      ["Rakennuksen julkisivun tai katon muuttaminen" :julkisivu-muutos]
      ["Markatilan laajentaminen" :markatilan-laajentaminen]
      ["linjasaneeraus" :linjasaneeraus]
      ["Parvekkeen tai terassin lasittaminen" :parveke-tai-terassi]
      ["Perustusten tai kantavien rakenteiden muuttaminen tai korjaaminen" :perus-tai-kant-rak-muutos]
      ["Takan ja savuhormin rakentaminen" :takka-tai-hormi]
      ["Asuinhuoneiston jakaminen tai yhdistaminen" :jakaminen-tai-yhdistaminen]]]
    ["Rakennelman rakentaminen"
     [["Auto- tai grillikatos, vaja, kioski tai vastaava" :auto-katos]
      ["Masto, piippu, sailio, laituri tai vastaava" :masto-tms]
      ["Mainoslaite" :mainoslaite]
      ["Aita" :aita]
      ["Maalampokaivon poraaminen tai lammonkeruuputkiston asentaminen" :maalampo]
      ["Rakennuksen jatevesijarjestelman uusiminen" :jatevesi]]]
    ["Rakennuksen purkaminen" :purkaminen]
    ["Maisemaa muutava toimenpide"
     [["Puun kaataminen" :puun-kaataminen]
      ["tontin-jarjestelymuutos" :tontin-jarjestelymuutos]
      ["Muu-tontti-tai-korttelialueen-jarjestelymuutos" :muu-tontti-tai-kort-muutos]
      ["Kaivaminen, louhiminen tai maan tayttaminen" :kaivuu]
      ["Muu maisemaa muuttava toimenpide" :muu-maisema-toimenpide]]]
    ["rakennustyo-muutostoimenpiteet"
     [["Suunnittelija" :suunnittelijan-nimeaminen]
      ["Tyonjohtaja" :tyonjohtajan-nimeaminen-v2]
      ["rak-valm-tyo" :rak-valm-tyo]
      ["Aloitusoikeus" :aloitusoikeus]
      ["raktyo-aloit-loppuunsaat" :raktyo-aloit-loppuunsaat]]]]])

(def- operation-tree-for-YA
  ["yleisten-alueiden-luvat"
   [["sijoituslupa"
     [["pysyvien-maanalaisten-rakenteiden-sijoittaminen"
       [["vesi-ja-viemarijohtojen-sijoittaminen" :ya-sijoituslupa-vesi-ja-viemarijohtojen-sijoittaminen]
        ["maalampoputkien-sijoittaminen" :ya-sijoituslupa-maalampoputkien-sijoittaminen]
        ["kaukolampoputkien-sijoittaminen" :ya-sijoituslupa-kaukolampoputkien-sijoittaminen]
        ["sahko-data-ja-muiden-kaapelien-sijoittaminen" :ya-sijoituslupa-sahko-data-ja-muiden-kaapelien-sijoittaminen]
        ["rakennuksen-tai-sen-osan-sijoittaminen" :ya-sijoituslupa-rakennuksen-tai-sen-osan-sijoittaminen]]]
      ["pysyvien-maanpaallisten-rakenteiden-sijoittaminen"
       [["ilmajohtojen-sijoittaminen" :ya-sijoituslupa-ilmajohtojen-sijoittaminen]
        ["muuntamoiden-sijoittaminen" :ya-sijoituslupa-muuntamoiden-sijoittaminen]
        ["jatekatoksien-sijoittaminen" :ya-sijoituslupa-jatekatoksien-sijoittaminen]
        ["leikkipaikan-tai-koiratarhan-sijoittaminen" :ya-sijoituslupa-leikkipaikan-tai-koiratarhan-sijoittaminen]
        ["rakennuksen-pelastuspaikan-sijoittaminen" :ya-sijoituslupa-rakennuksen-pelastuspaikan-sijoittaminen]]]
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

(def- operation-tree-for-P
  ["Poikkeusluvat ja suunnittelutarveratkaisut" :poikkeamis])

(def- operation-tree-for-Y
  ["Ymp\u00e4rist\u00f6luvat"
   (filterv identity
     [; permit/YI
      ["Meluilmoitus" :meluilmoitus]

      (when (env/feature? :pima) ["Pima" :pima])

      ; permit/MAL
      ["maa-ainesten_ottaminen" :maa-aineslupa]

      ; permit/YL
      ["ympariston-pilaantumisen-vaara"
       [["uusi-toiminta" :yl-uusi-toiminta]
        ["olemassa-oleva-toiminta" :yl-olemassa-oleva-toiminta]
        ["toiminnan-muutos" :yl-toiminnan-muutos]]]

      ; permit/YM
      ["muut-ymparistoluvat"
       [["muistomerkin-rauhoittaminen" :muistomerkin-rauhoittaminen]
        ["jatteen-keraystoiminta" :jatteen-keraystoiminta]
        ["lannan-varastointi" :lannan-varastointi]
        ["kaytostapoistetun-oljy-tai-kemikaalisailion-jattaminen-maaperaan" :kaytostapoistetun-oljy-tai-kemikaalisailion-jattaminen-maaperaan]
        ["koeluontoinen-toiminta" :koeluontoinen-toiminta]
        ["maa-ainesten-kotitarveotto" :maa-ainesten-kotitarveotto]
        ["ilmoitus-poikkeuksellisesta-tilanteesta" :ilmoitus-poikkeuksellisesta-tilanteesta]
        ["maastoliikennelaki-kilpailut-ja-harjoitukset" :maastoliikennelaki-kilpailut-ja-harjoitukset]
        ]]

      ; permit/VVVL
      ["vapautus-vesijohdosta-ja-viemariin-liitymisvelvollisuudeseta"
       [["vesijohdosta" :vvvl-vesijohdosta]
        ["viemarista" :vvvl-viemarista]
        ["vesijohdosta-ja-viemarista" :vvvl-vesijohdosta-ja-viemarista]
        ["hulevesiviemarista" :vvvl-hulevesiviemarista]]]])])

(def operation-tree-for-KT ; aka kiinteistotoimitus aka maanmittaustoimitukset
  ["maanmittaustoimitukset"
   [ ["kiinteistonmuodostus" :kiinteistonmuodostus]
    ["rasitetoimitus" :rasitetoimitus]
    ["rajankaynti" :rajankaynti]
    ]])

(def operation-tree-for-MM
  ["maankayton-muutos"
   [["tonttijako" :tonttijako]
    ["asemakaava" :asemakaava]
    ["ranta-asemakaava" :ranta-asemakaava]
    ["yleiskaava" :yleiskaava]
    ]])


(def operation-tree
  (filterv identity
    `[
      ~@[operation-tree-for-R]
      ~@[operation-tree-for-P
         operation-tree-for-Y
         operation-tree-for-YA]
      ~@(when (env/feature? :kiinteistonMuodostus)
          [operation-tree-for-KT operation-tree-for-MM])]))


(def schema-data-yritys-selected  [[["_selected"] "yritys"]])
(def schema-data-henkilo-selected [[["_selected"] "henkilo"]])

; Operations must be the same as in the tree structure above.
; Mappings to schemas and attachments are currently random.

(def- common-rakval-schemas ["hankkeen-kuvaus" "paatoksen-toimitus-rakval" "maksaja" "rakennuspaikka" "paasuunnittelija" "suunnittelija"])

(def- common-maanmittaus-schemas ["maksaja" "kiinteisto"])

(def- common-poikkeamis-schemas ["hankkeen-kuvaus" "maksaja" "poikkeusasian-rakennuspaikka"])

(def- common-yleiset-alueet-schemas ["yleiset-alueet-maksaja"])

(def- common-ymparistolupa-schemas ["ymp-maksaja" "rakennuspaikka"])

(def- common-vvvl-schemas ["hankkeen-kuvaus-vesihuolto" "vesihuolto-kiinteisto"])

(def- applicant-doc-schema-R    "hakija-r")
(def- applicant-doc-schema-YA   "hakija-ya")
(def- applicant-doc-schema-YI   "ilmoittaja")
(def- applicant-doc-schema-YL   "hakija")
(def- applicant-doc-schema-YM   "ilmoittaja")
(def- applicant-doc-schema-VVVL "hakija")
(def- applicant-doc-schema-P    "hakija")
(def- applicant-doc-schema-MAL  "hakija")
(def- applicant-doc-schema-KT   "hakija")
(def- applicant-doc-schema-MM   "hakija")


(def- uuden_rakennuksen_liitteet [:paapiirustus [:asemapiirros
                                                 :pohjapiirros
                                                 :julkisivupiirros
                                                 :leikkauspiirros]
                                  :rakennuspaikka [:selvitys_rakennuspaikan_perustamis_ja_pohjaolosuhteista]])

(def- rakennuksen_muutos_liitteet [:paapiirustus [:pohjapiirros
                                                  :julkisivupiirros]])

(def- rakennuksen_laajennuksen_liitteet [:paapiirustus [:asemapiirros
                                                        :pohjapiirros
                                                        :julkisivupiirros
                                                        :leikkauspiirros]])

(def- kaupunkikuva_toimenpide_liitteet [:paapiirustus [:asemapiirros
                                                       :julkisivupiirros]])

(def- ya-katulupa-general {:schema "tyomaastaVastaava"
                           :permit-type permit/YA
                           :applicant-doc-schema applicant-doc-schema-YA
                           :schema-data schema-data-henkilo-selected
                           :required (conj common-yleiset-alueet-schemas
                                       "yleiset-alueet-hankkeen-kuvaus-kaivulupa"
                                       "tyoaika")
                           :attachments []
                           :add-operation-allowed false
                           :min-outgoing-link-permits 0
                           :asianhallinta true})

(def- ya-kayttolupa-general {:schema "tyoaika"
                             :permit-type permit/YA
                             :applicant-doc-schema applicant-doc-schema-YA
                             :required (conj common-yleiset-alueet-schemas
                                         "yleiset-alueet-hankkeen-kuvaus-kayttolupa")
                             :attachments []
                             :add-operation-allowed false
                             :min-outgoing-link-permits 0
                             :asianhallinta true})

(def- ya-kayttolupa-with-tyomaastavastaava
  (update-in ya-kayttolupa-general [:required] conj "tyomaastaVastaava"))

(def- ya-sijoituslupa-general {:schema "yleiset-alueet-hankkeen-kuvaus-sijoituslupa"
                                        :permit-type permit/YA
                                        :applicant-doc-schema applicant-doc-schema-YA
                                        :required common-yleiset-alueet-schemas
                                        :attachments []
                                        :add-operation-allowed false
                                        :min-outgoing-link-permits 0
                                        :asianhallinta true})

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
                                           :applicant-doc-schema applicant-doc-schema-YA
                                           :required common-yleiset-alueet-schemas
                                           :attachments []
                                           :add-operation-allowed false
                                           :min-outgoing-link-permits 0
                                           :asianhallinta true}
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
   :ya-sijoituslupa-rakennuksen-tai-sen-osan-sijoittaminen            ya-sijoituslupa-general
   :ya-sijoituslupa-ilmajohtojen-sijoittaminen                        ya-sijoituslupa-general
   :ya-sijoituslupa-muuntamoiden-sijoittaminen                        ya-sijoituslupa-general
   :ya-sijoituslupa-jatekatoksien-sijoittaminen                       ya-sijoituslupa-general
   :ya-sijoituslupa-leikkipaikan-tai-koiratarhan-sijoittaminen        ya-sijoituslupa-general
   :ya-sijoituslupa-rakennuksen-pelastuspaikan-sijoittaminen          ya-sijoituslupa-general
   :ya-sijoituslupa-muu-sijoituslupa                                  ya-sijoituslupa-general
   :ya-jatkoaika                          {:schema "hankkeen-kuvaus-jatkoaika"
                                           :permit-type permit/YA
                                           :applicant-doc-schema applicant-doc-schema-YA
                                           :required (conj common-yleiset-alueet-schemas
                                                       "tyo-aika-for-jatkoaika")
                                           :attachments []
                                           :add-operation-allowed false
                                           :min-outgoing-link-permits 1
                                           :max-incoming-link-permits 1
                                           :asianhallinta true}})

(def- ymparistolupa-attachments []) ; TODO
(def- ymparistolupa-operation
  {:schema "yl-hankkeen-kuvaus"
   :permit-type permit/YL
   :applicant-doc-schema applicant-doc-schema-YL
   :schema-data []
   :required common-ymparistolupa-schemas
   :attachments ymparistolupa-attachments
   :add-operation-allowed false
   :min-outgoing-link-permits 0
   :asianhallinta true})

(def- yl-operations
  {:yl-uusi-toiminta ymparistolupa-operation
   :yl-olemassa-oleva-toiminta ymparistolupa-operation
   :yl-toiminnan-muutos ymparistolupa-operation
;   :pima                          {:schema "pima"
;                                   :permit-type permit/YL
;                                   :applicant-doc-schema applicant-doc-schema-YL
;                                   :required ["ymp-ilm-kesto-mini"]
;                                   :attachments []
;                                   :add-operation-allowed true
;                                   :min-outgoing-link-permits 0
;                                   :asianhallinta false}
   })

(def- yi-operations
  {:meluilmoitus                 {:schema "meluilmoitus"
                                  :permit-type permit/YI
                                  :applicant-doc-schema applicant-doc-schema-YI
                                  :required ["ymp-ilm-kesto"]
                                  :attachments [:kartat [:kartta-melun-ja-tarinan-leviamisesta]]
                                  :add-operation-allowed false
                                  :min-outgoing-link-permits 0
                                  :asianhallinta true}
   })

(def- ym-operations
  {:muistomerkin-rauhoittaminen  {:schema "luonnonmuistomerkin-rauhoittaminen"
                                  :permit-type permit/YM
                                  :applicant-doc-schema applicant-doc-schema-YM
                                  :required []
                                  :attachments []
                                  :add-operation-allowed false
                                  :min-outgoing-link-permits 0
                                  :asianhallinta true}

   :jatteen-keraystoiminta {:schema "jatteen-kerays"
                            :permit-type permit/YM
                            :applicant-doc-schema applicant-doc-schema-YM
                            :required []
                            :attachments []
                            :add-operation-allowed false
                            :min-outgoing-link-permits 0
                            :asianhallinta true}

   :lannan-varastointi     {:schema "lannan-varastointi"
                            :permit-type permit/YM
                            :applicant-doc-schema applicant-doc-schema-YM
                            :required ["yl-maatalous-hankkeen-kuvaus"]
                            :attachments []
                            :add-operation-allowed false
                            :min-outgoing-link-permits 0
                            :asianhallinta true}

   :kaytostapoistetun-oljy-tai-kemikaalisailion-jattaminen-maaperaan {:schema "kaytostapoistetun-sailion-jattaminen-maaperaan"
                                                                      :permit-type permit/YM
                                                                      :applicant-doc-schema applicant-doc-schema-YM
                                                                      :required ["kiinteisto"]
                                                                      ;; TODO: sync with attachments in Commons.
;                                                                      :attachments [:kaytostapoistetun-oljy-tai-kemikaalisailion-jattaminen-maaperaan [:sailion-tarkastuspoytakirja
;                                                                                                                                                       :kiinteiston-omistajien-suostumus]
;                                                                                    :kartat [:sailion-ja-rakenteiden-sijainti-kartalla]]
                                                                      :attachments []
                                                                      :add-operation-allowed false
                                                                      :min-outgoing-link-permits 0
                                                                      :asianhallinta true}

   :koeluontoinen-toiminta {:schema "koeluontoinen-toiminta"
                            :permit-type permit/YM
                            :applicant-doc-schema applicant-doc-schema-YM
                            :required []
                            :attachments []
                            :add-operation-allowed false
                            :min-outgoing-link-permits 0
                            :asianhallinta true}

   :maa-ainesten-kotitarveotto {:schema "maa-ainesten-kotitarveotto"
                                :permit-type permit/YM
                                :applicant-doc-schema applicant-doc-schema-YM
                                :required ["kiinteisto"]
                                :attachments []
                                :add-operation-allowed false
                                :min-outgoing-link-permits 0
                                :asianhallinta true}

   :ilmoitus-poikkeuksellisesta-tilanteesta {:schema "ilmoitus-poik-tilanteesta"
                                             :permit-type permit/YM
                                             :applicant-doc-schema applicant-doc-schema-YM
                                             :required []
                                             ;; TODO: sync with attachments in Commons.
;                                             :attachments [:ilmoitus-poikkeuksellisesta-tilanteesta [:kayttoturvallisuustiedote]
;                                                           :kartat [:jatteen-sijainti]]
                                             :attachments []
                                             :add-operation-allowed false
                                             :min-outgoing-link-permits 0
                                             :asianhallinta true}

   :maastoliikennelaki-kilpailut-ja-harjoitukset {:schema "maastoliikennelaki-kilpailut-ja-harjoitukset"
                                                  :permit-type permit/YM
                                                  :applicant-doc-schema applicant-doc-schema-YM
                                                  :required []
                                                  ;; TODO: sync with attachments in Commons.
;                                                  :attachments [:maastoliikennelaki-kilpailut-ja-harjoitukset [:asemapiirros-kilpailu-tai-harjoitusalueesta]]
                                                  :attachments []
                                                  :add-operation-allowed false
                                                  :min-outgoing-link-permits 0
                                                  :asianhallinta true}
   })

(defn- tyonjohtaja-state-machine-resolver [{subtype :permitSubtype :as application}]
  (if (= :tyonjohtaja-ilmoitus (keyword subtype))
    states/tj-ilmoitus-state-graph
    states/tj-hakemus-state-graph))

(def Operation
  {; Documents
   :schema sc/Str
   :required [sc/Str]
   :applicant-doc-schema sc/Str
   (sc/optional-key :optional) #{sc/Str}
   (sc/optional-key :schema-data) [sc/Any]

   :attachments [sc/Any]

   ; Type and workflow
   :permit-type (sc/pred permit/valid-permit-type?)
   (sc/optional-key :subtypes) [(sc/maybe sc/Keyword)]
   (sc/optional-key :state-graph-resolver) util/Fn

   ; Can be added to existing application (or only created with a new application)
   :add-operation-allowed sc/Bool

   ; Link permits
   :min-outgoing-link-permits sc/Num
   (sc/optional-key :max-outgoing-link-permits) sc/Num
   (sc/optional-key :max-incoming-link-permits) sc/Num
   ; allowed permit types of link permit
   (sc/optional-key :allowed-link-permit-types) #{sc/Str}

   :asianhallinta sc/Bool
   })

(def operations
  (merge
    {:asuinrakennus               {:schema "uusiRakennus"
                                   :permit-type permit/R
                                   :applicant-doc-schema applicant-doc-schema-R
                                   :schema-data [[["kaytto" "kayttotarkoitus"] usages/yhden-asunnon-talot]
                                                 [["huoneistot" "0" "huoneistonumero"] "000"]
                                                 [["huoneistot" "0" "muutostapa"] "lis\u00e4ys"]]
                                   :required common-rakval-schemas
                                   :attachments uuden_rakennuksen_liitteet
                                   :add-operation-allowed true
                                   :min-outgoing-link-permits 0
                                   :asianhallinta false} ;TODO old op-tree, remove later
     :kerrostalo-rivitalo         {:schema "uusiRakennus"
                                   :permit-type permit/R
                                   :applicant-doc-schema applicant-doc-schema-R
                                   :schema-data [[["kaytto" "kayttotarkoitus"] usages/rivitalot]
                                                 [["huoneistot" "0" "huoneistonumero"] "000"]
                                                 [["huoneistot" "0" "muutostapa"] "lis\u00e4ys"]]
                                   :required common-rakval-schemas
                                   :attachments uuden_rakennuksen_liitteet
                                   :add-operation-allowed true
                                   :min-outgoing-link-permits 0
                                   :asianhallinta false}
     :pientalo                    {:schema "uusiRakennus"
                                   :permit-type permit/R
                                   :applicant-doc-schema applicant-doc-schema-R
                                   :schema-data [[["kaytto" "kayttotarkoitus"] usages/yhden-asunnon-talot]
                                                 [["huoneistot" "0" "huoneistonumero"] "000"]
                                                 [["huoneistot" "0" "muutostapa"] "lis\u00e4ys"]]
                                   :required common-rakval-schemas
                                   :attachments uuden_rakennuksen_liitteet
                                   :add-operation-allowed true
                                   :min-outgoing-link-permits 0
                                   :asianhallinta false}
     :vapaa-ajan-asuinrakennus    {:schema "uusi-rakennus-ei-huoneistoa"
                                   :permit-type permit/R
                                   :applicant-doc-schema applicant-doc-schema-R
                                   :schema-data [[["kaytto" "kayttotarkoitus"] usages/vapaa-ajan-asuinrakennus]]
                                   :required common-rakval-schemas
                                   :attachments uuden_rakennuksen_liitteet
                                   :add-operation-allowed true
                                   :min-outgoing-link-permits 0
                                   :asianhallinta false}
     :varasto-tms                 {:schema "uusi-rakennus-ei-huoneistoa"
                                   :permit-type permit/R
                                   :applicant-doc-schema applicant-doc-schema-R
                                   :schema-data [[["kaytto" "kayttotarkoitus"] usages/talousrakennus]]
                                   :required common-rakval-schemas
                                   :attachments uuden_rakennuksen_liitteet
                                   :add-operation-allowed true
                                   :min-outgoing-link-permits 0
                                   :asianhallinta false}
     :julkinen-rakennus           {:schema "uusiRakennus"
                                   :permit-type permit/R
                                   :applicant-doc-schema applicant-doc-schema-R
                                   :schema-data [[["huoneistot" "0" "huoneistonumero"] "000"]
                                                 [["huoneistot" "0" "muutostapa"] "lis\u00e4ys"]]
                                   :required common-rakval-schemas
                                   :attachments uuden_rakennuksen_liitteet
                                   :add-operation-allowed true
                                   :min-outgoing-link-permits 0
                                   :asianhallinta false} ;TODO old op-tree, remove later
     :teollisuusrakennus          {:schema "uusiRakennus"
                                   :permit-type permit/R
                                   :applicant-doc-schema applicant-doc-schema-R
                                   :required common-rakval-schemas
                                   :attachments uuden_rakennuksen_liitteet
                                   :add-operation-allowed true
                                   :min-outgoing-link-permits 0
                                   :asianhallinta false}
     :muu-uusi-rakentaminen       {:schema "uusiRakennus"
                                   :permit-type permit/R
                                   :applicant-doc-schema applicant-doc-schema-R
                                   :schema-data [[["huoneistot" "0" "huoneistonumero"] "000"]
                                                 [["huoneistot" "0" "muutostapa"] "lis\u00e4ys"]]
                                   :required common-rakval-schemas
                                   :attachments uuden_rakennuksen_liitteet
                                   :add-operation-allowed true
                                   :min-outgoing-link-permits 0
                                   :asianhallinta false}
     :laajentaminen               {:schema "rakennuksen-laajentaminen"
                                   :permit-type permit/R
                                   :applicant-doc-schema applicant-doc-schema-R
                                   :required common-rakval-schemas
                                   :attachments rakennuksen_laajennuksen_liitteet
                                   :add-operation-allowed true
                                   :min-outgoing-link-permits 0
                                   :asianhallinta false} ;TODO old op-tree, remove later
     :kerrostalo-rt-laaj          {:schema "rakennuksen-laajentaminen"
                                   :permit-type permit/R
                                   :applicant-doc-schema applicant-doc-schema-R
                                   :required common-rakval-schemas
                                   :attachments rakennuksen_laajennuksen_liitteet
                                   :add-operation-allowed true
                                   :min-outgoing-link-permits 0
                                   :asianhallinta false}
     :pientalo-laaj               {:schema "rakennuksen-laajentaminen"
                                   :permit-type permit/R
                                   :applicant-doc-schema applicant-doc-schema-R
                                   :required common-rakval-schemas
                                   :attachments rakennuksen_laajennuksen_liitteet
                                   :add-operation-allowed true
                                   :min-outgoing-link-permits 0
                                   :asianhallinta false}
     :vapaa-ajan-rakennus-laaj    {:schema "rakennuksen-laajentaminen-ei-huoneistoja"
                                   :permit-type permit/R
                                   :applicant-doc-schema applicant-doc-schema-R
                                   :required common-rakval-schemas
                                   :attachments rakennuksen_laajennuksen_liitteet
                                   :add-operation-allowed true
                                   :min-outgoing-link-permits 0
                                   :asianhallinta false}
     :talousrakennus-laaj         {:schema "rakennuksen-laajentaminen-ei-huoneistoja"
                                   :permit-type permit/R
                                   :applicant-doc-schema applicant-doc-schema-R
                                   :required common-rakval-schemas
                                   :attachments rakennuksen_laajennuksen_liitteet
                                   :add-operation-allowed true
                                   :min-outgoing-link-permits 0
                                   :asianhallinta false}
     :teollisuusrakennus-laaj     {:schema "rakennuksen-laajentaminen"
                                   :permit-type permit/R
                                   :applicant-doc-schema applicant-doc-schema-R
                                   :required common-rakval-schemas
                                   :attachments rakennuksen_laajennuksen_liitteet
                                   :add-operation-allowed true
                                   :min-outgoing-link-permits 0
                                   :asianhallinta false}
     :muu-rakennus-laaj           {:schema "rakennuksen-laajentaminen"
                                   :permit-type permit/R
                                   :applicant-doc-schema applicant-doc-schema-R
                                   :required common-rakval-schemas
                                   :attachments rakennuksen_laajennuksen_liitteet
                                   :add-operation-allowed true
                                   :min-outgoing-link-permits 0
                                   :asianhallinta false}
     :perus-tai-kant-rak-muutos   {:schema "rakennuksen-muuttaminen-ei-huoneistoja"
                                   :permit-type permit/R
                                   :applicant-doc-schema applicant-doc-schema-R
                                   :schema-data [[["muutostyolaji"] schemas/perustusten-korjaus]]
                                   :required common-rakval-schemas
                                   :attachments rakennuksen_laajennuksen_liitteet
                                   :add-operation-allowed true
                                   :min-outgoing-link-permits 0
                                   :asianhallinta false}
     :kayttotark-muutos           {:schema "rakennuksen-muuttaminen"
                                   :permit-type permit/R
                                   :applicant-doc-schema applicant-doc-schema-R
                                   :schema-data [[["muutostyolaji"] schemas/kayttotarkotuksen-muutos]]
                                   :required common-rakval-schemas
                                   :attachments rakennuksen_muutos_liitteet
                                   :add-operation-allowed true
                                   :min-outgoing-link-permits 0
                                   :asianhallinta false}
     :sisatila-muutos             {:schema "rakennuksen-muuttaminen"
                                   :permit-type permit/R
                                   :applicant-doc-schema applicant-doc-schema-R
                                   :schema-data [[["muutostyolaji"] schemas/kayttotarkotuksen-muutos]]
                                   :required common-rakval-schemas
                                   :attachments rakennuksen_muutos_liitteet
                                   :add-operation-allowed true
                                   :min-outgoing-link-permits 0
                                   :asianhallinta false}
     :julkisivu-muutos            {:schema "rakennuksen-muuttaminen-ei-huoneistoja-ei-ominaisuuksia"
                                   :permit-type permit/R
                                   :applicant-doc-schema applicant-doc-schema-R
                                   :schema-data [[["muutostyolaji"] schemas/muumuutostyo]]
                                   :required common-rakval-schemas
                                   :attachments rakennuksen_laajennuksen_liitteet
                                   :add-operation-allowed true
                                   :min-outgoing-link-permits 0
                                   :asianhallinta false}
     :jakaminen-tai-yhdistaminen  {:schema "rakennuksen-muuttaminen"
                                   :permit-type permit/R
                                   :applicant-doc-schema applicant-doc-schema-R
                                   :schema-data [[["muutostyolaji"] schemas/muumuutostyo]]
                                   :required common-rakval-schemas
                                   :attachments rakennuksen_laajennuksen_liitteet
                                   :add-operation-allowed true
                                   :min-outgoing-link-permits 0
                                   :asianhallinta false}
     :markatilan-laajentaminen    {:schema "rakennuksen-muuttaminen-ei-huoneistoja-ei-ominaisuuksia"
                                   :permit-type permit/R
                                   :applicant-doc-schema applicant-doc-schema-R
                                   :schema-data [[["muutostyolaji"] schemas/muumuutostyo]]
                                   :required common-rakval-schemas
                                   :attachments rakennuksen_laajennuksen_liitteet
                                   :add-operation-allowed true
                                   :min-outgoing-link-permits 0
                                   :asianhallinta false}
     :linjasaneeraus              {:schema "rakennuksen-muuttaminen"
                                   :permit-type permit/R
                                   :applicant-doc-schema applicant-doc-schema-R
                                   :schema-data [[["muutostyolaji"] schemas/muumuutostyo]]
                                   :required common-rakval-schemas
                                   :attachments rakennuksen_laajennuksen_liitteet
                                   :add-operation-allowed true
                                   :min-outgoing-link-permits 0
                                   :asianhallinta false}
     :takka-tai-hormi             {:schema "rakennuksen-muuttaminen-ei-huoneistoja-ei-ominaisuuksia"
                                   :permit-type permit/R
                                   :applicant-doc-schema applicant-doc-schema-R
                                   :schema-data [[["muutostyolaji"] schemas/muumuutostyo]]
                                   :required common-rakval-schemas
                                   :attachments rakennuksen_laajennuksen_liitteet
                                   :add-operation-allowed true
                                   :min-outgoing-link-permits 0
                                   :asianhallinta false}
     :parveke-tai-terassi         {:schema "rakennuksen-muuttaminen-ei-huoneistoja-ei-ominaisuuksia"
                                   :permit-type permit/R
                                   :applicant-doc-schema applicant-doc-schema-R
                                   :schema-data [[["muutostyolaji"] schemas/muumuutostyo]]
                                   :required common-rakval-schemas
                                   :attachments rakennuksen_laajennuksen_liitteet
                                   :add-operation-allowed true
                                   :min-outgoing-link-permits 0
                                   :asianhallinta false}
     :muu-laajentaminen           {:schema "rakennuksen-muuttaminen"
                                   :permit-type permit/R
                                   :applicant-doc-schema applicant-doc-schema-R
                                   :schema-data [[["muutostyolaji"] schemas/muumuutostyo]]
                                   :required common-rakval-schemas
                                   :attachments rakennuksen_laajennuksen_liitteet
                                   :add-operation-allowed true
                                   :min-outgoing-link-permits 0
                                   :asianhallinta false}
     :auto-katos                  {:schema "kaupunkikuvatoimenpide"
                                   :permit-type permit/R
                                   :applicant-doc-schema applicant-doc-schema-R
                                   :required common-rakval-schemas
                                   :attachments kaupunkikuva_toimenpide_liitteet
                                   :add-operation-allowed true
                                   :min-outgoing-link-permits 0
                                   :asianhallinta false}
     :masto-tms                   {:schema "kaupunkikuvatoimenpide"
                                   :permit-type permit/R
                                   :applicant-doc-schema applicant-doc-schema-R
                                   :required common-rakval-schemas
                                   :attachments kaupunkikuva_toimenpide_liitteet
                                   :add-operation-allowed true
                                   :min-outgoing-link-permits 0
                                   :asianhallinta false}
     :mainoslaite                 {:schema "kaupunkikuvatoimenpide"
                                   :permit-type permit/R
                                   :applicant-doc-schema applicant-doc-schema-R
                                   :required common-rakval-schemas
                                   :attachments kaupunkikuva_toimenpide_liitteet
                                   :add-operation-allowed true
                                   :min-outgoing-link-permits 0
                                   :asianhallinta false}
     :aita                        {:schema "kaupunkikuvatoimenpide"
                                   :permit-type permit/R
                                   :applicant-doc-schema applicant-doc-schema-R
                                   :required common-rakval-schemas
                                   :attachments kaupunkikuva_toimenpide_liitteet
                                   :add-operation-allowed true
                                   :min-outgoing-link-permits 0
                                   :asianhallinta false}
     :maalampo                    {:schema "maalampokaivo"
                                   :permit-type permit/R
                                   :applicant-doc-schema applicant-doc-schema-R
                                   :required common-rakval-schemas
                                   :attachments kaupunkikuva_toimenpide_liitteet
                                   :add-operation-allowed true
                                   :min-outgoing-link-permits 0
                                   :asianhallinta false}
     :jatevesi                    {:schema "kaupunkikuvatoimenpide"
                                   :permit-type permit/R
                                   :applicant-doc-schema applicant-doc-schema-R
                                   :required common-rakval-schemas
                                   :attachments kaupunkikuva_toimenpide_liitteet
                                   :add-operation-allowed true
                                   :min-outgoing-link-permits 0
                                   :asianhallinta false}
     :muu-rakentaminen            {:schema "kaupunkikuvatoimenpide"
                                   :permit-type permit/R
                                   :applicant-doc-schema applicant-doc-schema-R
                                   :required common-rakval-schemas
                                   :attachments kaupunkikuva_toimenpide_liitteet
                                   :add-operation-allowed true
                                   :min-outgoing-link-permits 0
                                   :asianhallinta false}
     :purkaminen                  {:schema "purkaminen"
                                   :permit-type permit/R
                                   :applicant-doc-schema applicant-doc-schema-R
                                   :required common-rakval-schemas
                                   :attachments [:muut [:selvitys_rakennusjatteen_maarasta_laadusta_ja_lajittelusta
                                                        :selvitys_purettavasta_rakennusmateriaalista_ja_hyvaksikaytosta]]
                                   :add-operation-allowed true
                                   :min-outgoing-link-permits 0
                                   :asianhallinta false}
     :kaivuu                      {:schema "maisematyo"
                                   :permit-type permit/R
                                   :applicant-doc-schema applicant-doc-schema-R
                                   :required common-rakval-schemas
                                   :attachments [:paapiirustus [:asemapiirros]]
                                   :add-operation-allowed true
                                   :min-outgoing-link-permits 0
                                   :asianhallinta false}
     :puun-kaataminen             {:schema "maisematyo"
                                   :permit-type permit/R
                                   :applicant-doc-schema applicant-doc-schema-R
                                   :required common-rakval-schemas
                                   :attachments [:paapiirustus [:asemapiirros]]
                                   :add-operation-allowed true
                                   :min-outgoing-link-permits 0
                                   :asianhallinta false}
     :tontin-jarjestelymuutos     {:schema "maisematyo"
                                   :permit-type permit/R
                                   :applicant-doc-schema applicant-doc-schema-R
                                   :required  common-rakval-schemas
                                   :attachments [:paapiirustus [:asemapiirros]]
                                   :add-operation-allowed true
                                   :min-outgoing-link-permits 0
                                   :asianhallinta false}
     :muu-maisema-toimenpide      {:schema "maisematyo"
                                   :permit-type permit/R
                                   :applicant-doc-schema applicant-doc-schema-R
                                   :required  common-rakval-schemas
                                   :attachments [:paapiirustus [:asemapiirros]]
                                   :add-operation-allowed true
                                   :min-outgoing-link-permits 0
                                   :asianhallinta false}
     :tontin-ajoliittyman-muutos  {:schema "maisematyo"
                                   :permit-type permit/R
                                   :applicant-doc-schema applicant-doc-schema-R
                                   :required  common-rakval-schemas
                                   :attachments [:paapiirustus [:asemapiirros]]
                                   :add-operation-allowed true
                                   :min-outgoing-link-permits 0
                                   :asianhallinta false}
     :paikoutysjarjestus-muutos   {:schema "maisematyo"
                                   :permit-type permit/R
                                   :applicant-doc-schema applicant-doc-schema-R
                                   :required  common-rakval-schemas
                                   :attachments [:paapiirustus [:asemapiirros]]
                                   :add-operation-allowed true
                                   :min-outgoing-link-permits 0
                                   :asianhallinta false}
     :kortteli-yht-alue-muutos    {:schema "maisematyo"
                                   :permit-type permit/R
                                   :applicant-doc-schema applicant-doc-schema-R
                                   :required  common-rakval-schemas
                                   :attachments [:paapiirustus [:asemapiirros]]
                                   :add-operation-allowed true
                                   :min-outgoing-link-permits 0
                                   :asianhallinta false}
     :muu-tontti-tai-kort-muutos  {:schema "maisematyo"
                                   :permit-type permit/R
                                   :applicant-doc-schema applicant-doc-schema-R
                                   :required  common-rakval-schemas
                                   :attachments [:paapiirustus [:asemapiirros]]
                                   :add-operation-allowed true
                                   :min-outgoing-link-permits 0
                                   :asianhallinta false}
     :kiinteistonmuodostus         {:schema "kiinteistonmuodostus"
                                    :permit-type permit/KT
                                    :applicant-doc-schema applicant-doc-schema-KT
                                    :optional #{"secondary-kiinteistot"}
                                    :required common-maanmittaus-schemas
                                    :attachments []
                                    :add-operation-allowed true
                                    :min-outgoing-link-permits 0
                                    :asianhallinta true}
     :rasitetoimitus                {:schema "rasitetoimitus"
                                     :permit-type permit/KT
                                     :applicant-doc-schema applicant-doc-schema-KT
                                     :optional #{"secondary-kiinteistot"}
                                     :required common-maanmittaus-schemas
                                     :attachments []
                                     :add-operation-allowed true
                                     :min-outgoing-link-permits 0
                                     :asianhallinta true}
     :rajankaynti                  {:schema "rajankaynti"
                                    :permit-type permit/KT
                                    :applicant-doc-schema applicant-doc-schema-KT
                                    :optional #{"secondary-kiinteistot"}
                                    :required common-maanmittaus-schemas
                                    :attachments []
                                    :add-operation-allowed false
                                    :min-outgoing-link-permits 0
                                    :asianhallinta true}
     :poikkeamis                  {:schema "rakennushanke"
                                   :permit-type permit/P
                                   :applicant-doc-schema applicant-doc-schema-P
                                   :required  (conj common-poikkeamis-schemas "suunnittelutarveratkaisun-lisaosa")
                                   :attachments [:paapiirustus [:asemapiirros]]
                                   :add-operation-allowed false
                                   :min-outgoing-link-permits 0
                                   :asianhallinta true}
    :maa-aineslupa               {:schema "maa-aineslupa-kuvaus"
                                  :permit-type permit/MAL
                                  :applicant-doc-schema applicant-doc-schema-MAL
                                  :required ["ymp-maksaja" "rakennuspaikka"]
                                  :attachments []
                                  :add-operation-allowed false
                                  :min-outgoing-link-permits 0
                                  :asianhallinta true}
    :vvvl-vesijohdosta           {:schema "talousvedet"
                                  :permit-type permit/VVVL
                                  :applicant-doc-schema applicant-doc-schema-VVVL
                                  :required common-vvvl-schemas
                                  :attachments [:kartat [:kartta-melun-ja-tarinan-leviamisesta]]
                                  :add-operation-allowed false
                                  :min-outgoing-link-permits 0
                                  :asianhallinta true}
    :vvvl-viemarista             {:schema "jatevedet"
                                  :permit-type permit/VVVL
                                  :applicant-doc-schema applicant-doc-schema-VVVL
                                  :required common-vvvl-schemas
                                  :attachments [:kartat [:kartta-melun-ja-tarinan-leviamisesta]]
                                  :add-operation-allowed false
                                  :min-outgoing-link-permits 0
                                  :asianhallinta true}
    :vvvl-vesijohdosta-ja-viemarista {:schema "talousvedet"
                                      :permit-type permit/VVVL
                                      :applicant-doc-schema applicant-doc-schema-VVVL
                                      :required (conj common-vvvl-schemas "jatevedet")
                                      :attachments [:kartat [:kartta-melun-ja-tarinan-leviamisesta]]
                                      :add-operation-allowed false
                                      :min-outgoing-link-permits 0
                                      :asianhallinta true}
    :vvvl-hulevesiviemarista    {:schema "hulevedet"
                                 :permit-type permit/VVVL
                                 :applicant-doc-schema applicant-doc-schema-VVVL
                                 :required common-vvvl-schemas
                                 :attachments [:kartat [:kartta-melun-ja-tarinan-leviamisesta]]
                                 :add-operation-allowed false
                                 :min-outgoing-link-permits 0
                                 :asianhallinta true}

    :tyonjohtajan-nimeaminen     {:schema "hankkeen-kuvaus-minimum"
                                  :permit-type permit/R
                                  :applicant-doc-schema applicant-doc-schema-R
                                  :required ["tyonjohtaja" "maksaja"]
                                  :attachments []
                                  :add-operation-allowed false
                                  :min-outgoing-link-permits 1
                                  :max-outgoing-link-permits 1
                                  :max-incoming-link-permits 0
                                  :allowed-link-permit-types #{permit/R permit/P}
                                  :asianhallinta false}

    :tyonjohtajan-nimeaminen-v2  {:schema "tyonjohtaja-v2"
                                  :permit-type permit/R
                                  :applicant-doc-schema applicant-doc-schema-R
                                  :subtypes [:tyonjohtaja-hakemus :tyonjohtaja-ilmoitus]
                                  :state-graph-resolver tyonjohtaja-state-machine-resolver
                                  :required ["hankkeen-kuvaus-minimum"]
                                  :attachments []
                                  :add-operation-allowed false
                                  :min-outgoing-link-permits 1
                                  :max-outgoing-link-permits 1
                                  :max-incoming-link-permits 0
                                  :allowed-link-permit-types #{permit/R permit/P}
                                  :asianhallinta false}

    :suunnittelijan-nimeaminen   {:schema "hankkeen-kuvaus-minimum"
                                  :permit-type permit/R
                                  :applicant-doc-schema applicant-doc-schema-R
                                  :required ["suunnittelija" "maksaja"]
                                  :attachments []
                                  :add-operation-allowed false
                                  :min-outgoing-link-permits 1
                                  :asianhallinta false}

    :jatkoaika                   {:schema "hankkeen-kuvaus-minimum"
                                  :permit-type permit/R
                                  :applicant-doc-schema applicant-doc-schema-R
                                  :required ["maksaja"]
                                  :attachments []
                                  :add-operation-allowed false
                                  :min-outgoing-link-permits 1
                                  :asianhallinta false}

    :aiemmalla-luvalla-hakeminen {:schema "hankkeen-kuvaus"
                                  :permit-type permit/R
                                  :applicant-doc-schema applicant-doc-schema-R
                                  :required []
                                  :optional #{"maksaja" "paasuunnittelija" "suunnittelija"}
                                  :attachments []
                                  :add-operation-allowed false
                                  :min-outgoing-link-permits 0
                                  :asianhallinta false}

    :rak-valm-tyo                {:schema "maisematyo"
                                  :permit-type permit/R
                                  :applicant-doc-schema applicant-doc-schema-R
                                  :required common-rakval-schemas
                                  :attachments [:paapiirustus [:asemapiirros]]
                                  :add-operation-allowed true
                                  :min-outgoing-link-permits 0
                                  :asianhallinta false}

    :aloitusoikeus               {:schema "aloitusoikeus"
                                  :permit-type permit/R
                                  :applicant-doc-schema applicant-doc-schema-R
                                  :required ["maksaja"]
                                  :attachments []
                                  :add-operation-allowed false
                                  :min-outgoing-link-permits 1
                                  :asianhallinta false}
    :raktyo-aloit-loppuunsaat   {:schema "hankkeen-kuvaus-minimum"
                                 :permit-type permit/R
                                 :applicant-doc-schema applicant-doc-schema-R
                                 :required ["maksaja"]
                                 :attachments []
                                 :add-operation-allowed false
                                 :min-outgoing-link-permits 1
                                 :asianhallinta false}
    :tonttijako                 {:schema "maankayton-muutos"
                                 :permit-type permit/MM
                                 :applicant-doc-schema applicant-doc-schema-MM
                                 :required common-maanmittaus-schemas
                                 :attachments []
                                 :add-operation-allowed false
                                 :min-outgoing-link-permits 0
                                 :asianhallinta true}
    :asemakaava                 {:schema "maankayton-muutos"
                                 :permit-type permit/MM
                                 :applicant-doc-schema applicant-doc-schema-MM
                                 :required common-maanmittaus-schemas
                                 :attachments []
                                 :add-operation-allowed false
                                 :min-outgoing-link-permits 0
                                 :asianhallinta true}
     :ranta-asemakaava          {:schema "maankayton-muutos"
                                 :permit-type permit/MM
                                 :applicant-doc-schema applicant-doc-schema-MM
                                 :required common-maanmittaus-schemas
                                 :attachments []
                                 :add-operation-allowed false
                                 :min-outgoing-link-permits 0
                                 :asianhallinta true}
     :yleiskaava                {:schema "maankayton-muutos"
                                 :permit-type permit/MM
                                 :applicant-doc-schema applicant-doc-schema-MM
                                 :required common-maanmittaus-schemas
                                 :attachments []
                                 :add-operation-allowed false
                                 :min-outgoing-link-permits 0
                                 :asianhallinta true}
     }
    ya-operations
    yl-operations
    yi-operations
    ym-operations))

;; Validate operations
(doseq [[k op] operations]
  (let [v (sc/check Operation op)]
    (assert (nil? v) (str k \space v))))
;;
;; Functions
;;

(def link-permit-required-operations
  (reduce (fn [result [operation metadata]]
            (if (pos? (:min-outgoing-link-permits metadata))
              (conj result operation)
              result)) #{} operations))

(defn get-operation-metadata
  "First form returns all metadata for operation. Second form returns value of given metadata."
  ([operation-name] (when operation-name (operations (keyword operation-name))))
  ([operation-name metadata-key] (when operation-name ((keyword metadata-key) (operations (keyword operation-name))))))

(defn get-primary-operation-metadata
  ([{op :primaryOperation}] (get-operation-metadata (:name op)))
  ([{op :primaryOperation} metadata-key] (get-operation-metadata (:name op) metadata-key)))

(defn resolve-applicant-doc-schema [application]
  (:applicant-doc-schema (get-primary-operation-metadata application)))

(defn permit-type-of-operation [operation]
  (get-operation-metadata operation :permit-type))

(defn- is-add-operation-allowed-for-operation [operation]
  (get-operation-metadata operation :add-operation-allowed))

(defn operations-filtered [filtering-fn only-addable?]
  (clojure.walk/postwalk
    (fn [node]
      (if (keyword? node)
        (when (filtering-fn node)
          ; Return operation keyword if permit type matches,
          ; and if the only-addable filtering is required, apply that.
          ; Otherwise return nil.
          (if only-addable?
            (when (is-add-operation-allowed-for-operation node)
              node)
            node))
        (if (string? node)
          ; A step in a path is returned as is
          node
          ; Not a keyword or string, must be a sequence. Take only paths that have operations.
          (let [filtered (filter identity node)]
            (when (or (> (count filtered) 1) (sequential? (first filtered)))
              filtered)))))
    operation-tree))

(defn- sort-operation-tree [target-tree]
  (keep identity
    (for [part operation-tree]
      (some #(when (= (first %) (first part)) % ) target-tree))))

(defn organization-operations [organization]
  (let [permit-types (->> organization :scope (map :permitType) set)
        filtering-fn (fn [node] (permit-types (permit-type-of-operation node)))]
    (sort-operation-tree
      (operations-filtered filtering-fn false))))

(defn selected-operations-for-organizations [organizations]
  (let [orgs-with-selected-ops (filter (comp seq :selected-operations) organizations)
        ;; Resolving operation tree for organizations with "selected-operations" defined in db
        op-trees-for-orgs-with-selected-ops (if-not (empty? orgs-with-selected-ops)
                                              (let [selected-operations (->> orgs-with-selected-ops
                                                                          (map :selected-operations)
                                                                          flatten
                                                                          (map keyword)
                                                                          set)]
                                                (operations-filtered selected-operations false))
                                              [])]
    (sort-operation-tree op-trees-for-orgs-with-selected-ops)))

(defn addable-operations [selected-operations permit-type]
  (let [selected-operations (set selected-operations)
        filtering-fn (fn [node] (and
                                  (or (empty? selected-operations) (selected-operations node))
                                  (= (name permit-type) (permit-type-of-operation node))))]
    (sort-operation-tree
      (operations-filtered filtering-fn true))))

(comment
  ; operations (keys) with asianhallinta enabled
  (keys (into {} (filter (fn [[k v]] (:asianhallinta v)) operations))))

(defn printout! [x indent]
  (let [p! #(println (str indent (lupapalvelu.i18n/localize "fi" "operations.tree" %)))]
    (cond
      (string? x) (p! x)
      (keyword? (second x)) (p! (first x))
      (string? (first x)) (do (p! (first x)) (printout! (second x) (str indent \tab)))
      :else (doseq [x2 x] (printout! x2 indent)))))
