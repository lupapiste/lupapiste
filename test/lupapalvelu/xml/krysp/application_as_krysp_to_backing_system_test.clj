(ns lupapalvelu.xml.krysp.application-as-krysp-to-backing-system-test
  (:require [lupapalvelu.xml.krysp.application-as-krysp-to-backing-system :refer :all]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]))

(testable-privates lupapalvelu.xml.krysp.application-as-krysp-to-backing-system remove-unsupported-attachments)

(facts "about lupapalvelu.xml.krysp.application-as-krysp-to-backing-system-test"
       (fact "Remove function removes unsupported attachments"
             (let [application {:attachments [{:type {:type-group :muut
                                                      :type-id :paatos}}
                                              {:type {:type-group :muut
                                                      :type-id :muu}}
                                              {:type {:type-group :paapiirustus
                                                      :type-id :asemapiirros}}]
                                :id "LP-123456"}]
               (remove-unsupported-attachments application) => {:attachments [{:type {:type-group :muut
                                                                                      :type-id :muu}}
                                                                              {:type {:type-group :paapiirustus
                                                                                      :type-id :asemapiirros}}]
                                                                :id "LP-123456"})))
