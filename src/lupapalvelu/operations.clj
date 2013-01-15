(ns lupapalvelu.operations)

(defn operations [municipality]
  [["Rakentaminen ja purkaminen" [["Uuden rakennuksen rakentaminen" [["Asuinrakennus" "a"]
                                                                     ["Vapaa-ajan asuinrakennus" "v"]
                                                                     ["Varasto, sauna, autotalli tai muu talousrakennus" "v"]
                                                                     ["Julkinen rakennus" "j"]
                                                                     ["Muu" "m"]]]
                                  ["Rakennuksen laajentaminen tai muuttaminen" [["Rakennuksen laajentaminen tai korjaaminen" "k"]
                                                                                ["Käyttötarkoituksen muutos, esim loma-asunnon muuttaminen pysyvään käyttöön" "l"]
                                                                                ["Rakennuksen julkisivun tai katon materiaalin, värin tai muodon muuttaminen" "x"]
                                                                                ["Asuinhuoneiston jakaminen tai yhdistäminen" "y"]
                                                                                ["Märkätilan laajentaminen" "z"]
                                                                                ["Takan ja savuhormin rakentaminen" "a"]
                                                                                ["Parvekkeen tai terassin lasittaminen" "b"]
                                                                                ["Muu" "c"]]]] ]])
