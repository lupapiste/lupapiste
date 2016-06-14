(ns lupapalvelu.organization-test
  (:require [midje.sweet :refer :all]
            [lupapalvelu.organization :refer :all]))

(facts
 (let [organization {:operations-attachments {:kikka [["type-group-1" "type-id-123"]]}
                     :krysp {:good {:url "http://example.com" :ftpUser "user" :version "1.0.0"}
                             :bad1 {:ftpUser "user" :version "1.0.0"}
                             :bad2 {:version "1.0.0"}
                             :bad3 {}}}
       valid-op     {:name "kikka"}
       invalid-op   {:name "kukka"}]
   (get-organization-attachments-for-operation organization valid-op) => [["type-group-1" "type-id-123"]]
   (get-organization-attachments-for-operation organization invalid-op) => nil
   (fact "KRYSP integration defined"
         (krysp-integration? organization :good) => true?
         (krysp-integration? organization :bad1) => falsey
         (krysp-integration? organization :bad2) => falsey
         (krysp-integration? organization :bad3) => falsey)))
