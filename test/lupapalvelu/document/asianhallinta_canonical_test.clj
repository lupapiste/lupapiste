(ns lupapalvelu.document.asianhallinta_canonical_test
  (:require [lupapalvelu.factlet :as fl]
            [lupapalvelu.document.asianhallinta_canonical :as ah]
            [lupapalvelu.document.canonical-test-common :as ctc]
            [lupapalvelu.document.tools :as tools]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [lupapalvelu.document.poikkeamis-canonical-test :as poikkeus-test]))

(fl/facts* "UusiAsia canonical"
  (let [canonical (ah/application-to-asianhallinta-canonical poikkeus-test/poikkari-hakemus "fi") => truthy
        application poikkeus-test/poikkari-hakemus]
    (facts "UusiAsia canonical from poikkeus-test/poikkari-hakemus"
      (fact "UusiAsia not empty" (:UusiAsia canonical) => seq)
      (fact "UusiAsia keys" (keys (get-in canonical [:UusiAsia])) => (just [:Tyyppi
                                                                            :Kuvaus
                                                                            :Kuntanumero
                                                                            :Hakijat
                                                                            :Maksaja
                                                                            :HakemusTunnus
                                                                            :VireilletuloPvm
                                                                            :Asiointikieli
                                                                            :Toimenpiteet
                                                                            :Sijainti
                                                                            :Kiinteistotunnus] :in-any-order))
      (fact "HakemusTunnus is LP-753-2013-00001" (get-in canonical [:UusiAsia :HakemusTunnus]) => "LP-753-2013-00001")
      (fact "First Hakija of Hakijat has Henkilo" (keys (first (get-in canonical [:UusiAsia :Hakijat :Hakija]))) => (just [:Henkilo]))
      (facts "Maksaja"
        (fact "Maksaja is yritys, and has Laskuviite and Verkkolaskutustieto"
          (keys (get-in canonical [:UusiAsia :Maksaja])) => (just [:Yritys :Laskuviite :Verkkolaskutustieto]))
        (fact "Maksaja is not Henkilo"
          (keys (get-in canonical [:UusiAsia :Maksaja])) =not=> (contains [:Henkilo]))
        (fact "Yritys keys"
          (keys (get-in canonical [:UusiAsia :Maksaja :Yritys])) => (just [:Nimi :Ytunnus :Yhteystiedot :Yhteyshenkilo]))
        (fact "Yhteystiedot keys"
          (keys (get-in canonical [:UusiAsia :Maksaja :Yritys :Yhteystiedot])) => (just [:Jakeluosoite :Postinumero :Postitoimipaikka]))
        (fact "Yhteyshenkilo keys"
          (keys (get-in canonical [:UusiAsia :Maksaja :Yritys :Yhteyshenkilo])) => (just [:Etunimi :Sukunimi :Yhteystiedot]))
        (fact "Yhteyshenkilo yhteystiedot keys"
          (keys (get-in canonical [:UusiAsia :Maksaja :Yritys :Yhteyshenkilo :Yhteystiedot])) => (just [:Email :Puhelinnumero]))
        (fact "Verkkolaskutustieto keys is nil"
          (keys (get-in canonical [:UusiAsia :Maksaja :Verkkolaskutustieto])) => nil))
      (fact "VireilletuloPvm is XML date"
        (get-in canonical [:UusiAsia :VireilletuloPvm]) => #"\d{4}-\d{2}-\d{2}")
      (fact "Liitteet TODO" )
      (fact "Toimenpiteet"
        (let [op (first (get-in canonical [:UusiAsia :Toimenpiteet :Toimenpide]))]
          (keys op) => (just [:ToimenpideTunnus :ToimenpideTeksti])))
      (fact "Asiointikieli"
        (get-in canonical [:UusiAsia :Asiointikieli]) => "fi")
      (fact "Sijainti is correct"
        (get-in canonical [:UusiAsia :Sijainti :Sijaintipiste]) => (str (-> application :location :x) " " (-> application :location :y)))
      (fact "Kiinteistotunnus is human readable"
        (get-in canonical [:UusiAsia :Kiinteistotunnus]) => (sade.util/to-human-readable-property-id (:propertyId application))))))

