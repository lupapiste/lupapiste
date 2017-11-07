 (ns lupapalvelu.application-bulletins-itest
   (:require [midje.sweet :refer :all]
             [lupapalvelu.itest-util :refer :all]
             [lupapalvelu.application-bulletins-itest-util :refer :all]
             [lupapalvelu.factlet :refer :all]
             [sade.util :as util]
             [sade.strings :as ss]
             [sade.core :refer [now]]))

(apply-remote-minimal)

(let [r-app (create-and-submit-application mikko :operation "kerrostalo-rivitalo"
                                          :propertyId sipoo-property-id
                                          :x 406898.625 :y 6684125.375
                                          :address "Hitantine 108")
      app-id (:id r-app)]
  (fact "bulletin-op-description"
    (command sonja :update-app-bulletin-op-description :id (:id r-app) :description "Kuvausteksti123") => ok?
    (-> (get-by-id :applications app-id) :body :data) => (contains  {:bulletinOpDescription "Kuvausteksti123"}))
  (fact "check for backend verdict"
    (command sonja :check-for-verdict :id app-id) => ok?
    (-> (query-application mikko app-id) :verdicts count) => pos?)
  (fact "Bulletin is created automatically"
    (let [bulletins (:data (datatables mikko :application-bulletins :municipality sonja-muni :searchText "" :state "" :page 1 :sort ""))]
      (count bulletins) => 1
      (-> bulletins first :bulletinState) => "verdictGiven")))
