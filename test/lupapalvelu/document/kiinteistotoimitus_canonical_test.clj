(ns lupapalvelu.document.kiinteistotoimitus-canonical-test
  (:require [clojure.test :refer :all]
            [lupapalvelu.document.kiinteistotoimitus-canonical :as kt-canonical]
            [sade.date :as date]))

(def from-property-id "753-123-456-7")

(def rasitetoimitus-doc
  {:schema-info {:name "rasitetoimitus"}
   :data {:rasitetoimitus {:kayttooikeuslaji "Vesihuolto"
                           :paattymispvm "2025-12-31"}}})

(def rasitetoimitus-doc-no-date
  {:schema-info {:name "rasitetoimitus"}
   :data {:rasitetoimitus {:kayttooikeuslaji "Liikenne"
                           :paattymispvm nil}}})

(deftest rt-details-with-lisakohdekiinteisto
  (testing "When lisäkohdekiinteistö exists, kayttaja should be the lisäkohdekiinteistö id"
    (let [to-properties     [{:property-id "753-999-888-1"}
                             {:property-id "753-999-888-2"}]
          result            (kt-canonical/rt-details [rasitetoimitus-doc] from-property-id to-properties)
          kayttooikeustieto (get-in result [:details :kayttooikeustieto])]

      (is (= :Rasitetoimitus (:name result)))
      (is (= 2 (count kayttooikeustieto)))

      ;; First kayttooikeus should have first lisakohdekiinteisto as kayttaja
      (is (= "753-999-888-1" (get-in (first kayttooikeustieto) [:KayttoOikeus :kayttaja])))
      (is (= from-property-id (get-in (first kayttooikeustieto) [:KayttoOikeus :antaja])))
      (is (= "Vesihuolto" (get-in (first kayttooikeustieto) [:KayttoOikeus :kayttooikeuslaji])))

      ;; Second kayttooikeus should have second lisakohdekiinteisto as kayttaja
      (is (= "753-999-888-2" (get-in (second kayttooikeustieto) [:KayttoOikeus :kayttaja])))
      (is (= from-property-id (get-in (second kayttooikeustieto) [:KayttoOikeus :antaja])))

      ;; Both should have the same date
      (is (= (date/xml-date "2025-12-31")
             (get-in (first kayttooikeustieto) [:KayttoOikeus :paattymispvm])))
      (is (= true (get-in (first kayttooikeustieto) [:KayttoOikeus :valiaikainenKytkin]))))))

(deftest rt-details-without-lisakohdekiinteisto
  (testing "When lisäkohdekiinteistö does not exist, kayttaja should be same as antaja (from-property-id)"
    (let [to-properties     []
          result            (kt-canonical/rt-details [rasitetoimitus-doc-no-date] from-property-id to-properties)
          kayttooikeustieto (get-in result [:details :kayttooikeustieto])]

      (is (= :Rasitetoimitus (:name result)))
      (is (= 1 (count kayttooikeustieto)))

      ;; kayttaja should be the same as antaja when no lisakohdekiinteisto
      (is (= from-property-id (get-in (first kayttooikeustieto) [:KayttoOikeus :kayttaja])))
      (is (= from-property-id (get-in (first kayttooikeustieto) [:KayttoOikeus :antaja])))
      (is (= "Liikenne" (get-in (first kayttooikeustieto) [:KayttoOikeus :kayttooikeuslaji])))

      ;; No date means valiaikainenKytkin is false
      (is (nil? (get-in (first kayttooikeustieto) [:KayttoOikeus :paattymispvm])))
      (is (= false (get-in (first kayttooikeustieto) [:KayttoOikeus :valiaikainenKytkin]))))))

(deftest rt-details-with-nil-property-ids
  (testing "When to-properties contains items but no property-id (nil values), kayttaja should be same as antaja"
    (let [to-properties     [{:mat "some-maaraala-tunnus"}
                             {:something-else "value"}]
          result            (kt-canonical/rt-details [rasitetoimitus-doc] from-property-id to-properties)
          kayttooikeustieto (get-in result [:details :kayttooikeustieto])]

      (is (= :Rasitetoimitus (:name result)))
      (is (= 1 (count kayttooikeustieto)))

      ;; kayttaja should be the same as antaja when property-ids are nil
      (is (= from-property-id (get-in (first kayttooikeustieto) [:KayttoOikeus :kayttaja])))
      (is (= from-property-id (get-in (first kayttooikeustieto) [:KayttoOikeus :antaja]))))))

(deftest rt-details-with-mixed-property-ids
  (testing "When to-properties contains some property-ids and some nils, only property-ids should be used"
    (let [to-properties     [{:property-id "753-999-888-1"}
                             {:mat "some-maaraala"}
                             {:property-id "753-999-888-2"}]
          result            (kt-canonical/rt-details [rasitetoimitus-doc] from-property-id to-properties)
          kayttooikeustieto (get-in result [:details :kayttooikeustieto])]

      (is (= 2 (count kayttooikeustieto)))
      (is (= "753-999-888-1" (get-in (first kayttooikeustieto) [:KayttoOikeus :kayttaja])))
      (is (= "753-999-888-2" (get-in (second kayttooikeustieto) [:KayttoOikeus :kayttaja]))))))

(deftest rt-details-multiple-rasitetoimitus-docs
  (testing "Multiple rasitetoimitus documents should create multiple kayttooikeus entries per property"
    (let [to-properties     [{:property-id "753-999-888-1"}]
          result            (kt-canonical/rt-details [rasitetoimitus-doc rasitetoimitus-doc-no-date]
                                                     from-property-id
                                                     to-properties)
          kayttooikeustieto (get-in result [:details :kayttooikeustieto])]

      ;; 2 docs * 1 property = 2 kayttooikeus entries
      (is (= 2 (count kayttooikeustieto)))

      ;; Both should have the same property as kayttaja
      (is (= "753-999-888-1" (get-in (first kayttooikeustieto) [:KayttoOikeus :kayttaja])))
      (is (= "753-999-888-1" (get-in (second kayttooikeustieto) [:KayttoOikeus :kayttaja])))

      ;; Different kayttooikeuslaji
      (is (= "Vesihuolto" (get-in (first kayttooikeustieto) [:KayttoOikeus :kayttooikeuslaji])))
      (is (= "Liikenne" (get-in (second kayttooikeustieto) [:KayttoOikeus :kayttooikeuslaji]))))))

(deftest rt-details-no-rasitetoimitus-docs
  (testing "When there are no rasitetoimitus documents, should return nil"
    (let [to-properties [{:property-id "753-999-888-1"}]
          result        (kt-canonical/rt-details [] from-property-id to-properties)]

      (is (nil? result)))))

(deftest rt-details-single-lisakohdekiinteisto
  (testing "With a single lisäkohdekiinteistö, kayttaja should be that property"
    (let [to-properties     [{:property-id "753-555-666-8"}]
          result            (kt-canonical/rt-details [rasitetoimitus-doc] from-property-id to-properties)
          kayttooikeustieto (get-in result [:details :kayttooikeustieto])]

      (is (= 1 (count kayttooikeustieto)))
      (is (= "753-555-666-8" (get-in (first kayttooikeustieto) [:KayttoOikeus :kayttaja])))
      (is (= from-property-id (get-in (first kayttooikeustieto) [:KayttoOikeus :antaja]))))))
