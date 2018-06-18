(ns lupapalvelu.integrations.allu-test
  "Unit tests for lupapalvelu.integrations.allu. No side-effects except validation exceptions."
  (:require [midje.sweet :refer [facts fact =>]]
            [midje.util :refer [testable-privates]]
            [clojure.test.check :refer [quick-check]]
            [clojure.test.check.generators :as gen]
            [com.gfredericks.test.chuck.properties :refer [for-all]]
            [com.gfredericks.test.chuck.generators :refer [string-from-regex]]
            [schema.core :as sc :refer [defschema]]

            [sade.core :refer [def-]]
            [sade.schemas :refer [ApplicationId]]
            [sade.schema-generators :as sg]
            [lupapalvelu.test-util :refer [passing-quick-check]]

            [lupapalvelu.integrations.allu :as allu]
            [lupapalvelu.integrations.allu-schemas :refer [TypedPlacementApplication ValidPlacementApplication
                                                           PlacementContract]]))

(testable-privates lupapalvelu.integrations.allu application->allu-placement-contract)

;;;; # Refutation Utilities

(def- organizations (string-from-regex #"\d{3}-(R|YA|YMP)"))

(def- invalid-placement-application? (comp not nil? (partial sc/check ValidPlacementApplication)))

;;;; # Actual Tests

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
