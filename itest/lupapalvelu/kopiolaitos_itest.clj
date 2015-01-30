(ns lupapalvelu.kopiolaitos_itest
  (:require [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.factlet :refer :all]
            [lupapalvelu.kopiolaitos :refer :all]))

(testable-privates lupapalvelu.kopiolaitos get-kopiolaitos-html-table)

(fact "Sonja sets default values for organization kopiolaitos info"
  (command sipoo :set-kopiolaitos-info
    :kopiolaitosEmail "sipoo@example.com"
    :kopiolaitosOrdererAddress "Testikatu 2, 12345 Sipoo"
    :kopiolaitosOrdererPhone "0501231234"
    :kopiolaitosOrdererEmail "tilaaja@example.com") => ok?)

(facts "Kopiolaitos order"
  (let [app-id (create-app-id pena)
        app (query-application pena app-id)
        _ (upload-attachment-to-all-placeholders pena app)
        _ (command pena :submit-application :id app-id)
        app (query-application pena app-id)]
    (fact* "Sonja sets two attachments to be verdict attachments"
      (command
        sonja
        :set-attachments-as-verdict-attachment
        :id app-id
        :attachmentIds (map :id (take 2 (:attachments app)))
        :isVerdictAttachment true) => ok?
      (let [app (query-application sonja app-id) => map?
            attachments (get-in app [:attachments]) => sequential?]
        (fact "Two attachments have forPrinting flags set to true"
          (count (filter :forPrinting attachments)) => 2)))

    (command sonja :check-for-verdict :id app-id) => ok?

    (fact "Order prints"
      (let [app (query-application sonja app-id)
            attachments-with-amount (map #(assoc % :amount "2") (:attachments app))
            order-info {:ordererOrganization "Testi"
                        :ordererAddress      "Testikuja 2"
                        :ordererPhone        "12345"
                        :ordererEmail        "test@example.com"
                        :contentsTable       (get-kopiolaitos-html-table "fi" attachments-with-amount)
                        :applicantName       "Hakija"
                        :kuntalupatunnus     "2013-1"
                        :propertyId          "32131654"
                        :lupapisteId         (:id app)
                        :address             (:title app)}]

        (fact "without attachments order fails"
          (command sonja :order-verdict-attachment-prints
            :id app-id
            :lang "fi"
            :attachmentsWithAmounts nil
            :orderInfo order-info) => fail?)
        (fact "attachments without needed attachment key (amount) results in fail"
          (command sonja :order-verdict-attachment-prints
            :id app-id
            :lang "fi"
            :attachmentsWithAmounts (:attachments app)
            :orderInfo order-info) => (and fail? (contains {:required-keys ["forPrinting" "amount"]})))

        (fact "fail when all attachments for command don't have forPriting set to 'true'"
          (command sonja :order-verdict-attachment-prints
            :id app-id
            :lang "fi"
            :attachmentsWithAmounts attachments-with-amount
            :orderInfo order-info) => fail?)
        (fact "success as organization has email set"
          (command sonja :order-verdict-attachment-prints
            :id app-id
            :lang "fi"
            :attachmentsWithAmounts (filter :forPrinting attachments-with-amount)
            :orderInfo order-info) => ok?)

        (fact "unsetting organization kopiolaitos-email leads to failure in kopiolaitos order"
          (command sipoo :set-kopiolaitos-info
            :kopiolaitosEmail ""
            :kopiolaitosOrdererAddress ""
            :kopiolaitosOrdererPhone ""
            :kopiolaitosOrdererEmail "") => ok?
          (command sonja :order-verdict-attachment-prints
            :id app-id
            :lang "fi"
            :attachmentsWithAmounts (filter :forPrinting attachments-with-amount)
            :orderInfo order-info) => (partial expected-failure? "no-kopiolaitos-email-defined"))))))
