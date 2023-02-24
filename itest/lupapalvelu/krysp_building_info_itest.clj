(ns lupapalvelu.krysp-building-info-itest
  (:require [clojure.data :as data]
            [clojure.java.io :as io]
            [lupapalvelu.backing-system.krysp.application-from-krysp :as krysp-fetch]
            [lupapalvelu.backing-system.krysp.building-reader :as bld-rdr]
            [lupapalvelu.document.tools :as tools]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.factlet :refer :all]
            [lupapalvelu.fixture.core :as fixture]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.pate-itest-util :refer [toggle-pate]]
            [midje.sweet :refer :all]
            [mount.core :as mount]
            [sade.common-reader :as scr]
            [sade.coordinate :as coordinate]
            [sade.core :refer [now]]
            [sade.strings :as ss]
            [sade.util :as util]
            [sade.xml :as sxml]))

(apply-remote-minimal)
(fact* "Merging building information from KRYSP does not overwrite muutostyolaji or energiatehokkuusluvunYksikko"
  (let [application-id (create-app-id pena :propertyId sipoo-property-id :operation "kayttotark-muutos")
        app (query-application pena application-id)
        rakmuu-doc (domain/get-document-by-name app "rakennuksen-muuttaminen")
        _ (command pena :update-doc :id application-id :doc (:id rakmuu-doc) :updates [["muutostyolaji" "muut muutosty\u00f6t"]])
        updated-app (query-application pena application-id)
        building-info (query pena :get-building-info-from-wfs :id application-id) => ok?
        doc-before (domain/get-document-by-name updated-app "rakennuksen-muuttaminen")
        building-id (:buildingId (first (:data building-info)))

        _ (command pena :merge-details-from-krysp :id application-id :documentId (:id doc-before) :buildingId building-id :overwrite true) => ok?
        merged-app (query-application pena application-id)
        doc-after (domain/get-document-by-name merged-app "rakennuksen-muuttaminen")]

    (fact "muutostyolaji"
      (get-in doc-before [:data :muutostyolaji :value]) => "muut muutosty\u00f6t"
      (get-in doc-after [:data :muutostyolaji :value]) => "muut muutosty\u00f6t")

    (facts "energiatehokkuusluvunYksikko"
      (fact "document has default value"
        (get-in doc-before [:data :luokitus :energiatehokkuusluvunYksikko :value]) => "kWh/m2")
      (fact "was not altered"
        (get-in doc-after [:data :luokitus :energiatehokkuusluvunYksikko :value]) => "kWh/m2"))

    (get-in doc-after [:data :rakennusnro :value]) => "001"
    (get-in doc-after [:data :manuaalinen_rakennusnro :value]) => ss/blank?
    (get-in doc-after [:data :valtakunnallinenNumero :value]) => "122334455R"
    (count (get-in doc-after [:data :huoneistot])) => 21
    (get-in doc-after [:data :kaytto :kayttotarkoitus :value]) => "039 muut asuinkerrostalot"
    (get-in doc-after [:data :kaytto :kayttotarkoitus :source]) => "krysp"
    (get-in doc-after [:data :varusteet :sahkoKytkin :value]) => true

    (fact "KRYSP data is stored in source value field"
      (get-in doc-before [:data :mitat :tilavuus :sourceValue]) => nil
      (get-in doc-after [:data :mitat :tilavuus :sourceValue]) => "8240"
      (get-in doc-after [:data :mitat :tilavuus :value]) => "8240")

    (fact "Data changes correctly between two different buildings (overwrite true)"
     (let [building-id-2 (:buildingId (second (:data building-info)))
           _ (command pena :merge-details-from-krysp :id application-id :documentId (:id doc-before) :buildingId building-id-2 :overwrite true) => ok?
           merged-app (query-application pena application-id)
           doc-after-2 (domain/get-document-by-name merged-app "rakennuksen-muuttaminen")
           huoneistot (get-in doc-after-2 [:data :huoneistot])]

       (fact "muutostyo not changed because it's not KRYSP field"
         (get-in doc-after-2 [:data :muutostyolaji :value]) => "muut muutosty\u00f6t")
       (fact "Kayttotarkoitus has new value from KRYSP data"
         (get-in doc-after-2 [:data :kaytto :kayttotarkoitus :value]) => "021 rivitalot"
         (get-in doc-after-2 [:data :kaytto :kayttotarkoitus :source]) => "krysp")

       (fact "Huoneistot are replaced not merged"
         (count (get-in doc-after [:data :huoneistot])) => 21
         (count huoneistot) => 2)

       (fact "count of rakennuksenOmistajat is correct"
         (count (get-in doc-after [:data :rakennuksenOmistajat])) => 2
         (count (get-in doc-after-2 [:data :rakennuksenOmistajat])) => 4)))

    ; Merge back building-id 1 data for next test
    (command pena :merge-details-from-krysp :id application-id :documentId (:id doc-before) :buildingId building-id :overwrite true) => ok?

    (fact "Merging ID only"
      (let [building-id-2 (:buildingId (second (:data building-info)))
            _ (command pena :merge-details-from-krysp :id application-id :documentId (:id doc-before) :buildingId building-id-2 :overwrite false) => ok?
            merged-app (query-application pena application-id)
            doc-after-3 (domain/get-document-by-name merged-app "rakennuksen-muuttaminen")]

        (fact "muutostyo not changed because it's not KRYSP field"
         (get-in doc-after-3 [:data :muutostyolaji :value]) => "muut muutosty\u00f6t")

        (fact "kayttotarkoitus remains the same"
          (get-in doc-after-3 [:data :kaytto :kayttotarkoitus :value]) => "039 muut asuinkerrostalot")

        (fact "ID has changed"
          (get-in doc-after-3 [:data :valtakunnallinenNumero :value]) => "199887766E"
          (get-in doc-after-3 [:data :rakennusnro :value]) => "002"
          (get-in doc-after-3 [:data :manuaalinen_rakennusnro :value]) => ss/blank?)))

    (fact "When selecting 'other' from building selector, old KRYSP source data is not present (data is set to defaults). THIS USE CASE NO LONGER EXISTS?"
      (fact "the command"
        (command pena :merge-details-from-krysp :id application-id :documentId (:id doc-before) :buildingId "other" :overwrite true) => ok?)
      (fact "set manual"
        (command pena :update-doc :id application-id :doc (:id doc-before) :collection "documents" :updates [["manuaalinen_rakennusnro" "123"]]) => ok?)
      (let [merged-app (query-application pena application-id)
            doc-after-4 (domain/get-document-by-name merged-app "rakennuksen-muuttaminen")]

        (fact "muutostyo not changed because it's not KRYSP field"
          (get-in doc-after-4 [:data :muutostyolaji :value]) => "muut muutosty\u00f6t")

        (fact "buildingId is 'other'"
          (get-in doc-after-4 [:data :buildingId :value]) => "other")

        (fact "source and sourceValues are not present in updated document"
          (get-in doc-after [:data :mitat :tilavuus :sourceValue]) => "8240"
          (get-in doc-after [:data :mitat :tilavuus :source]) => "krysp"

          (get-in doc-after-4 [:data :mitat :tilavuus :sourceValue]) => "" ; default values are set
          (get-in doc-after-4 [:data :mitat :tilavuus :source]) => nil)

        (fact "values have been set to schema defaults"
          (get-in doc-after-4 [:data :rakennusnro :value]) => ss/blank?
          (get-in doc-after-4 [:data :manuaalinen_rakennusnro :value]) => "123"
          (get-in doc-after-4 [:data :valtakunnallinenNumero :value]) => ss/blank?
          (count (get-in doc-after-4 [:data :huoneistot])) => 0 ; huoneistot are nuked
          (get-in doc-after-4 [:data :kaytto :kayttotarkoitus :value]) => nil ; default value for select is null
          (get-in doc-after-4 [:data :kaytto :kayttotarkoitus :source]) => nil
          (get-in doc-after-4 [:data :varusteet :sahkoKytkin :value]) => false)

        (facts "changing back to some actual buildingId without overwriting"
          (fact "command ok"
            (command pena :merge-details-from-krysp :id application-id :documentId (:id doc-before) :buildingId building-id :overwrite false) => ok?)
          (let [merged-app (query-application pena application-id)
                doc-after-5 (domain/get-document-by-name merged-app "rakennuksen-muuttaminen")
                strip-modified #(util/postwalk-map (partial remove (comp (partial = :modified) key)) %)
                [in-4 in-5 _] (data/diff (strip-modified (:data doc-after-4)) (strip-modified (:data doc-after-5)))]
            (fact "doc 4 has other and manuaalinen set, others empty"
              in-4 => {:buildingId {:sourceValue "other" :value "other"}
                       :manuaalinen_rakennusnro {:value "123"}
                       :rakennusnro {:sourceValue "" :value ""}
                       :valtakunnallinenNumero {:sourceValue "" :value ""}})
            (fact "comparison to 5 , has buildingId set but manuaalinen rakennusnumero set to blank"
              in-5 => {:buildingId {:sourceValue "122334455R" :value "122334455R"}
                       :manuaalinen_rakennusnro {:value ""}
                       :rakennusnro {:sourceValue "001" :value "001"}
                       :valtakunnallinenNumero {:sourceValue "122334455R" :value "122334455R"}})))))))

(fact* "Merging building information from KRYSP succeeds even if document schema does not have place for all the info"
  (let [application-id (create-app-id pena :propertyId sipoo-property-id :operation "purkaminen")
        app (query-application pena application-id)
        doc (domain/get-document-by-name app "purkaminen")
        building-info (query pena :get-building-info-from-wfs :id application-id) => ok?
        building-id (:buildingId (first (:data building-info)))
        _ (command pena :merge-details-from-krysp :id application-id :documentId (:id doc) :buildingId building-id :overwrite true) => ok?
        merged-app (query-application pena application-id)
        doc-after (domain/get-document-by-name merged-app "purkaminen")]
    (get-in doc-after [:data :mitat :kokonaisala :source]) => "krysp"
    (get-in doc-after [:data :kaytto :kayttotarkoitus :source]) => "krysp"))

(fact* "Building id can be 'other' even if there is no backend (LPK-274)"
  (let [application-id (create-app-id pena :propertyId no-backend-property-id :operation "purkaminen")
        app (query-application pena application-id)
        doc (domain/get-document-by-name app "purkaminen")
        _ (query pena :get-building-info-from-wfs :id application-id) => ok?
        _ (command pena :merge-details-from-krysp :id application-id :documentId (:id doc) :buildingId "other" :overwrite true) => ok?
        merged-app (query-application pena application-id)
        doc-after (domain/get-document-by-name merged-app "purkaminen")]
    (get-in doc-after [:data :buildingId :value]) => "other"))

(facts "Merging building information from KuntaGML in a post-verdict state"
  (let [{application-id :id
         :as app} (create-and-submit-application pena :propertyId sipoo-property-id :operation "purkaminen")
        doc (domain/get-document-by-name app "purkaminen")
        building-info (query pena :get-building-info-from-wfs :id application-id) => ok?
        building-id (:buildingId (first (:data building-info)))
        merge-cmd #(command % :merge-details-from-krysp :id application-id
                            :documentId (:id doc)
                            :buildingId building-id :overwrite true)]
    (fact "Fetch verdict"
      (command sonja :check-for-verdict :id application-id) => ok?)
    (fact "Application state is verdictGiven"
      (:state (query-application pena application-id)) => "verdictGiven")
    (fact "Applicant no longer can merge the building information"
      (merge-cmd pena) => (partial expected-failure? "error.unauthorized"))
    (fact "... and neither can authority, since Pate is disabled"
      (merge-cmd sonja) => (partial expected-failure? "error.pate-disabled"))
    (fact "After Pate is enabled, the authority can now merge the building information"
      (toggle-pate "753-R" true)
      (merge-cmd sonja) => ok?)
    (fact ".. but applicant still cannot."
      (merge-cmd pena) => (partial expected-failure? "error.unauthorized"))))

(def db-name (str "test_krysp-building-info-test_" (now)))

(mount/start #'mongo/connection)
(mongo/with-db db-name
  (fixture/apply-fixture "minimal")
  (facts "pht (pysyva huoneistotunnus) gets updated for buildings when fetching verdict"
    (against-background [(coordinate/convert anything anything anything anything) => nil])
    (let [application-id          (:id (create-local-app sonja :propertyId sipoo-property-id :address "submitted 16"))
          _                       (local-command sonja :add-operation :id application-id :operation "pientalo")
          application             (query-application local-query sonja application-id)
          kerrostalo-doc          (domain/get-document-by-name application "uusiRakennus")
          kerrostalo-doc-id       (:id kerrostalo-doc)
          kerrostalo-operation-id (get-in kerrostalo-doc [:schema-info :op :id])
          pientalo-doc            (-> (domain/get-documents-by-name application "uusiRakennus") last)
          pientalo-doc-id         (:id pientalo-doc)
          pientalo-operation-id   (get-in pientalo-doc [:schema-info :op :id])]
      (fact "Update apartments for both operations"
        (local-command sonja :update-doc :id application-id :doc kerrostalo-doc-id :collection "documents"
                       :updates [["huoneistot.0.jakokirjain", "a"]
                                 ["huoneistot.0.huoneistonumero", "153"]
                                 ["huoneistot.0.porras", "b"]
                                 ["huoneistot.1.jakokirjain", "c"]
                                 ["huoneistot.1.huoneistonumero", "634"]
                                 ["huoneistot.1.porras", "d"]
                                 ["huoneistot.2.jakokirjain", "c"]
                                 ["huoneistot.2.huoneistonumero", "644"]
                                 ["huoneistot.2.porras", "d"]]) => ok?

        (local-command sonja :update-doc :id application-id :doc pientalo-doc-id :collection "documents"
                       :updates [["huoneistot.0.jakokirjain", "a"]
                                 ["huoneistot.0.huoneistonumero", "111"]
                                 ["huoneistot.0.porras", "b"]
                                 ["huoneistot.1.huoneistonumero", "222"]
                                 ["huoneistot.2.jakokirjain", "c"]
                                 ["huoneistot.2.huoneistonumero", "333"]
                                 ["huoneistot.2.porras", "d"]]) => ok?)

      (facts "submit and approve application"
        (local-command sonja :submit-application :id application-id) => ok?
        (local-command sonja :update-app-bulletin-op-description :id application-id :description "otsikko julkipanoon") => ok?
        (local-command sonja :approve-application :id application-id :lang "fi") => ok?)

      (fact "fetch verdict"
        (local-command sonja :check-for-verdict :id application-id) => ok?
        (provided (krysp-fetch/get-application-xml-by-application-id anything)
                  => (-> (slurp "resources/krysp/dev/verdict-2.2.4.xml")
                         (ss/replace #"LP-186-2013-00002" application-id)
                         (ss/replace #"589012" kerrostalo-operation-id)
                         (ss/replace #"3141592" pientalo-operation-id)
                         (sxml/parse-string "utf-8")
                         scr/strip-xml-namespaces)))

      (facts "phts (pysyvat huoneistotunnukset) are updated for apartments"
        (let [application           (query-application local-query sonja application-id)
              kerrostalo-apartments (->> application
                                         :documents
                                         (util/find-by-id kerrostalo-doc-id)
                                         :data
                                         :huoneistot)
              pientalo-apartments   (->> application
                                         :documents
                                         (util/find-by-id pientalo-doc-id)
                                         :data
                                         :huoneistot)]
          (facts "pht updates for kerrostalo apartments"
            (fact ":0 apartment"
              (-> kerrostalo-apartments :0 :pysyvaHuoneistotunnus :value) => "1234567890")
            (fact ":1 apartment"
              (-> kerrostalo-apartments :1 :pysyvaHuoneistotunnus :value) => "3141")
            (fact ":2 apartment is not updated because building data from background (xml) does not have matching apartment"
              (-> kerrostalo-apartments :2 :pysyvaHuoneistotunnus :value) => nil))

          (facts "pht updates for pientalo apartments"
            (fact ":0 apartment"
              (-> pientalo-apartments :0 :pysyvaHuoneistotunnus :value) => "555")
            (fact ":1 apartment"
              (-> pientalo-apartments :1 :pysyvaHuoneistotunnus :value) => "777")
            (fact ":2 apartment is not updated because building data from background (xml) does not have matching apartment"
              (-> pientalo-apartments :2 :pysyvaHuoneistotunnus :value) => nil))

          (facts "pht updates for kerrostalo building"
            (->> application :buildings first :apartments
                 (mapv #(select-keys % [:huoneistonumero :porras :jakokirjain :pysyvaHuoneistotunnus])))
            => [{:huoneistonumero       "153"
                 :jakokirjain           "a"
                 :porras                "b"
                 :pysyvaHuoneistotunnus "1234567890"}

                {:huoneistonumero       "634"
                 :jakokirjain           "c"
                 :porras                "d"
                 :pysyvaHuoneistotunnus "3141"}])

          (facts "pht updates for pientalo building"
            (->> application :buildings second :apartments
                 (mapv #(select-keys % [:huoneistonumero :porras :jakokirjain :pysyvaHuoneistotunnus])))
            => [{:huoneistonumero       "222"
                 :jakokirjain           nil
                 :porras                nil
                 :pysyvaHuoneistotunnus "777"}

                {:huoneistonumero       "111"
                 :jakokirjain           "a"
                 :porras                "b"
                 :pysyvaHuoneistotunnus "555"}])))))

  (facts "Merge details vs. personal owner"
    (let [{app-id :id} (create-local-app pena
                                         :propertyId sipoo-property-id
                                         :address "Merger Mile"
                                         :operation "laajentaminen")
          doc-id       (->> (mongo/by-id :applications app-id)
                            :documents
                            (util/find-first #(some-> % :schema-info :op))
                            :id)
          owner        #(->> (mongo/by-id :applications app-id)
                             :documents
                             (util/find-by-id doc-id)
                             :data :rakennuksenOmistajat :0
                             util/strip-blanks
                             tools/unwrapped)
          not-filled   (just {:modified pos? :source "krysp"})]
      (fact "Company owner"
        (local-command pena :merge-details-from-krysp :id app-id
                       :documentId doc-id
                       :buildingId "199887766E"
                       :overwrite true) => ok?
        (provided
          ;; Provided is not strictly necessary here, but this way we can be sure that the
          ;; next provided should work, too.
          (bld-rdr/building-xml anything anything anything)
          => (sxml/parse-string (slurp "resources/krysp/dev/building.xml") "utf8"))
        (let [{:keys [henkilo yritys _selected]} (owner)]
          _selected => "yritys"
          (:henkilotiedot henkilo)
          => (contains {:etunimi not-filled :sukunimi not-filled :hetu not-filled})
          (:liikeJaYhteisoTunnus yritys) => "1234567-1"
          (:osoite yritys) => {:katu                 "Testikatu 1 A 9242"
                               :maa                  "FIN"
                               :postinumero          "00380"
                               :postitoimipaikannimi "HELSINKI"}
          (:osoite henkilo) => (just {:katu                 not-filled
                                      :maa                  "FIN"
                                      :postinumero          not-filled
                                      :postitoimipaikannimi not-filled})))
      (fact "Person owner"
        (local-command pena :merge-details-from-krysp :id app-id
                       :documentId doc-id
                       :buildingId "1234567892"
                       :overwrite true) => ok?
        (provided
          ;; Provided is not strictly necessary here, but this way we can be sure that the
          ;; next provided should work, too.
          (bld-rdr/building-xml anything anything anything)
          => (sxml/parse-string (slurp "dev-resources/krysp/building-2.1.2.xml") "utf8"))
        (let [{:keys [henkilo yritys _selected]} (owner)]
          _selected => "henkilo"
          (:henkilotiedot henkilo)
          => (contains {:etunimi not-filled :sukunimi not-filled :hetu not-filled})
          (:liikeJaYhteisoTunnus yritys) => not-filled
          (:osoite yritys) => (just {:katu                 not-filled
                                     :maa                  "FIN"
                                     :postinumero          not-filled
                                     :postitoimipaikannimi not-filled})
          (:osoite henkilo) => (just {:katu                 not-filled
                                      :maa                  "FIN"
                                      :postinumero          not-filled
                                      :postitoimipaikannimi not-filled})))
      (fact "Building does have a person owner"
        (-> (slurp "dev-resources/krysp/building-2.1.2.xml")
            (sxml/parse-string "utf8")
            (bld-rdr/->rakennuksen-tiedot-by-id "1234567892"
                                                {:include-personal-owner-info? true})
            :rakennuksenOmistajat :0)
        => (contains {:_selected "henkilo"
                      :henkilo   (contains {:henkilotiedot (contains {:etunimi  "Antero"
                                                                      :sukunimi "Testaaja"})
                                            :osoite        {:katu                 "Krysp-testin tie 1"
                                                            :maa                  "FIN"
                                                            :postinumero          "06500"
                                                            :postitoimipaikannimi "PORVOO"}})})))))
