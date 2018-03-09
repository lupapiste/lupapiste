(ns lupapalvelu.pate-integration-itest
  (:require [midje.sweet :refer :all]
            [lupapalvelu.factlet :refer :all]
            [lupapalvelu.itest-util :refer :all]))

(apply-remote-minimal)

(command admin :set-organization-boolean-path
         :organizationId "753-R"
         :path "pate-enabled"
         :value true)

(defn check-count-and-last-state [id msg-count state]
  (fact
    (count (integration-messages id)) => msg-count
    (get-in (last (integration-messages id)) [:data :toState :name]) => state))

(facts* "R"
  (let [app-id (create-app-id pena :propertyId sipoo-property-id)
        cancel-app (create-app-id pena :propertyId sipoo-property-id)]
    (fact "canceled sends message"                          ; TODO SHOULD IT SENT IF IT WAS DRAFT?
      (command pena :cancel-application :id cancel-app :lang "fi" :text "prkl") => ok?
      (check-count-and-last-state cancel-app 1 "canceled"))
    (fact "no message for draft"
      (count (integration-messages app-id)) => 0)
    (fact "open application doesnt generate message"        ; :open is skipped state
      (comment-application pena app-id true) => ok?
      (count (integration-messages app-id)) => 0)
    (fact "submitting generates message"
      (command pena :submit-application :id app-id) => ok?
      (check-count-and-last-state app-id 1 "submitted"))
    (fact "moving to draft from submitted do trigger state change"
      (command sonja :return-to-draft :id app-id :text "comment-text" :lang :fi) => ok?
      (check-count-and-last-state app-id 2 "draft"))
    (fact "submitting generates message again"
      (command pena :submit-application :id app-id) => ok?
      (check-count-and-last-state app-id 3 "submitted"))
    (command sonja :update-app-bulletin-op-description :id app-id :description "otsikko julkipanoon") => ok?
    (fact "sent generates message"
      (command sonja :approve-application :id app-id :lang "fi") => ok?
      (check-count-and-last-state app-id 4 "sent"))
    (fact "verdictGiven generates message"
      (give-verdict sonja app-id :verdictId "321-2016-PATE")
      (check-count-and-last-state app-id 5 "verdictGiven"))
    #_(fact "canceled generates message"
      (command pena :cancel-application :id app-id :lang "fi" :text "prkl") => ok?
      (check-count-and-last-state app-id 5 "canceled"))))
