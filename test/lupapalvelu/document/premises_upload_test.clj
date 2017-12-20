(ns lupapalvelu.document.premises-upload-test
  (:require [clojure.test :refer :all]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]))

(testable-privates lupapalvelu.premises-api csv-data->ifc-coll
                                            ifc-to-lupapiste-keys
                                            premise->updates)

(fact "csv-data-comes-out-as-a-key-val-map"
  (csv-data->ifc-coll "a;b;c\nd;e;f\ng;h;i")
      => [{"a" "d", "b" "e", "c" "f"} {"a" "g", "b" "h", "c" "i"}])

(fact "premise data becomes updates data"
      (let [input (csv-data->ifc-coll "Porras;Huoneistonumero;Huoneiston jakokirjain;Sijaintikerros;Huoneiden lukumäärä;Keittiötyyppi;Huoneistoala;Varusteena WC;Varusteena amme/suihku;Varusteena parveke;Varusteena Sauna;Varusteena lämmin vesi\nA;001;;2;2;3;56;1;1;1;1;1")]
        (map premise->updates input))
      => [[["huoneistot.1.sijaintikerros" "2"]
           ["huoneistot.1.porras" "A"]
           ["huoneistot.1.ammeTaiSuihkuKytkin" true]
           ["huoneistot.1.WCKytkin" true]
           ["huoneistot.1.huoneistonumero" "001"]
           ["huoneistot.1.parvekeTaiTerassiKytkin" true]
           ["huoneistot.1.saunaKytkin" true]
           ["huoneistot.1.huoneistoala" "56"]
           ["huoneistot.1.keittionTyyppi" "keittotila"]
           ["huoneistot.1.huoneluku" "2"]
           ["huoneistot.1.lamminvesiKytkin" true]
           ["huoneistot.1.jakokirjain" ""]]])





