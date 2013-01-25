(ns lupapalvelu.operations)

(def default-description "Hankkeesi vaatii todenn\u00E4k\u00F6isesti luvan. Voit hakea lupaa Lupapisteen kautta. Sinun kannattaa my\u00F6s tutustua alla listattuihin sivustoihin, joilta l\u00F6yd\u00E4t lis\u00E4\u00E4 tietoa rakennusvalvonnasta. Voit my\u00F6s kysy\u00E4 lis\u00E4\u00E4 aiheesta kunnan rakennusvalvonnasta Lupapisteen kautta tekem\u00E4ll\u00E4 neuvontapyynn\u00F6n.")

(def ^:private operations-tree
  [["Rakentaminen ja purkaminen" [["Uuden rakennuksen rakentaminen" [["Asuinrakennus" {:op :asuinrakennus :text default-description}]
                                                                     ["Vapaa-ajan asuinrakennus" {:op :vapaa-ajan-asuinrakennus :text default-description}]
                                                                     ["Varasto, sauna, autotalli tai muu talousrakennus" {:op :varasto-tms :text default-description}]
                                                                     ["Julkinen rakennus" {:op :julkinen-rakennus :text default-description}]
                                                                     ["Muu" {:op :muu-uusi-rakentaminen :text default-description}]]]
                                  ["Rakennuksen laajentaminen tai muuttaminen" [["Rakennuksen laajentaminen tai korjaaminen" {:op :laajentaminen :text default-description}]
                                                                                ["Kayttotarkoituksen muutos, esim loma-asunnon muuttaminen pysyvaan kayttoon" {:op :kayttotark-muutos :text default-description}]
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

(def ^:private common-schemas ["maksaja" "rakennuspaikka" "lisatiedot"])

(def operations
  {:asuinrakennus               {:schema "asuinrakennus"
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
   :laajentaminen               {:schema "laajentaminen"
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
   :purkaminen                  {:schema "purkaminen"
                                 :required common-schemas
                                 :attachments []}
   :kaivuu                      {:schema "kaivuu"
                                 :required common-schemas
                                 :attachments []}
   :puun-kaataminen             {:schema "puun-kaataminen"
                                 :required common-schemas
                                 :attachments []}
   :muu-maisema-toimenpide      {:schema "muu-maisema-toimenpide"
                                 :required common-schemas
                                 :attachments []}})
