(ns lupapalvelu.operations)

(def default-description "Hankkeesi vaatii todenn\u00E4k\u00F6isesti luvan. Voit hakea lupaa Lupapisteen kautta. Sinun kannattaa my\u00F6s tutustua alla listattuihin sivustoihin, joilta l\u00F6yd\u00E4t lis\u00E4\u00E4 tietoa rakennusvalvonnasta. Voit my\u00F6s kysy\u00E4 lis\u00E4\u00E4 aiheesta kunnan rakennusvalvonnasta Lupapisteen kautta tekem\u00E4ll\u00E4 neuvontapyynn\u00F6n.")

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
