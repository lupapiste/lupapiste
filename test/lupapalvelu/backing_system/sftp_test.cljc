(ns lupapalvelu.backing-system.sftp-test
  (:require [clojure.test :refer [deftest is]]
            [lupapalvelu.backing-system.schema :as schemas]
            [lupapalvelu.backing-system.sftp]
            [schema.core :as sc]))

(deftest sftp-valid-test
  (is (sc/validate schemas/IntegrationProperties
                   (schemas/get-props {:integration-type :sftp}))))
