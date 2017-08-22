(ns lupapalvelu.financial-itest
  (:require [midje.sweet  :refer :all]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.factlet :refer :all]
            [lupapalvelu.domain :as domain]))

(apply-remote-minimal)

(facts "Financial authority invitations"
  (let [application-id (create-app-id sonja)
        application (query-application sonja application-id)
        {doc-id :id} (domain/get-document-by-name application "hankkeen-kuvaus-rakennuslupa")]

    (fact "Financial authority is added"
      (command admin :create-financial-handler :email "massi.mies@mail.com" :role "financialAuthority") => ok?)

    (fact "ARA funding is enabled"
      (command sonja :update-doc :id application-id :doc doc-id :updates [["rahoitus" true]]) => ok?)

    (fact "Financial authority get invitation"
      (last-email)
      (command sonja :invite-financial-handler :id application-id :documentId doc-id :path ["rahoitus"]) => ok?
      (get-in (last-email) [:body :plain]) => (contains "sähköpostiosoitteella massi.mies@mail.com"))

    (fact "Financial authority has rights to application")))

