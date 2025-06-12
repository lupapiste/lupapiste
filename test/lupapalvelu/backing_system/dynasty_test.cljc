(ns lupapalvelu.backing-system.dynasty-test
  (:require [clojure.test :refer [deftest is]]
            [lupapalvelu.backing-system.schema :as schemas]
            [lupapalvelu.backing-system.dynasty]
            [schema.core :as sc]))

(deftest dynasty-valid-test
  (is (sc/validate schemas/IntegrationProperties
                   (schemas/get-props {:integration-type :dynasty}))))
