(ns lupapalvelu.document.asianhallinta-canonical-test
  (:require [lupapalvelu.backing-system.asianhallinta.asianhallinta-mapping :as ahm]
            [lupapalvelu.document.asianhallinta-canonical :as ah]
            [lupapalvelu.document.poikkeamis-canonical-test :as poikkeus-test]
            [lupapalvelu.document.tools :as tools]
            [lupapalvelu.factlet :as fl]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.rakennuslupa-canonical-util :as rc-util]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [sade.core :refer :all]
            [sade.date :as date]
            [sade.property :as p]
            [sade.strings :as ss]
            [sade.util :as util]))


(testable-privates lupapalvelu.backing-system.asianhallinta.asianhallinta-mapping enrich-attachments-with-operation-data)

(def test-attachments [{:id :attachment1
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
                          :op [{:id "523844e1da063788effc1c56"}]
                          :modified 1424248442767}
                         {:id :attachment3
                          :type {:type-group "paapiirustus"
                                 :type-id    "pohjapiirros"}
                          :versions []}])

(defn- has-attachment-types [meta]
  (fact "type-group and type-id"
    (:Avain (first meta)) => "type-group"
    (:Avain (second meta)) => "type-id"))

(def- link-permit-data-kuntalupatunnus {:id "123-123-123-123" :type "kuntalupatunnus"})

(fl/facts* "UusiAsia canonical with Lupapiste link permit"
  (let [canonical   (ah/application-to-asianhallinta-canonical rc-util/application-suunnittelijan-nimeaminen "fi") => truthy
        application rc-util/application-suunnittelijan-nimeaminen]
    (fact "Viiteluvat"
      (let [links        (get-in canonical [:UusiAsia :Viiteluvat :Viitelupa])
            link         (first links)]
        (count links) => 1
        (keys link) => (just [:MuuTunnus])
        (let [link (get-in link [:MuuTunnus])]
          (keys link) => (just [:Tunnus :Sovellus])
          (:Tunnus link) => (get-in application [:linkPermitData 0 :id])
          (:Sovellus link) => "Lupapiste")))))

(fl/facts* "UusiAsia canonical with two link permits"
  (let [application (update-in rc-util/application-suunnittelijan-nimeaminen [:linkPermitData] conj link-permit-data-kuntalupatunnus)
        canonical   (ah/application-to-asianhallinta-canonical application "fi") => truthy]
    (fact "Viiteluvat"
      (let [links (get-in canonical [:UusiAsia :Viiteluvat :Viitelupa])
            link1 (first links)
            link2 (second links)        ]
        (count links) => 2

        (keys link1) => (just [:MuuTunnus])
        (let [link1 (get-in link1 [:MuuTunnus])]
          (keys link1) => (just [:Tunnus :Sovellus])
          (:Tunnus link1) => (get-in application [:linkPermitData 0 :id])
          (:Sovellus link1) => "Lupapiste")

        (fact "When link permit is not lupapistetunnus, AsianTunnus is used"
          (get-in link2 [:AsianTunnus]) => (get-in application [:linkPermitData 1 :id]))))))

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
                                                                            :Kiinteistotunnus
                                                                            :Viiteluvat] :in-any-order))
      (fact "HakemusTunnus is LP-753-2013-00001" (get-in canonical [:UusiAsia :HakemusTunnus]) => "LP-753-2013-00001")
      (fact "Kuvaus" (get-in canonical [:UusiAsia :Kuvaus]) => "S\u00f6derkullantie 146")
      (fact "Kuntanumero" (get-in canonical [:UusiAsia :Kuntanumero]) => "753")

      (fl/facts* "Hakija"
        (let [hakijat (get-in canonical [:UusiAsia :Hakijat :Hakija]) => truthy
              data (tools/unwrapped (get-in application [:documents 0 :data])) => truthy
              data-henkilo (:henkilo data) => truthy
              henkilo (-> hakijat first :Henkilo) => truthy]
          (fact "First Hakija of Hakijat has only Henkilo" (keys (first hakijat)) => (just [:Henkilo]))
          (fact "Henkilo expected fields"
            (keys henkilo) => (just [:Etunimi :Sukunimi :Yhteystiedot :Henkilotunnus :VainSahkoinenAsiointi :Turvakielto]))
          (fact "Etunimi" (:Etunimi henkilo) => (get-in data-henkilo [:henkilotiedot :etunimi]))
          (fact "Sukunimi" (:Sukunimi henkilo) => (get-in data-henkilo [:henkilotiedot :sukunimi]))
          (fact "Jakeluosoite" (get-in henkilo [:Yhteystiedot :Jakeluosoite]) => (get-in data-henkilo [:osoite :katu]))
          (fact "Postinumero" (get-in henkilo [:Yhteystiedot :Postinumero]) => (get-in data-henkilo [:osoite :postinumero]))
          (fact "Postitoimipaikka" (get-in henkilo [:Yhteystiedot :Postitoimipaikka]) => (get-in data-henkilo [:osoite :postitoimipaikannimi]))
          (fact "Email" (get-in henkilo [:Yhteystiedot :Email]) => (get-in data-henkilo [:yhteystiedot :email]))
          (fact "Puhelinnumero" (get-in henkilo [:Yhteystiedot :Puhelinnumero]) => (get-in data-henkilo [:yhteystiedot :puhelin]))
          (fact "Hetu" (get-in henkilo [:Henkilotunnus]) => (get-in data-henkilo [:henkilotiedot :hetu]))
          (fact "VainSahkoinenAsiointi" (get-in henkilo [:VainSahkoinenAsiointi]) => (get-in data-henkilo [:kytkimet :vainsahkoinenAsiointiKytkin]))
          (fact "Turvakielto" (get-in henkilo [:Turvakielto]) => (get-in data-henkilo [:henkilotiedot :turvakieltoKytkin]))))

      (facts "Maksaja"
        (let [maksaja (get-in canonical [:UusiAsia :Maksaja])
              data    (tools/unwrapped (get-in application [:documents 4 :data]))
              yritys  (:Yritys maksaja)]
          (fact "Maksaja is yritys, and has Laskuviite and Verkkolaskutustieto"
            (keys maksaja) => (just [:Yritys :Laskuviite] :in-any-order))
          (fact "Maksaja is not Henkilo"
            (keys keys) =not=> (contains [:Henkilo]))
          (facts "Yritys"
            (fact "Nimi" (:Nimi yritys) => (get-in data [:yritys :yritysnimi]))
            (fact "Ytunnus" (:Ytunnus yritys) => (get-in data [:yritys :liikeJaYhteisoTunnus]))
            (fact "Jakeluosoite" (get-in yritys [:Yhteystiedot :Jakeluosoite]) => (get-in data [:yritys :osoite :katu]))
            (fact "Postinumero" (get-in yritys [:Yhteystiedot :Postinumero]) => (get-in data [:yritys :osoite :postinumero]))
            (fact "Postitoimipaikka" (get-in yritys [:Yhteystiedot :Postitoimipaikka]) => (get-in data [:yritys :osoite :postitoimipaikannimi]))
            (fact "Yhteyshenkilo Etunimi" (get-in yritys [:Yhteyshenkilo :Etunimi]) => (get-in data [:yritys :yhteyshenkilo :henkilotiedot :etunimi]))
            (fact "Yhteyshenkilo Sukunimi" (get-in yritys [:Yhteyshenkilo :Sukunimi]) => (get-in data [:yritys :yhteyshenkilo :henkilotiedot :sukunimi]))
            (fact "Yhteyshenkilo Email" (get-in yritys [:Yhteyshenkilo :Yhteystiedot :Email]) => (get-in data [:yritys :yhteyshenkilo :yhteystiedot :email]))
            (fact "Yhteyshenkilo Puhelinnumero" (get-in yritys [:Yhteyshenkilo :Yhteystiedot :Puhelinnumero]) => (get-in data [:yritys :yhteyshenkilo :yhteystiedot :puhelin]))
            (fact "Laskuviite" (:Laskuviite maksaja) => (:laskuviite data))
            (fact "Verkkolaskutustieto does not exists" (:Verkkolaskutustieto maksaja) => falsey))
          (fact "Yhteystiedot keys"
            (keys (get-in canonical [:UusiAsia :Maksaja :Yritys :Yhteystiedot])) => (just [:Jakeluosoite :Postinumero :Postitoimipaikka]))
          (fact "Yhteyshenkilo keys"
            (keys (get-in canonical [:UusiAsia :Maksaja :Yritys :Yhteyshenkilo])) => (just [:Etunimi :Sukunimi :Yhteystiedot]))
          (fact "Yhteyshenkilo yhteystiedot keys"
            (keys (get-in canonical [:UusiAsia :Maksaja :Yritys :Yhteyshenkilo :Yhteystiedot])) => (just [:Email :Puhelinnumero]))
          (fact "Verkkolaskutustieto keys is nil"
            (keys (get-in canonical [:UusiAsia :Maksaja :Verkkolaskutustieto])) => nil)))

      (fact "VireilletuloPvm is XML date"
        (get-in canonical [:UusiAsia :VireilletuloPvm]) => #"\d{4}-\d{2}-\d{2}")
      (fact "VireilletuloPvm same as from doc"
        (get-in canonical [:UusiAsia :VireilletuloPvm]) => (date/xml-date (:submitted application)))

      (fact "Toimenpiteet"
        (let [ops (get-in canonical [:UusiAsia :Toimenpiteet :Toimenpide])
              op (first ops)]
          (count ops) => 1
          (keys op) => (just [:ToimenpideTunnus :ToimenpideTeksti])
          (:ToimenpideTunnus op) => (:name (:primaryOperation application))
          (:ToimenpideTeksti op) => (i18n/localize "fi" (str "operations." (:name (:primaryOperation application))))))

      (fact "Asiointikieli"
        (get-in canonical [:UusiAsia :Asiointikieli]) => "fi")
      (fact "Sijainti is correct"
        (get-in canonical [:UusiAsia :Sijainti :Sijaintipiste]) => (str (-> application :location first) " " (-> application :location second)))
      (fact "Kiinteistotunnus is human readable"
        (get-in canonical [:UusiAsia :Kiinteistotunnus]) => (p/to-human-readable-property-id (:propertyId application))))

    (facts "Canonical with attachments"
      (let [begin-of-link "sftp://localhost/test/"
            attachments test-attachments
            application-with-attachments (assoc poikkeus-test/poikkari-hakemus :attachments attachments)
            application-with-attachments (ahm/enrich-application application-with-attachments)
            canonical-attachments (ah/get-attachments-as-canonical (:attachments application-with-attachments) begin-of-link)]
        (fact "Canonical has correct count of attachments"
          (count canonical-attachments) => 2)
        (fact "attachment has correct keys"
          (keys (first canonical-attachments)) => (just [:Kuvaus :KuvausFi :KuvausSv :Tyyppi :LinkkiLiitteeseen :Luotu :Metatiedot]))
        (fact "Kuvaus fields"
          (:Kuvaus (first canonical-attachments)) => "asemapiirros"
          (:KuvausFi (first canonical-attachments)) => "Asemapiirros"
          #_(:KuvausSv (first canonical-attachments)) => "Situationsplan")
        (fact "All attachments have 'begin-of-link' prefix"
          (every? #(-> % :LinkkiLiitteeseen (ss/starts-with begin-of-link)) canonical-attachments) => true)
        (fact "filenames are in format 'fileId_filename'"
          (let [att1 (first attachments)
                canon-att1 (first canonical-attachments)]
            (ss/suffix (:LinkkiLiitteeseen canon-att1) "/") => (str (-> att1 :latestVersion :fileId) "_" (-> att1 :latestVersion :filename))))
        (facts "Metatiedot"
          (let [metas (map #(get-in % [:Metatiedot :Metatieto]) canonical-attachments)]
            (fact "All have type-group and type-id keys"
              (doseq [meta metas]
                (has-attachment-types meta)))
            (fact "Second attachment has operation meta"
              (last (second metas)) => {:Avain "operation" :Arvo (:name (:primaryOperation application-with-attachments))}))))))

  (fl/facts* "TaydennysAsiaan canonical"
    (let [application poikkeus-test/poikkari-hakemus
          canonical (ah/application-to-asianhallinta-taydennys-asiaan-canonical application) => truthy
          canonical-attachments (ah/get-attachments-as-canonical
                                  (enrich-attachments-with-operation-data test-attachments (conj (seq (:secondaryOperations application)) (:primaryOperation application)))
                                  "sftp://localhost/test")]
      (facts "TaydennysAsiaan canonical from poikkeus-test/poikkari-hakemus"
        (fact "TaydennysAsiaan not empty" (:TaydennysAsiaan canonical) => seq)
        (fact "TaydennysAsiaan keys" (keys (get-in canonical [:TaydennysAsiaan])) => (just [:HakemusTunnus] :in-any-order))
        (fact "HakemusTunnus is LP-753-2013-00001" (get-in canonical [:TaydennysAsiaan :HakemusTunnus]) => "LP-753-2013-00001")

        (facts "Attachments"
          (count canonical-attachments) => 2
          (fact "Second attachment has operation Metatieto"
            (last (get-in (second canonical-attachments) [:Metatiedot :Metatieto])) => {:Avain "operation" :Arvo (:name (:primaryOperation application))}))))))
