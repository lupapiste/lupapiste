(ns lupapalvelu.backing-system.krysp.verdict-test
  (:require [lupapalvelu.backing-system.krysp.verdict :refer :all]
            [midje.sweet :refer :all]
            [sade.strings :as ss]
            [sade.util :as util]))

(facts verdict-name
  (verdict-name nil) => nil
  (verdict-name "") => nil
  (verdict-name "  ") => nil
  (verdict-name "hello") => nil
  (verdict-name 0) => nil
  (verdict-name 99) => nil
  (verdict-name 12)
  => "valituksesta on luovuttu (oikaisuvaatimus tai lupa pysyy puollettuna)"
  (verdict-name "30") => "muutettu toimenpideluvaksi (konversio)"
  (verdict-name "  40  ") => "asia pantu pöydälle kokouksessa")

(facts "verdict mapping"
  (facts "by id"
    (verdict-id "lausunto")  => "33"
    (verdict-id "annettu lausunto")  => "11"
    (verdict-id "INVALID")   => nil)
  (facts "Names are canonized to the supported KuntaGML"
    (verdict-name "33")      => "annettu lausunto"
    (verdict-name :33)       => "annettu lausunto"
    (verdict-name 33)        => "annettu lausunto"
    (verdict-name 11)        => "annettu lausunto"
    (-> "ANNETTU LAUSUNTO (ENT. SELITYS)"
        verdict-id verdict-name) => "annettu lausunto"
    (-> "  Lausunto/Päätös (Muu Kuin Rlk)  "
        verdict-id verdict-name) => "annettu lausunto"
    (verdict-name "INVALID") => nil))

(facts "Every status code is supported"
  (doseq [code (range 1 45)]
    (fact {:midje/description (str "Status " code)}
      (verdict-name code) => ss/not-blank?
      (verdict-name (str code)) => ss/not-blank?
      (verdict-name (util/make-kw code)) => ss/not-blank?)))

(let [code-map @#'lupapalvelu.backing-system.krysp.verdict/status-code-map]
  (fact "Every code string is supported"
    (count code-map) => 40
    (doseq [s    (vals code-map)
            :let [id (verdict-id s)]]
      (fact {:midje/description s}
        id => ss/not-blank?
        (verdict-name id) => s))))
