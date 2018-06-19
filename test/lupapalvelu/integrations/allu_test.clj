(ns lupapalvelu.integrations.allu-test
  "Unit tests for lupapalvelu.integrations.allu. No side-effects except validation exceptions."
  (:require [midje.sweet :refer [facts fact =>]]
            [midje.util :refer [testable-privates]]
            [clojure.test.check :refer [quick-check]]
            [clojure.test.check.generators :as gen]
            [com.gfredericks.test.chuck.properties :refer [for-all]]
            [com.gfredericks.test.chuck.generators :refer [string-from-regex]]
            [schema.core :as sc :refer [defschema]]
            [lupapalvelu.document.canonical-common :refer [ya-operation-type-to-schema-name-key]]
            [lupapalvelu.integrations.geojson-2008-schemas :refer [GeoJSON-2008]]

            [sade.core :refer [def-]]
            [sade.schemas :refer [ApplicationId]]
            [sade.schema-generators :as sg]
            [lupapalvelu.test-util :refer [passing-quick-check]]

            [lupapalvelu.integrations.allu :as allu :refer [ValidPlacementApplication PlacementContract]]))

(testable-privates lupapalvelu.integrations.allu application->allu-placement-contract)

;;;; Refutation Utilities
;;;; ===================================================================================================================

(defschema TypedAddress
  {:katu                 {:value sc/Str}
   :postinumero          {:value sc/Str}
   :postitoimipaikannimi {:value sc/Str}
   :maa                  {:value sc/Str}})

(defschema TypedContactInfo
  {:puhelin {:value sc/Str}
   :email   {:value sc/Str}})

(defschema TypedPersonDoc
  {:henkilotiedot {:etunimi  {:value sc/Str}
                   :sukunimi {:value sc/Str}
                   :hetu     {:value sc/Str}}
   :osoite        TypedAddress
   :yhteystiedot  TypedContactInfo})

(defschema TypedCompanyDoc
  {:yritysnimi           {:value sc/Str}
   :liikeJaYhteisoTunnus {:value sc/Str}
   :osoite               TypedAddress
   :yhteyshenkilo        {:henkilotiedot {:etunimi  {:value sc/Str}
                                          :sukunimi {:value sc/Str}}
                          :yhteystiedot  TypedContactInfo}})

(defschema TypedCustomerDoc
  {:schema-info {:subtype (sc/enum :hakija :maksaja)}
   :data        {:_selected {:value (sc/enum "henkilo" "yritys")}
                 :henkilo   TypedPersonDoc
                 :yritys    TypedCompanyDoc}})

(defschema TypedApplicantDoc
  (assoc-in TypedCustomerDoc [:schema-info :subtype] (sc/eq :hakija)))

(defschema TypedPaymentInfo
  {:verkkolaskuTunnus {:value sc/Str}
   :ovtTunnus         {:value sc/Str}
   :valittajaTunnus   {:value sc/Str}})

(defschema TypedPayeeDoc
  (-> TypedCustomerDoc
      (assoc-in [:schema-info :subtype] (sc/eq :maksaja))
      (assoc-in [:data :laskuviite] {:value sc/Str})
      (assoc-in [:data :yritys :verkkolaskutustieto] TypedPaymentInfo)))

(defschema TypedDescriptionDoc
  {:schema-info {:subtype (sc/eq :hankkeen-kuvaus)}
   :data        {:kayttotarkoitus {:value sc/Str}}})

(defschema TypedPlacementApplication
  {:id               sc/Str
   :permitSubtype    sc/Str
   :organization     sc/Str
   :propertyId       sc/Str
   :municipality     sc/Str
   :address          sc/Str
   :primaryOperation {:name (apply sc/enum (keys ya-operation-type-to-schema-name-key))}
   :documents        [(sc/one TypedApplicantDoc "applicant")
                      (sc/one TypedDescriptionDoc "description")
                      (sc/one TypedPayeeDoc "payee")]
   :location-wgs84   [(sc/one sc/Num "longitude") (sc/one sc/Num "latitude")]
   :drawings         [{:geometry-wgs84 GeoJSON-2008}]})

(def- organizations (string-from-regex #"\d{3}-(R|YA|YMP)"))

(def- invalid-placement-application? (comp not nil? (partial sc/check ValidPlacementApplication)))

;;;; Actual Tests
;;;; ===================================================================================================================

(facts "allu-application?"
  (fact "Use ALLU integration for Helsinki YA sijoituslupa and sijoitussopimus."
    (allu/allu-application? {:organization "091-YA", :permitSubtype "sijoituslupa"}) => true
    (allu/allu-application? {:organization "091-YA", :permitSubtype "sijoitussopimus"}) => true)

  (fact "Do not use ALLU integration for anything else."
    (quick-check 10
                 (for-all [organization organizations
                           permitSubtype gen/string-alphanumeric
                           :when (not (and (= organization "091-YA")
                                           (or (= permitSubtype "sijoituslupa")
                                               (= permitSubtype "sijoitussopimus"))))]
                   (not (allu/allu-application? {:organization  organization, :permitSubtype permitSubtype}))))
    => passing-quick-check))

(facts "application->allu-placement-contract"
  (fact "Valid applications produce valid inputs for ALLU."
    (quick-check 10
                 (for-all [application (sg/generator ValidPlacementApplication)]
                   (nil? (sc/check PlacementContract (application->allu-placement-contract application)))))
    => passing-quick-check)

  (fact "Invalid applications get rejected."
    (quick-check 10
                 (for-all [application (sg/generator TypedPlacementApplication)
                           :when (invalid-placement-application? application)]
                   (try
                     (application->allu-placement-contract application)
                     false
                     (catch Exception _ true))))
    => passing-quick-check))
