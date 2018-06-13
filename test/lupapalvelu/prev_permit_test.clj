(ns lupapalvelu.prev-permit-test
    (:require [lupapalvelu.prev-permit :refer :all]
              [midje.sweet :refer :all]))

(def tyonjohtaja
  {:alkamisPvm "2013-09-13Z"
   :tyonjohtajaRooliKoodi "KVV-ty\u00F6njohtaja"
   :henkilo {:sahkopostiosoite "lappajarven-testityonjohtajat@jabadabaduu.fi"
             :puhelin "0400-123456"
             :nimi {:sukunimi "Testinen"
                    :etunimi "Tiina"}
             :osoite {:postinumero "00102"
                      :osoitenimi {:teksti "Mannerheimintie 1"}
                      :postitoimipaikannimi "HELSINKI"}
             :henkilotunnus "241277-122Y"}
   :hakemuksenSaapumisPvm "2013-09-13Z"
   :koulutus "LVI-teknikko"
   :valmistumisvuosi "1999"})

(fact "tyonjohtaja -element from KRYSP is converted into tyonjohtaja-v2 document"
  (let [document (tyonjohtaja->tj-document tyonjohtaja)]
    (fact "It contains the value of :tyonjohtajaRooliKoodi from input"
      (get-in document [:data :kuntaRoolikoodi :value]) => "KVV-ty\u00F6njohtaja")
    (fact "Education information is preserved"
      (get-in document [:data :patevyys-tyonjohtaja :koulutusvalinta :value]) => "LVI-teknikko")
    (fact "Name and social security info are in place"
      (doseq [heti (->> (get-in document [:data :henkilotiedot]) vals (mapcat vals))]
        (and (string? heti) (pos? (count heti))) => true?))))
