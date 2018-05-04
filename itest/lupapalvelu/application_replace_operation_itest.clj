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

      (fact "New operation has new id"
        (:id replaced-op) =not=> op-id)

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
        op-id  (-> app :primaryOperation :id)

        _ (command pena :add-operation :id app-id :operation "varasto-tms")
        _ (upload-attachment-to-all-placeholders pena app)

        updated-app     (query-application pena app-id)
        attachments     (:attachments updated-app)
        secondary-op-id (-> updated-app :secondaryOperations (first) :id)

        _ (command pena :replace-operation :id app-id :opId op-id :operation "kerrostalo-rivitalo")
        _ (command pena :replace-operation :id app-id :opId secondary-op-id :operation "auto-katos")

        updated-attachments     (:attachments (query-application pena app-id))

        attachment-has-version? (fn [att] (->> att :versions (not-empty)))
        not-empty-att-count (fn [atts] (->> atts
                                            (map attachment-has-version?)
                                            (remove nil?)
                                            (count)))]

    (fact "Originally no attachments"
      (not-empty-att-count (:attachments app)) => 0)

    (fact "Uploaded attachment count is positive"
      (> (not-empty-att-count attachments) 0) => true)

    (fact "Adding attachments and replacing operation does not remove uploaded attachments"
      (not-empty-att-count attachments) => (not-empty-att-count updated-attachments))))

(facts "Replacing operation keeps correct documents or updates them"
  (let [app       (create-application pena :operation "tontin-ajoliittyman-muutos" :propertyId tampere-property-id)
        doc-names (->> app :documents (map #(-> % :schema-info :name)) (set))
        app-id    (:id app)
        op-id     (-> app :primaryOperation :id)

        _                 (command pena :replace-operation :id app-id :opId op-id :operation "auto-katos")
        updated-app       (query-application pena app-id)
        new-op-id         (-> updated-app :primaryOperation :op-id)
        updated-doc-names (->> updated-app :documents (map #(-> % :schema-info :name)) (set))]


    (fact "originally documents are as they should"
      (doc-names "rakennusjatesuunnitelma") => nil?
      (doc-names "rakennuspaikka-ilman-ilmoitusta") => truthy
      (doc-names "rakennuspaikka") => nil?
      (doc-names "paasuunnittelija") = nil?)

    (fact "after replacing operation documents are as they should"
      (updated-doc-names "rakennusjatesuunnitelma") => truthy
      (updated-doc-names "rakennuspaikka-ilman-ilmoitusta") => nil?
      (updated-doc-names "rakennuspaikka") => truthy
      (updated-doc-names "paasuunnittelija") => truthy))

  (let [app       (create-application pena :operation "tontin-ajoliittyman-muutos" :propertyId tampere-property-id)
        doc-names (->> app :documents (map #(-> % :schema-info :name)) (set))
        app-id    (:id app)
        op-id     (-> app :primaryOperation :id)

        _                 (command pena :add-operation :id app-id :operation "auto-katos")
        updated-app       (query-application pena app-id)
        updated-doc-names (->> updated-app :documents (map #(-> % :schema-info :name)) (set))
        secondary-op-id   (->> updated-app :secondaryOperations (first) :id)

        _                   (command pena :replace-operation :id app-id :opId secondary-op-id :operation "varasto-tms")
        updated-app-2       (query-application pena app-id)
        new-op-id-2         (->> updated-app-2 :secondaryOperations (first) :id)
        updated-doc-names-2 (->> updated-app-2 :documents (map #(-> % :schema-info :name)) (set))]

    (fact "at first no rakennusjatesuunnitelma nor paasuunnittelija"
      (doc-names "rakennusjatesuunnitelma") => nil?
      (doc-names "paasuunnittelija") => nil?)

    (fact "at first rakennuspaikka-ilman-ilmoitusta"
      (doc-names "rakennuspaikka-ilman-ilmoitusta") => truthy)

    (fact "after adding second operation there is rakennusjatesuunnitelma and paasuunnittelija"
      (updated-doc-names "rakennusjatesuunnitelma") => truthy
      (updated-doc-names "paasuunnittelija") => truthy)

    (fact "after adding second operation there is no change in rakennuspaikka"
      (updated-doc-names "rakennuspaikka-ilman-ilmoitusta") => truthy
      (updated-doc-names "rakennuspaikka") => nil?)

    (fact "after replacing second operation there is new rakennuspaikka document"
      (updated-doc-names-2 "rakennuspaikka-ilman-ilmoitusta") => nil?
      (updated-doc-names-2 "rakennuspaikka") => truthy)

    (fact "other documents are as they should"
      (updated-doc-names-2 "rakennusjatesuunnitelma") => truthy)))
