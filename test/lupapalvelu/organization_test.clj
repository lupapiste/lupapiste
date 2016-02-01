(ns lupapalvelu.organization-test
  (:require [midje.sweet :refer :all]
            [lupapalvelu.organization :refer :all]))

(facts
    (let [organization {:operations-attachments {:kikka [["type-group-1" "type-id-123"]]}}
       valid-op     {:name "kikka"}
       invalid-op   {:name "kukka"}]
   (get-organization-attachments-for-operation organization valid-op) => [{:type-group :type-group-1 :type-id :type-id-123}]
   (get-organization-attachments-for-operation organization invalid-op) => []))
