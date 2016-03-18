(ns lupapalvelu.appeal-test
  (:require [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [sade.core :refer [now]]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.appeal :refer :all]))

(fact "Appeal input validator"
  (let [valid {:targetId       (mongo/create-id)
               :type           "appeal"
               :appellant      "Me"
               :made           (now)
               :text           "Some information"}
        invalid {:targetId       (mongo/create-id)
                 :type           "foobar"
                 :appellant      "Me"
                 :made           "18.3.2016"
                 :text           "Some information"}]

    (input-validator {:data valid}) => nil
    (keys (input-validator {:data invalid})) => (just [:type :made])))
