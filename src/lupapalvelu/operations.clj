(ns lupapalvelu.operations)

(def default-description "Mitaan lupia tollaseen tarvi, anna menna vaan. Tanne kannata tulla tommosen takia.")

(defn operations [municipality]
  [["Rakentaminen ja purkaminen" [["Uuden rakennuksen rakentaminen" [["Asuinrakennus" default-description]
                                                                     ["Vapaa-ajan asuinrakennus" default-description]
                                                                     ["Varasto, sauna, autotalli tai muu talousrakennus" default-description]
                                                                     ["Julkinen rakennus" default-description]
                                                                     ["Muu" default-description]]]
                                  ["Rakennuksen laajentaminen tai muuttaminen" [["Rakennuksen laajentaminen tai korjaaminen" default-description]
                                                                                ["Kayttotarkoituksen muutos, esim loma-asunnon muuttaminen pysyvaan kayttoon" default-description]
                                                                                ["Rakennuksen julkisivun tai katon materiaalin, varin tai muodon muuttaminen" default-description]
                                                                                ["Asuinhuoneiston jakaminen tai yhdistaminen" default-description]
                                                                                ["Markatilan laajentaminen" default-description]
                                                                                ["Takan ja savuhormin rakentaminen" default-description]
                                                                                ["Parvekkeen tai terassin lasittaminen" default-description]
                                                                                ["Muu" default-description]]]
                                  ["Muu rakentaminen" [["Auto- tai grillikatos, vaja, kioski tai vastaava" default-description]
                                                       ["Masto, piippu, sailio, laituri tai vastaava" default-description]
                                                       ["Mainoslaite" default-description]
                                                       ["Aita" default-description]
                                                       ["Maalampokaivon poraaminen tai lammonkeruuputkiston asentaminen" default-description]
                                                       ["Rakennuksen jatevesijarjestelman uusiminen" default-description]
                                                       ["Muu" default-description]]]
                                  ["Rakennuksen purkaminen" default-description]]]
   ["Maisemaa muuttava toimenpide" [["Kaivaminen, louhiminen tai maan tayttaminen" default-description]
                                    ["Puun kaataminen" default-description]
                                    ["Muu" default-description]]]])
