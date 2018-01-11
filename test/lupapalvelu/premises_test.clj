(ns lupapalvelu.premises-test
  (:require [clojure.test :refer :all]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]))

(testable-privates lupapalvelu.premises csv-data->ifc-coll
                                        ifc-to-lupapiste-keys
                                        premise->updates)

(fact "csv-data-comes-out-as-a-key-val-map"
  (csv-data->ifc-coll "Porras;Huoneistonumero;Huoneiston jakokirjain;Sijaintikerros;Huoneiden lukum\u00e4\u00e4r\u00e4;Keitti\u00f6tyyppi;Huoneistoala;Varusteena WC;Varusteena amme/suihku;Varusteena parveke;Varusteena Sauna;Varusteena l\u00e4mmin vesi\nA;001;;2;2;3;56;1;1;1;1;1")
      => [{"sijaintikerros" "2"
           "porras" "A"
           "varusteena amme/suihku" "1"
           "varusteena wc" "1"
           "huoneistonumero" "001"
           "varusteena parveke" "1"
           "varusteena sauna" "1"
           "huoneistoala" "56"
           "keitti\u00f6tyyppi" "3"
           "huoneiden lukum\u00e4\u00e4r\u00e4" "2"
           "varusteena l\u00e4mmin vesi" "1"
           "huoneiston jakokirjain" ""}])

(fact "premise data becomes updates data"
      (let [input (csv-data->ifc-coll "Porras;Huoneistonumero;Huoneiston jakokirjain;Huoneiden lukum\u00e4\u00e4r\u00e4;Keitti\u00f6tyyppi;Huoneistoala;Varusteena WC;Varusteena amme/suihku;Varusteena parveke;Varusteena Sauna;Varusteena l\u00e4mmin vesi\nA;001;;2;3;56;1;1;1;1;1")]
        (set (first (map #(premise->updates % 0) input))))
      => #{["huoneistot.0.porras" "A"]
           ["huoneistot.0.huoneistonumero" "001"]
           ["huoneistot.0.huoneluku" "2"]
           ["huoneistot.0.keittionTyyppi" "keittotila"]
           ["huoneistot.0.huoneistoala" "56"]
           ["huoneistot.0.WCKytkin" true]
           ["huoneistot.0.ammeTaiSuihkuKytkin" true]
           ["huoneistot.0.parvekeTaiTerassiKytkin" true]
           ["huoneistot.0.saunaKytkin" true]
           ["huoneistot.0.lamminvesiKytkin" true]})
