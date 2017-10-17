 (ns lupapalvelu.application-bulletins-itest
   (:require [midje.sweet :refer :all]
             [lupapalvelu.itest-util :refer :all]
             [lupapalvelu.application-bulletins-itest-util :refer :all]
             [lupapalvelu.factlet :refer :all]
             [sade.util :as util]
             [sade.strings :as ss]
             [sade.core :refer [now]]))

(apply-remote-minimal)

(fact "bulletin-op-description"
  (let [r-app (create-and-submit-application sonja :operation "kerrostalo-rivitalo"
                                            :propertyId sipoo-property-id
                                            :x 406898.625 :y 6684125.375
                                            :address "Hitantine 108")]
    (command sonja :update-app-bulletin-op-description :id (:id r-app) :description "Kuvausteksti123") => ok?
    (-> (get-by-id :applications (:id r-app)) :body :data) => (contains  {:bulletinOpDescription "Kuvausteksti123"})))
