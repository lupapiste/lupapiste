(ns lupapalvelu.pate-legacy-itest
  (:require [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.pate-itest-util :refer :all]
            [lupapalvelu.pate-legacy-itest-util :refer :all]
            [midje.sweet :refer :all]
            [sade.date :refer [timestamp]]))

(apply-remote-minimal)

(fact "Create and submit R application"
      (let [{app-id :id} (create-and-submit-application pena
                                                        :operation :pientalo
                                                        :propertyId sipoo-property-id)]
        (fact "Set Sonja as the application handler"
              (command sonja :upsert-application-handler :id app-id
                       :userId sonja-id :roleId sipoo-general-handler-id))
        (fact "New legacy verdict draft"
              (let [{:keys [verdict-id]} (command sonja :new-legacy-verdict-draft
                                                  :id app-id)
                    open (partial open-verdict sonja app-id verdict-id)
                    {:keys [verdict filled]} (open)
                    edit (partial edit-verdict sonja app-id verdict-id)]
                filled => false?
                verdict => (contains {:legacy?    true
                                      :data       (contains {:handler "Sonja Sibbo"})
                                      :inclusions (contains ["handler"
                                                             "reviews.type"
                                                             "reviews.name"
                                                             "verdict-code"
                                                             "verdict-text"
                                                             "verdict-section"
                                                             "conditions.name"
                                                             "foremen.role"
                                                             "upload"
                                                             "anto"
                                                             "lainvoimainen"
                                                             "kuntalupatunnus"]
                                                            :in-any-order
                                                            :gaps-ok)})
                (fact "Fill verdict"
                      (fill-verdict sonja app-id verdict-id
                                    :kuntalupatunnus "888-10-12"
                                    :verdict-code "1" ;; Granted
                                    :verdict-text "Lorem ipsum"
                                    :anto (timestamp "21.5.2018")
                                    :lainvoimainen (timestamp "30.5.2018"))
                      (open)
                      => (contains {:filled  true
                                    :verdict (contains {:data (contains {:kuntalupatunnus "888-10-12"
                                                                         :verdict-code    "1"
                                                                         :verdict-text    "Lorem ipsum"
                                                                         :anto            (timestamp "21.5.2018")
                                                                         :lainvoimainen   (timestamp "30.5.2018")})})}))

                (add-legacy-review sonja app-id verdict-id "First review" :paikan-merkitseminen)

                (fact "Add condition"
                      (let [condition-id (-> (edit :add-condition true) :changes flatten second)]
                        (edit [:conditions condition-id :name] "Strict condition")
                        => (contains {:filled true})))

                (fact "Add foreman"
                      (let [foreman-id (-> (edit :add-foreman true) :changes flatten second)]
                        (edit [:foremen foreman-id :role] "ty\u00f6njohtaja")
                        => (contains {:filled true})))

                (fact "Verdict draft is listed"
                      (:verdicts (query sonja :pate-verdicts :id app-id))
                      => (just [(contains {:id verdict-id :modified pos?})]))

                (facts "Publish verdict"
                       (command sonja :publish-legacy-verdict :id app-id
                                :verdict-id verdict-id) => ok?
                       (verdict-pdf-queue-test sonja {:app-id     app-id
                                                      :verdict-id verdict-id})
                       (check-verdict-date sonja app-id (timestamp "21.5.2018"))
                       (let [{:keys [tasks attachments
                                     state]} (query-application sonja app-id)
                             file-id   (-> attachments first :latestVersion :fileId)
                             review-id (-> tasks first :id)]
                         (fact "Application state is verdict given"
                               state => "verdictGiven")
                         (fact "Tasks have been created"
                               tasks => (just [(contains {:taskname    "First review"
                                                          :data        (contains {:katselmuksenLaji
                                                                                  (contains {:value
                                                                                             "rakennuksen paikan merkitseminen"})})
                                                          :schema-info (contains {:name    "task-katselmus"
                                                                                  :subtype "review"
                                                                                  :type    "task"})
                                                          :source      {:type "verdict" :id verdict-id}})
                                               (contains {:taskname    "Strict condition"
                                                          :schema-info (contains {:type "task"
                                                                                  :name "task-lupamaarays"})
                                                          :source      {:type "verdict" :id verdict-id}})
                                               (contains {:taskname    "Ty\u00f6njohtaja"
                                                          :data        (contains {:kuntaRoolikoodi (contains {:value "ty\u00f6njohtaja"})})
                                                          :schema-info (contains {:type    "task"
                                                                                  :name    "task-vaadittu-tyonjohtaja"
                                                                                  :subtype "foreman"})
                                                          :source      {:type "verdict" :id verdict-id}})]))

                         (check-file app-id file-id true)
                         (fact "Add attachment to review"
                               (let [task-file-id (upload-file-and-bind
                                                    sonja
                                                    app-id {:contents "Minutes"
                                                            :target   {:type "task"
                                                                       :id   review-id}
                                                            :type     {:type-group "katselmukset_ja_tarkastukset"
                                                                       :type-id    "katselmuksen_tai_tarkastuksen_poytakirja"}})]
                                 (check-file app-id task-file-id true)
                                 (facts "Delete verdict"
                                        (fact "Delete legacy verdict"
                                              (command sonja :delete-pate-verdict :id app-id
                                                       :verdict-id verdict-id)
                                              => ok?)
                                        (fact "verdict no longer exists"
                                              (query sonja :pate-verdict :id app-id
                                                     :verdict-id verdict-id)
                                              => fail?)
                                        (check-verdict-date sonja app-id nil)
                                        (let [{:keys [attachments tasks state]} (query-application sonja app-id)]
                                          (fact "No attachments and the file has been removed"
                                                attachments => empty?
                                                (check-file app-id file-id false))
                                          (fact "No tasks and the file has been removed"
                                                tasks => empty?
                                                (check-file app-id task-file-id false))
                                          (fact "State has been rewound"
                                                state => "submitted")))))))))

        (fact "Three verdicts"
              (let [{vid1 :verdict-id} (command sonja :new-legacy-verdict-draft
                                                :id app-id)
                    {vid2 :verdict-id} (command sonja :new-legacy-verdict-draft
                                                :id app-id)
                    {vid3 :verdict-id} (command sonja :new-legacy-verdict-draft
                                                :id app-id)]
                (fact "Add attachment to the third verdict"
                      (let [file-id (upload-file-and-bind sonja app-id {:contents "Notes"
                                                                        :target   {:type "verdict"
                                                                                   :id   vid3}
                                                                        :type     {:type-group "paatoksenteko"
                                                                                   :type-id    "muistio"}}
                                                          :draft? true)]
                        (fact "Attachment and file exist"
                              (:attachments (query-application sonja app-id))
                              => (just [(contains {:contents "Notes"})])
                              (check-file app-id file-id true))))
                (fact "Add attachment to the second verdict"
                      (let [file-id (upload-file-and-bind sonja app-id {:contents "Complaint"
                                                                        :target   {:type "verdict"
                                                                                   :id   vid2}
                                                                        :type     {:type-group "paatoksenteko"
                                                                                   :type-id    "valitusosoitus"}}
                                                          :draft? true)]
                        (fact "Attachment and file exist"
                              (:attachments (query-application sonja app-id))
                              => (just [(contains {:contents "Notes"})
                                        (contains {:contents "Complaint"})])
                              (check-file app-id file-id true))))
                (fact "Fill and publish the first verdict"
                      (fill-verdict sonja app-id vid1
                                    :kuntalupatunnus "888-10-13"
                                    :verdict-code "2" ;; Admitted
                                    :verdict-text "Quisque sed nibh"
                                    :anto (timestamp "22.5.2018")
                                    :lainvoimainen (timestamp "1.6.2018"))
                      (add-legacy-review sonja app-id vid1 "Review One" :aloituskokous)
                      (command sonja :publish-legacy-verdict :id app-id
                               :verdict-id vid1) => ok?)
                (verdict-pdf-queue-test sonja {:app-id     app-id
                                               :verdict-id vid1})
                (check-verdict-date sonja app-id (timestamp "22.5.2018"))
                (fact "State is verdictGiven"
                      (query-application sonja app-id)
                      => (contains {:state "verdictGiven"}))
                (fact "Fill and publish the second verdict"
                      (fill-verdict sonja app-id vid2
                                    :kuntalupatunnus "888-10-14"
                                    :verdict-code "3" ;; Partially granted
                                    :verdict-text "Quisque sed nibh"
                                    :anto (timestamp "23.5.2018")
                                    :lainvoimainen (timestamp "2.6.2018"))
                      (add-legacy-review sonja app-id vid2 "Review Two" :aloituskokous)
                      (command sonja :publish-legacy-verdict :id app-id
                               :verdict-id vid2) => ok?)
                (verdict-pdf-queue-test sonja {:app-id     app-id
                                               :verdict-id vid2})
                (check-verdict-date sonja app-id (timestamp "23.5.2018"))
                (fact "There are now two tasks and four attachments"
                      (let [{:keys [attachments tasks]} (query-application sonja app-id)]
                        (count attachments) => 4
                        (count tasks) => 2))
                (fact "Delete the second verdict"
                      (command sonja :delete-pate-verdict :id app-id
                               :verdict-id vid2) => ok?)
                (check-verdict-date sonja app-id (timestamp "22.5.2018"))
                (fact "There are now one task and two attachments."
                      (let [{:keys [attachments tasks state]} (query-application sonja app-id)]
                        (count attachments) => 2
                        (count tasks) => 1
                        state => "verdictGiven"))
                (fact "Enable Pate in Sipoo"
                      (command admin :set-organization-scope-pate-value
                               :permitType "R"
                               :municipality "753"
                               :value true) => ok?)
                (fact "New legacy draft fails"
                      (command sonja :new-legacy-verdict-draft :id app-id)
                      => fail?)
                (facts "Legacy deletion still works and rewinds"
                       (command sonja :delete-pate-verdict :id app-id
                                :verdict-id vid1) => ok?
                       (check-verdict-date sonja app-id nil)
                       (let [{:keys [attachments tasks state]} (query-application sonja app-id)]
                         (count attachments) => 1
                         tasks => empty?
                         state => "submitted"))
                (fact "Approve application"
                      (command sonja :update-app-bulletin-op-description
                               :id app-id
                               :description "Donec non mauris quis mauris") => ok?
                      (command sonja :approve-application :id app-id
                               :lang "fi") => ok?)
                (facts "Legacy publishing works"
                       (fill-verdict sonja app-id vid3
                                     :kuntalupatunnus "888-10-15"
                                     :verdict-code "4" ;; Upheld partially ...
                                     :verdict-text "Vestibulum quis eros sit amet "
                                     :anto (timestamp "24.5.2018")
                                     :lainvoimainen (timestamp "3.6.2018"))
                       (fact "Legacy verdict cannot be published with modern command"
                             (command sonja :publish-pate-verdict :id app-id
                                      :verdict-id vid3) => fail?)
                       (command sonja :publish-legacy-verdict :id app-id
                                :verdict-id vid3) => ok?)
                (check-verdict-date sonja app-id (timestamp "24.5.2018"))
                (fact "Delete the verdict and rewind to sent"
                      (command sonja :delete-pate-verdict :id app-id
                               :verdict-id vid3) => ok?
                      (:state (query-application sonja app-id))
                      => "sent")
                (check-verdict-date sonja app-id nil)
                (fact "Disable Pate in Sipoo"
                      (command admin :set-organization-scope-pate-value
                               :permitType "R"
                               :municipality "753"
                               :value false) => ok?)
                (fact "Verdict cannot be published in complementNeeded state"
                      (command sonja :request-for-complement :id app-id) => ok?
                      (:state (query-application sonja app-id)) => "complementNeeded"
                      (let [{:keys [verdict-id]} (command sonja :new-legacy-verdict-draft
                                                          :id app-id)]
                        verdict-id => truthy
                        (fill-verdict sonja app-id verdict-id
                                      :kuntalupatunnus "888-10-15"
                                      :verdict-code "4" ;; Upheld partially ...
                                      :verdict-text "Vestibulum quis eros sit amet "
                                      :anto (timestamp "24.5.2018")
                                      :lainvoimainen (timestamp "3.6.2018"))
                        (command sonja :publish-legacy-verdict :id app-id
                                 :verdict-id verdict-id)
                        => (err :error.command-illegal-state)
                        (fact "Preview fetch works, though"
                              (:headers (raw sonja :preview-pate-verdict :id app-id
                                             :verdict-id verdict-id))
                              => (contains {"Content-Disposition" (contains "P\u00e4\u00e4t\u00f6sluonnos")}))
                        (fact "Approve application and publish"
                              (command sonja :approve-application :id app-id :lang "fi") => ok?
                              (command sonja :publish-legacy-verdict :id app-id
                                       :verdict-id verdict-id) => ok?
                              (:state (query-application sonja app-id)) => "verdictGiven")
                        (check-verdict-date sonja app-id (timestamp "24.5.2018"))))))))

(fact "Legacy TJ verdict"
      (let [{app-id :id} (create-and-submit-application pena
                                                        :operation :pientalo
                                                        :propertyId sipoo-property-id)
            {tj-app-id :id} (command sonja :create-foreman-application :id app-id
                                     :taskId "" :foremanRole "ei tiedossa" :foremanEmail "foreman@mail.com")]

        (fact "Approve application"
              (command sonja :update-app-bulletin-op-description
                       :id app-id
                       :description "Donec non mauris quis mauris") => ok?
              (command sonja :approve-application :id app-id :lang "fi") => ok?)

        (fact "Approve TJ application"
              (command sonja :change-permit-sub-type :id tj-app-id :permitSubtype "tyonjohtaja-hakemus") => ok?
              (command sonja :submit-application :id tj-app-id) => ok?
              (command sonja :approve-application :id tj-app-id :lang "fi") => ok?)

        (let [{:keys [verdict-id]} (command sonja :new-legacy-verdict-draft :id tj-app-id)]

          (fact "Fill and publish the TJ verdict"
                (fill-verdict sonja tj-app-id verdict-id
                              :kuntalupatunnus "TJ-123"
                              :verdict-code "1"
                              :verdict-text "TJ Verdict --special-data-to-be-found-from-tags--"
                              :anto (timestamp "9.8.2018")
                              :lainvoimainen (timestamp "19.8.2018"))
                (command sonja :publish-legacy-verdict :id tj-app-id :verdict-id verdict-id) => ok?)

          (check-verdict-date sonja tj-app-id (timestamp "9.8.2018"))
          (let [tj-app     (query-application sonja tj-app-id)
                tj-verdict (first (:pate-verdicts tj-app))]

            (fact "TJ application state is foremanVerdictGiven"
                  tj-app => (contains {:state "foremanVerdictGiven"}))

            (fact "TJ verdict has the given data"
                  (get-in tj-verdict [:data :handler]) => "Sonja Sibbo"
                  (get-in tj-verdict [:data :kuntalupatunnus]) => "TJ-123"
                  (get-in tj-verdict [:data :verdict-code]) => "1"
                  (get-in tj-verdict [:data :verdict-text]) => "TJ Verdict --special-data-to-be-found-from-tags--"
                  (get-in tj-verdict [:data :anto]) => (timestamp "9.8.2018")
                  (get-in tj-verdict [:data :lainvoimainen]) => (timestamp "19.8.2018")
                  (clojure.string/includes? (get-in tj-verdict [:published :tags]) "--special-data-to-be-found-from-tags--") => true)))))
