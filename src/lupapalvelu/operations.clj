(ns lupapalvelu.operations
  (:require [lupapalvelu.document.allu-schemas]
            [lupapalvelu.document.poikkeamis-schemas]
            [lupapalvelu.document.schemas :as schemas]
            [lupapalvelu.document.vesihuolto-schemas]
            [lupapalvelu.document.waste-schemas :as waste-schemas]
            [lupapalvelu.document.yleiset-alueet-schemas]
            [lupapalvelu.document.ymparisto-schemas]
            [lupapalvelu.document.yhteiskasittely-maa-aines-ja-ymparistoluvalle-schemas]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.states :as states]
            [monger.operators :refer :all]
            [sade.core :refer :all]
            [sade.strings :as ss]
            [sade.util :as util]
            [schema.core :as sc]
            [sade.env :as env]))

(def default-description "operations.tree.default-description")

(def- operation-tree-for-R
  ["Rakentaminen ja purkaminen"
   (filter identity
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
        ["Asuinhuoneiston jakaminen tai yhdistaminen" :jakaminen-tai-yhdistaminen]
        ["rakennustietojen-korjaus" :rakennustietojen-korjaus]]]
      ["Rakennelman rakentaminen"
       [["Auto- tai grillikatos, vaja, kioski tai vastaava" :auto-katos]
        ["Masto, piippu, sailio, laituri tai vastaava" :masto-tms]
        ["Mainoslaite" :mainoslaite]
        ["Aita" :aita]
        ["Maalampokaivon poraaminen tai lammonkeruuputkiston asentaminen" :maalampo]
        ["Rakennuksen jatevesijarjestelman uusiminen" :jatevesi]]]
      ["Rakennuksen purkaminen" :purkaminen]
      ["Maisemaa muuttava toimenpide"
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
        ["raktyo-aloit-loppuunsaat" :raktyo-aloit-loppuunsaat]]]
      ["rasite-tai-yhteisjarjestely" :rasite-tai-yhteisjarjestely]
      ])])

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
        ["muu-liikennealuetyo" :ya-katulupa-muu-liikennealuetyo]]]
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
      ["promootio" :promootio]
      ["lyhytaikainen-maanvuokraus" :lyhytaikainen-maanvuokraus]
      ["muu-kayttolupa" :ya-kayttolupa-muu-kayttolupa]]]
    ["jatkoaika" :ya-jatkoaika]]])

(def- operation-tree-for-P
  ["Poikkeusluvat ja suunnittelutarveratkaisut" :poikkeamis])

(def- operation-tree-for-Y
  ["Ymp\u00e4rist\u00f6luvat"
   [["ymparistonsuojelulain-mukaiset-ilmoitukset"
     [["Meluilmoitus" :meluilmoitus]                        ; permit/YI
      ["yleinen-ilmoitus" :yleinen-ilmoitus]                ; permit/YI
      ["koeluontoinen-toiminta" :koeluontoinen-toiminta]    ; permit/YM
      ["ilmoitus-poikkeuksellisesta-tilanteesta" :ilmoitus-poikkeuksellisesta-tilanteesta] ; permit/YM
      ["ylijaamamaiden-hyodyntaminen" :ylijaamamaiden-hyodyntaminen] ; permit/YI
      ["jatteiden-hyodyntaminen-maarakentamisessa" :jatteiden-hyodyntaminen-maarakentamisessa]
      ["rekisterointi-ilmoitus" :rekisterointi-ilmoitus]]]

    ["maatalouden-ilmoitukset"
     [["lannan-varastointi-v2" :lannan-varastointi-v2]  ; permit/YM
      ["lannan-levittamisesta-poikkeustilanteessa" :lannan-levittamisesta-poikkeustilanteessa]]]

    ; permit/YL
    ["Pima" :pima]

    ; permit/YM
    ["kunnan-ympmaarayksista-poikkeaminen"
     [["kaytostapoistetun-oljy-tai-kemikaalisailion-jattaminen-maaperaan" :kaytostapoistetun-oljy-tai-kemikaalisailion-jattaminen-maaperaan]
      ["talousjatevesien-kasittelysta-poikkeaminen" :talousjatevesien-kasittelysta-poikkeaminen]]]

    ["maa-ainekset"
     [["maa-ainesten_ottaminen" :maa-aineslupa]             ; permit/MAL
      ["maa-ainesten_ottaminen_jatkoaika" :maa-aineslupa-jatkoaika] ; permit/MAL
      ["maa-ainesten-kotitarveotto" :maa-ainesten-kotitarveotto]]] ; permit/YM
    ["yhteiskasittely-maa-aines-ja-ymparistoluvalle" :yhteiskasittely-maa-aines-ja-ymparistoluvalle]

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
      ["hulevesiviemarista" :vvvl-hulevesiviemarista]]]

    ; permit/YM
    ["jatelain-mukaiset-ilmoitukset"
     [["jatteen-keraystoiminta" :jatteen-keraystoiminta]]]

    ; permit/YM
    ["luonnonsuojelulain-mukaiset-ilmoitukset"
     [["muistomerkin-rauhoittaminen" :muistomerkin-rauhoittaminen]]]

    ; permit/YM
    ["maastoliikennelaki-kilpailut-ja-harjoitukset" :maastoliikennelaki-kilpailut-ja-harjoitukset]
    ["vesiliikennelaki-kilpailut-ja-harjoitukset" :vesiliikennelaki-kilpailut-ja-harjoitukset]

    ["puiden-kaataminen"
     [["ilmoitus-puiden-kaatamisesta-asemakaava-alueella" :yl-puiden-kaataminen]]]

    ["ymparistoluvan-selventaminen"
     [["ymparistoluvan-selventaminen" :ymparistoluvan-selventaminen]]]
    ["leirintaalueilmoitus" :leirintaalueilmoitus]
    ["kirjallinen-vireillepano" :kirjallinen-vireillepano]]])

(def operation-tree-for-KT ; aka kiinteistotoimitus aka maanmittaustoimitukset
  ["maanmittaustoimitukset"
   [["tonttijako" :tonttijako]
    ["kiinteistonmuodostus" :kiinteistonmuodostus]
    ["rasitetoimitus" :rasitetoimitus]
    ["rajankaynti" :rajankaynti]]])

(def operation-tree-for-MM
  ["maankayton-muutos"
   [["asemakaava" :asemakaava]
    ["ranta-asemakaava" :ranta-asemakaava]
    ["yleiskaava" :yleiskaava]]])

(def operation-tree
  (filterv identity
    `[
      ~@[operation-tree-for-R]
      ~@[operation-tree-for-P
         operation-tree-for-Y
         operation-tree-for-YA]
      ~@[operation-tree-for-KT
         operation-tree-for-MM]]))


; Operations must be the same as in the tree structure above.
; Mappings to schemas and attachments are currently random.

(def- common-rakval-schemas ["hankkeen-kuvaus" "paatoksen-toimitus-rakval" "maksaja" "rakennuspaikka" "paasuunnittelija" "suunnittelija"])
(def- mini-rakval-schemas ["hankkeen-kuvaus" "paatoksen-toimitus-rakval" "maksaja" "rakennuspaikka-ilman-ilmoitusta" "suunnittelija"])

(def- common-rakval-org-schemas [waste-schemas/construction-waste-plan-for-organization])

(def- puun-kaataminen-schemas (filterv (partial not= "suunnittelija")
                                        mini-rakval-schemas))

(def- optional-rakval-schemas #{"hakijan-asiamies"})

(def- optional-mini-rakval-schemas #{"paasuunnittelija" "hakijan-asiamies"})

(def- common-maanmittaus-schemas ["maksaja" "kiinteisto"])

(def- common-poikkeamis-schemas ["hankkeen-kuvaus" "paatoksen-toimitus-rakval" "maksaja" "poikkeusasian-rakennuspaikka"])

(def- common-yleiset-alueet-schemas ["yleiset-alueet-maksaja"])

(def- common-ymparistolupa-schemas ["ymp-maksaja" "toiminnan-sijainti"])

(def- optional-ymparistolupa-schemas #{"ymp-maksaja"})

(def- common-vvvl-schemas ["hankkeen-kuvaus-vesihuolto" "vesihuolto-kiinteisto"])

(def- optional-allu-schemas #{"hakijan-asiamies"})

(def- applicant-doc-schema-name-hakija     "hakija")
(def- applicant-doc-schema-name-R          "hakija-r")
(def- applicant-doc-schema-name-R-TJ       "hakija-tj")
(def- applicant-doc-schema-name-YA         "hakija-ya")
(def- applicant-doc-schema-name-ilmoittaja "ilmoittaja")
(def- applicant-doc-schema-name-hakija-KT  "hakija-kt")


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

(def- ya-katulupa-general {:schema "yleiset-alueet-hankkeen-kuvaus-kaivulupa"
                           :permit-type permit/YA
                           :subtypes [:tyolupa]
                           :state-graph-resolver (constantly states/ya-tyolupa-state-graph)
                           :applicant-doc-schema applicant-doc-schema-name-YA
                           :required (conj common-yleiset-alueet-schemas
                                       "tyomaastaVastaava"
                                       "tyoaika")
                           :attachments []
                           :attachment-op-selector false
                           :add-operation-allowed false
                           :copying-allowed true
                           :min-outgoing-link-permits 0
                           :asianhallinta true})

(def- ya-kayttolupa-general {:schema "tyoaika"
                             :permit-type permit/YA
                             :subtypes [:kayttolupa]
                             :state-graph-resolver (constantly states/ya-kayttolupa-state-graph)
                             :applicant-doc-schema applicant-doc-schema-name-YA
                             :required (conj common-yleiset-alueet-schemas
                                         "yleiset-alueet-hankkeen-kuvaus-kayttolupa")
                             :optional #{"tyomaasta-vastaava-optional"}
                             :attachments []
                             :attachment-op-selector false
                             :add-operation-allowed false
                             :copying-allowed true
                             :min-outgoing-link-permits 0
                             :asianhallinta true})

(def- ya-kayttolupa-with-tyomaastavastaava
  (-> ya-kayttolupa-general
      (dissoc :optional)
      (update :required conj "tyomaastaVastaava")
      (assoc :state-graph-resolver (constantly states/ya-tyolupa-state-graph))))

(defn- sijoittaminen-state-resolver [{:keys [permitSubtype]}]
  (if (= :sijoitussopimus (keyword permitSubtype))
    states/ya-sijoitussopimus-state-graph
    states/ya-sijoituslupa-state-graph))

(def ya-sijoituslupa-subtypes [:sijoituslupa :sijoitussopimus])

(def- ya-sijoituslupa-general {:schema "yleiset-alueet-hankkeen-kuvaus-sijoituslupa"
                               :permit-type permit/YA
                               :subtypes ya-sijoituslupa-subtypes
                               :state-graph-resolver sijoittaminen-state-resolver
                               :applicant-doc-schema applicant-doc-schema-name-YA
                               :required common-yleiset-alueet-schemas
                               :optional #{"hakijan-asiamies" "tyomaasta-vastaava-optional"}
                               :attachments []
                               :attachment-op-selector false
                               :add-operation-allowed false
                               :copying-allowed true
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
   :ya-kayttolupa-vaihtolavat                                         ya-kayttolupa-general
   :ya-kayttolupa-mainostus-ja-viitoitus  {:schema "mainosten-tai-viitoitusten-sijoittaminen"
                                           :permit-type permit/YA
                                           :subtypes [:kayttolupa]
                                           :state-graph-resolver (constantly states/ya-kayttolupa-state-graph)
                                           :applicant-doc-schema applicant-doc-schema-name-YA
                                           :required common-yleiset-alueet-schemas
                                           :attachments []
                                           :attachment-op-selector false
                                           :optional #{"tyomaasta-vastaava-optional"}
                                           :add-operation-allowed false
                                           :copying-allowed true
                                           :min-outgoing-link-permits 0
                                           :asianhallinta true}
   :ya-kayttolupa-nostotyot                                           ya-kayttolupa-with-tyomaastavastaava
   :ya-kayttolupa-kattolumien-pudotustyot                             ya-kayttolupa-with-tyomaastavastaava
   :ya-kayttolupa-talon-julkisivutyot                                 ya-kayttolupa-with-tyomaastavastaava
   :ya-kayttolupa-talon-rakennustyot                                  ya-kayttolupa-with-tyomaastavastaava
   :ya-kayttolupa-muu-tyomaakaytto                                    ya-kayttolupa-with-tyomaastavastaava
   :ya-katulupa-muu-liikennealuetyo                                   ya-katulupa-general
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
                                           :state-graph-resolver (constantly states/ya-jatkoaika-state-graph)
                                           :applicant-doc-schema applicant-doc-schema-name-YA
                                           :required (conj common-yleiset-alueet-schemas
                                                           "tyo-aika-for-jatkoaika")
                                           :optional #{"tyomaasta-vastaava-optional"}
                                           :attachments []
                                           :attachment-op-selector false
                                           :add-operation-allowed false
                                           :copying-allowed false
                                           :min-outgoing-link-permits 1
                                           :max-incoming-link-permits 1
                                           :asianhallinta true}})

(def- ymparistolupa-operation
  {:schema "yl-hankkeen-kuvaus"
   :permit-type permit/YL
   :applicant-doc-schema applicant-doc-schema-name-hakija
   :schema-data []
   :required common-ymparistolupa-schemas
   :attachments []
   :add-operation-allowed false
   :copying-allowed true
   :min-outgoing-link-permits 0
   :asianhallinta true})

(def- yl-operations
  {:yl-uusi-toiminta (assoc ymparistolupa-operation :schema "yl-uusi-toiminta")
   :yl-olemassa-oleva-toiminta ymparistolupa-operation
   :yl-toiminnan-muutos ymparistolupa-operation
   :pima                          {:schema "pima"
                                   :permit-type permit/YL
                                   :applicant-doc-schema applicant-doc-schema-name-hakija
                                   :required ["ymp-ilm-kesto-mini"]
                                   :attachments []
                                   :add-operation-allowed false
                                   :copying-allowed true
                                   :min-outgoing-link-permits 0
                                   :asianhallinta false}
   :yl-puiden-kaataminen           {:schema "maisematyo"
                                    :permit-type permit/YL
                                    :applicant-doc-schema applicant-doc-schema-name-R
                                    :required mini-rakval-schemas
                                    :optional optional-mini-rakval-schemas
                                    :attachments [:kartat-ja-piirustukset [:asemapiirros]]
                                    :add-operation-allowed false
                                    :copying-allowed true
                                    :min-outgoing-link-permits 0
                                    :asianhallinta true}
   })

(def- yi-operations
      {:meluilmoitus         {:schema                    "meluilmoitus"
                              :permit-type               permit/YI
                              :applicant-doc-schema      applicant-doc-schema-name-ilmoittaja
                              :required                  ["ymp-ilm-kesto"]
                              :optional                  optional-ymparistolupa-schemas
                              :attachments               [:kartat-ja-piirustukset [:kartta-melun-ja-tarinan-leviamisesta]]
                              :add-operation-allowed     false
                              :copying-allowed           true
                              :min-outgoing-link-permits 0
                              :asianhallinta             true}
       :yleinen-ilmoitus     {:schema                    "yi-yleinen-ilmoitus"
                              :permit-type               permit/YI
                              :applicant-doc-schema      applicant-doc-schema-name-ilmoittaja
                              :required                  []
                              :optional                  optional-ymparistolupa-schemas
                              :attachments               [:hakemukset-ja-ilmoitukset [:ilmoituslomake]]
                              :add-operation-allowed     false
                              :copying-allowed           true
                              :min-outgoing-link-permits 0
                              :asianhallinta             false}
       :rekisterointi-ilmoitus {:schema                    "rekisterointi-ilmoitus"
                                :permit-type               permit/YI
                                :applicant-doc-schema      applicant-doc-schema-name-ilmoittaja
                                :required                  []
                                :optional                  optional-ymparistolupa-schemas
                                :attachments               [:hakemukset-ja-ilmoitukset [:rekisterointi-ilmoitus]]
                                :add-operation-allowed     false
                                :copying-allowed           true
                                :min-outgoing-link-permits 0
                                :asianhallinta             false}
       :leirintaalueilmoitus {:schema                    "leirintaalueilmoitus"
                              :permit-type               permit/YI
                              :applicant-doc-schema      applicant-doc-schema-name-hakija
                              :required                  ["hakijan-yhteyshenkilo"]
                              :attachments               []
                              :add-operation-allowed     false
                              :copying-allowed           true
                              :min-outgoing-link-permits 0
                              :asianhallinta             false}
       :jatteiden-hyodyntaminen-maarakentamisessa {:schema "jatteiden-hyodyntaminen-maarakentamisessa"
                                                   :permit-type permit/YI
                                                   :applicant-doc-schema applicant-doc-schema-name-hakija
                                                   :required ["hyodyntamispaikka"]
                                                   :optional optional-ymparistolupa-schemas
                                                   :attachments []
                                                   :add-operation-allowed false
                                                   :copying-allowed true
                                                   :min-outgoing-link-permits 0
                                                   :asianhallinta false}
       :lannan-levittamisesta-poikkeustilanteessa {:schema "lannan-levittamisesta-poikkeustilanteessa"
                                                   :permit-type permit/YI
                                                   :applicant-doc-schema applicant-doc-schema-name-ilmoittaja
                                                   :required []
                                                   :attachments []
                                                   :add-operation-allowed false
                                                   :copying-allowed true
                                                   :min-outgoing-link-permits 0
                                                   :asianhallinta false}
       :kirjallinen-vireillepano {:schema "kirjallinen-vireillepano"
                                  :permit-type permit/YI
                                  :applicant-doc-schema "vireillepanija"
                                  :required []
                                  :attachments []
                                  :add-operation-allowed false
                                  :copying-allowed true
                                  :min-outgoing-link-permits 0
                                  :asianhallinta false}})

(def- ym-operations
  {:muistomerkin-rauhoittaminen  {:schema "luonnonmuistomerkin-rauhoittaminen"
                                  :permit-type permit/YM
                                  :applicant-doc-schema applicant-doc-schema-name-hakija
                                  :required []
                                  :attachments []
                                  :add-operation-allowed false
                                  :copying-allowed true
                                  :min-outgoing-link-permits 0
                                  :asianhallinta true}

   :jatteen-keraystoiminta {:schema "jatteen-kerays"
                            :permit-type permit/YM
                            :applicant-doc-schema applicant-doc-schema-name-ilmoittaja
                            :required []
                            :attachments []
                            :add-operation-allowed false
                            :copying-allowed true
                            :min-outgoing-link-permits 0
                            :asianhallinta true}

   :lannan-varastointi     {:schema "lannan-varastointi"
                            :permit-type permit/YM
                            :applicant-doc-schema applicant-doc-schema-name-ilmoittaja
                            :required ["yl-maatalous-hankkeen-kuvaus"]
                            :attachments []
                            :add-operation-allowed false
                            :copying-allowed true
                            :min-outgoing-link-permits 0
                            :asianhallinta true
                            :hidden true}

  :lannan-varastointi-v2   {:schema "lannan-varastointi-v2"
                            :permit-type permit/YM
                            :applicant-doc-schema applicant-doc-schema-name-ilmoittaja
                            :required ["lannan-varastointi-ilmoituksen-tilan-tiedot"]
                            :attachments []
                            :add-operation-allowed false
                            :copying-allowed true
                            :min-outgoing-link-permits 0
                            :asianhallinta true}

   :kaytostapoistetun-oljy-tai-kemikaalisailion-jattaminen-maaperaan {:schema "kaytostapoistetun-sailion-jattaminen-maaperaan"
                                                                      :permit-type permit/YM
                                                                      :applicant-doc-schema applicant-doc-schema-name-hakija
                                                                      :required ["kiinteisto"]
                                                                      :optional optional-ymparistolupa-schemas
                                                                      :attachments []
                                                                      :add-operation-allowed false
                                                                      :copying-allowed true
                                                                      :min-outgoing-link-permits 0
                                                                      :asianhallinta true}

   :koeluontoinen-toiminta {:schema "koeluontoinen-toiminta"
                            :permit-type permit/YM
                            :applicant-doc-schema applicant-doc-schema-name-ilmoittaja
                            :required []
                            :attachments []
                            :add-operation-allowed false
                            :copying-allowed true
                            :min-outgoing-link-permits 0
                            :asianhallinta true}

   :maa-ainesten-kotitarveotto {:schema "maa-ainesten-kotitarveotto"
                                :permit-type permit/YM
                                :applicant-doc-schema applicant-doc-schema-name-ilmoittaja
                                :required ["kiinteisto"]
                                :attachments []
                                :add-operation-allowed false
                                :copying-allowed true
                                :min-outgoing-link-permits 0
                                :asianhallinta true}

   :ilmoitus-poikkeuksellisesta-tilanteesta {:schema "ilmoitus-poik-tilanteesta"
                                             :permit-type permit/YM
                                             :applicant-doc-schema applicant-doc-schema-name-ilmoittaja
                                             :required []
                                             :attachments []
                                             :add-operation-allowed false
                                             :copying-allowed true
                                             :min-outgoing-link-permits 0
                                             :asianhallinta true}

   :maastoliikennelaki-kilpailut-ja-harjoitukset {:schema "maastoliikennelaki-kilpailut-ja-harjoitukset"
                                                  :permit-type permit/YM
                                                  :applicant-doc-schema applicant-doc-schema-name-hakija
                                                  :required []
                                                  :attachments []
                                                  :add-operation-allowed true
                                                  :copying-allowed true
                                                  :min-outgoing-link-permits 0
                                                  :asianhallinta true}

   :vesiliikennelaki-kilpailut-ja-harjoitukset {:schema "vesiliikennelaki-kilpailut-ja-harjoitukset"
                                                :permit-type permit/YM
                                                :applicant-doc-schema applicant-doc-schema-name-hakija
                                                :required []
                                                :attachments []
                                                :add-operation-allowed true
                                                :copying-allowed true
                                                :min-outgoing-link-permits 0
                                                :asianhallinta true}

   :ymparistoluvan-selventaminen {:schema "ymparistoluvan-selventaminen"
                                  :permit-type permit/YM
                                  :applicant-doc-schema applicant-doc-schema-name-hakija
                                  :required []
                                  :attachments []
                                  :add-operation-allowed false
                                  :copying-allowed true
                                  :min-outgoing-link-permits 0
                                  :asianhallinta false
                                  :state-graph-resolver (constantly states/selventaminen-application-state-graph)}

   :ylijaamamaiden-hyodyntaminen {:schema "ylijaamamaiden-hyodyntaminen"
                                  :permit-type permit/YI
                                  :applicant-doc-schema applicant-doc-schema-name-hakija
                                  :required ["ylijaamamaiden-hyodyntaminen-hyodyntamispaikka"]
                                  :attachments []
                                  :add-operation-allowed false
                                  :copying-allowed true
                                  :min-outgoing-link-permits 0
                                  :asianhallinta false}

   :talousjatevesien-kasittelysta-poikkeaminen {:schema                    "talousjatevesien-kasittelysta-poikkeaminen"
                                                :permit-type               permit/YM
                                                :applicant-doc-schema      applicant-doc-schema-name-hakija
                                                :required                  ["vesihuolto-kiinteisto" "ymp-maksaja"]
                                                :attachments               [:kartat-ja-piirustukset [:kartta-melun-ja-tarinan-leviamisesta]]
                                                :add-operation-allowed     false
                                                :copying-allowed           true
                                                :min-outgoing-link-permits 0
                                                :asianhallinta             true}

   :yhteiskasittely-maa-aines-ja-ymparistoluvalle {:schema                    "yhteiskasittely-maa-aines-ja-ymparistoluvalle-luvan-kohde"
                                                   :permit-type               permit/YM
                                                   :applicant-doc-schema      applicant-doc-schema-name-hakija
                                                   :required                  ["yhteiskasittelyn-toiminta-alueen-sijainti"
                                                                               "yhteiskasittelyn-otettava-maa-aines-ja-ottamisen-jarjestaminen"
                                                                               "yhteiskasittelyn-kivenmurskaamoa-ja-louhintoa-koskevat-tiedot"
                                                                               "yhteiskasittelyn-liikenne-ja-liikenne-jarjestelyt"
                                                                               "yhteiskasittelyn-arvio-toiminnan-vaikutuksista-ymparistoon"
                                                                               "yhteiskasittelyn-ymparistoriskit-onnettomuuksien-ehkaisy-ja-poikkeustilanteet"
                                                                               "yhteiskasittelyn-toiminnan-tarkkailu"
                                                                               "yhteiskasittelyn-voimassa-tai-vireilla-olevat-luvat"]
                                                   :attachments               []
                                                   :add-operation-allowed     false
                                                   :copying-allowed           true
                                                   :min-outgoing-link-permits 0
                                                   :asianhallinta             true}
   })

(def- p-operations
  {:poikkeamis {:schema                    "rakennushanke"
                :permit-type               permit/P
                :applicant-doc-schema      applicant-doc-schema-name-hakija
                :required                  (conj common-poikkeamis-schemas "suunnittelutarveratkaisun-lisaosa")
                :optional                  #{"suunnittelija"}
                :attachments               [:paapiirustus [:asemapiirros]]
                :add-operation-allowed     false
                :copying-allowed           true
                :min-outgoing-link-permits 0
                :asianhallinta             true}
   })

(defn- tyonjohtaja-state-machine-resolver [{subtype :permitSubtype}]
  (if (= :tyonjohtaja-ilmoitus (keyword subtype))
    states/tj-ilmoitus-state-graph
    states/tj-hakemus-state-graph))

(defn- state-machine-resolver [{subtype :permitSubtype}]
  (if (= :muutoslupa (keyword subtype))
    states/r-muutoslupa-state-graph
    states/full-application-state-graph))

(def- r-operations
  {:asuinrakennus            {:schema                    "uusiRakennus" ; Deprecated operation. Not in current operation tree, but in use in legacy applications.
                              :permit-type               permit/R
                              :applicant-doc-schema      applicant-doc-schema-name-R
                              :schema-data               [#_[["kaytto" "kayttotarkoitus"] usages/yhden-asunnon-talot]
                                                          [["huoneistot" "0" "huoneistonumero"] "000"]
                                                          [["huoneistot" "0" "muutostapa"] "lis\u00e4ys"]]
                              :required                  common-rakval-schemas
                              :org-required              common-rakval-org-schemas
                              :optional                  optional-rakval-schemas
                              :attachments               uuden_rakennuksen_liitteet
                              :add-operation-allowed     true
                              :copying-allowed           true
                              :min-outgoing-link-permits 0
                              :asianhallinta             false
                              :building                  true
                              :state-graph-resolver      state-machine-resolver}
   :kerrostalo-rivitalo      {:schema                    "uusiRakennus"
                              :permit-type               permit/R
                              :applicant-doc-schema      applicant-doc-schema-name-R
                              :schema-data               [#_[["kaytto" "kayttotarkoitus"] usages/rivitalot]
                                                          [["huoneistot" "0" "huoneistonumero"] "000"]
                                                          [["huoneistot" "0" "muutostapa"] "lis\u00e4ys"]]
                              :required                  common-rakval-schemas
                              :org-required              common-rakval-org-schemas
                              :optional                  optional-rakval-schemas
                              :attachments               uuden_rakennuksen_liitteet
                              :add-operation-allowed     true
                              :copying-allowed           true
                              :min-outgoing-link-permits 0
                              :asianhallinta             false
                              :building                  true
                              :state-graph-resolver      state-machine-resolver}
   :pientalo                 {:schema                    "uusiRakennus"
                              :permit-type               permit/R
                              :applicant-doc-schema      applicant-doc-schema-name-R
                              :schema-data               [#_[["kaytto" "kayttotarkoitus"] usages/yhden-asunnon-talot]
                                                          [["huoneistot" "0" "huoneistonumero"] "000"]
                                                          [["huoneistot" "0" "muutostapa"] "lis\u00e4ys"]]
                              :required                  common-rakval-schemas
                              :org-required              common-rakval-org-schemas
                              :optional                  optional-rakval-schemas
                              :attachments               uuden_rakennuksen_liitteet
                              :add-operation-allowed     true
                              :copying-allowed           true
                              :min-outgoing-link-permits 0
                              :asianhallinta             false
                              :building                  true
                              :state-graph-resolver      state-machine-resolver}
   :vapaa-ajan-asuinrakennus {:schema                    "uusi-rakennus-ei-huoneistoa"
                              :permit-type               permit/R
                              :applicant-doc-schema      applicant-doc-schema-name-R
                              ;:schema-data               [[["kaytto" "kayttotarkoitus"] usages/vapaa-ajan-asuinrakennus]]
                              :required                  common-rakval-schemas
                              :org-required              common-rakval-org-schemas
                              :optional                  optional-rakval-schemas
                              :attachments               uuden_rakennuksen_liitteet
                              :add-operation-allowed     true
                              :copying-allowed           true
                              :min-outgoing-link-permits 0
                              :asianhallinta             false
                              :building                  true
                              :state-graph-resolver      state-machine-resolver}
   :varasto-tms              {:schema                    "uusi-rakennus-ei-huoneistoa"
                              :permit-type               permit/R
                              :applicant-doc-schema      applicant-doc-schema-name-R
                              ;:schema-data               #_[[["kaytto" "kayttotarkoitus"] usages/talousrakennus]]
                              :required                  common-rakval-schemas
                              :org-required              common-rakval-org-schemas
                              :optional                  optional-rakval-schemas
                              :attachments               uuden_rakennuksen_liitteet
                              :add-operation-allowed     true
                              :copying-allowed           true
                              :min-outgoing-link-permits 0
                              :asianhallinta             false
                              :building                  true
                              :state-graph-resolver      state-machine-resolver}
   :julkinen-rakennus        {:schema                    "uusiRakennus" ; Deprecated operation. Not in current operation tree, but in use in legacy applications.
                              :permit-type               permit/R
                              :applicant-doc-schema      applicant-doc-schema-name-R
                              :schema-data               [[["huoneistot" "0" "huoneistonumero"] "000"]
                                                          [["huoneistot" "0" "muutostapa"] "lis\u00e4ys"]]
                              :required                  common-rakval-schemas
                              :org-required              common-rakval-org-schemas
                              :attachments               uuden_rakennuksen_liitteet
                              :add-operation-allowed     true
                              :copying-allowed           true
                              :min-outgoing-link-permits 0
                              :asianhallinta             false
                              :building                  true
                              :state-graph-resolver      state-machine-resolver}
   :teollisuusrakennus       {:schema                    "uusiRakennus"
                              :permit-type               permit/R
                              :applicant-doc-schema      applicant-doc-schema-name-R
                              :required                  common-rakval-schemas
                              :org-required              common-rakval-org-schemas
                              :optional                  optional-rakval-schemas
                              :attachments               uuden_rakennuksen_liitteet
                              :add-operation-allowed     true
                              :copying-allowed           true
                              :min-outgoing-link-permits 0
                              :asianhallinta             false
                              :building                  true
                              :state-graph-resolver      state-machine-resolver}
   :muu-uusi-rakentaminen    {:schema                    "uusiRakennus"
                              :permit-type               permit/R
                              :applicant-doc-schema      applicant-doc-schema-name-R
                              :schema-data               [[["huoneistot" "0" "huoneistonumero"] "000"]
                                                          [["huoneistot" "0" "muutostapa"] "lis\u00e4ys"]]
                              :required                  common-rakval-schemas
                              :org-required              common-rakval-org-schemas
                              :optional                  optional-rakval-schemas
                              :attachments               uuden_rakennuksen_liitteet
                              :add-operation-allowed     true
                              :copying-allowed           true
                              :min-outgoing-link-permits 0
                              :asianhallinta             false
                              :building                  true
                              :state-graph-resolver      state-machine-resolver}

   :laajentaminen            {:schema                    "rakennuksen-laajentaminen" ; Deprecated operation. Not in current operation tree, but in use in legacy applications.
                              :permit-type               permit/R
                              :applicant-doc-schema      applicant-doc-schema-name-R
                              :required                  common-rakval-schemas
                              :org-required              common-rakval-org-schemas
                              :optional                  optional-rakval-schemas
                              :attachments               rakennuksen_laajennuksen_liitteet
                              :add-operation-allowed     true
                              :copying-allowed           true
                              :min-outgoing-link-permits 0
                              :asianhallinta             false
                              :building                  true
                              :state-graph-resolver      state-machine-resolver}
   :kerrostalo-rt-laaj       {:schema                    "rakennuksen-laajentaminen"
                              :permit-type               permit/R
                              :applicant-doc-schema      applicant-doc-schema-name-R
                              :required                  common-rakval-schemas
                              :org-required              common-rakval-org-schemas
                              :optional                  optional-rakval-schemas
                              :attachments               rakennuksen_laajennuksen_liitteet
                              :add-operation-allowed     true
                              :copying-allowed           true
                              :min-outgoing-link-permits 0
                              :asianhallinta             false
                              :building                  true
                              :state-graph-resolver      state-machine-resolver}
   :pientalo-laaj            {:schema                    "rakennuksen-laajentaminen"
                              :permit-type               permit/R
                              :applicant-doc-schema      applicant-doc-schema-name-R
                              :required                  common-rakval-schemas
                              :org-required              common-rakval-org-schemas
                              :optional                  optional-rakval-schemas
                              :attachments               rakennuksen_laajennuksen_liitteet
                              :add-operation-allowed     true
                              :copying-allowed           true
                              :min-outgoing-link-permits 0
                              :asianhallinta             false
                              :building                  true
                              :state-graph-resolver      state-machine-resolver}
   :vapaa-ajan-rakennus-laaj {:schema                    "rakennuksen-laajentaminen-ei-huoneistoja"
                              :permit-type               permit/R
                              :applicant-doc-schema      applicant-doc-schema-name-R
                              :required                  common-rakval-schemas
                              :org-required              common-rakval-org-schemas
                              :optional                  optional-rakval-schemas
                              :attachments               rakennuksen_laajennuksen_liitteet
                              :add-operation-allowed     true
                              :copying-allowed           true
                              :min-outgoing-link-permits 0
                              :asianhallinta             false
                              :building                  true
                              :state-graph-resolver      state-machine-resolver}
   :talousrakennus-laaj      {:schema                    "rakennuksen-laajentaminen-ei-huoneistoja"
                              :permit-type               permit/R
                              :applicant-doc-schema      applicant-doc-schema-name-R
                              :required                  common-rakval-schemas
                              :org-required              common-rakval-org-schemas
                              :optional                  optional-rakval-schemas
                              :attachments               rakennuksen_laajennuksen_liitteet
                              :add-operation-allowed     true
                              :copying-allowed           true
                              :min-outgoing-link-permits 0
                              :asianhallinta             false
                              :building                  true
                              :state-graph-resolver      state-machine-resolver}
   :teollisuusrakennus-laaj  {:schema                    "rakennuksen-laajentaminen"
                              :permit-type               permit/R
                              :applicant-doc-schema      applicant-doc-schema-name-R
                              :required                  common-rakval-schemas
                              :org-required              common-rakval-org-schemas
                              :optional                  optional-rakval-schemas
                              :attachments               rakennuksen_laajennuksen_liitteet
                              :add-operation-allowed     true
                              :copying-allowed           true
                              :min-outgoing-link-permits 0
                              :asianhallinta             false
                              :building                  true
                              :state-graph-resolver      state-machine-resolver}
   :muu-rakennus-laaj        {:schema                    "rakennuksen-laajentaminen"
                              :permit-type               permit/R
                              :applicant-doc-schema      applicant-doc-schema-name-R
                              :required                  common-rakval-schemas
                              :org-required              common-rakval-org-schemas
                              :optional                  optional-rakval-schemas
                              :attachments               rakennuksen_laajennuksen_liitteet
                              :add-operation-allowed     true
                              :copying-allowed           true
                              :min-outgoing-link-permits 0
                              :asianhallinta             false
                              :building                  true
                              :state-graph-resolver      state-machine-resolver}

   :perus-tai-kant-rak-muutos  {:schema                    "rakennuksen-muuttaminen-ei-huoneistoja"
                                :permit-type               permit/R
                                :applicant-doc-schema      applicant-doc-schema-name-R
                                :schema-data               [[["muutostyolaji"] schemas/perustusten-korjaus]]
                                :required                  common-rakval-schemas
                                :org-required              common-rakval-org-schemas
                                :optional                  optional-rakval-schemas
                                :attachments               rakennuksen_laajennuksen_liitteet
                                :add-operation-allowed     true
                                :copying-allowed           true
                                :min-outgoing-link-permits 0
                                :asianhallinta             false
                                :building                  true
                                :state-graph-resolver      state-machine-resolver}
   :kayttotark-muutos          {:schema                    "rakennuksen-muuttaminen"
                                :permit-type               permit/R
                                :applicant-doc-schema      applicant-doc-schema-name-R
                                :schema-data               [[["muutostyolaji"] schemas/kayttotarkotuksen-muutos]]
                                :required                  common-rakval-schemas
                                :org-required              common-rakval-org-schemas
                                :optional                  optional-rakval-schemas
                                :attachments               rakennuksen_muutos_liitteet
                                :add-operation-allowed     true
                                :copying-allowed           true
                                :min-outgoing-link-permits 0
                                :asianhallinta             false
                                :building                  true
                                :state-graph-resolver      state-machine-resolver}
   :sisatila-muutos            {:schema                    "rakennuksen-muuttaminen"
                                :permit-type               permit/R
                                :applicant-doc-schema      applicant-doc-schema-name-R
                                :schema-data               [[["muutostyolaji"] schemas/kayttotarkotuksen-muutos]]
                                :required                  common-rakval-schemas
                                :org-required              common-rakval-org-schemas
                                :optional                  optional-rakval-schemas
                                :attachments               rakennuksen_muutos_liitteet
                                :add-operation-allowed     true
                                :copying-allowed           true
                                :min-outgoing-link-permits 0
                                :asianhallinta             false
                                :building                  true
                                :state-graph-resolver      state-machine-resolver}
   :julkisivu-muutos           {:schema                    "rakennuksen-muuttaminen-ei-huoneistoja-ei-ominaisuuksia"
                                :permit-type               permit/R
                                :applicant-doc-schema      applicant-doc-schema-name-R
                                :schema-data               [[["muutostyolaji"] schemas/muumuutostyo]]
                                :required                  common-rakval-schemas
                                :org-required              common-rakval-org-schemas
                                :optional                  optional-rakval-schemas
                                :attachments               rakennuksen_laajennuksen_liitteet
                                :add-operation-allowed     true
                                :copying-allowed           true
                                :min-outgoing-link-permits 0
                                :asianhallinta             false
                                :building                  true
                                :state-graph-resolver      state-machine-resolver}
   :jakaminen-tai-yhdistaminen {:schema                    "rakennuksen-muuttaminen"
                                :permit-type               permit/R
                                :applicant-doc-schema      applicant-doc-schema-name-R
                                :schema-data               [[["muutostyolaji"] schemas/muumuutostyo]]
                                :required                  common-rakval-schemas
                                :org-required              common-rakval-org-schemas
                                :optional                  optional-rakval-schemas
                                :attachments               rakennuksen_laajennuksen_liitteet
                                :add-operation-allowed     true
                                :copying-allowed           true
                                :min-outgoing-link-permits 0
                                :asianhallinta             false
                                :building                  true
                                :state-graph-resolver      state-machine-resolver}
   :rakennustietojen-korjaus   {:schema                    "rakennustietojen-korjaus"
                                :permit-type               permit/R
                                :applicant-doc-schema      applicant-doc-schema-name-R
                                :required                  ["hankkeen-kuvaus-minimum" "maksaja" "rakennuspaikka"]
                                :attachments               []
                                :add-operation-allowed     true
                                :copying-allowed           false
                                :min-outgoing-link-permits 0
                                :asianhallinta             false
                                :building                  true
                                :state-graph-resolver      state-machine-resolver}
   :markatilan-laajentaminen   {:schema                    "rakennuksen-muuttaminen-ei-huoneistoja-ei-ominaisuuksia"
                                :permit-type               permit/R
                                :applicant-doc-schema      applicant-doc-schema-name-R
                                :schema-data               [[["muutostyolaji"] schemas/muumuutostyo]]
                                :required                  common-rakval-schemas
                                :org-required              common-rakval-org-schemas
                                :optional                  optional-rakval-schemas
                                :attachments               rakennuksen_laajennuksen_liitteet
                                :add-operation-allowed     true
                                :copying-allowed           true
                                :min-outgoing-link-permits 0
                                :asianhallinta             false
                                :building                  true
                                :state-graph-resolver      state-machine-resolver}
   :linjasaneeraus             {:schema                    "rakennuksen-muuttaminen"
                                :permit-type               permit/R
                                :applicant-doc-schema      applicant-doc-schema-name-R
                                :schema-data               [[["muutostyolaji"] schemas/muumuutostyo]]
                                :required                  common-rakval-schemas
                                :org-required              common-rakval-org-schemas
                                :optional                  optional-rakval-schemas
                                :attachments               rakennuksen_laajennuksen_liitteet
                                :add-operation-allowed     true
                                :copying-allowed           true
                                :min-outgoing-link-permits 0
                                :asianhallinta             false
                                :building                  true
                                :state-graph-resolver      state-machine-resolver}
   :takka-tai-hormi            {:schema                    "rakennuksen-muuttaminen-ei-huoneistoja-ei-ominaisuuksia"
                                :permit-type               permit/R
                                :applicant-doc-schema      applicant-doc-schema-name-R
                                :schema-data               [[["muutostyolaji"] schemas/muumuutostyo]]
                                :required                  common-rakval-schemas
                                :org-required              common-rakval-org-schemas
                                :optional                  optional-rakval-schemas
                                :attachments               rakennuksen_laajennuksen_liitteet
                                :add-operation-allowed     true
                                :copying-allowed           true
                                :min-outgoing-link-permits 0
                                :asianhallinta             false
                                :building                  true
                                :state-graph-resolver      state-machine-resolver}
   :parveke-tai-terassi        {:schema                    "rakennuksen-muuttaminen-ei-huoneistoja-ei-ominaisuuksia"
                                :permit-type               permit/R
                                :applicant-doc-schema      applicant-doc-schema-name-R
                                :schema-data               [[["muutostyolaji"] schemas/muumuutostyo]]
                                :required                  common-rakval-schemas
                                :org-required              common-rakval-org-schemas
                                :optional                  optional-rakval-schemas
                                :attachments               rakennuksen_laajennuksen_liitteet
                                :add-operation-allowed     true
                                :copying-allowed           true
                                :min-outgoing-link-permits 0
                                :asianhallinta             false
                                :building                  true
                                :state-graph-resolver      state-machine-resolver}
   :muu-laajentaminen          {:schema                    "rakennuksen-muuttaminen"
                                :permit-type               permit/R
                                :applicant-doc-schema      applicant-doc-schema-name-R
                                :schema-data               [[["muutostyolaji"] schemas/muumuutostyo]]
                                :required                  common-rakval-schemas
                                :org-required              common-rakval-org-schemas
                                :optional                  optional-rakval-schemas
                                :attachments               rakennuksen_laajennuksen_liitteet
                                :add-operation-allowed     true
                                :copying-allowed           true
                                :min-outgoing-link-permits 0
                                :asianhallinta             false
                                :building                  true
                                :state-graph-resolver      state-machine-resolver}

   :auto-katos       {:schema                    "kaupunkikuvatoimenpide-kokoontumistilalla"
                      :permit-type               permit/R
                      :applicant-doc-schema      applicant-doc-schema-name-R
                      :required                  common-rakval-schemas
                      :org-required              common-rakval-org-schemas
                      :optional                  optional-rakval-schemas
                      :attachments               kaupunkikuva_toimenpide_liitteet
                      :add-operation-allowed     true
                      :copying-allowed           true
                      :min-outgoing-link-permits 0
                      :asianhallinta             false
                      :structure                 true
                      :state-graph-resolver      state-machine-resolver}
   :masto-tms        {:schema                    "kaupunkikuvatoimenpide"
                      :permit-type               permit/R
                      :applicant-doc-schema      applicant-doc-schema-name-R
                      :required                  common-rakval-schemas
                      :org-required              common-rakval-org-schemas
                      :optional                  optional-rakval-schemas
                      :attachments               kaupunkikuva_toimenpide_liitteet
                      :add-operation-allowed     true
                      :copying-allowed           true
                      :min-outgoing-link-permits 0
                      :asianhallinta             false
                      :structure                 true
                      :state-graph-resolver      state-machine-resolver}
   :mainoslaite      {:schema                    "kaupunkikuvatoimenpide"
                      :permit-type               permit/R
                      :applicant-doc-schema      applicant-doc-schema-name-R
                      :required                  common-rakval-schemas
                      :org-required              common-rakval-org-schemas
                      :optional                  optional-rakval-schemas
                      :attachments               kaupunkikuva_toimenpide_liitteet
                      :add-operation-allowed     true
                      :copying-allowed           true
                      :min-outgoing-link-permits 0
                      :asianhallinta             false
                      :structure                 true
                      :state-graph-resolver      state-machine-resolver}
   :aita             {:schema                    "kaupunkikuvatoimenpide-ei-tunnusta"
                      :permit-type               permit/R
                      :applicant-doc-schema      applicant-doc-schema-name-R
                      :required                  (remove #{"paasuunnittelija"} common-rakval-schemas)
                      :org-required              common-rakval-org-schemas
                      :optional                  optional-mini-rakval-schemas
                      :attachments               kaupunkikuva_toimenpide_liitteet
                      :add-operation-allowed     true
                      :copying-allowed           true
                      :min-outgoing-link-permits 0
                      :asianhallinta             false
                      :structure                 true
                      :state-graph-resolver      state-machine-resolver}
   :maalampo         {:schema                    "maalampokaivo"
                      :permit-type               permit/R
                      :applicant-doc-schema      applicant-doc-schema-name-R
                      :required                  (remove #{"paasuunnittelija"} common-rakval-schemas)
                      :org-required              common-rakval-org-schemas
                      :optional                  optional-mini-rakval-schemas
                      :attachments               kaupunkikuva_toimenpide_liitteet
                      :add-operation-allowed     true
                      :copying-allowed           true
                      :min-outgoing-link-permits 0
                      :asianhallinta             false
                      :structure                 true
                      :state-graph-resolver      state-machine-resolver}
   :jatevesi         {:schema                    "kaupunkikuvatoimenpide"
                      :permit-type               permit/R
                      :applicant-doc-schema      applicant-doc-schema-name-R
                      :required                  common-rakval-schemas
                      :org-required              common-rakval-org-schemas
                      :optional                  optional-rakval-schemas
                      :attachments               kaupunkikuva_toimenpide_liitteet
                      :add-operation-allowed     true
                      :copying-allowed           true
                      :min-outgoing-link-permits 0
                      :asianhallinta             false
                      :structure                 true
                      :state-graph-resolver      state-machine-resolver}
   :muu-rakentaminen {:schema                    "kaupunkikuvatoimenpide"
                      :permit-type               permit/R
                      :applicant-doc-schema      applicant-doc-schema-name-R
                      :required                  common-rakval-schemas
                      :org-required              common-rakval-org-schemas
                      :optional                  optional-rakval-schemas
                      :attachments               kaupunkikuva_toimenpide_liitteet
                      :add-operation-allowed     true
                      :copying-allowed           true
                      :min-outgoing-link-permits 0
                      :asianhallinta             false
                      :structure                 true
                      :state-graph-resolver      state-machine-resolver}

   :purkaminen {:schema                    "purkaminen"
                :permit-type               permit/R
                :applicant-doc-schema      applicant-doc-schema-name-R
                :required                  common-rakval-schemas
                :org-required              common-rakval-org-schemas
                :optional                  optional-rakval-schemas
                :attachments               [:muut [:selvitys_rakennusjatteen_maarasta_laadusta_ja_lajittelusta
                                                   :selvitys_purettavasta_rakennusmateriaalista_ja_hyvaksikaytosta]]
                :add-operation-allowed     true
                :copying-allowed           true
                :min-outgoing-link-permits 0
                :asianhallinta             false
                :building                  true
                :state-graph-resolver      state-machine-resolver}
   :kaivuu                     {:schema                    "maisematyo"
                                :permit-type               permit/R
                                :applicant-doc-schema      applicant-doc-schema-name-R
                                :required                  mini-rakval-schemas
                                :optional                  optional-mini-rakval-schemas
                                :attachments               [:paapiirustus [:asemapiirros]]
                                :add-operation-allowed     true
                                :copying-allowed           true
                                :min-outgoing-link-permits 0
                                :asianhallinta             false
                                :state-graph-resolver      state-machine-resolver}
   :puun-kaataminen            {:schema                    "maisematyo"
                                :permit-type               permit/R
                                :applicant-doc-schema      applicant-doc-schema-name-R
                                :required                  puun-kaataminen-schemas
                                :optional                  optional-mini-rakval-schemas
                                :attachments               [:paapiirustus [:asemapiirros]]
                                :add-operation-allowed     true
                                :copying-allowed           true
                                :min-outgoing-link-permits 0
                                :asianhallinta             false
                                :state-graph-resolver      state-machine-resolver}
   :tontin-jarjestelymuutos    {:schema                    "maisematyo"
                                :permit-type               permit/R
                                :applicant-doc-schema      applicant-doc-schema-name-R
                                :required                  mini-rakval-schemas
                                :optional                  optional-mini-rakval-schemas
                                :attachments               [:paapiirustus [:asemapiirros]]
                                :add-operation-allowed     true
                                :copying-allowed           true
                                :min-outgoing-link-permits 0
                                :asianhallinta             false
                                :state-graph-resolver      state-machine-resolver}
   :muu-maisema-toimenpide     {:schema                    "maisematyo"
                                :permit-type               permit/R
                                :applicant-doc-schema      applicant-doc-schema-name-R
                                :required                  mini-rakval-schemas
                                :optional                  optional-mini-rakval-schemas
                                :attachments               [:paapiirustus [:asemapiirros]]
                                :add-operation-allowed     true
                                :copying-allowed           true
                                :min-outgoing-link-permits 0
                                :asianhallinta             false
                                :state-graph-resolver      state-machine-resolver}
   :tontin-ajoliittyman-muutos {:schema                    "maisematyo"
                                :permit-type               permit/R
                                :applicant-doc-schema      applicant-doc-schema-name-R
                                :required                  mini-rakval-schemas
                                :optional                  optional-mini-rakval-schemas
                                :attachments               [:paapiirustus [:asemapiirros]]
                                :add-operation-allowed     true
                                :copying-allowed           true
                                :min-outgoing-link-permits 0
                                :asianhallinta             false
                                :state-graph-resolver      state-machine-resolver}
   :paikoutysjarjestus-muutos  {:schema                    "maisematyo"
                                :permit-type               permit/R
                                :applicant-doc-schema      applicant-doc-schema-name-R
                                :required                  mini-rakval-schemas
                                :optional                  optional-mini-rakval-schemas
                                :attachments               [:paapiirustus [:asemapiirros]]
                                :add-operation-allowed     true
                                :copying-allowed           true
                                :min-outgoing-link-permits 0
                                :asianhallinta             false
                                :state-graph-resolver      state-machine-resolver}
   :kortteli-yht-alue-muutos   {:schema                    "maisematyo"
                                :permit-type               permit/R
                                :applicant-doc-schema      applicant-doc-schema-name-R
                                :required                  mini-rakval-schemas
                                :optional                  optional-mini-rakval-schemas
                                :attachments               [:paapiirustus [:asemapiirros]]
                                :add-operation-allowed     true
                                :copying-allowed           true
                                :min-outgoing-link-permits 0
                                :asianhallinta             false
                                :state-graph-resolver      state-machine-resolver}
   :muu-tontti-tai-kort-muutos {:schema                    "maisematyo"
                                :permit-type               permit/R
                                :applicant-doc-schema      applicant-doc-schema-name-R
                                :required                  mini-rakval-schemas
                                :optional                  optional-mini-rakval-schemas
                                :attachments               [:paapiirustus [:asemapiirros]]
                                :add-operation-allowed     true
                                :copying-allowed           true
                                :min-outgoing-link-permits 0
                                :asianhallinta             false
                                :state-graph-resolver      state-machine-resolver}

   :tyonjohtajan-nimeaminen    {:schema                          "hankkeen-kuvaus-minimum"
                                :permit-type                     permit/R
                                :applicant-doc-schema            applicant-doc-schema-name-R
                                :required                        ["tyonjohtaja" "maksaja"]
                                :attachments                     []
                                :attachment-op-selector          false
                                :attachments-readonly-after-sent true
                                :add-operation-allowed           false
                                :copying-allowed                 false
                                :min-outgoing-link-permits       1
                                :max-outgoing-link-permits       1
                                :max-incoming-link-permits       0
                                :allowed-link-permit-types       #{permit/R permit/P}
                                :asianhallinta                   false
                                :hidden                          true}
   :tyonjohtajan-nimeaminen-v2 {:schema                          "tyonjohtaja-v2"
                                :permit-type                     permit/R
                                :applicant-doc-schema            applicant-doc-schema-name-R-TJ
                                :subtypes                        [(keyword "") :tyonjohtaja-hakemus :tyonjohtaja-ilmoitus]
                                :state-graph-resolver            tyonjohtaja-state-machine-resolver
                                :required                        ["hankkeen-kuvaus-minimum"]
                                :attachments                     []
                                :attachment-op-selector          false
                                :attachments-readonly-after-sent true
                                :add-operation-allowed           false
                                :copying-allowed                 false
                                :min-outgoing-link-permits       1
                                :max-outgoing-link-permits       1
                                :max-incoming-link-permits       0
                                :allowed-link-permit-types       #{permit/R permit/P}
                                :asianhallinta                   false}

   :suunnittelijan-nimeaminen {:schema                    "hankkeen-kuvaus-minimum"
                               :permit-type               permit/R
                               :applicant-doc-schema      applicant-doc-schema-name-R
                               :required                  ["suunnittelija" "maksaja"]
                               :attachments               []
                               :add-operation-allowed     false
                               :copying-allowed           true
                               :min-outgoing-link-permits 1
                               :asianhallinta             false}

   :jatkoaika                   {:schema                    "hankkeen-kuvaus-minimum"
                                 :permit-type               permit/R
                                 :applicant-doc-schema      applicant-doc-schema-name-R
                                 :required                  ["maksaja" "paatoksen-toimitus-rakval"] ;; TODO: Tuleeko talle myos "rakennusjatesuunnitelma"?
                                 :attachments               []
                                 :add-operation-allowed     false
                                 :copying-allowed           false
                                 :min-outgoing-link-permits 1
                                 :asianhallinta             false
                                 :state-graph-resolver      (constantly states/r-jatkoaika-state-graph)}
   :aiemmalla-luvalla-hakeminen {:schema                    "aiemman-luvan-toimenpide"
                                 :permit-type               permit/R
                                 :applicant-doc-schema      applicant-doc-schema-name-R
                                 :required                  []
                                 :optional                  #{"maksaja" "paasuunnittelija" "suunnittelija"} ;; TODO: Tuleeko talle myos "rakennusjatesuunnitelma"?
                                 :attachments               []
                                 :add-operation-allowed     false
                                 :copying-allowed           true
                                 :min-outgoing-link-permits 0
                                 :asianhallinta             false
                                 :unsubscribe-notifications true
                                 :state-graph-resolver state-machine-resolver}
   :konversio                   {:schema "hankkeen-kuvaus"
                                 :permit-type permit/R
                                 :applicant-doc-schema applicant-doc-schema-name-R
                                 :required []
                                 :optional #{"maksaja" "paasuunnittelija" "suunnittelija"}
                                 :attachments []
                                 :add-operation-allowed false
                                 :copying-allowed true
                                 :min-outgoing-link-permits 0
                                 :asianhallinta false
                                 :unsubscribe-notifications true
                                 :state-graph-resolver state-machine-resolver}
   :rak-valm-tyo                {:schema "maisematyo"
                                 :permit-type permit/R
                                 :applicant-doc-schema applicant-doc-schema-name-R
                                 :required mini-rakval-schemas
                                 :optional optional-mini-rakval-schemas
                                 :attachments [:paapiirustus [:asemapiirros]]
                                 :add-operation-allowed true
                                 :copying-allowed true
                                 :min-outgoing-link-permits 0
                                 :asianhallinta             false
                                 :state-graph-resolver      state-machine-resolver}
   :aloitusoikeus               {:schema                    "aloitusoikeus"
                                 :permit-type               permit/R
                                 :applicant-doc-schema      applicant-doc-schema-name-R
                                 :required                  ["maksaja"]
                                 :attachments               []
                                 :add-operation-allowed     false
                                 :copying-allowed           true
                                 :min-outgoing-link-permits 1
                                 :asianhallinta             false}
   :raktyo-aloit-loppuunsaat    {:schema                    "jatkoaika-hankkeen-kuvaus"
                                 :permit-type               permit/R
                                 :applicant-doc-schema      applicant-doc-schema-name-R
                                 :required                  ["maksaja" "paatoksen-toimitus-rakval"] ;; TODO: Tuleeko talle myos "rakennusjatesuunnitelma"?
                                 :attachments               []
                                 :add-operation-allowed     false
                                 :copying-allowed           true
                                 :min-outgoing-link-permits 1
                                 :asianhallinta             false
                                 :state-graph-resolver      (constantly states/r-jatkoaika-state-graph)}

   :rasite-tai-yhteisjarjestely {:schema                    "rasite-tai-yhteisjarjestely"
                                 :permit-type               permit/R
                                 :applicant-doc-schema      applicant-doc-schema-name-R
                                 :subtypes                  []
                                 :required                  ["maksaja"]
                                 :optional                  optional-rakval-schemas
                                 :attachments               []
                                 :add-operation-allowed     true
                                 :copying-allowed           true
                                 :min-outgoing-link-permits 0
                                 :allowed-link-permit-types #{permit/R}
                                 :asianhallinta             false
                                 :state-graph-resolver      (constantly states/rakennusrasite-application-state-graph)}})

(def- vvvl-operations
  {:vvvl-vesijohdosta           {:schema "talousvedet"
                                 :permit-type permit/VVVL
                                 :applicant-doc-schema applicant-doc-schema-name-hakija
                                 :required common-vvvl-schemas
                                 :attachments [:kartat-ja-piirustukset [:kartta-melun-ja-tarinan-leviamisesta]]
                                 :add-operation-allowed false
                                 :copying-allowed true
                                 :min-outgoing-link-permits 0
                                 :asianhallinta true}
   :vvvl-viemarista             {:schema "jatevedet"
                                 :permit-type permit/VVVL
                                 :applicant-doc-schema applicant-doc-schema-name-hakija
                                 :required common-vvvl-schemas
                                 :attachments [:kartat-ja-piirustukset [:kartta-melun-ja-tarinan-leviamisesta]]
                                 :add-operation-allowed false
                                 :copying-allowed true
                                 :min-outgoing-link-permits 0
                                 :asianhallinta true}
   :vvvl-vesijohdosta-ja-viemarista {:schema "talousvedet"
                                     :permit-type permit/VVVL
                                     :applicant-doc-schema applicant-doc-schema-name-hakija
                                     :required (conj common-vvvl-schemas "jatevedet")
                                     :attachments [:kartat-ja-piirustukset [:kartta-melun-ja-tarinan-leviamisesta]]
                                     :add-operation-allowed false
                                     :copying-allowed true
                                     :min-outgoing-link-permits 0
                                     :asianhallinta true}
   :vvvl-hulevesiviemarista    {:schema "hulevedet"
                                :permit-type permit/VVVL
                                :applicant-doc-schema applicant-doc-schema-name-hakija
                                :required common-vvvl-schemas
                                :attachments [:kartat-ja-piirustukset [:kartta-melun-ja-tarinan-leviamisesta]]
                                :add-operation-allowed false
                                :copying-allowed true
                                :min-outgoing-link-permits 0
                                :asianhallinta true}
   })

(def- mal-operations
  {:maa-aineslupa               {:schema "maa-aineslupa-kuvaus"
                                 :optional #{"secondary-kiinteistot"}
                                 :permit-type permit/MAL
                                 :applicant-doc-schema applicant-doc-schema-name-hakija
                                 :required ["ymp-maksaja" "ottamisalue"]
                                 :attachments []
                                 :add-operation-allowed false
                                 :copying-allowed true
                                 :min-outgoing-link-permits 0
                                 :asianhallinta true}
   :maa-aineslupa-jatkoaika     {:schema "maa-aineslupa-jatkoaika-kuvaus"
                                 :optional #{"secondary-kiinteistot"}
                                 :permit-type permit/MAL
                                 :applicant-doc-schema applicant-doc-schema-name-hakija
                                 :required ["ymp-maksaja" "ottamisalue"]
                                 :attachments []
                                 :add-operation-allowed false
                                 :copying-allowed false
                                 :min-outgoing-link-permits 1
                                 :allowed-link-permit-types #{"MAL"}
                                 :asianhallinta true}
   })

(def- kt-operations
  {:kiinteistonmuodostus         {:schema "kiinteistonmuodostus"
                                  :permit-type permit/KT
                                  :applicant-doc-schema applicant-doc-schema-name-hakija-KT
                                  :optional #{"secondary-kiinteistot"}
                                  :required common-maanmittaus-schemas
                                  :attachments []
                                  :add-operation-allowed true
                                  :copying-allowed true
                                  :min-outgoing-link-permits 0
                                  :asianhallinta true}
   :rasitetoimitus                {:schema "rasitetoimitus"
                                   :permit-type permit/KT
                                   :applicant-doc-schema applicant-doc-schema-name-hakija-KT
                                   :optional #{"secondary-kiinteistot"}
                                   :required common-maanmittaus-schemas
                                   :attachments []
                                   :add-operation-allowed true
                                   :copying-allowed true
                                   :min-outgoing-link-permits 0
                                   :asianhallinta true}
   :rajankaynti                  {:schema "rajankaynti"
                                  :permit-type permit/KT
                                  :applicant-doc-schema applicant-doc-schema-name-hakija-KT
                                  :optional #{"secondary-kiinteistot"}
                                  :required common-maanmittaus-schemas
                                  :attachments []
                                  :add-operation-allowed false
                                  :copying-allowed true
                                  :min-outgoing-link-permits 0
                                  :asianhallinta true}
   })

(def- mm-operations
  {:tonttijako                  {:schema "maankayton-muutos"
                                 :permit-type permit/MM
                                 :applicant-doc-schema applicant-doc-schema-name-hakija
                                 :required common-maanmittaus-schemas
                                 :attachments []
                                 :add-operation-allowed false
                                 :copying-allowed true
                                 :min-outgoing-link-permits 0
                                 :asianhallinta true}
    :asemakaava                 {:schema "maankayton-muutos"
                                 :permit-type permit/MM
                                 :applicant-doc-schema applicant-doc-schema-name-hakija
                                 :required common-maanmittaus-schemas
                                 :attachments []
                                 :add-operation-allowed false
                                 :copying-allowed true
                                 :min-outgoing-link-permits 0
                                 :asianhallinta true}
     :ranta-asemakaava          {:schema "maankayton-muutos"
                                 :permit-type permit/MM
                                 :applicant-doc-schema applicant-doc-schema-name-hakija
                                 :required common-maanmittaus-schemas
                                 :attachments []
                                 :add-operation-allowed false
                                 :copying-allowed true
                                 :min-outgoing-link-permits 0
                                 :asianhallinta true}
     :yleiskaava                {:schema "maankayton-muutos"
                                 :permit-type permit/MM
                                 :applicant-doc-schema applicant-doc-schema-name-hakija
                                 :required common-maanmittaus-schemas
                                 :attachments []
                                 :add-operation-allowed false
                                 :copying-allowed true
                                 :min-outgoing-link-permits 0
                                 :asianhallinta true}
     })

(def- ark-operations
  {:archiving-project {:schema "archiving-project"
                       :permit-type permit/ARK
                       :applicant-doc-schema "hakija-ark"
                       :required []
                       :optional #{}
                       :attachments []
                       :add-operation-allowed false
                       :copying-allowed false
                       :min-outgoing-link-permits 0
                       :asianhallinta false
                       :unsubscribe-notifications true}})


;; Allu operations (permit type A). Located within YA in the operations tree.
(def- a-operations
  {:promootio                  {:schema                    "promootio"
                                :permit-type               permit/A
                                :applicant-doc-schema      applicant-doc-schema-name-YA
                                :required                  ["yleiset-alueet-maksaja" "promootio-time"
                                                            "promootio-location" "promootio-structures" "promootio-info"]
                                :optional                  optional-allu-schemas
                                :attachments               []
                                :add-operation-allowed     false
                                :copying-allowed           true
                                :min-outgoing-link-permits 0
                                :asianhallinta             false}
   :lyhytaikainen-maanvuokraus {:schema                    "lyhytaikainen-maanvuokraus"
                                :permit-type               permit/A
                                :applicant-doc-schema      applicant-doc-schema-name-YA
                                :required                  ["yleiset-alueet-maksaja" "lmv-location" "lmv-time"]
                                :optional                  optional-allu-schemas
                                :attachments               []
                                :add-operation-allowed     false
                                :copying-allowed           true
                                :min-outgoing-link-permits 0
                                :asianhallinta             false}
   })

(def operations
  (merge
    r-operations
    p-operations
    ya-operations
    yl-operations
    yi-operations
    ym-operations
    vvvl-operations
    mal-operations
    kt-operations
    mm-operations
    ark-operations
    a-operations))

(sc/defschema Operation
  {; Documents
   :schema sc/Str
   :required [sc/Str]
   :applicant-doc-schema sc/Str
   (sc/optional-key :org-required) [(sc/pred fn?)]
   (sc/optional-key :optional) #{sc/Str}
   (sc/optional-key :schema-data) [sc/Any]

   ; Attachment groups and types
   :attachments [sc/Any]

   ; Attachments are read only for applicants after the application has been sent
   ; to municipality backend (state is :sent or later)
   (sc/optional-key :attachments-readonly-after-sent) sc/Bool

   ; Allow selecting attachment operation
   (sc/optional-key :attachment-op-selector) sc/Bool

   ; Type and workflow
   :permit-type (sc/pred permit/valid-permit-type?)
   (sc/optional-key :subtypes) [(sc/maybe sc/Keyword)]
   (sc/optional-key :state-graph-resolver) util/Fn

   ; Structure types for r-operations
   (sc/optional-key :building) sc/Bool
   (sc/optional-key :structure) sc/Bool ; a free-standing structure

   ; Can be added to existing application (or only created with a new application)
   :add-operation-allowed sc/Bool

   ; Can the application be copied if this operation is the primary operation
   :copying-allowed sc/Bool

   ; Link permits
   :min-outgoing-link-permits sc/Num
   (sc/optional-key :max-outgoing-link-permits) sc/Num
   (sc/optional-key :max-incoming-link-permits) sc/Num
   ; allowed permit types of link permit
   (sc/optional-key :allowed-link-permit-types) #{sc/Str}

   :asianhallinta sc/Bool
   (sc/optional-key :hidden) sc/Bool
   (sc/optional-key :unsubscribe-notifications) sc/Bool})

;; Validate operations
(doseq [[k op] operations]
  (let [v (sc/check Operation op)]
    (assert (nil? v) (str k \space v))))

;;
;; Functions
;;

(defn get-operation-metadata
  "First form returns all metadata for operation. Second form returns value of given metadata."
  ([operation-name] (get-in operations [(keyword operation-name)]))
  ([operation-name metadata-key] (get-in operations [(keyword operation-name) (keyword metadata-key)])))

(def link-permit-required-operations
  (->> (filter (comp pos? :min-outgoing-link-permits val) operations)
       keys
       set))

(defn required-link-permits [operation-name]
  (get-operation-metadata operation-name :min-outgoing-link-permits))

(defn get-primary-operation-metadata
  ([{op :primaryOperation}] (get-operation-metadata (:name op)))
  ([{op :primaryOperation} metadata-key] (get-operation-metadata (:name op) metadata-key)))

(defn get-applicant-doc-schema-name [application]
  (get-primary-operation-metadata application :applicant-doc-schema))

(defn permit-type-of-operation [operation]
  (get-operation-metadata operation :permit-type))

(defn- is-add-operation-allowed-for-operation [operation]
  (get-operation-metadata operation :add-operation-allowed))

(defn operation-id-exists? [{:keys [primaryOperation secondaryOperations]} op-id]
  (string? ((set (cons (:id primaryOperation) (map :id secondaryOperations))) op-id)))

(defn operations-in [operation-path]
  (-> (util/get-in-tree operation-tree operation-path) util/get-leafs))

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

(defn resolve-operation-names-by-permit-type
  ([include-hidden?]
   (->> (cond->> operations
          (not include-hidden?) (remove (comp :hidden val)))
       (group-by (comp :permit-type val))
       (map (juxt (comp keyword key) (comp keys val)))
       (into {})))
  ([] (resolve-operation-names-by-permit-type false)))

(def operation-names-by-permit-type
  (resolve-operation-names-by-permit-type false))

(defn get-selected-operation-infos
  "Get a projection of operation properties for each unique selected operations.
   Currently (2022-09), the projection contains only one attribute: permit-type, and it is used in info request form."
  [organizations]
  (let [selected-ops (->> (mapcat :selected-operations organizations)
                          (map keyword)
                          (distinct))]

    ;; Resolving operation tree for organizations with "selected-operations" defined in db
    (->> (select-keys operations selected-ops)
         (map (fn [[k v]] (hash-map k (select-keys v [:permit-type]))))
         (into {}))))

(defn selected-operations-for-organizations
  ([organizations] (selected-operations-for-organizations organizations (constantly true)))
  ([organizations filter-fn]
   (let [orgs-with-selected-ops (filter (comp seq :selected-operations) organizations)
         ;; Resolving operation tree for organizations with "selected-operations" defined in db
         op-trees-for-orgs-with-selected-ops (if-not (empty? orgs-with-selected-ops)
                                               (let [selected-operations (->> orgs-with-selected-ops
                                                                              (map :selected-operations)
                                                                              flatten
                                                                              (map keyword)
                                                                              set)]
                                                 (operations-filtered #(and (selected-operations %)
                                                                            (filter-fn %))
                                                                      false))
                                               [])]
     (sort-operation-tree op-trees-for-orgs-with-selected-ops))))

(defn selected-operation-for-organization?
  "Has the organization selected the given operation?
  Returns true if not any selected operations."
  [organization operation-name]
  {:pre [(or (string? operation-name) (keyword? operation-name))]}
  (let [selected-operations (->> organization :selected-operations (map keyword) (set))]
    (or (empty? selected-operations)
        (contains? selected-operations (keyword operation-name)))))

(defn addable-operations [selected-operations permit-type]
  (let [selected-operations (set selected-operations)
        filtering-fn (fn [node] (and
                                  (or (empty? selected-operations) (selected-operations node))
                                  (= (name permit-type) (permit-type-of-operation node))))]
    (sort-operation-tree
     (operations-filtered filtering-fn true))))

(defn validate-primary-operation-is
  "Pre-check that demands the given application has one of the listed operations as its primary operation"
  [& validator-operations]
  (let [valid-operations (set validator-operations)]
    (fn [{:keys [application]}]
      (if application
        (when-not (-> application :primaryOperation :name keyword valid-operations)
          (fail :error.invalid-operation :operation validator-operations))
        (fail :error.invalid-application-parameter)))))

(defn visible-operation
  "Input validator for operation id. Operation must exist and be visible.
   param: Parameter key for the operation id.
   command: Action command."
  [param command]
  (if-let [operation (some-> (get-in command [:data param])
                             ss/trim
                             get-operation-metadata)]
    (when (:hidden operation)
      (fail :error.operations.hidden))
    (fail :error.operations.not-found)))


(defn- normalize-operation-name [i18n-text]
  (when-let [lc (ss/lower-case i18n-text)]
    (-> lc
        (ss/replace #"\p{Punct}" "")
        (ss/replace #"\s{2,}"    " "))))

(def operation-index
  (reduce
    (fn [ops k]
      (let [localizations (map #(i18n/localize % "operations" (name k)) i18n/supported-langs)
            normalized (map normalize-operation-name localizations)]
        (conj ops {:op (name k) :locs (remove ss/blank? normalized)})))
    []
    (keys operations)))

(defn operation-names [filter-search]
  (let [normalized (normalize-operation-name filter-search)]
    (map :op
         (filter
           (fn [{locs :locs}] (some (fn [i18n-text] (ss/contains? i18n-text normalized)) locs))
           operation-index))))

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
