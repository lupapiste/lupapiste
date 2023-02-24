(ns lupapalvelu.review-assignment-itest
  (:require [lupapalvelu.automatic-assignment.factory :as factory]
            [lupapalvelu.fixture.core :as fixture]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.pate-itest-util :refer :all]
            [lupapalvelu.pate-legacy-itest-util :refer :all]
            [lupapalvelu.test-util :refer [in-text]]
            [midje.sweet :refer :all]
            [monger.operators :refer :all]
            [mount.core :as mount]
            [sade.core :refer [now]]
            [sade.date :refer [timestamp]]
            [sade.strings :as ss]
            [sade.util :as util]))

(def db-name (str "test_review_assignment_" (now)))
(def sipoo-org-id "753-R")
(def default-request {:message "Please, could I have a review?"
                      :contact {:name  "Coco Contact"
                                :email "contact@example.net"
                                :phone "12345678"}})

(defn assignment-task-ids
  ([application-id]
   (->> (mongo/select :assignments
                      (util/strip-nils {:application.id application-id})
                      [:targets])
        (mapcat :targets)
        (map :id)))
  ([]
   (assignment-task-ids nil)))

(defn upsert-filter [& {:as params}]
  (:filter (command sipoo :upsert-automatic-assignment-filter
                    :organizationId sipoo-org-id
                    :filter params)))

(defn requestable
  "Returns ids of tasks/reviews that can be required"
  [apikey app-id]
  (->> (query apikey :allowed-actions-for-category :id app-id :category :reviews)
       :actionsById
       (filter (fn [[_ actions]]
                 (-> actions :request-review :ok)))
       (map first)))

(defn make-legacy-verdict
  "Creates one legacy verdict with Kahvikokous reviews. Returns the verdict id."
  ([apikey app-id review-count]
   (let [{:keys [verdict-id]} (command apikey :new-legacy-verdict-draft
                                       :id app-id)]

     (facts "Fill and publish legacy verdict"
       verdict-id => truthy
       (fill-verdict apikey app-id verdict-id
                     :kuntalupatunnus "888-10-12"
                     :verdict-code    "1" ;; Granted
                     :verdict-text    "Lorem ipsum"
                     :handler         "Decider"
                     :anto            (timestamp "21.4.2021")
                     :lainvoimainen   (timestamp "30.4.2021"))
       (doseq [_ (range review-count)]
         (add-legacy-review apikey app-id verdict-id "Kahvikokous" :rakennekatselmus))
       (command apikey :publish-legacy-verdict :id app-id
                :verdict-id verdict-id) => ok?)
     verdict-id))
  ([apikey app-id]
   (make-legacy-verdict apikey app-id 1)))

(defn change-task-state [app-id task-id state]
  (fact {:midje/description (format "Change %s task %s -> %s" app-id task-id state)}
    (mongo/update-by-query :applications
                           {:_id   app-id
                            :tasks {$elemMatch {:id task-id}}}
                           {$set {:tasks.$.state state}}) => 1))

(mount/start #'mongo/connection)
(mongo/with-db db-name
  (fixture/apply-fixture "minimal")
  ;; Verdicts are fetched from remote
  (mongo/update-by-id :organizations sipoo-org-id
                      {$set {:krysp.R.url (str (server-address) "/dev/krysp")}})
  (with-local-actions
    (let [{filter-id :id} (upsert-filter :name "Morjens!" :rank 0
                                         :criteria {:reviews ["*morjens*"]}
                                         :target {:user-id ronja-id})]
      (fact "Morjens review filter created"
        filter-id => truthy)
      (fact "Prepare application: create, submit, fetch verdicts"
        (let [{app-id :id}       (create-and-submit-local-application pena
                                                                      :address "Review Road"
                                                                      :operation "pientalo"
                                                                      :propertyId sipoo-property-id)
              _                  (command sonja :check-for-verdict :id app-id)
              {:keys [primaryOperation
                      tasks]}    (query-application pena app-id)
              primary-op-id      (:id primaryOperation)
              {aloitus-id :id}   (util/find-by-key :taskname "Aloituskokous" tasks)
              {tarkastus-id :id} (util/find-by-key :taskname "Käyttöönottotarkastus" tasks)
              {loppu-id :id}     (util/find-by-key :taskname "loppukatselmus" tasks)
              {plan-id :id}      (util/find-by-key :taskname "Ilmanvaihtosuunnitelma" tasks)]
          primary-op-id => truthy
          aloitus-id => truthy
          tarkastus-id => truthy
          loppu-id => truthy
          (map :taskname tasks) => (just "Aloituskokous" "Käyttöönottotarkastus"
                                         "Radontekninen suunnitelma" "Ilmanvaihtosuunnitelma"
                                         "Vastaava työnjohtaja" "Vastaava IV-työnjohtaja"
                                         "Työnjohtaja" "loppukatselmus"
                                         "Valaistussuunnitelma" :in-any-order)
          (fact "Nothing can be yet requested"
            (let [allowed (->> (query pena :allowed-actions-for-category :id app-id
                                      :category :reviews)
                               :actionsById
                               (reduce-kv (fn [acc id actions]
                                            (update acc
                                                    (-> actions :request-review :text keyword)
                                                    conj id))
                                          {}))]
              (:error.no-matching-filters allowed)
              => (just aloitus-id tarkastus-id loppu-id :in-any-order)
              (count (:error.invalid-task-type allowed)) => 6
              (requestable pena app-id) => empty?))

          (fact "Only reviews match filter (rules are trimmed)"
            (-> (upsert-filter :name "Morjens!" :rank 0 :id filter-id
                               :criteria {:reviews [" *morjens* " "  *kokous"
                                                    "*suunnitelma  "]}
                               :target {:user-id ronja-id})
                :criteria :reviews)
            => ["*morjens*" "*kokous" "*suunnitelma"]
            (requestable pena app-id) => (just aloitus-id))

          (fact "Authority cannot request review"
            (command sonja :request-review :id app-id :taskId aloitus-id)
            => unauthorized?)

          (facts "Bad review requests"
            (fact "No matching filter"
              (command pena :request-review :id app-id :taskId loppu-id
                       :request default-request)
              => (err :error.no-matching-filters))
            (fact "Task is not review or does not exist"
              (command pena :request-review :id app-id :taskId plan-id
                       :request default-request)
              => (err :error.invalid-task-type)
              (command pena :request-review :id app-id :taskId "bad-id"
                       :request default-request)
              => (err :error.invalid-task-type))
            (fact "Bad operation-ids"
              (command pena :request-review :id app-id :taskId aloitus-id
                       :request (assoc default-request
                                       :operation-ids ["bad-id"]))
              => (err :error.invalid-operation-ids)))

          (fact "Successful review request"
            (command pena :request-review :id app-id :taskId aloitus-id
                     :request (assoc default-request
                                     :operation-ids [primary-op-id]))
            => ok?)

          (fact "Assignment is created"
            (let [[{ts :modified :as assignment}] (mongo/select :assignments {})]
              assignment => (just {:application {:address      "Review Road"
                                                 :id           app-id
                                                 :municipality "753"
                                                 :organization sipoo-org-id}
                                   :description "Aloituskokous"
                                   :filter-id   filter-id
                                   :id          truthy
                                   :modified    ts
                                   :recipient   {:firstName "Ronja"
                                                 :id        ronja-id
                                                 :lastName  "Sibbo"
                                                 :role      "authority"
                                                 :username  "ronja"}
                                   :states      [{:timestamp ts
                                                  :type      "created"
                                                  :user      {:firstName "Pena"
                                                              :id        "777777777777777777000020"
                                                              :lastName  "Panaani"
                                                              :role      "applicant"
                                                              :username  "pena"}}]
                                   :status      "active"
                                   :targets     [{:group     "review-request"
                                                  :id        aloitus-id
                                                  :timestamp ts}]
                                   :trigger     "review"})

              (fact "Task has a request"
                (->> (query-application pena app-id)
                     :tasks
                     (util/find-by-id aloitus-id)
                     :request)
                => {:created ts
                    :user    {:firstName "Pena"
                              :lastName  "Panaani"
                              :username  "pena"
                              :role      "applicant"
                              :id        pena-id}
                    :details (assoc default-request :operation-ids [primary-op-id])})))

          (fact "Request cannot be repeated"
            (command pena :request-review :id app-id :taskId aloitus-id
                     :request default-request)
            => (err :error.bad-review-request-state)
            (requestable pena app-id) => empty?)

          (facts "Cancel review request"
            (fact "Non-existing request cannot be canceled"
              (command pena :cancel-review-request :id app-id :taskId loppu-id)
              => (err :error.bad-review-request-state))
            (fact "Cancel the request"
              (command pena :cancel-review-request :id app-id :taskId aloitus-id) => ok?)
            (fact "Assignments are gone"
              (mongo/any? :assignments {}) => false)
            (fact "Request is gone"
              (->> (query-application pena app-id)
                   :tasks
                   (util/find-by-id aloitus-id)
                   :request)
              => nil))

          (fact "Make the canceled request again"
            (command pena :request-review :id app-id :taskId aloitus-id
                     :request (assoc default-request
                                     :operation-ids [primary-op-id]))
            => ok?)


          (fact "Fill the review and mark it partially done"
            (command sonja :update-task :id app-id :doc aloitus-id
                     :updates [["katselmus.tila" "osittainen"]
                               ["katselmus.pitoPvm" "8.4.2021"]
                               ["katselmus.pitaja" "Sonja Sibbo"]]) => ok?
            (command sonja :review-done :id app-id
                     :taskId aloitus-id  :lang "fi") => ok?)

          (fact "The assignment has been deleted"
            (mongo/any? :assignments {}) => false)

          (fact "Cancel request not possible for done reviews"
            (command pena :cancel-review-request :id app-id :taskId aloitus-id)
            => (err :error.command-illegal-state))

          (fact "New task has been created and the review can be requested"
            (let [[task-id] (requestable pena app-id)]
              (command pena :request-review :id app-id :taskId task-id
                       :request default-request) => ok?
              (mongo/count :assignments {}) => 1))

          (fact "Request not allowed for faulty reviews"
            (command sonja :mark-review-faulty :id app-id :taskId aloitus-id) => ok?
            (requestable pena app-id) => empty?)

          (fact "Create new review"
            (let [{moro-id :taskId
                   :as     result} (command sonja :create-task :id app-id
                                            :taskName "Soon morjens!"
                                            :schemaName "task-katselmus"
                                            :taskSubtype "ei tiedossa")]
              result => ok?
              moro-id => truthy
              (fact "Request review"
                (command pena :request-review :id app-id :taskId moro-id
                         :request default-request) => ok?)
              (fact "Another assignment created"
                (mongo/count :assignments {}) => 2)

              (fact "Fetching new verdicts nukes old verdict and thus also review assignments"
                (command sonja :check-for-verdict :id app-id) => ok?
                (mongo/select :assignments {})
                => (just (contains {:filter-id filter-id
                                    :targets   (just [(contains {:id moro-id})])})))

              (fact "Deleting task removes assigment"
                (command sonja :delete-task :id app-id :taskId moro-id) => ok?
                (mongo/any? :assignments {}) => false)))

          (facts "New (Pate legacy) verdict with review"
            (let [verdict-id (make-legacy-verdict sonja app-id)]
              (fact "Request legacy verdict review"
                (let [task-id (->> (query-application pena app-id)
                                   :tasks
                                   (util/find-by-key :taskname "Kahvikokous")
                                   :id)]
                  (command pena :request-review :id app-id :taskId task-id
                           :request default-request) => ok?
                  (mongo/count :assignments {}) => 1
                  (fact "Deleting legacy verdict deletes the assignment"
                    (command sonja :delete-pate-verdict :id app-id
                             :verdict-id verdict-id) => ok?
                    (mongo/any? :assignments {}) => false)))))

          (facts "Prune application review assignments"
            (let [{app-id :id}  (create-and-submit-local-application
                                  pena
                                  :address "Checkout Circle"
                                  :operation "purkaminen"
                                  :propertyId sipoo-property-id)
                  _             (make-legacy-verdict sonja app-id 3)
                  [r11 r12 r13
                   :as r1]      (requestable pena app-id)
                  {app-id2 :id} (create-and-submit-local-application
                                  pena
                                  :address "Inspection Inn"
                                  :operation "aita"
                                  :propertyId sipoo-property-id)
                  _             (make-legacy-verdict sonja app-id2 3)
                  [r21 r22 r23
                   :as r2]      (requestable pena app-id2)]
              (count r1) => 3
              (count r2) => 3
              (fact "No assignments"
                (mongo/any? :assignments {}) => false)
              (fact "Request review for every task"
                (doseq [task-id r1]
                  (command pena :request-review :id app-id :taskId task-id
                           :request default-request) => ok?)
                (doseq [task-id r2]
                  (command pena :request-review :id app-id2 :taskId task-id
                           :request default-request) => ok?))
              (fact "Assignments have been created"
                (mongo/count :assignments {}) => 6
                (assignment-task-ids) => (just (concat r1 r2) :in-any-order))
              (fact "Mark one task in each application sent and one faulty."
                (change-task-state app-id r11 :sent)
                (change-task-state app-id r12 :faulty_review_task)
                (change-task-state app-id2 r21 :sent)
                (change-task-state app-id2 r22 :faulty_review_task))
              (facts "Prune 1"
                (factory/prune-application-review-assignments app-id)
                (assignment-task-ids) => (just (cons r13 r2) :in-any-order))
              (facts "Prune 2"
                (factory/prune-application-review-assignments app-id2)
                (assignment-task-ids) => (just r13 r23 :in-any-order)))))

        (facts "application-operation-buildings"
          (let [{app-id :id
                 :as    application} (create-and-submit-local-application
                                       pena
                                       :operation "kerrostalo-rivitalo"
                                       :address "Operations Overpass"
                                       :propertyId sipoo-property-id)
                primary-id           (-> application :primaryOperation :id)
                _                    (command pena :add-operation :id app-id
                                              :operation "sisatila-muutos")
                secondary-id         (-> (query-application pena app-id)
                                         :secondaryOperations first :id)
                get-buildings        #(:buildings (query pena :application-operation-buildings
                                                         :id app-id))]
            (fact "Operations created"
              primary-id => truthy
              secondary-id => truthy)
            (fact "No buildings yet"
              (get-buildings) => [])
            (fact "Add building corresponding to primary operation"
              (mongo/update-by-id :applications app-id
                                  {$set {:buildings [{:buildingId  "build1"
                                                      :nationalId  "national1"
                                                      :foo         "bar"
                                                      :description "Description one"
                                                      :operationId primary-id}]}})
              (get-buildings) => [{:buildingId  "build1"
                                   :nationalId  "national1"
                                   :description "Description one"
                                   :opName      "kerrostalo-rivitalo"
                                   :opId        primary-id}])
            (fact "Operation description overrides building"
              (command pena :update-op-description :id app-id
                       :op-id primary-id
                       :desc "This is the new thing!") => ok?
              (get-buildings) => [{:buildingId  "build1"
                                   :nationalId  "national1"
                                   :description "This is the new thing!"
                                   :opName      "kerrostalo-rivitalo"
                                   :opId        primary-id}])
            (fact "Add building for secondary operation and one extra"
              (mongo/update-by-id :applications app-id
                                  {$push {:buildings {$each [{:operationId secondary-id
                                                              :one         "two"
                                                              :nationalId  "national2"}
                                                             {:operationId "weird-id"
                                                              :nationalId  "national-weird"
                                                              :buildingId  "building-weird"
                                                              :description "Looks weird"}]}}})
              (get-buildings) => [{:buildingId  "build1"
                                   :nationalId  "national1"
                                   :description "This is the new thing!"
                                   :opName      "kerrostalo-rivitalo"
                                   :opId        primary-id}
                                  {:nationalId "national2"
                                   :opId       secondary-id
                                   :opName     "sisatila-muutos"}])
            (facts "Review assignment email notifications"
              (fact "No assignments"
                (assignment-task-ids app-id) => empty?)
              (let [_         (make-legacy-verdict sonja app-id)
                    [task-id] (requestable pena app-id)]
                (mongo/update-by-id :applications app-id
                                    {$set {:buildings [{:buildingId  "build1"
                                                        :nationalId  "national1"
                                                        :foo         "bar"
                                                        :description "Description one"
                                                        :operationId primary-id}]}})
                (fact "Upsert filter with two email recipients and message"
                  (upsert-filter :name "Morjens!" :rank 0 :id filter-id
                                 :criteria {:reviews [" *morjens* " "  *kokous"
                                                      "*suunnitelma  "]}
                                 :target {:user-id ronja-id}
                                 :email {:emails  ["randy.random@example.com"
                                                   "luukas.lukija@sipoo.fi"]
                                         :message "Automatic filter message"})
                  => truthy)
                (last-email);; Clear inbox
                (fact "Request review"
                  (command pena :request-review :id app-id :taskId task-id
                           :request (assoc default-request
                                           :operation-ids [primary-id])) => ok?)
                (fact "One assignment created"
                  (assignment-task-ids app-id) => [task-id])
                (facts "Two emails sent"
                  ;; TODO: Add Sven, when localisations are available.
                  (let [[randy luukas] (map #(update % :body
                                                     (fn [body]
                                                       (-> body :html
                                                           (ss/replace "\n" " ")
                                                           (ss/replace #" \s+" " "))))
                                            (sent-emails))]
                    (fact "Randy is not a Lupapiste user"
                      randy => (contains {:subject "Lupapiste: Operations Overpass, Sipoo. Katselmus tilattu."
                                          :to      "randy.random@example.com"})
                      (in-text (:body randy)
                               "automaattitehtävän Morjens!" "Automatic filter message"
                               app-id "Asuinkerrostalon tai rivitalon rakentaminen"
                               "Operations Overpass, Sipoo" "tilattu katselmus Kahvikokous"
                               "Rakennukset, joita katselmus koskee"
                               "Asuinkerrostalon tai rivitalon rakentaminen, This is the new thing!, national1"
                               (:message default-request) "Tilaajan tiedot"
                               "Coco Contact, contact@example.net, 12345678"
                               (str "/app/fi/applicant#!/application/" app-id "/tasks")))
                    (fact "Luukas is an authority in Lupapiste"
                      luukas => (contains {:subject "Lupapiste: Operations Overpass, Sipoo. Katselmus tilattu."
                                           :to      "Luukas Lukija <luukas.lukija@sipoo.fi>"})
                      (in-text (:body luukas)
                               "automaattitehtävän Morjens!" "Automatic filter message"
                               app-id "Asuinkerrostalon tai rivitalon rakentaminen"
                               "Operations Overpass, Sipoo" "tilattu katselmus Kahvikokous"
                               "Rakennukset, joita katselmus koskee"
                               "Asuinkerrostalon tai rivitalon rakentaminen, This is the new thing!, national1"
                               (:message default-request) "Tilaajan tiedot"
                               "Coco Contact, contact@example.net, 12345678"
                               (str "/app/fi/authority#!/application/" app-id "/tasks")))))))))))))
