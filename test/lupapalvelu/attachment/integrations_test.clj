(ns lupapalvelu.attachment.integrations-test
  (:require [lupapalvelu.attachment :as att]
            [lupapalvelu.integrations-api]
            [midje.sweet :refer :all]))

(facts "Transmittable"
  (let [conversation  {:type     {:type-group "muut" :type-id "keskustelu"}
                       :versions [{:fileId "123"}]}]

    (fact "Target is a conversation - KRYSP"
      (att/transmittable-to-krysp? conversation) => true)))
