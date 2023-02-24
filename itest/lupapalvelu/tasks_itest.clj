(ns lupapalvelu.tasks-itest
  (:require [lupapalvelu.document.model :as model]
            [lupapalvelu.document.schemas :as schemas]
            [lupapalvelu.factlet :refer :all]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.pate-legacy-itest-util :refer :all]
            [midje.sweet :refer :all]
            [sade.strings :as ss]
            [sade.util :refer [fn->>] :as util]))

(apply-remote-minimal)

(defn- task-by-type [task-type task] (= (str "task-" task-type) (-> task :schema-info :name)))

(let [application    (create-and-submit-application pena :propertyId sipoo-property-id)
      application-id (:id application)
      resp           (command sonja :check-for-verdict :id application-id)
      application    (query-application pena application-id)
      modified       (:modified application)
      tasks          (:tasks application)
      katselmukset   (filter (partial task-by-type "katselmus") tasks)
      maaraykset     (filter (partial task-by-type "lupamaarays") tasks)
      tyonjohtajat   (filter (partial task-by-type "vaadittu-tyonjohtaja") tasks)]

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
    (-> tasks first :assignee :id) => sonja-id
    (-> tasks first :assignee :firstName) => "Sonja"
    (-> tasks first :assignee :lastName) => "Sibbo")

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
    (count maaraykset) => 4 ; One is a coerced foreman task
    (map :taskname maaraykset) => ["Radontekninen suunnitelma"
                                   "Ilmanvaihtosuunnitelma"
                                   "Vastaava IV-työnjohtaja"
                                   "Valaistussuunnitelma"]
    (map #(get-in % [:data :maarays :value]) maaraykset) => (partial every? truthy))

  (facts "tyonjohtajat"
    (count tyonjohtajat) => 2 ; One is missing because it was coerced to other task
    (map :taskname tyonjohtajat) => ["Vastaava ty\u00f6njohtaja" "Ty\u00f6njohtaja"]
    (map :data tyonjohtajat) => (partial not-any? empty?))

  (fact "totally 9 tasks in fixture"
    (count tasks) => 9)

  (let [resp        (command sonja :check-for-verdict :id application-id)
        application (query-application pena application-id)
        tasks       (:tasks application)]

    (fact "read 2 verdicts again"
      resp => ok?
      (:verdictCount resp) => 2)

    (fact "application has been modified"
      (:modified application) => (partial < modified))

    (fact "tasks were generated again"
      (:taskCount resp) => 9
      (distinct (map :created tasks)) => [(:modified application)]

      ;; (reduce + 0 (map #(let [schema (schemas/get-schema (:schema-info %))]
      ;;                     (model/modifications-since-approvals (:body schema) [] (:data %) {} true modified)) tasks)) => 0

      )

    )

  (let [{tasks :tasks :as application} (query-application sonja application-id)
        task-id                        (->> tasks (remove (fn->> :schema-info :subtype keyword (= :review))) first :id)
        review-id                      (->> tasks (filter (fn->> :schema-info :subtype keyword (= :review))) first :id)
        task                           (util/find-by-id task-id (:tasks application))]
    (fact "Pena can't approve"
      (command pena :approve-task :id application-id :taskId task-id) => unauthorized?)

    (facts* "Approve the first non-review task"
      (let [_            (command sonja :approve-task :id application-id :taskId task-id) => ok?
            updated-app  (query-application pena application-id)
            updated-task (util/find-by-id task-id (:tasks updated-app))]
        (:state task) => "requires_user_action"
        (:state updated-task) => "ok"))

    (fact "Review cannot be approved"
       (command sonja :approve-task :id application-id :taskId review-id) => (partial expected-failure? "error.invalid-task-type"))

    (facts* "Reject the first task"
      (let [_            (command sonja :reject-task :id application-id :taskId task-id) => ok?
            updated-app  (query-application pena application-id)
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
      (let [application             (query-application pena application-id)
            tasks                   (:tasks application)
            buildings               (:buildings application)
            katselmus-task          (some #(when (and (= "task-katselmus" (-> % :schema-info :name))
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
      (command sonja :create-task :id application-id :taskName "do the shopping" :schemaName "task-katselmus" :taskSubtype "foofaa") => (partial expected-failure? "error.illegal-value:select"))
    (fact "Lupamääräys taskname is repeated in the maarays field"
      (let [{:keys [taskId]} (command sonja :create-task :id application-id
                                      :schemaName "task-lupamaarays"
                                      :taskName "Lorem ipsum")
            {:keys [tasks]}  (query-application sonja application-id)]
        (util/find-by-id taskId tasks) => (contains {:taskname    "Lorem ipsum"
                                                     :schema-info (contains {:name "task-lupamaarays"})
                                                     :data        (contains {:maarays (contains {:value    "Lorem ipsum"
                                                                                                 :modified truthy})})}))))

  (facts "Configure review officers for Sipoo"
    (command sipoo :set-organization-review-officers-list-enabled
             :organizationId "753-R"
             :enabled true) => ok?
    (command sipoo :create-review-officer
             :organizationId "753-R"
             :name "Sonja Sibbo"
             :code "sonjasibbo") => ok?
    (command sipoo :create-review-officer
             :organizationId "753-R"
             :name "Ines Inspector"
             :code "ines") => ok?)

  (facts "review edit and done"
    (let [{:keys [tasks reviewOfficers]
           :as   application}    (query-application sonja application-id)
          {task-id :id :as task} (second tasks)]
      (fact "Task is required by verdict"
        (-> task :source :type)  => "verdict"
        (-> task :data :vaadittuLupaehtona :value) => true)
      (fact "readonly field can't be updated"
        (command sonja :update-task :id application-id :doc task-id :updates [["katselmuksenLaji" "rakennekatselmus"]]) => (partial expected-failure? :error-trying-to-update-readonly-field))

      (fact "Review officers are listed in the application"
        reviewOfficers => (just {:enabled  true
                                 :officers (just (contains {:code "sonjasibbo"})
                                                 (contains {:code "ines"})
                                                 :in-any-order)}))

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
                                                                              ["katselmus.pitaja"
                                                                               {:name         "Sonja Sibbo"
                                                                                :code         "sonjasibbo"
                                                                                :id           "234a24b5dc"
                                                                                :_atomic-map? true
                                                                                }]]) => ok?
        (fact "can now be marked done, required fields are filled"
          (command sonja :review-done :id application-id :taskId task-id :lang "fi") => ok?)

        (fact "notes cannot no longer be updated"
          (command sonja :update-task :id application-id :doc task-id :updates [["katselmus.huomautukset.kuvaus" "additional notes"]])
          => (partial expected-failure? :error.task-is-not-editable))

        (fact "review state can no longer be updated"
          (command sonja :update-task :id application-id :doc task-id :updates [["katselmus.tila" "osittainen"]]) => fail?)

        (let [app            (query-application sonja application-id)
              tasks          (:tasks app)
              root-task      (second tasks)
              expected-count (+ 9 2 1) ; fixture + prev. facts + review-done
              new-task       (last tasks)
              new-id         (:id new-task)]
          (fact "as the review was not final, a new task has been created"
            (count tasks) => expected-count)
          (fact "buildings are populated to newly created task"
            (count (:buildings app)) => (-> new-task :data :rakennus vals count))
          (fact "review officer is properly updated"
            (-> app :tasks (nth 1) :data :katselmus :pitaja :value) => {:name         "Sonja Sibbo"
                                                                        :code         "sonjasibbo"
                                                                        :id           "234a24b5dc"
                                                                        :_atomic-map? true})
          (fact "New task is also a permit condition"
            (-> new-task :data :vaadittuLupaehtona :value) => true)
          (fact "mark the new task final"
            (command sonja :update-task :id application-id :doc new-id :updates [ ["katselmus.tila" "lopullinen"]
                                                                                 ["katselmus.pitoPvm" "12.04.2016"]
                                                                                 ["katselmus.pitaja" "Sonja Sibbo"]]) => ok?)

          (fact "mark the new task done"
            (command sonja :review-done :id application-id :taskId new-id :lang "fi") => ok?)

          (let [tasks    (:tasks (query-application sonja application-id))
                new-task (last tasks)]
            (fact "no new tasks were generated (see count above)"
              (count tasks) => expected-count)

            (fact "Source is the root task"
              (:source new-task) => {:type "task"
                                     :id   (:id root-task)})

            (fact "the root task is untouched"
              (= root-task (util/find-by-id (:id root-task) tasks)) => true))))))

  (facts "reviewer dynamic value: review officer map"
    (let [{:keys [reviewOfficers
                  tasks]} (query-application sonja application-id)
          task-id         (-> tasks (nth 7) :id)
          sonja-officer   (util/find-by-key :code "sonjasibbo" (:officers reviewOfficers))]
      (facts "mark done"
        (fact "can't be done as required fields are not filled"
          (command sonja :review-done :id application-id :taskId task-id :lang "fi") => fail?)

        (command sonja :update-task :id application-id :doc task-id :updates [["katselmus.tila" "osittainen"]
                                                                              ["katselmus.pitoPvm" "12.04.2016"]]) => ok?
        (fact "can now be marked done, required fields are filled"
          (command sonja :review-done :id application-id :taskId task-id :lang "fi") => ok?)

        (fact "review officer is properly updated"
          (-> (query-application sonja application-id) :tasks (nth 7) :data :katselmus :pitaja :value)
          => sonja-officer))))


  (facts "reviewer dynamic value: string"
    (fact "Disable review officers"
      (command sipoo :set-organization-review-officers-list-enabled
             :organizationId "753-R"
             :enabled false) => ok?)

    (let [{:keys [reviewOfficers
                  tasks]} (query-application sonja application-id)
          task-id         (-> tasks last :id)]
      (fact "No review officers"
        reviewOfficers => empty?)
      (facts "mark done"
        (fact "can't be done as required fields are not filled"
          (command sonja :review-done :id application-id :taskId task-id :lang "fi") => fail?)

        (command sonja :update-task :id application-id :doc task-id :updates [["katselmus.tila" "lopullinen"]
                                                                              ["katselmus.pitoPvm" "12.04.2016"]]) => ok?
        (fact "can now be marked done, required fields are filled"
          (command ronja :review-done :id application-id :taskId task-id :lang "fi") => ok?)

        (fact "review officer is properly updated"
          (-> (query-application sonja application-id) :tasks last :data :katselmus :pitaja :value)
          => "Ronja Sibbo")))))

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
        url               (str (server-address) "/dev/fileinfo/" application-id "/")]
    (fact "final review template exists from verdict"
      loppukatselmus => truthy)

    (fact "Fill required review data"
      (command sonja :update-task :id application-id :doc loppukatselmus-id
               :updates [["katselmus.tila" "lopullinen"]
                         ["katselmus.pitoPvm" "11.09.2017"]
                         ["katselmus.pitaja" "Auburn Authority"]]))
    (let [_ (upload-file-and-bind sonja application-id {:contents "Asiantuntijatarkastuksen lausunto"
                                                        :group    {}
                                                        :target   {:type "task" :id loppukatselmus-id}
                                                        :type     {:type-group "katselmukset_ja_tarkastukset"
                                                                   :type-id    "tarkastusasiakirja"}})]
      (fact "Mark review as done"
        (command sonja :review-done :id application-id :lang "fi" :taskId loppukatselmus-id)
        => ok?)
      (fact "Verdicts cannot be fetched when sent reviews"
        (command sonja :check-for-verdict :id application-id)
        => (partial expected-failure? :error.verdicts-have-sent-tasks))
      (let [review-attachments      (->> (query-application sonja application-id)
                                         :attachments
                                         (filter #(= loppukatselmus-id (-> % :target :id)) ))
            review-attachment-files (map :latestVersion review-attachments)]
        (doseq [{:keys [archivable]} review-attachment-files]
          (fact "Attachment is archivable"
            archivable => true))
        (doseq [{att-id :id} review-attachments]
          (fact "review attachment cannot be deleted"
            (command sonja :delete-attachment :id application-id :attachmentId att-id)
            => fail?))
        (fact "Mark loppukatselmus faulty"
          (command sonja :mark-review-faulty :id application-id
                   :taskId loppukatselmus-id :notes "   Bad review! Sad!  ")
          => ok?)
        (let [updated-application        (query-application sonja application-id)
              updated-loppukatselmus     (util/find-by-id (:id loppukatselmus)
                                                          (:tasks updated-application))
              updated-review-attachments (util/find-by-key :target {:id   loppukatselmus-id
                                                                    :type "task"}
                                                           (:attachments updated-application))]
          (fact "Attachments have been cleared"
            updated-review-attachments => nil)
          (doseq [{:keys [originalFileId
                          filename]} review-attachment-files]
            (let [body (decode-body (http-get (str url originalFileId) {}))]
              (fact "Original file is still accessible"
                body => (contains {:fileId originalFileId}))
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
                     :taskId aloituskokous-id)
            => ok?)
          (let [app (query-application sonja application-id)
                ak  (util/find-by-id aloituskokous-id (:tasks app))]
            (fact "Attachment is gone"
              (util/find-first (fn->> :target :id (= aloituskokous-id))
                               (:attachments app))
              => nil)
            (fact "Aloituskokous is faulty"
              (:state ak) => "faulty_review_task")
            (fact "Notes is empty"
              (get-in ak [:data :katselmus :huomautukset :kuvaus])
              => (contains {:modified (:modified app)
                            :value    ""}))
            (fact "Attachment file is stored and available"
              (decode-body (http-get (str url (-> ak :faulty :files first :originalFileId)) {}))
              => (contains {:fileId (-> ak :faulty :files first :originalFileId)})))
          (let [{:keys [tasks]} (query-application pena application-id)]
            (facts "Applicant does not see faulty reviews"
              tasks => not-empty
              (util/find-by-id aloituskokous-id tasks) => nil))
          (fact "Reader authority sees faulty reviews"
            (->> (query-application luukas application-id)
                 :tasks
                 (util/find-by-id aloituskokous-id)) => truthy))))
    (fact "Approve Radontekninen suunnitelma"
      (let [{radon-id :id} (util/find-by-key :taskname "Radontekninen suunnitelma" tasks)]
        (command sonja :approve-task :id application-id :taskId radon-id) => ok?
        (fact "Check-for-verdict fails"
          (command sonja :check-for-verdict :id application-id)
          => (partial expected-failure? :error.verdicts-have-sent-tasks))
        (fact "Reject Radontekninen suunnitelma"
          (command sonja :reject-task :id application-id :taskId radon-id) => ok?)))
    (fact "Check for verdict succeeds"
      (command sonja :check-for-verdict :id application-id) => ok?)))

(facts "Reviews with PATE"
  (let [sipoo-application     (create-and-submit-application pena :propertyId sipoo-property-id)
        jarvenpaa-application (create-and-submit-application pena :propertyId jarvenpaa-property-id)
        sipoo-app-id          (:id sipoo-application)
        jarvenpaa-app-id      (:id jarvenpaa-application)
        _                     (give-legacy-verdict sonja sipoo-app-id)
        _                     (give-legacy-verdict raktark-jarvenpaa jarvenpaa-app-id)]

    (fact "Application should be in verdict given state"
      (:state (query-application pena sipoo-app-id)) => "verdictGiven")

    (fact "Enable Pate in Sipoo"
      (command admin :set-organization-scope-pate-value
               :permitType "R"
               :municipality "753"
               :value true) => ok?)
    (state-change-integration sipoo "753-R")

    (fact "First review marked done should change state when PATE in use"
      (let [result  (command sonja :create-task :id sipoo-app-id :taskName "Aloituskokous" :schemaName "task-katselmus" :taskSubtype "aloituskokous") => ok?
            task-id (:taskId result)]
       (command sonja :update-task :id sipoo-app-id :doc task-id :updates [["katselmus.tila" "lopullinen"] ["katselmus.pitoPvm" "15.02.2018"] ["katselmus.pitaja" "Auburn Authority"]]) => ok?
       (command sonja :review-done :id sipoo-app-id :lang "fi" :taskId task-id) => ok?
       (count (:tasks (query-application pena sipoo-app-id))) => 1
       (:state (query-application pena sipoo-app-id)) => "constructionStarted"))

    (fact "And send message"
      (get-in (last (integration-messages sipoo-app-id)) [:data :toState :name]) => "constructionStarted")

    (fact "Second done review should not change state"
     (command sonja :change-application-state :id sipoo-app-id :state "appealed") => ok?
     (command sonja :change-application-state :id sipoo-app-id :state "verdictGiven") => ok?
     (:state (query-application sonja sipoo-app-id)) => "verdictGiven"
     (let [result  (command sonja :create-task :id sipoo-app-id :taskName "Loppukatselmus" :schemaName "task-katselmus" :taskSubtype "loppukatselmus") => ok?
           task-id (:taskId result)]
     (command sonja :update-task :id sipoo-app-id :doc task-id :updates [["katselmus.tila" "lopullinen"] ["katselmus.pitoPvm" "15.02.2018"] ["katselmus.pitaja" "Auburn Authority"]]) => ok?
     (command sonja :review-done :id sipoo-app-id :lang "fi" :taskId task-id) => ok?
     (count (:tasks (query-application pena sipoo-app-id))) => 2
     (:state (query-application pena sipoo-app-id)) => "verdictGiven"))

    (fact "No automatic state change if organization flag is false"
      (command sipoo :set-automatic-construction-started
               :organizationId "753-R"
               :enabled false) => ok?
      (let [{app-id :id}      (create-and-submit-application pena :propertyId sipoo-property-id
                                                        :operation "pientalo")
            _                 (command sonja :check-for-verdict :id app-id)
            {task-id :taskId} (command sonja :create-task :id app-id
                                       :taskName "Review" :schemaName "task-katselmus"
                                       :taskSubtype "aloituskokous")]
        (command sonja :update-task :id app-id :doc task-id
                 :updates [["katselmus.tila" "lopullinen"]
                           ["katselmus.pitoPvm" "23.04.2020"]
                           ["katselmus.pitaja" "Auburn Authority"]]) => ok?
        (command sonja :review-done :id app-id :lang "fi" :taskId task-id) => ok?
        (:state (query-application pena app-id)) => "verdictGiven"))

   (fact "First review marked done without PATE shouldn't change state"
     (let [result  (command raktark-jarvenpaa :create-task :id jarvenpaa-app-id :taskName "Aloituskokous" :schemaName "task-katselmus" :taskSubtype "aloituskokous") => ok?
           task-id (:taskId result)]
       (command raktark-jarvenpaa :update-task :id jarvenpaa-app-id :doc task-id :updates [["katselmus.tila" "lopullinen"] ["katselmus.pitoPvm" "15.02.2018"] ["katselmus.pitaja" "Auburn Authority"]]) => ok?
       (command raktark-jarvenpaa :review-done :id jarvenpaa-app-id :lang "fi" :taskId task-id) => ok?
       (count (:tasks (query-application pena jarvenpaa-app-id))) => 1
       (:state (query-application pena jarvenpaa-app-id)) => "verdictGiven"))))
