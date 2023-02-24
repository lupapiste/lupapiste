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

(def tyonjohtaja-multiple-phones
  {:alkamisPvm "2020-08-11",
   :tyonjohtajaRooliKoodi "vastaava työnjohtaja",
   :paatostyyppi "hyväksytty",
   :henkilo {:nimi {:sukunimi "Teppo Terhakka"},
             :osoite {:osoitenimi {:teksti "Testitie 3"},
                      :postinumero "02200",
                      :postitoimipaikannimi "Heikkola"},
             :sahkopostiosoite "teppo@example.com",
             :puhelin '("0401231234" "0441231234")},
   :paatosPvm "2020-08-11",
   :hakemuksenSaapumisPvm "2020-08-10",
   :VRKrooliKoodi "työnjohtaja",
   :koulutus "rakennusinsinööri",
   :valmistumisvuosi "1998"})

(fact "tyonjohtaja -element from KRYSP is converted into tyonjohtaja-v2 document"
  (let [document (tyonjohtaja->tj-document tyonjohtaja)]
    (fact "It contains the value of :tyonjohtajaRooliKoodi from input"
      (get-in document [:data :kuntaRoolikoodi :value]) => "KVV-ty\u00F6njohtaja")
    (fact "Education information is preserved"
      (get-in document [:data :patevyys-tyonjohtaja :koulutusvalinta :value]) => "LVI-teknikko")
    (fact "Name and social security info are in place"
      (map #(-> % second :value)
           (-> (get-in document [:data :henkilotiedot])
               (select-keys [:hetu :not-finnish-hetu :etunimi :sukunimi])))
      => ["241277-122Y" false "Tiina" "Testinen"])))

(fact "multiple phones work without crashing - first one is taken"
  (-> tyonjohtaja-multiple-phones
      (tyonjohtaja->tj-document)
      (get-in [:data :yhteystiedot :puhelin :value]))
  => "0401231234")
