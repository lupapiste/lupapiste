(ns lupapalvelu.pate-legacy-itest
  (:require [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.pate-itest-util :refer :all]
            [midje.sweet :refer :all]
            [sade.util :as util]))

(apply-remote-minimal)

(defn err [error]
  (partial expected-failure? error))

(def timestamp util/to-millis-from-local-date-string)

(defn edit-legacy-verdict [app-id verdict-id path value]
  (let [result (command sonja :edit-pate-verdict :id app-id
                        :verdict-id verdict-id
                        :path (flatten [path])
                        :value value)]
    (fact {:midje/description (format "Edit verdict: %s -> %s" path value)}
      result => no-errors?)
    result))

(defn open-verdict [app-id verdict-id]
  (query sonja :pate-verdict :id app-id :verdict-id verdict-id))

(fact "Create and submit R application"
  (let [{app-id :id} (create-and-submit-application pena
                                                    :operation :pientalo
                                                    :propertyId sipoo-property-id)]
    (fact "Set Sonja as the application handler"
      (command sonja :upsert-application-handler :id app-id
               :userId sonja-id :roleId sipoo-general-handler-id))
    (fact "New legacy verdict draft"
      (let [{:keys [verdict-id]}     (command sonja :new-legacy-verdict-draft
                                              :id app-id)
            open                     (partial open-verdict app-id verdict-id)
            {:keys [verdict filled]} (open)
            edit                     (partial edit-legacy-verdict app-id verdict-id)]
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
                                                     "attachments"
                                                     "anto"
                                                     "lainvoimainen"
                                                     "kuntalupatunnus"]
                                                    :in-any-order
                                                    :gaps-ok)})
        (fact "Fill verdict"
          (edit :kuntalupatunnus "888-10-12")
          (edit :verdict-code "1") ;; Granted
          (edit :verdict-text "Lorem ipsum")
          (edit :anto (timestamp "21.5.2018"))
          (edit :lainvoimainen (timestamp "30.5.2018"))
          (open)
          => (contains {:filled  true
                        :verdict (contains {:data (contains {:kuntalupatunnus "888-10-12"
                                                             :verdict-code    "1"
                                                             :verdict-text    "Lorem ipsum"
                                                             :anto            (timestamp "21.5.2018")
                                                             :lainvoimainen   (timestamp "30.5.2018")})})}))

        (facts "Add review"
          (let [review-id (-> (edit :add-review true) :changes flatten second)]
            (fact "Add review name"
              (edit [:reviews review-id :name] "First review")
              => (contains {:filled false}))
            (fact "Add review type"
              (edit [:reviews review-id :type] :paikan-merkitseminen)
              => (contains {:filled true}))))

        (fact "Add condition"
          (let [condition-id (-> (edit :add-condition true) :changes flatten second)]
            (edit [:conditions condition-id :name] "Strict condition")
            => (contains {:filled true})))

        (fact "Add foreman"
          (let [foreman-id (-> (edit :add-foreman true) :changes flatten second)]
            (edit [:foremen foreman-id :role] "Some random foreman")
            => (contains {:filled true})))

        (fact "Verdict draf tis listed"
          (:verdicts (query sonja :pate-verdicts :id app-id))
          => (just [(contains {:id verdict-id :modified pos?})]))

        (facts "Publish verdict"
          (command sonja :publish-legacy-verdict :id app-id
                   :verdict-id verdict-id) => ok?
          (let [{:keys [tasks attachments state]} (query-application sonja app-id)]
            (fact "Application state is verdict given"
              state => "verdictGiven")
            (fact "Tasks have been created"
              tasks => (just [(contains {:taskname    "First review"
                                         :data        (contains {:katselmuksenLaji (contains {:value "rakennuksen paikan merkitseminen"})})
                                         :schema-info (contains {:name    "task-katselmus"
                                                                 :subtype "review"
                                                                 :type    "task"})
                                         :source      {:type "verdict" :id verdict-id}})
                              (contains {:taskname    "Strict condition"
                                         :schema-info (contains {:type "task"
                                                                 :name "task-lupamaarays"})
                                         :source      {:type "verdict" :id verdict-id}})
                              (contains {:taskname    "Some random foreman"
                                         :schema-info (contains {:type    "task"
                                                                 :name    "task-vaadittu-tyonjohtaja"
                                                                 :subtype "foreman"})
                                         :source      {:type "verdict" :id verdict-id}})]))
            (fact "Attachment has been created"
              attachments => (just [(contains {:target {:type "verdict" :id verdict-id}
                                               :type   {:type-id    "paatos"
                                                        :type-group "paatoksenteko"}
                                               :applicationState "verdictGiven"})]))))))))
