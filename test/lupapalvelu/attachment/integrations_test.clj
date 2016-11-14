(ns lupapalvelu.attachment.integrations-test
  (:require [schema.core :as sc]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [lupapalvelu.itest-util :refer [expected-failure?]]
            [lupapalvelu.integrations-api]))

(testable-privates lupapalvelu.integrations-api has-unsent-attachments)

(facts "Unsent attachments"
       (let [no-unsent (partial expected-failure? "error.no-unsent-attachments")]
         (fact "No attachments"
               (has-unsent-attachments {}) => no-unsent
               (has-unsent-attachments {:application {:attachments []}}) => no-unsent)
         (fact "No files"
               (has-unsent-attachments {:application {:attachments [{:versions []}]}}) => no-unsent)
         (fact "File has been sent"
               (has-unsent-attachments {:application {:attachments [{:versions [{:created 1}]
                                                        :sent 2}]}}) => no-unsent)
         (fact "Type is verdict"
               (has-unsent-attachments {:application {:attachments [{:versions [{:created 1}]
                                                        :target {:type "verdict"}}]}}) => no-unsent)
         (fact "Type is statement"
               (has-unsent-attachments {:application {:attachments [{:versions [{:created 1}]
                                                                     :target {:type "statement"}}]}})
               => no-unsent)
         (fact "Has unsent"
               (has-unsent-attachments {:application {:attachments [{:versions [{:created 1}]
                                                                     :target {:type "foo"}}]}}) => nil
               (has-unsent-attachments {:application {:attachments [{:versions [{:created 2}]
                                                                     :sent 1
                                                                     :target {:type "foo"}}]}}) => nil)))
