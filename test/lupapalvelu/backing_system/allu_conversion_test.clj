(ns lupapalvelu.backing-system.allu-conversion-test
  (:require [schema.core :as sc]
            [sade.env :as env]
            [sade.schema-generators :as sg]

            [midje.sweet :refer [facts fact =>]]
            [clojure.test.check :refer [quick-check]]
            [com.gfredericks.test.chuck.properties :refer [for-all]]
            [lupapalvelu.test-util :refer [passing-quick-check]]

            [lupapalvelu.backing-system.allu.schemas :refer [ValidPlacementApplication PlacementContract]]
            [lupapalvelu.backing-system.allu.conversion :refer [application->allu-placement-contract]]))

(env/with-feature-value :allu true
  (sc/with-fn-validation
    (facts "application->allu-placement-contract"
      (fact "Valid applications produce valid inputs for ALLU."
        (quick-check 10
                     (for-all [application (sg/generator ValidPlacementApplication)]
                       (nil? (sc/check PlacementContract
                                       (application->allu-placement-contract (sg/generate sc/Bool) application)))))
        => passing-quick-check))))
