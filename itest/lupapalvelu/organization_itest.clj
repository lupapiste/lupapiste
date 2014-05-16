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
  (let [organization         (first (:organizations (query admin :organizations)))
        organization-id      (:id organization)
        resp                 (command admin :update-organization
                               :organizationScope (:scope organization)
                               :inforequestEnabled (not (-> organization :scope :inforequest-enabled))
                               :applicationEnabled (not (-> organization :scope :new-application-enabled))
                               :openInforequestEnabled (not (-> organization :scope :open-inforequest))
                               :openInforequestEmail "someone@localhost")
        updated-organization (query admin :organization-by-id :organizationId organization-id)]
    (:inforequest-enabled updated-organization) => (not (-> organization :scope :inforequest-enabled))
    (:new-application-enabled updated-organization) => (not (-> organization :scope :new-application-enabled))
    (:open-inforequest updated-organization) => (not (-> organization :scope :open-inforequest))
    (:open-inforequest-email updated-organization) => "someone@localhost"))

(fact* "Tampere-ya sees (only) YA operations and attachments (LUPA-917, LUPA-1006)"
  (let [resp (query tampere-ya :organization-by-user) => ok?
        tre  (:organization resp)]
    (keys (:operations-attachments tre)) => [:YA]
    (-> tre :operations-attachments :YA) => truthy
    (keys (:attachmentTypes resp)) => [:YA]
    (-> resp :attachmentTypes :YA) => truthy))
