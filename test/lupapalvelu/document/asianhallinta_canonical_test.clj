(ns lupapalvelu.document.asianhallinta_canonical_test
  (:require [lupapalvelu.factlet :as fl]
            [lupapalvelu.document.asianhallinta_canonical :as ah]
            [lupapalvelu.document.canonical-test-common :as ctc]
            [lupapalvelu.document.tools :as tools]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [lupapalvelu.document.poikkeamis-canonical-test :as poikkeus-test]))

(fl/facts* "UusiAsia canonical"
  (let [canonical (ah/application-to-asianhallinta-canonical poikkeus-test/poikkari-hakemus "fi") => truthy]
    (facts "Has (at least) required elements"
      (fact "UusiAsia" (keys (get-in canonical [:UusiAsia])) => (contains [:Tyyppi
                                                                           :Kuvaus
                                                                           :Kuntanumero
                                                                           :Hakijat
                                                                           :Maksaja
                                                                           :HakemusTunnus] :in-any-order))
      (fact "HakemusTunnus is LP-753-2013-00001" (get-in canonical [:UusiAsia :HakemusTunnus]) => "LP-753-2013-00001")
      (fact "First Hakija of Hakijat has Henkilo" (keys (:Hakija (first (get-in canonical [:UusiAsia :Hakijat])))) => (contains [:Henkilo]))
      (facts "Maksaja"
        (fact "Maksaja is yritys, and has Laskuviite and Verkkolaskutustieto"
          (keys (get-in canonical [:UusiAsia :Maksaja])) => (contains [:Yritys :Laskuviite :Verkkolaskutustieto]))
        (fact "Maksaja is not Henkilo"
          (keys (get-in canonical [:UusiAsia :Maksaja])) =not=> (contains [:Henkilo]))
        (fact "Yritys keys"
          (keys (get-in canonical [:UusiAsia :Maksaja :Yritys])) => (contains [:Nimi :Ytunnus :Yhteystiedot :Yhteyshenkilo]))
        (fact "Yhteystiedot keys"
          (keys (get-in canonical [:UusiAsia :Maksaja :Yritys :Yhteystiedot])) => (contains [:Jakeluosoite :Postinumero :Postitoimipaikka]))
        (fact "Yhteyshenkilo keys"
          (keys (get-in canonical [:UusiAsia :Maksaja :Yritys :Yhteyshenkilo])) => (contains [:Etunimi :Sukunimi :Yhteystiedot]))
        (fact "Yhteyshenkilo yhteystiedot keys"
          (keys (get-in canonical [:UusiAsia :Maksaja :Yritys :Yhteyshenkilo :Yhteystiedot])) => (contains [:Email :Puhelin]))
        (fact "Verkkolaskutustieto keys"
          (keys (get-in canonical [:UusiAsia :Maksaja :Verkkolaskutustieto])) => (contains [:OVT-tunnus :Verkkolaskutunnus :Operaattoritunnus]))))))

