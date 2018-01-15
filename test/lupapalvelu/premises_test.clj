(ns lupapalvelu.premises-test
  (:require [clojure.test :refer :all]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]))

(testable-privates lupapalvelu.premises csv->header-and-data
                                        ifc-to-lupapiste-keys
                                        premise->updates
                                        data->model-updates
                                        pick-only-header-and-data-rows)

(fact "picking header and data rows"
  (let [sample-data [["info-line"]
                     ["Porras" "Huoneistonumero" "Huoneiston jakokirjain" "Sijaintikerros"
                      "Huoneiden lukum\u00e4\u00e4r\u00e4" "Keitti\u00f6tyyppi" "Huoneistoala"
                      "Varusteena WC" "Varusteena amme/suihku" "Varusteena parveke"
                      "Varusteena Sauna"  "Varusteena l\u00e4mmin vesi"]
                     ["A" "001" "" "2" "2" "3" "56" "1" "1" "1" "1" "1"]]]
    (pick-only-header-and-data-rows sample-data)
    => {:header [:porras :huoneistonumero :jakokirjain :sijaintikerros :huoneluku :keittionTyyppi :huoneistoala
                 :WCKytkin :ammeTaiSuihkuKytkin :parvekeTaiTerassiKytkin :saunaKytkin :lamminvesiKytkin]
        :data [["A" "001" "" "2" "2" "3" "56" "1" "1" "1" "1" "1"]]}))

(fact "works with lupapiste labels also"
  (let [sample-data [["test-rows"]
                     ["info-line"]
                     ["Huoneiston tyyppi" "Porras" "Huoneiston numero" "Jakokirjain" "Huoneluku"
                        "Keittiön tyyppi" "Huoneistoala m2" "WC" "Amme/ suihku" "Parveke/ terassi"
                        "Sauna" "Lämmin vesi"]
                     ["1" "A" "001" "" "2" "3" "56" "1" "1" "1" "1" "1"]]]
    (pick-only-header-and-data-rows sample-data)
    => {:header [:huoneistoTyyppi :porras :huoneistonumero :jakokirjain :huoneluku :keittionTyyppi :huoneistoala
                 :WCKytkin :ammeTaiSuihkuKytkin :parvekeTaiTerassiKytkin :saunaKytkin :lamminvesiKytkin]
        :data [["1" "A" "001" "" "2" "3" "56" "1" "1" "1" "1" "1"]]}))

(fact "csv-data comes out as a header-data-map"
  (csv->header-and-data "Porras;Huoneistonumero;Huoneiston jakokirjain;Sijaintikerros;Huoneiden lukumäärä;Keittiötyyppi;Huoneistoala;Varusteena WC;Varusteena amme/suihku;Varusteena parveke;Varusteena Sauna;Varusteena lämmin vesi\nA;001;;2;2;3;56;1;1;1;1;1")
      => {:header [:porras :huoneistonumero :jakokirjain :sijaintikerros :huoneluku :keittionTyyppi :huoneistoala
                   :WCKytkin :ammeTaiSuihkuKytkin :parvekeTaiTerassiKytkin :saunaKytkin :lamminvesiKytkin]
          :data    [["A" "001" "" "2" "2" "3" "56" "1" "1" "1" "1" "1"]]})

(fact "premise data becomes updates data"
      (let [{header :header data :data} (csv->header-and-data "Porras;Huoneistonumero;Huoneiston jakokirjain;Sijaintikerros;Huoneiden lukumäärä;Keittiötyyppi;Huoneistoala;Varusteena WC;Varusteena amme/suihku;Varusteena parveke;Varusteena Sauna;Varusteena lämmin vesi\nA;001;;2;2;3;56;1;1;1;1;1")]
        (set (data->model-updates header data)))
      => #{[[:huoneistot :0 :porras] "A"]
           [[:huoneistot :0 :huoneistonumero] "001"]
           [[:huoneistot :0 :huoneluku] "2"]
           [[:huoneistot :0 :keittionTyyppi] "keittotila"]
           [[:huoneistot :0 :huoneistoala] "56"]
           [[:huoneistot :0 :WCKytkin] true]
           [[:huoneistot :0 :ammeTaiSuihkuKytkin] true]
           [[:huoneistot :0 :parvekeTaiTerassiKytkin] true]
           [[:huoneistot :0 :saunaKytkin] true]
           [[:huoneistot :0 :lamminvesiKytkin] true]})
