(ns lupapalvelu.financial-itest
  (:require [lupapalvelu.domain :as domain]
            [lupapalvelu.factlet :refer :all]
            [lupapalvelu.itest-system :as test-system]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.minimal-util :as min-util]
            [midje.sweet  :refer :all]))

(def financial-authority (min-util/apikey-for "financial"))

(with-test-system (-> {:fixture "minimal"}
                      test-system/with-remote-components)
  (facts "Financial authority invitations"
    (let [application-id (create-app-id sonja)
          application    (query-application sonja application-id)
          {doc-id :id}   (domain/get-document-by-name application "hankkeen-kuvaus")]

      (fact "Financial authority don't see any applications yet"
        (count (get-in (datatables financial-authority :applications-search) [:data :applications])) => 0)

      (fact "Varke funding is enabled"
        (command sonja :update-doc :id application-id :doc doc-id :updates [["rahoitus" true]]) => ok?)

      (fact "Financial authority get invitation"
        (last-email)
        (command sonja :invite-financial-handler :id application-id :documentId doc-id :path ["rahoitus"]) => ok?
        (get-in (last-email) [:body :plain]) => (contains "sähköpostiosoitteella financial@ara.fi"))

      (fact "Financial authority has rights to application"
        (let [fetched-application (query-application sonja application-id)
              financial-auth      (second (:auth fetched-application))]
          (:username financial-auth) => "financial"
          (:role financial-auth) => "financialAuthority"))

      (fact "Financial authority can now see the application"
        (count (get-in (datatables financial-authority :applications-search) [:data :applications])) => 1)

      (fact "Financial authority can be removed from application"
        (command sonja :remove-financial-handler-invitation :id application-id) => ok?
        (count (:auth (query-application sonja application-id))) => 1))

    (fact "Financial authority can't see the application after Varke funding is removed"
      (count (get-in (datatables financial-authority :applications-search) [:data :applications])) => 0))

  (facts "Financial authority can query map layers"
    (query financial-authority :map-config :lang "fi")
    => (just {:ok true :base-layers truthy}))

  (facts "Admin can create financila authority"
    (fact "Financial authority is added"
      (command admin :create-financial-handler :email "massi.mies@mail.com" :role "financialAuthority") => ok?)))
