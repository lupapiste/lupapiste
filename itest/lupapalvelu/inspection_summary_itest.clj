(ns lupapalvelu.inspection-summary-itest
  (:require [midje.sweet :refer :all]
            [clojure.java.io :as io]
            [clojure.set :refer [difference]]
            [lupapalvelu.factlet :refer [fact* facts*]]
            [lupapalvelu.itest-util :refer :all]
            [sade.util :as util]))

(apply-remote-minimal)

(facts "inspection-summary-templates"
  (fact "Feature not enabled in Sipoo"
    (query sipoo :organization-inspection-summary-settings) => unauthorized?
    (command sipoo :create-inspection-summary-template :name "foo" :templateText "bar\nbar2\n\n bar3") => unauthorized?)
  (fact "Create template ok"
    (command jarvenpaa :create-inspection-summary-template :name "foo" :templateText "bar\nbar2\n\n bar3") => ok?)
  (fact "Created template included in the query result"
    (let [resp (query jarvenpaa :organization-inspection-summary-settings)
          data (-> resp :templates)
          id1 (-> data first :id)]
      resp => ok?
      (count data) => 1
      (first data) => (contains {:name "foo" :id id1 :items ["bar" "bar2" "bar3"]})
      (command jarvenpaa :modify-inspection-summary-template :templateId "invalid-id" :name "foo2" :templateText "bar325146") => (partial expected-failure? :error.not-found)
      (command jarvenpaa :modify-inspection-summary-template :templateId id1 :name "foo2" :templateText "bar325146") => ok?
      (fact "Map template into operations as default"
        (command jarvenpaa :set-inspection-summary-template-for-operation :operationId :kerrostalo-rivitalo :templateId id1) => ok?
        (command jarvenpaa :set-inspection-summary-template-for-operation :operationId :pientalo :templateId id1) => ok?
        (-> (query jarvenpaa :organization-inspection-summary-settings) :operations-templates) => (contains [{:kerrostalo-rivitalo id1}
                                                                                                             {:pientalo id1}] :in-any-order)
      (fact "...and unmapping"
        (command jarvenpaa :set-inspection-summary-template-for-operation :operationId :pientalo :templateId "_unset") => ok?
        (-> (query jarvenpaa :organization-inspection-summary-settings) :operations-templates) =not=> (contains {:pientalo id1})))
      (fact "Deleting the template"
        (command jarvenpaa :delete-inspection-summary-template :templateId id1) => ok?
        (-> (query jarvenpaa :organization-inspection-summary-settings) :operations-templates) => empty?))))

(facts "Inspection summaries in applications"
  (fact "Create test data"
    (let [app-sipoo     (create-and-submit-application pena :propertyId sipoo-property-id :address "Peltomaankatu 9")
          {id1 :id :as app-jarvenpaa}
                        (create-and-submit-application pena :propertyId jarvenpaa-property-id :address "Jarvikatu 29")
          _ (command jarvenpaa :create-inspection-summary-template :name "foo" :templateText "bar\nbar2\n\n bar3")
          templates (-> (query jarvenpaa :organization-inspection-summary-settings) :templates)]
      (fact "auth admin binds template to operation"
        (command jarvenpaa :set-inspection-summary-template-for-operation :operationId :kerrostalo-rivitalo :templateId (-> templates first :id)) => ok?)

      (fact "Feature not enabled in Sipoo"
        (query sipoo :inspection-summaries-for-application :id (:id app-sipoo)) => unauthorized?)
      (fact "Default template created upon verdict given"
        (give-verdict raktark-jarvenpaa (:id app-jarvenpaa) :verdictId "3323") => ok?
        (let [{summaries :summaries} (query raktark-jarvenpaa :inspection-summaries-for-application :id (:id app-jarvenpaa))
              {summaryId1 :id summaryName :name targets :targets
               :as default-summary} (first summaries)]
          summaryName => "foo"
          (fact "Applicant can see the default summary but not create new ones or modify them"
            (query pena :inspection-summaries-for-application :id id1) => ok?
            (command pena :create-inspection-summary :id id1) => unauthorized?
            (command pena :remove-target-from-inspection-summary :id id1) => unauthorized?
            (command pena :edit-inspection-summary-target :id id1) => unauthorized?)
          (fact "Applicant can add attachment for target"
            (let [summary-target (second targets)
                  file-id (upload-file-and-bind pena id1 {:type {:type-group "katselmukset_ja_tarkastukset"
                                                                 :type-id    "tarkastusasiakirja"}
                                                          :constructionTime true
                                                          :target {:type "inspection-summary-item"
                                                                   :id (:id summary-target)}})]
              (fact "Queries have uploaded attachment"
                (let [summary-attachments (->> (query pena :inspection-summaries-for-application :id id1)
                                               :summaries
                                               (util/find-by-id summaryId1)
                                               :targets
                                               (util/find-by-id (:id summary-target))
                                               :attachments)
                      application-attachments (->> (query-application pena id1)
                                                   :attachments
                                                   (filter (fn [{{:keys [id type]} :target}]
                                                             (and (= type "inspection-summary-item")
                                                                  (= id (:id summary-target))))))]
                  (count summary-attachments) => (count application-attachments)
                  (-> (first summary-attachments) :latestVersion :originalFileId) => file-id))))
          (fact "Authority can remove a single target"
            (command raktark-jarvenpaa :remove-target-from-inspection-summary
                                       :id id1 :summaryId summaryId1 :targetId (-> targets second :id)) => ok?
            (->> (query pena :inspection-summaries-for-application :id id1)
                 :summaries first :targets (map :target-name)) => (just ["bar" "bar3"]))
          (fact "Authority can add a single target"
            (command raktark-jarvenpaa :add-target-to-inspection-summary
                     :id id1 :summaryId summaryId1
                     :targetName "bar4") => ok?
            (->> (query pena :inspection-summaries-for-application :id id1)
                 :summaries first :targets (map :target-name)) => (just ["bar" "bar3" "bar4"]))
          (fact "Authority can rename targets"
            (command raktark-jarvenpaa :edit-inspection-summary-target
                     :id id1 :summaryId summaryId1 :targetId (-> targets first :id)
                     :targetName "bar1") => ok?
            (->> (query pena :inspection-summaries-for-application :id id1)
                 :summaries first :targets (map :target-name)) => (just ["bar1" "bar3" "bar4"])
            (fact "Removed target can obviously not be renamed"
              (command raktark-jarvenpaa :remove-target-from-inspection-summary
                       :id id1 :summaryId summaryId1
                       :targetId (-> targets first :id)) => ok?
              (command raktark-jarvenpaa :edit-inspection-summary-target
                       :id id1 :summaryId summaryId1 :targetId (-> targets first :id)
                       :targetName "bar1") => (partial expected-failure? :error.summary-target.edit.not-found))))))))