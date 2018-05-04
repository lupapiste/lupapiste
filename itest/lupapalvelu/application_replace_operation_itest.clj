(ns lupapalvelu.application-replace-operation-itest
  (:require [lupapalvelu.application-replace-operation :as replace-operation]
            [lupapalvelu.attachment :as att]
            [lupapalvelu.itest-util :refer :all]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]))

(apply-remote-minimal)

(facts "Replacing operations"
  (let [app (create-application pena :operation "kerrostalo-rivitalo" :propertyId tampere-property-id)
        app-id (:id app)
        op-id (-> app :primaryOperation :id)
        org (query admin :organization-by-id :organizationId (:organization app))]

    (fact "Pena adds operation"
      (command pena :add-operation :id app-id :operation "varasto-tms"))

    (fact "Pena replaces primary operation"
      (command pena :replace-operation :id app-id :opId op-id :operation "asuinrakennus") => ok?)

    (let [updated-app (query-application pena app-id)
          replaced-op (replace-operation/get-operation-by-key updated-app :name "asuinrakennus")
          secondary-op-id (-> updated-app :secondaryOperations (first) :id)]

      (fact "Application has new primaryOperation"
        (-> updated-app :primaryOperation :name) => "asuinrakennus"
        (-> updated-app :secondaryOperations (count)) => 1)

      (fact "Application has correct attachment templates"
        (map :type (att/get-attachments-by-operation updated-app (:id replaced-op)))
        => (just [{:type-group "paapiirustus", :type-id "julkisivupiirustus"}
                  {:type-group "paapiirustus", :type-id "leikkauspiirustus"}
                  {:type-group "paapiirustus", :type-id "pohjapiirustus"}
                  {:type-group "suunnitelmat", :type-id "piha_tai_istutussuunnitelma"}]
                  :in-any-order))

      (fact "Application has new primaryOperation"
        (-> updated-app :primaryOperation :name) => "asuinrakennus")

      (fact "Pena replaces the secondary operation"
        (command pena :replace-operation :id app-id :opId secondary-op-id :operation "auto-katos")))

    (let [updated-app (query-application pena app-id)
          replaced-secondary-op (-> updated-app :secondaryOperations (first) :id)]

      (fact "There is correct secondary operation"
        (-> updated-app :secondaryOperations (first) :name) => "auto-katos"
        (-> updated-app :secondaryOperations (count)) => 1)

      (fact "Autokatos has no attachment templates"
        (att/get-attachments-by-operation updated-app (:id replaced-secondary-op))
        => empty?))))

(facts "Adding attachments and replacing operations"
  (let [app    (create-application pena :operation "asuinrakennus" :propertyId tampere-property-id)
        app-id (:id app)
        op-id  (-> app :primaryOperation :id)]

    (command pena :add-operation :id app-id :operation "varasto-tms")
    (upload-attachment-to-all-placeholders pena app)

    (let [updated-app (query-application pena app-id)
          attachments (:attachments updated-app)
          secondary-op-id (-> updated-app :secondaryOperations (first) :id)
          _ (command pena :replace-operation :id app-id :opId op-id :operation "kerrostalo-rivitalo")
          _ (command pena :replace-operation :id app-id :opId secondary-op-id :operation "auto-katos")
          updated-attachments (:attachments (query-application pena app-id))
          attachment-has-version? (fn [att] (->> att :versions (not-empty)))
          not-empty-att-count (fn [atts] (->> atts
                                              (map attachment-has-version?)
                                              (remove nil?)
                                              (count)))]
      (fact "Uploaded attachment count is positive"
        (> (not-empty-att-count attachments) 0) => true)

      (fact "Adding attachments and replacing operation does not remove uploaded attachments"
        (not-empty-att-count attachments) => (not-empty-att-count updated-attachments)))))
