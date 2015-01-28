(ns lupapalvelu.kopiolaitos_itest
  (:require [midje.sweet :refer :all]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.factlet :refer :all]
            [lupapalvelu.kopiolaitos :refer :all]))

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
          (count (filter :forPrinting attachments)) => 2)))))
