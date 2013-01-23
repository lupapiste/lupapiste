(ns lupapalvelu.operations)

(def default-description "Hankkeesi vaatii todenn\u00E4k\u00F6isesti luvan. Voit hakea lupaa Lupapisteen kautta. Sinun kannattaa my\u00F6s tutustua alla listattuihin sivustoihin, joilta l\u00F6yd\u00E4t lis\u00E4\u00E4 tietoa rakennusvalvonnasta. Voit my\u00F6s kysy\u00E4 lis\u00E4\u00E4 aiheesta kunnan rakennusvalvonnasta Lupapisteen kautta tekem\u00E4ll\u00E4 neuvontapyynn\u00F6n.")

(def operations-data
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

(defn operations [municipality]
  ; Same data for all municipalities for now.
  operations-data)

(def ^:private uusi-rakennus "uusiRakennus")

(def operation->schema-name
  {:asuinrakennus uusi-rakennus
   :vapaa-ajan-asuinrakennus "rakennuspaikka" ; Just for testing...
   :varasto-tms "paasuunnittelija"
   :julkinen-rakennus "hakija"
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

(def ^:private common-schemas ["maksaja" "rakennuspaikka" "lisatiedot"])

(def operation->initial-schema-names
  {:asuinrakennus (concat common-schemas ["paasuunnittelija" "suunnittelija"])
   :vapaa-ajan-asuinrakennus (concat common-schemas ["suunnittelija"])
   :varasto-tms common-schemas
   :julkinen-rakennus common-schemas
   :muu-uusi-rakentaminen common-schemas
   :laajentaminen common-schemas
   :kayttotark-muutos common-schemas
   :julkisivu-muutos common-schemas
   :jakaminen-tai-yhdistaminen common-schemas
   :markatilan-laajentaminen common-schemas
   :takka-tai-hormi common-schemas
   :parveke-tai-terassi common-schemas
   :muu-laajentaminen common-schemas
   :auto-katos common-schemas
   :masto-tms common-schemas
   :mainoslaite common-schemas
   :aita common-schemas
   :maalampo common-schemas
   :jatevesi common-schemas
   :muu-rakentaminen common-schemas
   :purkaminen common-schemas
   :kaivuu common-schemas
   :puun-kaataminen common-schemas
   :muu-maisema-toimenpide common-schemas})

(def operation->initial-attachemnt-types
  {:asuinrakennus [:hakija [:valtakirja]
                   :rakennuspaikka [:ote_alueen_peruskartasta]
                   :paapiirustus [:asemapiirros
                                  :pohjapiirros
                                  :julkisivupiirros]
                   :ennakkoluvat_ja_lausunnot [:naapurien_suostumukset]]})

(def operation->allowed-attachemnt-types
  {:asuinrakennus [:hakija [:valtakirja]
                   :rakennuspaikka [:ote_alueen_peruskartasta
                                    :tonttikartta_tarvittaessa]
                   :paapiirustus [:asemapiirros
                                  :pohjapiirros
                                  :julkisivupiirros]
                   :ennakkoluvat_ja_lausunnot [:naapurien_suostumukset
                                               :suunnittelutarveratkaisu
                                               :ymparistolupa]
                   :muut [:selvitys_liittymisesta_ymparoivaan_rakennuskantaan
                          :julkisivujen_varityssuunnitelma
                          :selvitys_tontin_tai_rakennuspaikan_pintavesien_kasittelysta
                          :energiataloudellinen_selvitys
                          :paloturvallisuussuunnitelma
                          :selvitys_rakennuksen_rakennustaiteellisesta_ja_kulttuurihistoriallisesta_arvosta_jos_korjaus_tai_muutostyo
                          :selvitys_kiinteiston_jatehuollon_jarjestamisesta
                          :rakennesuunnitelma
                          :ilmanvaihtosuunnitelma
                          :lammityslaitesuunnitelma
                          :radontekninen_suunnitelma
                          :kalliorakentamistekninen_suunnitelma
                          :paloturvallisuusselvitys
                          :selvitys_rakennusjatteen_maarasta_laadusta_ja_lajittelusta
                          :muu]]})


