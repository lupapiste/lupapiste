(ns lupapalvelu.tasks-itest
  (:require [lupapalvelu.document.model :as model]
            [lupapalvelu.document.persistence :as doc-persistence]
            [lupapalvelu.document.schemas :as schemas]
            [lupapalvelu.factlet :refer :all]
            [lupapalvelu.itest-util :refer :all]
            [midje.sweet :refer :all]
            [sade.strings :as ss]
            [sade.util :refer [fn->>] :as util]))

(apply-remote-minimal)

(defn- task-by-type [task-type task] (= (str "task-" task-type) (-> task :schema-info :name)))

(let [application (create-and-submit-application pena :propertyId sipoo-property-id)
      application-id (:id application)
      resp (command sonja :check-for-verdict :id application-id)
      application (query-application pena application-id)
      modified (:modified application)
      tasks (:tasks application)
      katselmukset (filter (partial task-by-type "katselmus") tasks)
      maaraykset (filter (partial task-by-type "lupamaarays") tasks)
      tyonjohtajat (filter (partial task-by-type "vaadittu-tyonjohtaja") tasks)]

  (fact "fixture has 2 verdicts and 9 tasks"
    resp => ok?
    (:verdictCount resp) => 2
    (:taskCount resp) => 9)

  (fact "id is set" (-> tasks first :id) => truthy)
  (fact "duedate is not set" (-> tasks first :duedate) => nil)
  (fact "source is verdict" (-> tasks first :source :type) => "verdict")
  (fact "not closed" (-> tasks first :closed) => nil)
  (fact "requires user action" (-> tasks first :state) => "requires_user_action")

  (fact "created timestamp is set" (-> tasks first :created) => truthy)

  (fact "Assignee is set"
    (-> tasks first :assignee :id) => pena-id
    (-> tasks first :assignee :firstName) => "Pena"
    (-> tasks first :assignee :lastName) => "Panaani")

    (fact "Applicant can see Tasks tab"
          (query pena :tasks-tab-visible :id application-id) => ok?)
    (fact "Authority can see Tasks tab"
          (query sonja :tasks-tab-visible :id application-id) => ok?)
    (fact "Reader authority can see Tasks tab"
          (query luukas :tasks-tab-visible :id application-id) => ok?)

  (facts "katselmukset"
    (count katselmukset) => 3
    (map :taskname katselmukset) => ["Aloituskokous" "K\u00e4ytt\u00f6\u00f6nottotarkastus" "loppukatselmus"]
    (-> katselmukset first :data :katselmuksenLaji :value) => "aloituskokous"
    (map #(get-in % [:data :vaadittuLupaehtona :value]) katselmukset) => (partial every? true?))

  (facts "maaraykset"
    (count maaraykset) => 3
    (map :taskname maaraykset) => ["Radontekninen suunnitelma" "Ilmanvaihtosuunnitelma" "Valaistussuunnitelma"]
    (map #(get-in % [:data :maarays :value]) maaraykset) => (partial every? truthy))

  (facts "tyonjohtajat"
    (count tyonjohtajat) => 3
    (map :taskname tyonjohtajat) => ["Vastaava ty\u00f6njohtaja" "Vastaava IV-ty\u00f6njohtaja" "Ty\u00f6njohtaja"]
    (map :data tyonjohtajat) => (partial not-any? empty?))

  (fact "totally 9 tasks in fixture"
    (count tasks) => 9)

  (let [resp (command sonja :check-for-verdict :id application-id)
        application (query-application pena application-id)
        tasks (:tasks application)]

    (fact "read 2 verdicts again"
      resp => ok?
      (:verdictCount resp) => 2)

    (fact "application has been modified"
      (:modified application) => (partial < modified))

    (fact "tasks were not generated again"
      (:taskCount resp) => 0
      (distinct (map :created tasks)) => [modified]

      (reduce + 0 (map #(let [schema (schemas/get-schema (:schema-info %))]
                         (model/modifications-since-approvals (:body schema) [] (:data %) {} true modified)) tasks)) => 0))

  (let [task-id (->> tasks (remove (fn->> :schema-info :subtype keyword (= :review))) first :id)
        review-id (->> tasks (filter (fn->> :schema-info :subtype keyword (= :review))) first :id)
        task (util/find-by-id task-id (:tasks application))
        review (util/find-by-id review-id (:tasks application))]

    (fact "Pena can't approve"
      (command pena :approve-task :id application-id :taskId task-id) => unauthorized?)

    (facts* "Approve the first non-review task"
      (let [_ (command sonja :approve-task :id application-id :taskId task-id) => ok?
            updated-app (query-application pena application-id)
            updated-task (util/find-by-id task-id (:tasks updated-app))]
        (:state task) => "requires_user_action"
        (:state updated-task) => "ok"))

    (fact "Review cannot be approved"
       (command sonja :approve-task :id application-id :taskId review-id) => (partial expected-failure? "error.invalid-task-type"))

    (facts* "Reject the first task"
      (let [_ (command sonja :reject-task :id application-id :taskId task-id) => ok?
            updated-app (query-application pena application-id)
            updated-task (util/find-by-id task-id (:tasks updated-app))]
        (:state updated-task) => "requires_user_action"))

    (fact "Review cannot be rejected"
      (command sonja :reject-task :id application-id :taskId review-id) => (partial expected-failure? "error.invalid-task-type"))
    #_(fact "Review can't be deleted, because Vaadittu lupaehtona"
      (command sonja :delete-task :id application-id :taskId review-id) => (partial expected-failure? :error.task-is-required)))

  (facts "create task"
    (fact "Applicant can't create tasks"
      (command pena :create-task :id application-id :taskName "do the shopping" :schemaName "task-katselmus") => unauthorized?)
    (fact "Authority can create tasks"
      (command sonja :create-task :id application-id :taskName "do the shopping" :schemaName "task-katselmus" :taskSubtype "aloituskokous") => ok?
      (let [application (query-application pena application-id)
            tasks (:tasks application)
            buildings (:buildings application)
            katselmus-task (some #(when (and (= "task-katselmus" (-> % :schema-info :name))
                                             (= "do the shopping" (:taskname %)))
                                   %)
                                 tasks)
            katselmus-rakennus-data (-> katselmus-task :data :rakennus)]
        katselmus-task => truthy
        (fact "katselmuksenLaji saved to doc"
          (-> katselmus-task :data :katselmuksenLaji :value) => "aloituskokous")
        (fact "buildings are saved to task-katselmus"
          (count buildings) => (count (vals katselmus-rakennus-data)))))
    (fact "Can't create documents with create-task command"
      (command sonja :create-task :id application-id :taskName "do the shopping" :schemaName "uusiRakennus") => (partial expected-failure? "illegal-schema"))
    (fact "Can't create katselmus without valid katselmuksenLaji"
      (command sonja :create-task :id application-id :taskName "do the shopping" :schemaName "task-katselmus") => (partial expected-failure? "error.illegal-value:select")
      (command sonja :create-task :id application-id :taskName "do the shopping" :schemaName "task-katselmus" :taskSubtype "foofaa") => (partial expected-failure? "error.illegal-value:select")))

  (facts "review edit and done"
    (let [task-id (-> tasks second :id)]
      (fact "readonly field can't be updated"
        (command sonja :update-task :id application-id :doc task-id :updates [["katselmuksenLaji" "rakennekatselmus"]]) => (partial expected-failure? :error-trying-to-update-readonly-field))

      (facts "mark done"
        (fact "can't be done as required fieds are not filled"
          (command sonja :review-done :id application-id :taskId task-id :lang "fi") => fail?)

        (command sonja :update-task :id application-id :doc task-id :updates [["rakennus.0.rakennus.jarjestysnumero" "1"]
                                                                              ["rakennus.0.rakennus.rakennusnro" "001"]
                                                                              ["rakennus.0.rakennus.valtakunnallinenNumero" "1234567892"]
                                                                              ["rakennus.0.tila.tila" "osittainen"]
                                                                              ["rakennus.0.rakennus.kiinttun" (:propertyId application)]
                                                                              ["katselmus.tila" "osittainen"]
                                                                              ["katselmus.pitoPvm" "12.04.2016"]
                                                                              ["katselmus.pitaja" "Sonja Sibbo"]]) => ok?

        (fact "can now be marked done, required fields are filled"
          (command sonja :review-done :id application-id :taskId task-id :lang "fi") => ok?)

        (fact "notes can still be updated"
          (command sonja :update-task :id application-id :doc task-id :updates [["katselmus.huomautukset.kuvaus" "additional notes"]]) => ok?)

        (fact "review state can no longer be updated"
          (command sonja :update-task :id application-id :doc task-id :updates [["katselmus.tila" "osittainen"]]) => fail?)

        (let [app (query-application sonja application-id)
              tasks (:tasks app)
              expected-count (+ 9 1 1) ; fixture + prev. facts + review-done
              new-task (last tasks)
              new-id (:id new-task)]
          (fact "as the review was not final, a new task has been createad"
            (count tasks) => expected-count)
          (fact "buildings are populated to newly created task"
            (count (:buildings app)) => (-> new-task :data :rakennus vals count))

          (fact "mark the new task final"
                (command sonja :update-task :id application-id :doc new-id :updates [ ["katselmus.tila" "lopullinen"]
                                                                                 ["katselmus.pitoPvm" "12.04.2016"]
                                                                                 ["katselmus.pitaja" "Sonja Sibbo"]]) => ok?)

          (fact "mark the new task done"
            (command sonja :review-done :id application-id :taskId new-id :lang "fi") => ok?)

          (let [tasks (:tasks (query-application sonja application-id))]
            (fact "no new tasks were generated (see count above)"
              (count tasks) => expected-count)

            (fact "the original task was marked final too"
              (get-in (second tasks) [:data :katselmus :tila :value]) => "lopullinen")))))))

(facts "mark review faulty"
  (fact "Activate PDF/A conversion"
    (command admin :set-organization-boolean-attribute
             :attribute "permanent-archive-enabled"
             :enabled true
             :organizationId "753-R") => ok?)
  (let [application       (create-and-submit-application pena :propertyId sipoo-property-id)
        application-id    (:id application)
        _                 (command sonja :check-for-verdict :id application-id)
        application       (query-application pena application-id)
        tasks             (:tasks application)
        katselmukset      (filter (partial task-by-type "katselmus") tasks)
        loppukatselmus    (util/find-by-key :taskname "loppukatselmus" katselmukset)
        loppukatselmus-id (:id loppukatselmus)
        url               (str (server-address) "/dev/fileinfo/")]
    (fact "final review template exists from verdict"
      loppukatselmus => truthy)

    (fact "Fill required review data"
      (command sonja :update-task :id application-id :doc loppukatselmus-id
               :updates [["katselmus.tila" "lopullinen"]
                         ["katselmus.pitoPvm" "11.09.2017"]
                         ["katselmus.pitaja" "Auburn Authority"]]))
    (let [att-file-id (upload-file-and-bind sonja application-id {:contents "Asiantuntijatarkastuksen lausunto"
                                                                  :group    {}
                                                                  :target   {:type "task" :id loppukatselmus-id}
                                                                  :type     {:type-group "katselmukset_ja_tarkastukset"
                                                                             :type-id    "tarkastusasiakirja"}})]
      (fact "Mark review as done"
        (command sonja :review-done :id application-id :lang "fi" :taskId loppukatselmus-id)
        => ok?)
      (let [review-attachments      (->> (query-application sonja application-id)
                                         :attachments
                                         (filter #(= loppukatselmus-id (-> % :target :id)) ))
            review-attachment-files (map :latestVersion review-attachments)]
        (doseq [{:keys [fileId originalFileId filename]} review-attachment-files]
          (if (util/=as-kw filename :Katselmus.pdf)
            (fact "Katselmus.pdf is already valid PDF/A"
              (= fileId originalFileId) => true)
            (fact "Attachment has been converted"
              (= fileId originalFileId) => false)))
        (doseq [{att-id :id} review-attachments]
          (fact "review attachment cannot be deleted"
            (command sonja :delete-attachment :id application-id :attachmentId att-id)
            => fail?))
        (fact "Mark loppukatselmus faulty"
          (command sonja :mark-review-faulty :id application-id
                   :taskId loppukatselmus-id :notes "Bad review! Sad!")
          => ok?)
        (let [updated-application        (query-application pena application-id)
              updated-loppukatselmus     (util/find-by-id (:id loppukatselmus)
                                                          (:tasks updated-application))
              updated-review-attachments (util/find-by-key :target {:id   loppukatselmus-id
                                                                    :type "task"}
                                                           (:attachments updated-application))]
          (fact "Attachments have been cleared"
            updated-review-attachments => nil)
          (doseq [{:keys [fileId
                          originalFileId
                          filename]} review-attachment-files]
            (when (util/not=as-kw filename :Katselmus.pdf)
              (fact "Converted file is no longer available"
                (decode-body (http-get (str url fileId) {})) => nil))
            (let [body (decode-body (http-get (str url originalFileId) {}))]
              (fact "Original file is still accessible"
                body => (contains {:fileId      originalFileId
                                   :application application-id}))
              (fact "Filename basename is the same (conversion may have changed the extension)"
                (first (ss/split filename #"\."))
                => (first (ss/split (:filename body) #"\.")))))
          (fact "review state is faulty"
            (:state updated-loppukatselmus) => "faulty_review_task")
          (fact "Timestamp matches"
            (:modified updated-application)
            => (-> updated-loppukatselmus :faulty :timestamp))
          (fact "Original file ids are stored"
            (set (map #(select-keys % [:originalFileId :filename])
                      review-attachment-files))
            => (-> updated-loppukatselmus :faulty :files set))
          (fact "Task notes have been updated"
            (-> updated-loppukatselmus :data :katselmus :huomautukset :kuvaus)
            => (contains {:modified (:modified updated-application)
                          :value    "Bad review! Sad!"}))
          (fact "faulty review can not be sent to background"
            (command sonja :resend-review-to-backing-system :id application-id
                     :taskId loppukatselmus-id :lang "fi")
            => (partial expected-failure? :error.command-illegal-state)))))
    (fact "Dectivate PDF/A conversion"
    (command admin :set-organization-boolean-attribute
             :attribute "permanent-archive-enabled"
             :enabled false
             :organizationId "753-R") => ok?)
    (fact "Review from background"
      (let [aloituskokous    (util/find-by-key :taskname "Aloituskokous" katselmukset)
            aloituskokous-id (:id aloituskokous)]
        (fact "Fetch finished Aloituskokous"
          (http-post (str (server-address) "/dev/review-from-background/" application-id "/" aloituskokous-id) {})
          => http200?)
        (let [ak-attachments (->> (query-application sonja application-id)
                                  :attachments
                                  (filter #(= aloituskokous-id (get-in % [:target :id]))))]
          (fact "Only one attachment for Aloituskokous"
            (count ak-attachments) => 1)
          (fact "No conversion"
            (let [{:keys [fileId originalFileId]} (-> ak-attachments first :latestVersion)]
              fileId => originalFileId))
          (fact "Mark Aloituskokous as faulty"
            (command sonja :mark-review-faulty :id application-id
                     :taskId aloituskokous-id :notes "No notes")
            => ok?)
          (let [app (query-application sonja application-id)
                ak  (util/find-by-id aloituskokous-id (:tasks app))]
            (fact "Attachment is gone"
              (util/find-first (fn->> :target :id (= aloituskokous-id))
                               (:attachments app))
              => nil)
            (fact "Aloituskokous is faulty"
              (:state ak) => "faulty_review_task")
            (fact "Attachment file is stored and available"
              (decode-body (http-get (str url (-> ak :faulty :files first :originalFileId)) {}))
              => (contains {:application application-id}))))))))

(facts "Reviews with PATE"
  (command admin :set-organization-scope-pate-value
           :permitType "R"
           :municipality "753"
           :value true)
  (let [sipoo-application        (create-and-submit-application pena :propertyId sipoo-property-id)
        jarvenpaa-application    (create-and-submit-application pena :propertyId jarvenpaa-property-id)
        sipoo-app-id             (:id sipoo-application)
        jarvenpaa-app-id         (:id jarvenpaa-application)
        _                        (give-verdict sonja sipoo-app-id :verdictId "753-2018-PATE")
        _                        (give-verdict raktark-jarvenpaa jarvenpaa-app-id :verdictId "186-2018-0001")]

    (fact "Application should be in verdict given state"
      (:state (query-application pena sipoo-app-id)) => "verdictGiven")

    (fact "First review marked done should change state when PATE in use"
     (let [result  (command sonja :create-task :id sipoo-app-id :taskName "Aloituskokous" :schemaName "task-katselmus" :taskSubtype "aloituskokous") => ok?
           task-id (:taskId result)]
       (command sonja :update-task :id sipoo-app-id :doc task-id :updates [["katselmus.tila" "lopullinen"] ["katselmus.pitoPvm" "15.02.2018"] ["katselmus.pitaja" "Auburn Authority"]]) => ok?
       (command sonja :review-done :id sipoo-app-id :lang "fi" :taskId task-id) => ok?
       (count (:tasks (query-application pena sipoo-app-id))) => 1
       (:state (query-application pena sipoo-app-id)) => "constructionStarted"))

    (fact "And send message"
      (get-in (last (integration-messages sipoo-app-id)) [:data :toState :name]) => "constructionStarted")

    (fact "Second done review should change state"
     (command sonja :change-application-state :id sipoo-app-id :state "appealed") => ok?
     (command sonja :change-application-state :id sipoo-app-id :state "verdictGiven") => ok?
     (:state (query-application sonja sipoo-app-id)) => "verdictGiven"
     (let [result  (command sonja :create-task :id sipoo-app-id :taskName "Loppukatselmus" :schemaName "task-katselmus" :taskSubtype "loppukatselmus") => ok?
           task-id (:taskId result)]
     (command sonja :update-task :id sipoo-app-id :doc task-id :updates [["katselmus.tila" "lopullinen"] ["katselmus.pitoPvm" "15.02.2018"] ["katselmus.pitaja" "Auburn Authority"]]) => ok?
     (command sonja :review-done :id sipoo-app-id :lang "fi" :taskId task-id) => ok?
     (count (:tasks (query-application pena sipoo-app-id))) => 2
     (:state (query-application pena sipoo-app-id)) => "verdictGiven"))

   (fact "First review marked done without PATE shouldnt change state"
     (let [result  (command raktark-jarvenpaa :create-task :id jarvenpaa-app-id :taskName "Aloituskokous" :schemaName "task-katselmus" :taskSubtype "aloituskokous") => ok?
           task-id (:taskId result)]
       (command raktark-jarvenpaa :update-task :id jarvenpaa-app-id :doc task-id :updates [["katselmus.tila" "lopullinen"] ["katselmus.pitoPvm" "15.02.2018"] ["katselmus.pitaja" "Auburn Authority"]]) => ok?
       (command raktark-jarvenpaa :review-done :id jarvenpaa-app-id :lang "fi" :taskId task-id) => ok?
       (count (:tasks (query-application pena jarvenpaa-app-id))) => 1
       (:state (query-application pena jarvenpaa-app-id)) => "verdictGiven"))))
