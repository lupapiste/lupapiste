(ns lupapalvelu.backing-system.allu.conversion-test
  (:require [schema.core :as sc]
            [sade.env :as env]
            [sade.schema-generators :as sg]

            [clojure.test :refer [use-fixtures]]
            [clojure.test.check :refer [quick-check]]
            [clojure.test.check.clojure-test :refer [defspec]]
            [com.gfredericks.test.chuck.properties :refer [for-all]]
            [lupapalvelu.test-util :refer [passing-quick-check]]

            [lupapalvelu.backing-system.allu.schemas :refer [ValidPlacementApplication PlacementContract
                                                             ValidShortTermRental ShortTermRental
                                                             ValidPromotion Promotion]]
            [lupapalvelu.backing-system.allu.conversion :refer [application->allu-placement-contract
                                                                application->allu-short-term-rental
                                                                application->allu-promotion]]))

(use-fixtures :once
              (fn [run-tests] (env/with-feature-value :allu true (run-tests)))
              (fn [run-tests] (sc/with-fn-validation (run-tests))))

(defspec application->allu-placement-contract-test 10
  (for-all [application (sg/generator ValidPlacementApplication)]
    (nil? (sc/check PlacementContract
                    (application->allu-placement-contract (sg/generate sc/Bool) application)))))

(defspec application->allu-short-term-rental-test 10
  (for-all [application (sg/generator ValidShortTermRental)]
    (nil? (sc/check ShortTermRental
                    (application->allu-short-term-rental (sg/generate sc/Bool) application)))))

(defspec application->allu-promotion-test 10
  (for-all [application (sg/generator ValidPromotion)]
    (nil? (sc/check Promotion
                    (application->allu-promotion (sg/generate sc/Bool) application)))))
