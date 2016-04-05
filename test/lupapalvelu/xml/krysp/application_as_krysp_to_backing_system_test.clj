(ns lupapalvelu.xml.krysp.application-as-krysp-to-backing-system-test
  (:require [lupapalvelu.xml.krysp.application-as-krysp-to-backing-system :refer :all]
            [lupapalvelu.document.model :as model]
            [lupapalvelu.document.schemas :as schemas]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]))

(testable-privates lupapalvelu.xml.krysp.application-as-krysp-to-backing-system remove-unsupported-attachments remove-non-approved-designers)

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
                                                     :id "LP-123456"}))

(facts "Designer documents that have not been approved are removes"
  (let [documents (map #(model/new-document (schemas/get-schema 1 %) 123) ["paasuunnittelija" "suunnittelija" "hakija-r"])
        approved-docs (map #(assoc-in % [:meta :_approved] {:value "approved"}) documents)
        application {:documents documents}]

    (fact "Approved have not been removed"
      (let [filtered (:documents (remove-non-approved-designers {:documents approved-docs}))]
        (count filtered) => 3
        (get-in (first filtered) [:schema-info :name]) => "paasuunnittelija"
        (get-in (second filtered) [:schema-info :name]) => "suunnittelija"
        (get-in (last filtered) [:schema-info :name]) => "hakija-r"))

    (fact "Non-approved have been removed"
      (let [filtered (:documents (remove-non-approved-designers {:documents documents}))]
        (count filtered) => 1
        (get-in (first filtered) [:schema-info :name]) => "hakija-r"))))
