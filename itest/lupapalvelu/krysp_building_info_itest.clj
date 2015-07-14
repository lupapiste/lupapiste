(ns lupapalvelu.krysp-building-info-itest
  (:require [midje.sweet :refer :all]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.factlet :refer :all]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.test-util :refer [doc-result doc-check]]
            [lupapalvelu.mongo :as mongo]
            [sade.strings :as ss]))


(fact* "Merging building information from KRYSP does not overwrite muutostyolaji or energiatehokkuusluvunYksikko"
  (let [application-id (create-app-id pena :propertyId sipoo-property-id :operation "kayttotark-muutos")
        app (query-application pena application-id)
        rakmuu-doc (domain/get-document-by-name app "rakennuksen-muuttaminen")
        resp2 (command pena :update-doc :id application-id :doc (:id rakmuu-doc) :collection "documents" :updates [["muutostyolaji" "muut muutosty\u00f6t"]])
        updated-app (query-application pena application-id)
        building-info (command pena :get-building-info-from-wfs :id application-id) => ok?
        doc-before (domain/get-document-by-name updated-app "rakennuksen-muuttaminen")
        building-id (:buildingId (first (:data building-info)))

        resp3 (command pena :merge-details-from-krysp :id application-id :documentId (:id doc-before) :collection "documents" :buildingId building-id :path "buildingId" :overwrite true) => ok?
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
    (get-in doc-after [:data :valtakunnallinenNumero :value]) => "481123123R"
    (count (get-in doc-after [:data :huoneistot])) => 21
    (get-in doc-after [:data :kaytto :kayttotarkoitus :value]) => "039 muut asuinkerrostalot"
    (get-in doc-after [:data :kaytto :kayttotarkoitus :source]) => "krysp"

    (fact "KRYSP data is stored in source value field"
      (get-in doc-before [:data :mitat :tilavuus :sourceValue]) => nil
      (get-in doc-after [:data :mitat :tilavuus :sourceValue]) => "8240"
      (get-in doc-after [:data :mitat :tilavuus :value]) => "8240")

    (fact "Repeating data has been cleared before new merge (overwrite true)"
      (let [building-id-2 (:buildingId (second (:data building-info)))
            _ (command pena :merge-details-from-krysp :id application-id :documentId (:id doc-before) :collection "documents" :buildingId building-id-2 :path "buildingId" :overwrite true) => ok?
            merged-app (query-application pena application-id)
            doc-after-2 (domain/get-document-by-name merged-app "rakennuksen-muuttaminen")]

        (fact "count of huoneistot is correct"
          (count (get-in doc-after [:data :huoneistot])) => 21
          (count (get-in doc-after-2 [:data :huoneistot])) => 2)
        (fact "count of rakennuksenOmistajat is correct"
          (count (get-in doc-after [:data :rakennuksenOmistajat])) => 2
          (count (get-in doc-after-2 [:data :rakennuksenOmistajat])) => 4)))

    ; Merge back building-id data for next test
    (command pena :merge-details-from-krysp :id application-id :documentId (:id doc-before) :collection "documents" :buildingId building-id :path "buildingId" :overwrite true) => ok?

    (fact "Merging ID only"
      (let [building-id-2 (:buildingId (second (:data building-info)))
            _ (command pena :merge-details-from-krysp :id application-id :documentId (:id doc-before) :collection "documents" :buildingId building-id-2 :path "buildingId" :overwrite false) => ok?
            merged-app (query-application pena application-id)
            doc-after-3 (domain/get-document-by-name merged-app "rakennuksen-muuttaminen")]

        (fact "kayttotarkoitus remains the same"
          (get-in doc-after-3 [:data :kaytto :kayttotarkoitus :value]) => "039 muut asuinkerrostalot")

        (fact "ID has changed"
          (get-in doc-after-3 [:data :valtakunnallinenNumero :value]) => "478123123J"
          (get-in doc-after-3 [:data :rakennusnro :value]) => "002"
          (get-in doc-after-3 [:data :manuaalinen_rakennusnro :value]) => ss/blank?)))

    (fact "When selecting 'other' from building selector, old KRYSP source data is not present (data is set to defaults)"
      (let [_ (command pena :merge-details-from-krysp :id application-id :documentId (:id doc-before) :collection "documents" :buildingId "other" :path "buildingId" :overwrite false) => ok?
            merged-app (query-application pena application-id)
            doc-after-4 (domain/get-document-by-name merged-app "rakennuksen-muuttaminen")]

        (fact "buildingId is 'other'"
          (get-in doc-after-4 [:data :buildingId :value]) => "other")

        (fact "source and sourceValues are not present in updated document"
          (get-in doc-after [:data :mitat :tilavuus :sourceValue]) => "8240"
          (get-in doc-after [:data :mitat :tilavuus :source]) => "krysp"

          (get-in doc-after-4 [:data :mitat :tilavuus :sourceValue]) => nil
          (get-in doc-after-4 [:data :mitat :tilavuus :source]) => nil)

        (fact "values have been set to schema defaults"
          (get-in doc-after-4 [:data :rakennusnro :value]) => ss/blank?
          (get-in doc-after-4 [:data :manuaalinen_rakennusnro :value]) => ss/blank?
          (get-in doc-after-4 [:data :valtakunnallinenNumero :value]) => ss/blank?
          (count (get-in doc-after-4 [:data :huoneistot])) => 1
          (get-in doc-after-4 [:data :kaytto :kayttotarkoitus :value]) => nil ; default value for select is null
          (get-in doc-after-4 [:data :kaytto :kayttotarkoitus :source]) => nil)))))

(fact* "Merging building information from KRYSP succeeds even if document schema does not have place for all the info"
  (let [application-id (create-app-id pena :propertyId sipoo-property-id :operation "purkaminen")
        app (query-application pena application-id)
        doc (domain/get-document-by-name app "purkaminen")
        building-info (command pena :get-building-info-from-wfs :id application-id) => ok?
        building-id (:buildingId (first (:data building-info)))
        resp (command pena :merge-details-from-krysp :id application-id :documentId (:id doc) :collection "documents" :buildingId building-id :path "buildingId" :overwrite true) => ok?
        merged-app (query-application pena application-id)
        doc-after (domain/get-document-by-name merged-app "purkaminen")]
    (get-in doc-after [:data :mitat :kokonaisala :source]) => "krysp"
    (get-in doc-after [:data :kaytto :kayttotarkoitus :source]) => "krysp"))