(ns lupapalvelu.operations)

(def default-description "Hankkeesi vaatii todenn\u00E4k\u00F6isesti luvan. Voit hakea lupaa Lupapisteen kautta. Sinun kannattaa my\u00F6s tutustua alla listattuihin sivustoihin, joilta l\u00F6yd\u00E4t lis\u00E4\u00E4 tietoa rakennusvalvonnasta. Voit my\u00F6s kysy\u00E4 lis\u00E4\u00E4 aiheesta kunnan rakennusvalvonnasta Lupapisteen kautta tekem\u00E4ll\u00E4 neuvontapyynn\u00F6n.")

(def operations-data [["Rakentaminen ja purkaminen" [["Uuden rakennuksen rakentaminen" [["Asuinrakennus" {:op :asuinrakennus :text default-description}]
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

(defn ops [r [_ v]]
  (if (vector? v) (reduce ops r v) (conj r (:op v))))
(reduce ops [] operations-data)

(defn operations [municipality]
  ; Same data for all municipalities for now.
  operations-data)

(def ^:private uusi-rakennus "uusiRakennus")

(def operation->schema
  {:asuinrakennus uusi-rakennus
   :vapaa-ajan-asuinrakennus uusi-rakennus 
   :varasto-tms uusi-rakennus
   :julkinen-rakennus uusi-rakennus
   :muu-uusi-rakentaminen uusi-rakennus
   :laajentaminen uusi-rakennus
   :kayttotark-muutos uusi-rakennus
   :julkisivu-muutos uusi-rakennus
   :jakaminen-tai-yhdistaminen uusi-rakennus
   :markatilan-laajentaminen uusi-rakennus
   :takka-tai-hormi uusi-rakennus
   :parveke-tai-terassi uusi-rakennus
   :muu-laajentaminen uusi-rakennus
   :auto-katos uusi-rakennus
   :masto-tms uusi-rakennus
   :mainoslaite uusi-rakennus
   :aita uusi-rakennus
   :maalampo uusi-rakennus
   :jatevesi uusi-rakennus
   :muu-rakentaminen uusi-rakennus
   :purkaminen uusi-rakennus
   :kaivuu uusi-rakennus
   :puun-kaataminen uusi-rakennus
   :muu-maisema-toimenpide uusi-rakennus})

(def ^:private common ["hakija" "maksaja" "rakennuspaikka" "lisatiedot"])

(def initial-operation->schemas
  {:asuinrakennus (concat common ["paasuunnittelija" "suunnittelija"])
   :vapaa-ajan-asuinrakennus (concat common ["suunnittelija"])
   :varasto-tms common
   :julkinen-rakennus common
   :muu-uusi-rakentaminen common
   :laajentaminen common
   :kayttotark-muutos common
   :julkisivu-muutos common
   :jakaminen-tai-yhdistaminen common
   :markatilan-laajentaminen common
   :takka-tai-hormi common
   :parveke-tai-terassi common
   :muu-laajentaminen common
   :auto-katos common
   :masto-tms common
   :mainoslaite common
   :aita common
   :maalampo common
   :jatevesi common
   :muu-rakentaminen common
   :purkaminen common
   :kaivuu common
   :puun-kaataminen common
   :muu-maisema-toimenpide common})
