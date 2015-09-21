(ns lupapalvelu.organization-test
  (:require [midje.sweet :refer :all]
            [lupapalvelu.organization :refer :all]))

(facts
  (let [organization {:operations-attachments {:kikka ..stuff..}}
        valid-op     {:name "kikka"}
        invalid-op   {:name "kukka"}]
    (get-organization-attachments-for-operation organization valid-op) => ..stuff..
    (get-organization-attachments-for-operation organization invalid-op) => nil))
