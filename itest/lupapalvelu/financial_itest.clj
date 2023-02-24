(ns lupapalvelu.financial-itest
  (:require [midje.sweet  :refer :all]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.factlet :refer :all]
            [lupapalvelu.domain :as domain]))

(apply-remote-minimal)

(def financial-authority (apikey-for "financial"))

(facts "Financial authority invitations"
  (let [application-id (create-app-id sonja)
        application (query-application sonja application-id)
        {doc-id :id} (domain/get-document-by-name application "hankkeen-kuvaus")]

    (fact "Financial authority don't see any applications yet"
      (count (get-in (datatables financial-authority :applications-search) [:data :applications])) => 0)

    (fact "ARA funding is enabled"
      (command sonja :update-doc :id application-id :doc doc-id :updates [["rahoitus" true]]) => ok?)

    (fact "Financial authority get invitation"
      (last-email)
      (command sonja :invite-financial-handler :id application-id :documentId doc-id :path ["rahoitus"]) => ok?
      (get-in (last-email) [:body :plain]) => (contains "s\u00e4hk\u00f6postiosoitteella financial@ara.fi"))

    (fact "Financial authority has rights to application"
      (let [fetched-application (query-application sonja application-id)
            financial-auth (second (:auth fetched-application))]
        (:username financial-auth) => "financial"
        (:role financial-auth) => "financialAuthority"))

    (fact "Financial authority can now see the application"
      (count (get-in (datatables financial-authority :applications-search) [:data :applications])) => 1)

    (fact "Financial authority can be removed from application"
      (command sonja :remove-financial-handler-invitation :id application-id) => ok?
      (count (:auth (query-application sonja application-id))) => 1))

    (fact "Financial authority can't see the application after ARA funding is removed"
      (count (get-in (datatables financial-authority :applications-search) [:data :applications])) => 0))


(facts "Admin can create financila authority"
  (fact "Financial authority is added"
    (command admin :create-financial-handler :email "massi.mies@mail.com" :role "financialAuthority") => ok?))
