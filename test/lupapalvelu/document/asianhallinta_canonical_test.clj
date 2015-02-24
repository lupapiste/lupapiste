(ns lupapalvelu.document.asianhallinta_canonical_test
  (:require [lupapalvelu.factlet :as fl]
            [lupapalvelu.document.asianhallinta_canonical :as ah]
            [lupapalvelu.document.canonical-test-common :as ctc]
            [lupapalvelu.document.tools :as tools]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [lupapalvelu.document.poikkeamis-canonical-test :as poikkeus-test]
            [sade.strings :as ss]))

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
      (fact "Kuvaus" (get-in canonical [:UusiAsia :Kuvaus]) => "S\u00f6derkullantie 146")
      (fact "Kuntanumero" (get-in canonical [:UusiAsia :Kuntanumero]) => "753")
      (fact "First Hakija of Hakijat has Henkilo" (keys (first (get-in canonical [:UusiAsia :Hakijat :Hakija]))) => (just [:Henkilo]))
      (fl/facts* "Hakija"
        (let [hakijat (get-in canonical [:UusiAsia :Hakijat :Hakija]) => truthy
              data    (tools/unwrapped (get-in application [:documents 0 :data :henkilo])) => truthy
              henkilo (get-in (first hakijat) [:Henkilo])]
          (fact "First Hakija of Hakijat has only Henkilo" (keys (first hakijat)) => (just [:Henkilo]))
          (fact "Etunimi" (:Etunimi henkilo) => (get-in data [:henkilotiedot :etunimi]))
          (fact "Sukunimi" (:Sukunimi henkilo) => (get-in data [:henkilotiedot :sukunimi]))
          (fact "Jakeluosoite" (get-in henkilo [:Yhteystiedot :Jakeluosoite]) => (get-in data [:osoite :katu]))
          (fact "Postinumero" (get-in henkilo [:Yhteystiedot :Postinumero]) => (get-in data [:osoite :postinumero]))
          (fact "Postitoimipaikka" (get-in henkilo [:Yhteystiedot :Postitoimipaikka]) => (get-in data [:osoite :postitoimipaikannimi]))
          (fact "Email" (get-in henkilo [:Yhteystiedot :Email]) => (get-in data [:yhteystiedot :email]))
          (fact "Puhelinnumero" (get-in henkilo [:Yhteystiedot :Puhelinnumero]) => (get-in data [:yhteystiedot :puhelin]))
          (fact "Hetu" (get-in henkilo [:Henkilotunnus]) => (get-in data [:henkilotiedot :hetu]))))
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
        (get-in canonical [:UusiAsia :Kiinteistotunnus]) => (sade.util/to-human-readable-property-id (:propertyId application))))

    (facts "Canonical with attachments"
      (let [begin-of-link "sftp://localhost/test/"
            attachments [{:id :attachment1
                          :type {:type-group "paapiirustus"
                                 :type-id    "asemapiirros"}
                          :latestVersion {:version { :major 1 :minor 0 }
                                          :fileId "file321"
                                          :filename "asemapiirros.pdf"
                                          :contentType "application/pdf"}
                          :modified 1424248442767}
                         {:id :attachment2
                          :type {:type-group "hakija"
                                 :type-id    "valtakirja"}
                          :latestVersion {:version { :major 1 :minor 0 }
                                          :fileId "file123"
                                          :filename "valtakirja.pdf"
                                          :contentType "application/pdf"}
                          :modified 1424248442767}
                         {:id :attachment3
                          :type {:type-group "paapiirustus"
                                 :type-id    "pohjapiirros"}
                          :versions []}]
            application-with-attachments (assoc poikkeus-test/poikkari-hakemus :attachments attachments)
            canonical-attachments (ah/get-attachments-as-canonical application-with-attachments begin-of-link)]

        (fact "Canonical has correct count of attachments"
          (count canonical-attachments) => 2)
        (fact "attachment has correct keys"
          (keys (first canonical-attachments)) => (just [:Kuvaus :Tyyppi :LinkkiLiitteeseen :Luotu :Metatiedot]))
        (fact "All attachments have 'begin-of-link' prefix"
          (every? #(-> % :LinkkiLiitteeseen (ss/starts-with begin-of-link)) canonical-attachments) => true)
        (fact "filenames are in format 'fileId_filename'"
          (let [att1 (first attachments)
                canon-att1 (first canonical-attachments)]
            (ss/suffix (:LinkkiLiitteeseen canon-att1) "/") => (str (-> att1 :latestVersion :fileId) "_" (-> att1 :latestVersion :filename))))))))

