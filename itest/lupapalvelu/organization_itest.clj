(ns lupapalvelu.organization-itest
  (:require [midje.sweet :refer :all]
            [lupapalvelu.organization :refer :all]
            [lupapalvelu.core :refer [ok?]]
            [lupapalvelu.itest-util :refer [admin pena query]]
            [sade.util :refer [fn->]]))

(facts "Organization itests"
  (fact "Operation details query works"
   (let [resp  (query pena "organization-details" :municipality "753" :operation "asuinrakennus" :lang "fi")]
     resp => ok?
     resp => (fn-> :attachmentsForOp count (> 0))
     resp => (fn-> :links count (> 0))))

  (fact "query /organizations"
        (let [resp            (query admin :organizations)]
          resp => ok?))

  (fact "update organization"
        (let [organization   (first (:organizations (query admin :organizations)))
              organization-id      (:id organization)
              resp                 (command admin :update-organization :organizationId organization-id :inforequestEnabled (not (:inforequest-enabled organization)) :applicationEnabled (not (:new-application-enabled organization)))
              updated-organization (query admin :organization-by-id :organizationId organization-id)]
        (= (:inforequest-enabled updated-organization) (not (:inforequest-enabled organization))) => true
        (= (:new-application-enabled updated-organization) (not (:new-application-enabled organization))) => true)))
