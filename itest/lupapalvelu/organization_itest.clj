(ns lupapalvelu.organization-itest
  (:require [midje.sweet :refer :all]
            [lupapalvelu.organization :refer :all]
            [lupapalvelu.core :refer [ok?]]
            [lupapalvelu.factlet :refer [fact* facts*]]
            [lupapalvelu.itest-util :refer [admin tampere-ya pena query command apply-remote-minimal]]
            [sade.util :refer [fn->]]))

(apply-remote-minimal)

(fact* "Operation details query works"
 (let [resp  (query pena "organization-details" :municipality "753" :operation "asuinrakennus" :lang "fi") => ok?]
   (count (:attachmentsForOp resp )) => pos?
   (count (:links resp)) => pos?))

(fact* "query /organizations"
  (let [resp (query admin :organizations) => ok?]
    (count (:organizations resp)) => pos?))

(fact "update organization"
  (let [organization   (first (:organizations (query admin :organizations)))
        organization-id      (:id organization)
        resp                 (command admin :update-organization
                               :organizationId organization-id
                               :inforequestEnabled (not (:inforequest-enabled organization))
                               :applicationEnabled (not (:new-application-enabled organization))
                               :openInforequestEnabled (not (:open-inforequest organization))
                               :openInforequestEmail "someone@localhost")
        updated-organization (query admin :organization-by-id :organizationId organization-id)]
    (:inforequest-enabled updated-organization) => (not (:inforequest-enabled organization))
    (:new-application-enabled updated-organization) => (not (:new-application-enabled organization))
    (:open-inforequest updated-organization) => (not (:open-inforequest organization))
    (:open-inforequest-email updated-organization) => "someone@localhost"))

(fact* "Tampere-ya sees (only) YA operations and attachments (LUPA-917, LUPA-1006)"
  (let [resp (query tampere-ya :organization-by-user) => ok?
        tre  (:organization resp)]
    (keys (:operations-attachments tre)) => [:YA]
    (-> tre :operations-attachments :YA) => truthy
    (keys (:attachmentTypes resp)) => [:YA]
    (-> resp :attachmentTypes :YA) => truthy))
