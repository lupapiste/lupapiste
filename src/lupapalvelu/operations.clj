(ns lupapalvelu.operations)

(def default-description "Mitään lupia tollaseen tarvi, anna mennä vaan. Tänne kannata tulla tommosen takia.")

(defn operations [municipality]
  [["Rakentaminen ja purkaminen" [["Uuden rakennuksen rakentaminen" [["Asuinrakennus" default-description]
                                                                     ["Vapaa-ajan asuinrakennus" default-description]
                                                                     ["Varasto, sauna, autotalli tai muu talousrakennus" default-description]
                                                                     ["Julkinen rakennus" default-description]
                                                                     ["Muu" default-description]]]
                                  ["Rakennuksen laajentaminen tai muuttaminen" [["Rakennuksen laajentaminen tai korjaaminen" default-description]
                                                                                ["Käyttötarkoituksen muutos, esim loma-asunnon muuttaminen pysyvään käyttöön" default-description]
                                                                                ["Rakennuksen julkisivun tai katon materiaalin, värin tai muodon muuttaminen" default-description]
                                                                                ["Asuinhuoneiston jakaminen tai yhdistäminen" default-description]
                                                                                ["Märkätilan laajentaminen" default-description]
                                                                                ["Takan ja savuhormin rakentaminen" default-description]
                                                                                ["Parvekkeen tai terassin lasittaminen" default-description]
                                                                                ["Muu" default-description]]]
                                  ["Muu rakentaminen" [["Auto- tai grillikatos, vaja, kioski tai vastaava" default-description]
                                                       ["Masto, piippu, säiliö, laituri tai vastaava" default-description]
                                                       ["Mainoslaite" default-description]
                                                       ["Aita" default-description]
                                                       ["Maalämpökaivon poraaminen tai lämmönkeruuputkiston asentaminen" default-description]
                                                       ["Rakennuksen jätevesijärjestelmän uusiminen" default-description]
                                                       ["Muu" default-description]]]
                                  ["Rakennuksen purkaminen" default-description]]]
   ["Maisemaa muuttava toimenpide" [["Kaivaminen, louhiminen tai maan täyttäminen" default-description]
                                    ["Puun kaataminen" default-description]
                                    ["Muu" default-description]]]])
