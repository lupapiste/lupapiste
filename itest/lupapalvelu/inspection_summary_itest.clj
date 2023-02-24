(ns lupapalvelu.inspection-summary-itest
  (:require [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.pate-legacy-itest-util :refer :all]
            [midje.sweet :refer :all]
            [sade.util :as util]))

(def ^:private sipoo-R-org-id "753-R")
(def ^:private jarvenpaa-R-org-id "186-R")

(apply-remote-minimal)

(facts "inspection-summary-templates"
  (fact "Feature not enabled in Sipoo"
    (query sipoo :organization-inspection-summary-settings :organizationId sipoo-R-org-id) => unauthorized?
    (command sipoo :create-inspection-summary-template :organizationId sipoo-R-org-id
             :name "foo" :templateText "bar\nbar2\n\n bar3") => unauthorized?)
  (fact "Create template ok"
    (command jarvenpaa :create-inspection-summary-template :organizationId jarvenpaa-R-org-id
             :name "foo" :templateText "bar\nbar2\n\n bar3") => ok?)
  (fact "Created template included in the query result"
    (let [resp (query jarvenpaa :organization-inspection-summary-settings :organizationId jarvenpaa-R-org-id)
          data (-> resp :templates)
          id1 (-> data first :id)]
      resp => ok?
      (count data) => 1
      (first data) => (contains {:name "foo" :id id1 :items ["bar" "bar2" "bar3"]})
      (command jarvenpaa :modify-inspection-summary-template :organizationId jarvenpaa-R-org-id
               :templateId "invalid-id" :name "foo2" :templateText "bar325146")
      => (partial expected-failure? :error.not-found)
      (command jarvenpaa :modify-inspection-summary-template :organizationId jarvenpaa-R-org-id
               :templateId id1 :name "foo2" :templateText "bar325146") => ok?
      (fact "Map template into operations as default"
        (command jarvenpaa :set-inspection-summary-template-for-operation :organizationId jarvenpaa-R-org-id
                 :operationId :kerrostalo-rivitalo :templateId id1) => ok?
        (command jarvenpaa :set-inspection-summary-template-for-operation :organizationId jarvenpaa-R-org-id
                 :operationId :pientalo :templateId id1) => ok?
        (-> (query jarvenpaa :organization-inspection-summary-settings :organizationId jarvenpaa-R-org-id)
            :operations-templates) => (contains [{:kerrostalo-rivitalo id1}
                                                 {:pientalo id1}] :in-any-order)
        (fact "...and unmapping"
          (command jarvenpaa :set-inspection-summary-template-for-operation :organizationId jarvenpaa-R-org-id
                   :operationId :pientalo :templateId "_unset") => ok?
          (-> (query jarvenpaa :organization-inspection-summary-settings :organizationId jarvenpaa-R-org-id)
              :operations-templates) =not=> (contains {:pientalo id1})))
      (fact "Deleting the template"
        (command jarvenpaa :delete-inspection-summary-template :organizationId jarvenpaa-R-org-id
                 :templateId id1) => ok?
        (-> (query jarvenpaa :organization-inspection-summary-settings :organizationId jarvenpaa-R-org-id)
            :operations-templates) => empty?)
      (fact "Can not be associated with P permits"
        (command jarvenpaa :set-inspection-summary-template-for-operation :organizationId jarvenpaa-R-org-id
                 :operationId :poikkeamis :templateId id1)
        => (partial expected-failure? :error.inspection-summary.invalid-permit-type)))))

(facts "Inspection summaries in applications"
  (fact "Create test data"
    (let [_           (create-and-submit-application pena :propertyId sipoo-property-id :address "Peltomaankatu 9")
          {id1 :id :as app-jarvenpaa}
          (create-and-submit-application pena :propertyId jarvenpaa-property-id :address "Jarvikatu 29")
          _           (command jarvenpaa :create-inspection-summary-template :organizationId jarvenpaa-R-org-id
                               :name "foo" :templateText "bar\nbar2\n\n bar3")
          templates   (:templates (query jarvenpaa :organization-inspection-summary-settings
                                         :organizationId jarvenpaa-R-org-id))
          template-id (-> templates first :id)]
      (fact "auth admin binds template to operation"
        (command jarvenpaa :set-inspection-summary-template-for-operation :organizationId jarvenpaa-R-org-id
                 :operationId :kerrostalo-rivitalo :templateId template-id) => ok?)

      (fact "Feature not enabled in Sipoo"
        (get-in (query sipoo :organization-by-user :organizationId sipoo-R-org-id)
                [:organization :inspection-summaries-enabled]) => false)
      (fact "Default template created upon verdict given"
        (give-legacy-verdict raktark-jarvenpaa (:id app-jarvenpaa))
        (let [{summaries :summaries}                              (query raktark-jarvenpaa :inspection-summaries-for-application
                                                                         :id (:id app-jarvenpaa))
              {summaryId1 :id summaryName :name targets :targets} (first summaries)
              test-target                                         (second targets)
              test-target-attachment-pred                         (fn [{{:keys [id type]} :target}] (and (= type "inspection-summary-item")
                                                                                                         (= id (:id test-target))))]
          summaryName => "foo"
          (fact "Applicant can see the default summary but not create new ones or modify them"
            (query pena :inspection-summaries-for-application :id id1) => ok?
            (command pena :create-inspection-summary
                     :id id1
                     :templateId template-id
                     :operationId :kerrostalo-rivitalo) => unauthorized?
            (command pena :remove-target-from-inspection-summary :id id1
                     :summaryId summaryId1
                     :targetId (:id test-target)) => unauthorized?
            (command pena :edit-inspection-summary-target :id id1
                     :summaryId summaryId1
                     :targetId (:id test-target)
                     :targetName "Hello") => unauthorized?)
          (doseq [{:keys [name apikey]} [{:name "pena" :apikey pena}
                                         {:name "raktark-jarvenpaa" :apikey raktark-jarvenpaa}]]
            (fact {:midje/description (str name " can add attachment for target")}
              (let [file-id (upload-file-and-bind apikey id1 {:type   {:type-group "katselmukset_ja_tarkastukset"
                                                                       :type-id    "tarkastusasiakirja"}
                                                              :target {:type "inspection-summary-item"
                                                                       :id   (:id test-target)}})]
                (fact "Queries have uploaded attachment"
                  (let [summary-attachments     (->> (query apikey :inspection-summaries-for-application :id id1)
                                                     :summaries
                                                     (util/find-by-id summaryId1)
                                                     :targets
                                                     (util/find-by-id (:id test-target))
                                                     :attachments)
                        application-attachments (->> (query-application apikey id1)
                                                     :attachments
                                                     (filter test-target-attachment-pred))]
                    (count summary-attachments) => (count application-attachments)
                    (-> (last summary-attachments) :latestVersion :originalFileId) => file-id)))))
          (fact "Authority can remove a single target"
            (fact "initially two attachments"
              (->> (query raktark-jarvenpaa :inspection-summaries-for-application :id id1)
                   :summaries
                   (util/find-by-id summaryId1)
                   :targets
                   second
                   :attachments
                   count) => 2)
            (command raktark-jarvenpaa :remove-target-from-inspection-summary
                     :id id1 :summaryId summaryId1 :targetId (:id test-target)) => ok?
            (let [targets                 (->> (query pena :inspection-summaries-for-application :id id1)
                                               :summaries (util/find-by-id summaryId1) :targets)
                  application-attachments (->> (query-application raktark-jarvenpaa id1)
                                               :attachments
                                               (filter test-target-attachment-pred))]
              (map :target-name targets) => (just ["bar" "bar3"])
              (map :id targets) =not=> (contains (:id test-target))
              (fact "Attachments are removed from application"
                (count application-attachments) => 0)))
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
                       :targetName "bar1") => (partial expected-failure?
                                                       :error.edit-inspection-summary-target.not-found)))))))

  (facts "Marking inspection target DONE"
    (let [{id1 :id}                         (create-and-submit-application pena :propertyId jarvenpaa-property-id :address "Jarvikatu 29")
          _                                 (give-legacy-verdict raktark-jarvenpaa id1)
          {summaries :summaries}            (query pena :inspection-summaries-for-application :id id1)
          {summaryId1 :id targets :targets} (first summaries)
          test-target                       (second targets)
          test-target-attachment-pred       (fn [{{:keys [id type]} :target}] (and (= type "inspection-summary-item")
                                                                                   (= id (:id test-target))))]
      (fact "Three targets in above default template" (count targets) => 3)
      (fact "Pena sees summaries" (count summaries) => 1)
      (upload-file-and-bind pena id1 {:type   {:type-group "katselmukset_ja_tarkastukset"
                                               :type-id    "tarkastusasiakirja"}
                                      :target {:type "inspection-summary-item"
                                               :id   (:id test-target)}})
      (fact "Pena marks target as done"
        (command pena :set-target-status :id id1
                 :summaryId summaryId1
                 :targetId (:id test-target)
                 :isFinished true)
        => ok?)
      (let [summary-target     (->> (query pena :inspection-summaries-for-application :id id1)
                                    :summaries
                                    (util/find-by-id summaryId1)
                                    :targets
                                    (util/find-by-id (:id test-target)))
            summary-attachment (->> (query-application pena id1)
                                    :attachments
                                    (util/find-first test-target-attachment-pred))]
        (-> summary-attachment :target :id) => (:id test-target)
        (count (:attachments summary-target)) => 1
        (fact "Target is marked as finished" (:finished summary-target) => true)
        (fact "Raktar can't edit when finished"
          (command raktark-jarvenpaa :edit-inspection-summary-target
                   :id id1 :summaryId summaryId1 :targetId (:id test-target)
                   :targetName "Nope") => (partial expected-failure? :error.inspection-summary-target.finished))
        (fact "Can't upload to finished"
          (upload-file-and-bind pena id1 {:type   {:type-group "katselmukset_ja_tarkastukset"
                                                   :type-id    "tarkastusasiakirja"}
                                          :target {:type "inspection-summary-item"
                                                   :id   (:id test-target)}}
                                :fails :error.inspection-summary-target.finished))
        (fact "Pena can edit attachment even when the inspection summary is finished"
          (command pena :set-attachment-meta :id id1 :attachmentId (:id summary-attachment)
                   :meta {:contents "Tarkastuskohde"}) => ok?)
        (fact "Pena cannot delete attachment"
          (command pena :delete-attachment :id id1 :attachmentId (:id summary-attachment))
          => (partial expected-failure? :error.inspection-summary-target.finished))
        (fact "Authority can edit attachment"
          (command raktark-jarvenpaa :set-attachment-meta :id id1
                   :attachmentId (:id summary-attachment) :meta {:contents "Tarkastuskohde"})
          => ok?)
        (fact "Authority cannot delete attachment either"
          (command raktark-jarvenpaa :delete-attachment :id id1
                   :attachmentId (:id summary-attachment))
          => (partial expected-failure? :error.inspection-summary-target.finished))
        (fact "Authority can unmark target"
          (command raktark-jarvenpaa :set-target-status :id id1
                   :summaryId summaryId1
                   :targetId (:id test-target)
                   :isFinished false) => ok?)
        (fact "Authority can remark target"
          (command raktark-jarvenpaa :set-target-status :id id1
                   :summaryId summaryId1
                   :targetId (:id test-target)
                   :isFinished true) => ok?)
        (fact "Applicant can unmark target"
          (command pena :set-target-status :id id1
                   :summaryId summaryId1
                   :targetId (:id test-target)
                   :isFinished false) => ok?))))

  (facts "Setting inspection date"
    (let [{app-id :id}                      (create-and-submit-application pena :propertyId jarvenpaa-property-id :address "Jarvikatu 27")
          _                                 (give-legacy-verdict raktark-jarvenpaa app-id)
          {summaries :summaries}            (query pena :inspection-summaries-for-application :id app-id)
          {summary-id :id targets :targets} (first summaries)
          target                            (first targets)]
      (fact "Inspection date can be set"
        (command pena :set-inspection-date :id app-id :summaryId summary-id :targetId (:id target)
                 :date 1500000000001)
        => ok?
        (fact "Authority can also set the date"
          (command raktark-jarvenpaa :set-inspection-date :id app-id :summaryId summary-id :targetId (:id target)
                   :date 1500000000000)
          => ok?)
        (let [modified-target (->> (query pena :inspection-summaries-for-application :id app-id)
                                   :summaries
                                   (util/find-by-id summary-id)
                                   :targets
                                   (util/find-by-id (:id target)))]
          (:inspection-date modified-target) => 1500000000000))
      (fact "Inspection date should be valid with value nil"
        (command pena :set-inspection-date :id app-id :summaryId summary-id :targetId (:id target) :date nil) => ok?)
      (fact "Inspection date can't be set to finished target"
        (command pena :set-target-status :id app-id
                 :summaryId summary-id
                 :targetId (:id target)
                 :isFinished true) => ok?
        (command pena :set-inspection-date :id app-id :summaryId summary-id
                 :targetId (:id target) :date 1500000000000)
        => (partial expected-failure? :error.inspection-summary-target.finished))
      (fact "Inspection date can't be set to locked summary"
        (command raktark-jarvenpaa :toggle-inspection-summary-locking :id app-id
                 :summaryId summary-id :isLocked true)
        => ok?
        (command pena :set-inspection-date :id app-id
                 :summaryId summary-id :targetId (:id target) :date 1500000000000)
        => (partial expected-failure? :error.inspection-summary.locked)))))

(facts "Inspection summary locking"
  (let [app                        (create-and-submit-application pena :propertyId sipoo-property-id
                                                                  :address "Peltomaankatu 9")
        _                          (command sipoo :set-organization-inspection-summaries
                                            :organizationId sipoo-R-org-id :enabled true)
        {template-id :id}          (command sipoo :create-inspection-summary-template
                                            :organizationId sipoo-R-org-id :name "foo"
                                            :templateText "bar\nbar2\nbar3")
        _                          (command sipoo :set-inspection-summary-template-for-operation
                                            :organizationId sipoo-R-org-id
                                            :operationId :kerrostalo-rivitalo :templateId template-id)
        _                          (give-legacy-verdict sonja (:id app))
        {summaries :summaries}     (query sonja :inspection-summaries-for-application :id (:id app))
        {attachments :attachments} (query sonja :attachments :id (:id app))
        {summary-id :id}           (first summaries)
        {target-id :id}            (command sonja :add-target-to-inspection-summary :id (:id app)
                                            :summaryId summary-id
                                            :targetName "some name")]

    (fact "One summary exists"
      (count summaries) => 1)

    (fact "Inspection summary is not locked by default"
      (-> (query sonja :inspection-summaries-for-application :id (:id app)) :summaries first :locked) => falsey)

    (fact "Applicant can see the default summary but not lock it"
      (query pena :inspection-summaries-for-application :id (:id app)) => ok?
      (command pena :toggle-inspection-summary-locking :id (:id app)
               :summaryId summary-id :isLocked true) => unauthorized?)

    (fact "Authority can toggle inspection summary locking"
      (let [{job :job :as resp} (command sonja :toggle-inspection-summary-locking :id (:id app)
                                         :summaryId summary-id :isLocked true)]
        resp => ok?

        (fact "Job id is returned"
          (:id job) => truthy)

        (when-not (= "done" (:status job))
          (poll-job pena :bind-attachments-job (:id job) (:version job) 25) => ok?)))

    (fact "Inspection summary is now locked"
      (-> (query sonja :inspection-summaries-for-application :id (:id app)) :summaries first :locked) => true)

    (facts "Attachment is generated"
      (let [{updated-attachments :attachments} (query sonja :attachments :id (:id app))
            inspection-summary-attachment      (util/find-first (comp #{summary-id} :id :source) updated-attachments)
            latest-version                     (:latestVersion inspection-summary-attachment)]

        (fact "One attachment added"
          (-> updated-attachments count (= (inc (count attachments)))))

        (fact "inspection summary attachment exists"
          inspection-summary-attachment => not-empty)

        (fact "Attachment has a version"
          latest-version => not-empty)

        (let [{:keys [headers]} (raw sonja "download-attachment" :file-id (:fileId latest-version) :id (:id app))]
          (fact "content type"
            (get headers "Content-Type") => "application/pdf"))))

    (facts "Restricted actions for locked inspection summary"
      (fact "delete-inspection-summary"
        (command sonja :delete-inspection-summary :id (:id app) :summaryId summary-id) => (partial expected-failure? :error.inspection-summary.locked))

      (fact "add-target-to-inspection-summary"
        (command sonja :add-target-to-inspection-summary :id (:id app) :summaryId summary-id :targetName "some name") => (partial expected-failure? :error.inspection-summary.locked))

      (fact "edit-inspection-summary-target"
        (command sonja :edit-inspection-summary-target :id (:id app) :summaryId summary-id :targetId target-id :targetName "some name") => (partial expected-failure? :error.inspection-summary.locked))

      (fact "remove-target-from-inspection-summary"
        (command sonja :remove-target-from-inspection-summary :id (:id app) :summaryId summary-id :targetId target-id) => (partial expected-failure? :error.inspection-summary.locked))

      (fact "set-target-status: finished"
        (command pena :set-target-status :id (:id app)
                 :summaryId summary-id
                 :targetId target-id
                 :isFinished true)
        => (partial expected-failure? :error.inspection-summary.locked)))

    (fact "Unlock inspection summary"
      (command sonja :toggle-inspection-summary-locking :id (:id app) :summaryId summary-id :isLocked false) => ok?
      (-> (query sonja :inspection-summaries-for-application :id (:id app)) :summaries first :locked) => false)

    (facts "Summary attachment is removed"
      (let [{updated-attachments :attachments} (query sonja :attachments :id (:id app))
            inspection-summary-attachment      (util/find-first (comp #{summary-id} :id :source) updated-attachments)]

        (fact "attachment count is same before locking"
          (-> updated-attachments count (= (count attachments))))

        (fact "inspection summary attachment does not exist"
          inspection-summary-attachment => nil)))

    (fact "Setting status is allowed for unlocked inspection summary"
      (command pena :set-target-status :id (:id app)
               :summaryId summary-id
               :targetId target-id
               :isFinished true) => ok?)

    (fact "Trying to lock unexsisting inspection summary"
      (command sonja :toggle-inspection-summary-locking :id (:id app) :summaryId "not-found-id" :isLocked true) => (partial expected-failure? :error.toggle-inspection-summary-locking.not-found))))
