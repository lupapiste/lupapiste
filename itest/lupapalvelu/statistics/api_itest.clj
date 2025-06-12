(ns lupapalvelu.statistics.api-itest
  "Integration tests for statistics API endpoints."
  (:require [clojure.test :refer :all]
            [lupapalvelu.itest-system :as test-system]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.statistics.db :as stats-db]
            [lupapalvelu.test-util :refer [with-ts]]
            [monger.operators :refer :all]
            [sade.date :as date]
            [sade.env :as env]
            [sade.strings :as ss]
            [sade.util :as util]))

(use-fixtures :once
  (local-fixture-factory (-> {:db-name "test_statistics-api-itest"}
                             test-system/with-mock-laundry))
  (fn [f]
    (env/with-feature-value :statistics-processing true
      (f))))

;; ==========================================
;; Helper functions
;; ==========================================

(defn create-submitted-app
  "Creates and submits an R application, returns app-id"
  [apikey ts]
  (with-ts ts
    (let [{:keys [id]} (create-local-app apikey :operation "aita"
                                         :address "Entry Test Street")]
      (local-command apikey :submit-application :id id)
      id)))

(defn get-entries
  "Query processing entries for application"
  [apikey app-id]
  (query apikey :processing-entries :id app-id))

(defn get-processing-days
  "Query processing days for application"
  [apikey app-id]
  (:processing-days (query apikey :processing-days :id app-id)))

(defn ->shorthands [actions]
  (set (mapv #(case %
                :new-processing-request  :new-request
                :new-processing-delay    :new-delay
                :update-processing-entry :update
                :answer-processing-entry :answer
                :close-processing-entry  :close
                :toggle-entry-seen       :toggle)
             actions)))

(defn entry-auths [model entry-id]
  (->> (get model entry-id)
       (util/filter-map-by-val :ok)
       keys
       ->shorthands))

(defn email-sent [target-to target-subject & texts]
  (let [{:keys [to subject body]} (last-email)]
    (testing (format "To %s: %s" target-to target-subject)
      (is (= target-to to))
      (is (= target-subject subject))
      (is (every? #(ss/includes-i? (:plain body) %) texts)))))

(defn deadstamp [zoned]
  (some-> zoned date/end-of-day date/timestamp))

;; ==========================================
;; Entry CRUD Tests
;; ==========================================

(deftest create-processing-entry-test
  (let [app-id (create-submitted-app pena "15.1.2024 10.00")]
    (last-email) ;; Purge inbox
    (testing "Authority can create a request entry"
      (let [{:keys [entry auth-model]
             :as   result} (with-ts "16.1.2024 9.00"
                             (command sonja :new-processing-request
                                      :id app-id
                                      :delay-start (date/timestamp "17.1.2024")
                                      :created-text "Please provide floor plans"))
            entry-id       (:id entry)]
        (is (ok? result))
        (is (some? entry-id))
        (is (= :request (:type entry)))
        (is (= "Please provide floor plans" (:created-text entry)))
        (is (= #{:close :toggle} (entry-auths auth-model entry-id)))
        (email-sent "Pena Panaani <pena@example.com>" "Lupapiste: Täydennyspyyntö"
                    app-id "täydennyspyyntö" "Please provide")))

    (testing "Authority can create a delay entry."
      (let [{:keys [entry auth-model]
             :as   result} (with-ts "16.1.2024 10.00"
                             (command sonja :new-processing-delay
                                      :id app-id
                                      :delay-start (date/timestamp "17.1.2024")
                                      :created-text "Waiting for neighbor's statement"
                                      :explanation "Neighbor consultation required"))
            entry-id       (:id entry)]
        (is (ok? result))
        (is (= :delay (:type entry)))
        (is (= "Neighbor consultation required" (:explanation entry)))
        (is (= #{:close :toggle} (entry-auths auth-model entry-id)))
        (email-sent "Pena Panaani <pena@example.com>" "Lupapiste: Hakijasta johtuva viivästys"
                    app-id "viivästys" "waiting for neighbor")))

    (testing "Applicant cannot create entries"
      (let [result (with-ts "16.1.2024 11.00"
                     (command pena :new-processing-request
                              :id app-id
                              :delay-start (date/timestamp "18.1.2024")
                              :created-text "I want to add an entry"))]
        (is (fail? result))))

    (testing "Authority can update an existing entry"
      (let [{:keys [entries]} (get-entries sonja app-id)
            entry-id          (-> entries first :id)
            result            (with-ts "17.1.2024 9.00"
                                (command sonja :update-processing-entry
                                         :id app-id
                                         :entry-id entry-id
                                         :created-text "Updated: Please provide detailed floor plans"))]
        (is (ok? result))
        (is (= "Updated: Please provide detailed floor plans"
               (-> result :entry :created-text)))
        (is (= #{:close :toggle}
               (entry-auths (:auth-model result) entry-id)))
        (is (nil? (last-email))
            "No email sent for old entries")))

    (testing "Invalid entry-id fails"
      (let [result (with-ts "17.1.2024 10.00"
                     (command sonja :update-processing-entry
                              :id app-id
                              :entry-id "000000000000000000000000"
                              :created-text "This should fail"))]
        (is (fail? result))
        (is (= "error.not-found" (:text result)))))

    (testing "new-processing-request requires delay-start"
      (let [result (with-ts "17.1.2024 12.00"
                     (command sonja :new-processing-request
                              :id app-id
                              :created-text "Missing delay-start"))]
        (is (fail? result))
        (is (= "error.missing-parameters" (:text result)))))

    (testing "new-processing-delay requires delay-start"
      (let [result (with-ts "17.1.2024 13.00"
                     (command sonja :new-processing-delay
                              :id app-id
                              :created-text "Missing delay-start"
                              :explanation "This should fail"))]
        (is (fail? result))
        (is (= "error.missing-parameters" (:text result)))))))

(deftest delay-date-validation-test
  (let [app-id (create-submitted-app pena "1.2.2024 10.00")]

    (testing "Cannot create delay with bad dates."
      (let [result (with-ts "2.2.2024 9.00"
                     (command sonja :new-processing-delay
                              :id app-id
                              :delay-start (date/timestamp "5.2.2024 9.00")
                              :closed? true
                              :closed-ts (date/timestamp "4.2.2024 9.00")
                              :created-text "Delay for testing"))]
        (is (= "error.illegal-value:schema-validation" (:text result)))))

    (testing "Cannot close delay entry with closed-ts before delay-start"
      ;; Create a delay entry starting on 5.2.2024
      (let [{:keys [entry]} (with-ts "2.2.2024 9.00"
                              (command sonja :new-processing-delay
                                       :id app-id
                                       :delay-start (date/timestamp "5.2.2024 9.00")
                                       :created-text "Delay for testing"))
            entry-id        (:id entry)
            ;; Try to close it with a timestamp before delay-start (3.2.2024 < 5.2.2024)
            result          (with-ts "6.2.2024 9.00"
                              (command sonja :close-processing-entry
                                       :id app-id
                                       :entry-id entry-id
                                       :closed-ts (date/timestamp "3.2.2024 9.00")
                                       :closed-text "Trying to close before delay started"))]
        (is (fail? result))
        (is (= "error.invalid-date" (:text result)))))

    (testing "Can close delay entry with closed-ts equal to delay-start"
      (let [{:keys [entry]} (with-ts "7.2.2024 9.00"
                              (command sonja :new-processing-delay
                                       :id app-id
                                       :delay-start (date/timestamp "8.2.2024 9.00")
                                       :created-text "Another delay"))
            entry-id        (:id entry)
            result          (with-ts "9.2.2024 9.00"
                              (command sonja :close-processing-entry
                                       :id app-id
                                       :entry-id entry-id
                                       :closed-ts (date/timestamp "8.2.2024 9.00")
                                       :closed-text "Closing at exact delay start"))]
        (is (ok? result))
        (is (= (date/timestamp "8.2.2024 9.00") (-> result :entry :closed-ts)))))

    (testing "Can close delay entry with closed-ts after delay-start"
      (let [{:keys [entry]} (with-ts "10.2.2024 9.00"
                              (command sonja :new-processing-delay
                                       :id app-id
                                       :delay-start (date/timestamp "11.2.2024 9.00")
                                       :created-text "Yet another delay"))
            entry-id        (:id entry)
            result          (with-ts "15.2.2024 9.00"
                              (command sonja :close-processing-entry
                                       :id app-id
                                       :entry-id entry-id
                                       :closed-ts (date/timestamp "14.2.2024 9.00")
                                       :closed-text "Closing after delay start"))]
        (is (ok? result))
        (is (= (date/timestamp "14.2.2024 9.00") (-> result :entry :closed-ts)))))

    (testing "Request entries have no date validation for closing"
      ;; Create a request entry
      (let [{:keys [entry]} (with-ts "16.2.2024 9.00"
                              (command sonja :new-processing-request
                                       :id app-id
                                       :delay-start (date/timestamp "20.2.2024 9.00")
                                       :created-text "Request entry"))
            entry-id        (:id entry)
            ;; Close it with timestamp before delay-start - should succeed for requests
            result          (with-ts "21.2.2024 9.00"
                              (command sonja :close-processing-entry
                                       :id app-id
                                       :entry-id entry-id
                                       :closed-ts (date/timestamp "17.2.2024 9.00")
                                       :closed-text "Closing request before delay-start"))]
        (is (ok? result))))

    (testing "Cannot update delay-start to be after existing closed-ts"
      ;; Create and close a delay entry
      (let [{:keys [entry]} (with-ts "22.2.2024 9.00"
                              (command sonja :new-processing-delay
                                       :id app-id
                                       :delay-start (date/timestamp "23.2.2024 9.00")
                                       :created-text "Delay to be closed then modified"))
            entry-id        (:id entry)]
        ;; Close it on 25.2.2024
        (with-ts "26.2.2024 9.00"
          (command sonja :close-processing-entry
                   :id app-id
                   :entry-id entry-id
                   :closed-ts (date/timestamp "25.2.2024 9.00")
                   :closed-text "Closed"))
        ;; Try to update delay-start to 27.2.2024 (after closed-ts 25.2.2024)
        (let [result (with-ts "27.2.2024 9.00"
                       (command sonja :update-processing-entry
                                :id app-id
                                :entry-id entry-id
                                :delay-start (date/timestamp "27.2.2024 9.00")))]
          (is (fail? result))
          (is (= "error.invalid-date" (:text result))))))

    (testing "Can update delay-start to be before existing closed-ts"
      ;; Create and close a delay entry
      (let [{:keys [entry]} (with-ts "1.3.2024 9.00"
                              (command sonja :new-processing-delay
                                       :id app-id
                                       :delay-start (date/timestamp "5.3.2024 9.00")
                                       :created-text "Delay for delay-start update test"))
            entry-id        (:id entry)]
        ;; Close it on 10.3.2024
        (with-ts "11.3.2024 9.00"
          (command sonja :close-processing-entry
                   :id app-id
                   :entry-id entry-id
                   :closed-ts (date/timestamp "10.3.2024 9.00")
                   :closed-text "Closed"))
        ;; Update delay-start to 3.3.2024 (before closed-ts 10.3.2024) - should succeed
        (let [result (with-ts "12.3.2024 9.00"
                       (command sonja :update-processing-entry
                                :id app-id
                                :entry-id entry-id
                                :delay-start (date/timestamp "3.3.2024 9.00")))]
          (is (ok? result))
          (is (= (date/timestamp "3.3.2024 9.00") (-> result :entry :delay-start))))))))

(deftest answer-processing-entry-test
  (let [app-id (create-submitted-app pena "20.1.2024 10.00")]
    (is (ss/not-blank? app-id))
    ;; Create a request entry first and verify it succeeds
    (let [{:keys [entry] :as create-result}
          (with-ts "21.1.2024 9.00"
            (command sonja :new-processing-request
                     :id app-id
                     :delay-start (date/timestamp "22.1.2024")
                     :created-text "Please provide structural calculations"))
          entry-id (:id entry)]
      (testing "Entry was created successfully"
        (is (ok? create-result) (str "Failed to create entry: " (:text create-result)))
        (is (some? entry-id) "Expected entry to have an id"))

      (testing "Entry is visible to applicant"
        (let [{:keys [entries] :as entries-result} (get-entries pena app-id)]
          (is (ok? entries-result))
          (is (seq entries) "Expected entries to be visible to applicant")))

      (testing "Authority cannot answer a request entry"
        (let [{:keys [auth-models]} (get-entries sonja app-id)]
          (is (false? (contains? (entry-auths auth-models entry-id)
                                 :answer)))
          (is (fails-with? :error.unauthorized
                           (with-ts "22.1.2024 14.00"
                             (command sonja :answer-processing-entry
                                      :id app-id
                                      :entry-id entry-id
                                      :answered-text "Calculations attached as PDF"))))))

      (testing "Applicant can answer a request entry"
        (let [{:keys [auth-models]} (get-entries pena app-id)]
          (is (= #{:answer} (entry-auths auth-models entry-id)))
          (let [{:keys [entry auth-model]
                 :as   result} (with-ts "22.1.2024 14.00"
                                 (command pena :answer-processing-entry
                                          :id app-id
                                          :entry-id entry-id
                                          :answered-text "Calculations attached as PDF"))]
            (is (ok? result) (str "Answer failed: " (:text result)))
            (is (:answered-ts entry))
            (is (= "Calculations attached as PDF"
                   (:answered-text entry)))
            ;; After answering, answer action is still available for editing
            (is (= #{:answer} (entry-auths auth-model entry-id))))))

      (testing "Answer can be edited (re-answered)"
        (let [{:keys [entry auth-model]
               :as   result} (with-ts "23.1.2024 9.00"
                               (command pena :answer-processing-entry
                                        :id app-id
                                        :entry-id entry-id
                                        :answered-text "Updated: Calculations v2 attached"))]
          (is (ok? result) (str "Edit answer failed: " (:text result)))
          (is (= "Updated: Calculations v2 attached" (:answered-text entry)))
          (is (= #{:answer} (entry-auths auth-model entry-id)))))

      (testing "Cannot answer a closed entry"
        ;; Close the entry first
        (let [close-result (with-ts "23.1.2024 10.00"
                             (command sonja :close-processing-entry
                                      :id app-id
                                      :entry-id entry-id
                                      :closed-ts (date/timestamp "23.1.2024")
                                      :closed-text "Calculations verified"))]
          (is (ok? close-result) (str "Close failed: " (:text close-result))))
        ;; Now try to answer - should fail
        (let [result (with-ts "23.1.2024 11.00"
                       (command pena :answer-processing-entry
                                :id app-id
                                :entry-id entry-id
                                :answered-text "Trying to answer closed entry"))]
          (is (fail? result))
          (is (= "error.entry" (:text result))))))

    ;; Create another entry to test blank validation
    (let [{:keys [entry] :as create-result}
          (with-ts "23.1.2024 12.00"
            (command sonja :new-processing-request
                     :id app-id
                     :delay-start (date/timestamp "24.1.2024")
                     :created-text "Need more info"))
          new-entry-id (:id entry)]

      (testing "Second entry was created successfully"
        (is (ok? create-result)))

      (testing "Blank answer text is rejected"
        (let [result (with-ts "24.1.2024 9.00"
                       (command pena :answer-processing-entry
                                :id app-id
                                :entry-id new-entry-id
                                :answered-text "   "))]
          (is (fail? result)))))))

(deftest close-processing-entry-test
  (let [app-id (create-submitted-app pena "25.1.2024 10.00")]

    ;; Create a request entry
    (with-ts "26.1.2024 9.00"
      (command sonja :new-processing-request
               :id app-id
               :delay-start (date/timestamp "27.1.2024")
               :created-text "Provide energy certificate"))

    (let [{:keys [entries]} (get-entries sonja app-id)
          entry-id          (-> entries first :id)]

      (testing "Authority can close an entry"
        (let [closed-ts      (date/timestamp "28.1.2024 15.00")
              {:keys [entry auth-model]
               :as   result} (with-ts "28.1.2024 15.00"
                               (command sonja :close-processing-entry
                                        :id app-id
                                        :entry-id entry-id
                                        :closed-ts closed-ts
                                        :closed-text "Certificate received and approved"))]
          (is (ok? result))
          (is (= closed-ts (:closed-ts entry)))
          (is (= "Certificate received and approved"
                 (:closed-text entry)))
          (is (= #{:toggle} (entry-auths auth-model entry-id)))))

      (testing "Cannot close an already closed entry"
        (let [result (with-ts "29.1.2024 9.00"
                       (command sonja :close-processing-entry
                                :id app-id
                                :entry-id entry-id
                                :closed-ts (date/timestamp "29.1.2024 9.00")))]
          (is (fail? result))
          (is (= "error.entry" (:text result)))))

      (testing "Applicant cannot close entries"
        ;; Create another entry
        (with-ts "29.1.2024 10.00"
          (command sonja :new-processing-delay
                   :id app-id
                   :delay-start (date/timestamp "30.1.2024")
                   :created-text "Processing paused"))
        (let [{:keys [entries]} (get-entries sonja app-id)
              new-entry-id      (->> entries (filter #(not (:closed-ts %))) first :id)
              result            (with-ts "30.1.2024 9.00"
                                  (command pena :close-processing-entry
                                           :id app-id
                                           :entry-id new-entry-id
                                           :closed-ts (date/timestamp "30.1.2024 9.00")))]
          (is (fail? result)))))))

(deftest deleted-entry-test
  (let [app-id (create-submitted-app pena "1.2.2024 10.00")]

    ;; Create a request entry
    (with-ts "2.2.2024 9.00"
      (command sonja :new-processing-request
               :id app-id
               :delay-start (date/timestamp "3.2.2024")
               :created-text "Entry to be deleted"))

    (let [{:keys [entries]} (get-entries sonja app-id)
          entry-id          (-> entries first :id)]

      (testing "Authority can mark entry as deleted"
        (let [result (with-ts "2.2.2024 10.00"
                       (command sonja :update-processing-entry
                                :id app-id
                                :entry-id entry-id
                                :deleted? true))]
          (is (ok? result))
          (is (true? (-> result :entry :deleted?)))))

      (testing "Cannot answer a deleted entry"
        (let [result (with-ts "3.2.2024 9.00"
                       (command pena :answer-processing-entry
                                :id app-id
                                :entry-id entry-id
                                :answered-text "Trying to answer deleted"))]
          (is (fail? result))
          (is (= "error.entry" (:text result)))))

      (testing "Authority can undo deletion"
        (let [result (with-ts "2.2.2024 10.00"
                       (command sonja :update-processing-entry
                                :id app-id
                                :entry-id entry-id
                                :deleted? false))]
          (is (ok? result))
          (is (not (-> result :entry :deleted?))))))))

;; ==========================================
;; Query Tests
;; ==========================================

(deftest processing-entries-query-test
  (let [app-id (create-submitted-app pena "5.2.2024 10.00")]

    ;; Create multiple entries
    (with-ts "6.2.2024 9.00"
      (command sonja :new-processing-request
               :id app-id
               :delay-start (date/timestamp "7.2.2024")
               :created-text "First request"))

    (with-ts "6.2.2024 10.00"
      (command sonja :new-processing-delay
               :id app-id
               :delay-start (date/timestamp "7.2.2024")
               :created-text "First delay"
               :explanation "Waiting for expert"))

    (testing "Authority sees all entries with auth-models"
      (let [{:keys [entries auth-models]} (get-entries sonja app-id)
            request-entry                 (util/find-by-key :type :request entries)
            delay-entry                   (util/find-by-key :type :delay entries)]
        (is (= 2 (count entries)))
        (is request-entry)
        (is delay-entry)
        (is (= #{:toggle :close} (entry-auths auth-models (:id request-entry))))
        (is (= #{:toggle :close} (entry-auths auth-models (:id delay-entry))))))

    (testing "Applicant sees entries with appropriate auth-models"
      (let [{:keys [entries auth-models]} (get-entries pena app-id)
            request-entry                 (util/find-by-key :type :request entries)
            delay-entry                   (util/find-by-key :type :delay entries)]
        (is (= 2 (count entries)))
        (is (= #{:answer} (entry-auths auth-models (:id request-entry))))
        (is (= #{:answer} (entry-auths auth-models (:id delay-entry))))))

    ;; Delete one entry
    (let [{:keys [entries]} (get-entries sonja app-id)
          entry-id          (-> entries first :id)]
      (with-ts "7.2.2024 9.00"
        (command sonja :update-processing-entry
                 :id app-id
                 :entry-id entry-id
                 :deleted? true)))

    (testing "Authority still sees deleted entries"
      (let [{:keys [entries auth-models]} (get-entries sonja app-id)
            deleted-entry                 (util/find-first :deleted? entries)]
        (is (= 2 (count entries)))
        (is deleted-entry)
        (is (= #{:close} (entry-auths auth-models (:id deleted-entry))))))

    (testing "Applicant does not see deleted entries"
      (let [{:keys [entries auth-models]} (get-entries pena app-id)]
        (is (= 1 (count entries)))
        (is (not-any? :deleted? entries))
        ;; Only one auth-model for the non-deleted entry
        (is (= 1 (count auth-models)))))))

(deftest processing-entries-tab-query-test
  (let [app-id (create-submitted-app pena "10.2.2024 10.00")]

    (testing "Authority can see tab from open state onwards"
      (is (ok? (query sonja :processing-entries-tab :id app-id))))

    (testing "Applicant cannot see tab without entries"
      (is (fail? (query pena :processing-entries-tab :id app-id))))

    ;; Configure processing complexity
    (with-ts "10.2.2024 11.00"
      (command sonja :set-processing-complexity :id app-id :complexity "medium"))

    (testing "Applicant still cannot see tab without entries even when processing is configured"
      (is (fail? (query pena :processing-entries-tab :id app-id))))

    ;; Create an entry
    (with-ts "11.2.2024 9.00"
      (command sonja :new-processing-request
               :id app-id
               :delay-start (date/timestamp "12.2.2024")
               :created-text "Now there's an entry"))

    (testing "Applicant can see tab when there are entries"
      (is (ok? (query pena :processing-entries-tab :id app-id))))

    ;; Delete the entry
    (let [{:keys [entries]} (get-entries sonja app-id)
          entry-id          (-> entries first :id)]
      (with-ts "11.2.2024 10.00"
        (command sonja :update-processing-entry
                 :id app-id
                 :entry-id entry-id
                 :deleted? true)))

    (testing "Applicant cannot see tab when all entries are deleted"
      ;; Deleted entries don't count for applicant visibility
      (is (fail? (query pena :processing-entries-tab :id app-id))))

    (testing "Authority can still see tab regardless of entries"
      (is (ok? (query sonja :processing-entries-tab :id app-id))))))

;; ==========================================
;; Processing Calculation with Delays
;; ==========================================

(deftest delay-entries-affect-processing-test
  ;; Note: Using dates where weekends affect deadline calculation
  ;; 12.2.2024 is Monday, 12.5.2024 (deadline) is Sunday -> adjusted to 13.5.2024
  (let [app-id (create-submitted-app pena "12.2.2024 9.00")]

    ;; Set processing complexity first (required for processing to be active)
    (with-ts "12.2.2024 10.00"
      (command sonja :set-processing-complexity :id app-id :complexity "medium"))

    (testing "Initial processing days without delays"
      ;; Query returns STORED data from when complexity was set (12.2.2024 10:00)
      ;; At that time: used=0, remaining=91 (calendar days from 12.2 to 13.5)
      (let [proc (with-ts "13.2.2024 10.00"
                   (get-processing-days pena app-id))]
        (is (= {:status           :running :complexity :medium
                :used             0        :remaining  91
                :used-period      {:y 0 :m 0 :d 0}
                :remaining-period {:y 0 :m 3 :d 1}
                :delays           0        :requests   0
                :deadline         (deadstamp "13.5.2024")}
               proc))))

    (testing "Update/calculate processing"
      ;; update-processing! recalculates at current time (13.2.2024 10:00)
      ;; Now used=1 (one day elapsed), remaining=90
      (is (= {:status           :running :complexity :medium
              :used             1        :remaining  90
              :used-period      {:y 0 :m 0 :d 1}
              :remaining-period {:y 0 :m 3 :d 0}
              :delays           0        :requests   0
              :deadline         (deadstamp "13.5.2024")}
             (with-ts "13.2.2024 10.00"
               (stats-db/update-processing! (mongo/by-id :applications app-id))))))

    ;; Create a delay entry starting Tuesday
    (let [{{delay-id :id} :entry} (with-ts "13.2.2024 10.00"
                                    (command sonja :new-processing-delay
                                             :id app-id
                                             :delay-start (date/timestamp "13.2.2024 10.00")
                                             :created-text "Waiting for additional review"
                                             :explanation "External review needed"))]

      (testing "Processing status changes to delayed"
        ;; At delay creation, date/now = delay-start, so before? returns false
        ;; Status is stored as :running at that instant, requests=1
        ;; Query returns STORED data - deadline not yet shifted
        (let [proc (with-ts "14.2.2024 9.00"
                     (get-processing-days pena app-id))]
          (is (= {:status           :running :complexity :medium
                  :used             1        :remaining  90
                  :used-period      {:y 0 :m 0 :d 1}
                  :remaining-period {:y 0 :m 3 :d 0}
                  :delays           0        :requests   1
                  :deadline         (deadstamp "13.5.2024")
                  :unseen?          true}
                 proc))))

      ;; Close the delay on Friday (delay: 13.2 10:00 to 16.2 17:00 = 3 days 7 hours)
      (with-ts "16.2.2024 17.00"
        (command sonja :close-processing-entry
                 :id app-id
                 :entry-id delay-id
                 :closed-ts (date/timestamp "16.2.2024 17.00")
                 :closed-text "Review completed")))

    (testing "Processing status returns to running after delay is closed. The closing updates processing"
      ;; Delay was 3 days 7 hours = 79 hours
      ;; Original deadline: 13.5.2024 23:59:59
      ;; New deadline: 13.5.2024 + 3 days 7 hours = 16.5.2024 ~7:00 -> end-of-day = 16.5.2024 23:59:59
      (let [proc (with-ts "16.2.2024 17.00"
                   (get-processing-days pena app-id))]
        (is (= {:status           :running :complexity :medium
                :used             1        :remaining  90
                :used-period      {:y 0 :m 0 :d 1}
                :remaining-period {:y 0 :m 3 :d 0}
                :delays           3        :requests   0
                :deadline         (deadstamp "16.5.2024")
                :unseen?          true}
               proc))))))

(deftest multiple-delays-test
  ;; Submitted: 25.2.2024 9:00 (Sunday!)
  ;; Deadline: 25.2.2024 + 3 months = 25.5.2024 (Saturday) -> 27.5.2024 (Monday) 23:59:59
  (let [app-id (create-submitted-app pena "25.2.2024 9.00")]
    ;; Set processing complexity first (required for processing to be active)
    (with-ts "25.2.2024 10.00"
      (is (= {:complexity       :medium
              :deadline         (deadstamp "27.5.2024")
              :delays           0
              :remaining        92
              :remaining-period {:y 0 :m 3 :d 2}
              :requests         0
              :status           :running
              :used             0
              :used-period      {:y 0 :m 0 :d 0}}
             (:processing-days (command sonja :set-processing-complexity
                                        :id app-id :complexity "medium")))))

    ;; Create first delay
    (with-ts "26.2.2024 9.00"
      (command sonja :new-processing-delay
               :id app-id
               :delay-start (date/timestamp "26.2.2024 9.00")
               :created-text "First delay"
               :explanation "Reason 1"))

    ;; Create second delay (overlapping)
    (with-ts "27.2.2024 9.00"
      (command sonja :new-processing-delay
               :id app-id
               :delay-start (date/timestamp "27.2.2024 9.00")
               :created-text "Second delay"
               :explanation "Reason 2"))

    (testing "Multiple active delays"
      (let [{:keys [entries]} (get-entries sonja app-id)
            delays            (filter #(= :delay (:type %)) entries)]
        (is (= 2 (count delays)))))

    ;; Close first delay
    (let [{:keys [entries]} (get-entries sonja app-id)
          first-delay-id    (->> entries
                                 (filter #(= :delay (:type %)))
                                 (sort-by :delay-start)
                                 first
                                 :id)]
      (with-ts "28.2.2024 9.00"
        (command sonja :close-processing-entry
                 :id app-id
                 :entry-id first-delay-id
                 :closed-ts (date/timestamp "28.2.2024 9.00"))))

    (testing "Still delayed while one delay remains open"
      ;; At 28.2.2024 10:00, merged delay is from 26.2.2024 9:00 to cutoff (2 days 1 hour)
      ;; Deadline shifts by accumulated delay: 1717016399999 = 29.5.2024 23:59:59.999
      (let [proc (with-ts "28.2.2024 10.00"
                   (get-processing-days pena app-id))]
        (is (= {:status           :delayed :complexity :medium
                :used             1        :remaining  91
                :used-period      {:y 0 :m 0 :d 1}
                :remaining-period {:y 0 :m 3 :d 1}
                :delays           2        :requests   1
                :deadline         (deadstamp "29.5.2024")
                :unseen?          true}
               proc))))))

;; ==========================================
;; Entry Visibility (seen-by) Tests
;; ==========================================

(deftest mark-entries-seen-test
  (let [app-id (create-submitted-app pena "1.3.2024 10.00")]

    ;; Set processing complexity first (required for processing-days to work properly)
    (with-ts "1.3.2024 11.00"
      (command sonja :set-processing-complexity :id app-id :complexity "medium"))

    ;; Create entries
    (with-ts "2.3.2024 9.00"
      (command sonja :new-processing-request
               :id app-id
               :delay-start (date/timestamp "3.3.2024")
               :created-text "First request"))

    (with-ts "2.3.2024 10.00"
      (command sonja :new-processing-request
               :id app-id
               :delay-start (date/timestamp "3.3.2024")
               :created-text "Second request"))

    (testing "Entries are marked as seen by creator (authority)"
      (let [{:keys [entries]} (get-entries sonja app-id)]
        (is (every? :seen-by? entries))))

    (testing "Entries are not seen by applicant initially"
      (let [{:keys [entries]} (get-entries pena app-id)]
        (is (not-any? :seen-by? entries))))

    (testing "Applicant can mark entries as seen"
      (let [{:keys [entries]} (get-entries pena app-id)
            entry-ids         (mapv :id entries)
            result            (with-ts "3.3.2024 9.00"
                                (command pena :mark-entries-seen
                                         :id app-id
                                         :entry-ids entry-ids))]
        (is (ok? result))))

    (testing "Entries are now seen by applicant"
      (let [{:keys [entries]} (get-entries pena app-id)]
        (is (every? :seen-by? entries))))

    (testing "Processing days includes unseen flag"
      ;; Create new entry that applicant hasn't seen
      (with-ts "4.3.2024 9.00"
        (command sonja :new-processing-request
                 :id app-id
                 :delay-start (date/timestamp "5.3.2024")
                 :created-text "New unseen request"))
      (let [proc (with-ts "4.3.2024 10.00"
                   (get-processing-days pena app-id))]
        (is (true? (:unseen? proc)))))))

(deftest toggle-entry-seen-test
  (let [app-id (create-submitted-app pena "5.3.2024 10.00")]

    ;; Set processing complexity
    (with-ts "5.3.2024 11.00"
      (command sonja :set-processing-complexity :id app-id :complexity "medium"))

    ;; Create an entry
    (with-ts "6.3.2024 9.00"
      (command sonja :new-processing-request
               :id app-id
               :delay-start (date/timestamp "7.3.2024")
               :created-text "Toggle seen test entry"))

    (let [{:keys [entries]} (get-entries sonja app-id)
          entry-id          (-> entries first :id)]

      (testing "Entry is seen by creator"
        (is (-> entries first :seen-by?)))

      (testing "Entry is not seen by applicant initially"
        (let [{:keys [entries]} (get-entries pena app-id)]
          (is (not (-> entries first :seen-by?)))))

      (testing "Applicant marks entry as seen"
        (is (ok? (with-ts "6.3.2024 10.00"
                   (command pena :mark-entries-seen
                            :id app-id
                            :entry-ids [entry-id]))))
        (let [{:keys [entries]} (get-entries pena app-id)]
          (is (-> entries first :seen-by?))))

      (testing "Authority can toggle entry as unseen"
        (is (ok? (with-ts "6.3.2024 11.00"
                   (command sonja :toggle-entry-seen
                            :id app-id
                            :entry-id entry-id
                            :seen false))))
        ;; Authority's own seen status is now false
        (let [{:keys [entries]} (get-entries sonja app-id)]
          (is (not (-> entries first :seen-by?)))))

      (testing "Entry is still marked seen by applicant"
        (let [{:keys [entries]} (get-entries pena app-id)]
          (is (-> entries first :seen-by?))))

      (testing "Authority can toggle entry back to seen"
        (is (ok? (with-ts "6.3.2024 12.00"
                   (command sonja :toggle-entry-seen
                            :id app-id
                            :entry-id entry-id
                            :seen true))))
        (let [{:keys [entries]} (get-entries sonja app-id)]
          (is (-> entries first :seen-by?))))

      (testing "Cannot toggle seen on deleted entry"
        ;; Delete the entry
        (with-ts "6.3.2024 13.00"
          (command sonja :update-processing-entry
                   :id app-id
                   :entry-id entry-id
                   :deleted? true))
        (let [result (with-ts "6.3.2024 14.00"
                       (command sonja :toggle-entry-seen
                                :id app-id
                                :entry-id entry-id
                                :seen true))]
          (is (fail? result))
          (is (= "error.entry" (:text result))))))))

;; ==========================================
;; Processing Days Query Tests
;; ==========================================

(deftest processing-days-query-test
  ;; Submitted: 8.4.2024 9:00 (Monday)
  ;; Deadline: 8.4.2024 + 3 months = 8.7.2024 (Monday) -> no adjustment, 8.7.2024 23:59:59
  (let [app-id (create-submitted-app pena "8.4.2024 9.00")]

    (testing "Processing days returns no-complexity status before complexity is set"
      (let [result (query pena :processing-days :id app-id)]
        (is (ok? result))
        (is (= :no-complexity (-> result :processing-days :status)))))

    ;; Set processing complexity
    (with-ts "8.4.2024 10.00"
      (command sonja :set-processing-complexity :id app-id :complexity "medium"))

    (testing "Authority can query processing days"
      (let [result (query sonja :processing-days :id app-id)]
        (is (ok? result))
        ;; Calendar days: remaining=91 (days from 8.4.2024 to 8.7.2024 end-of-day)
        (is (= {:status           :running :complexity :medium
                :used             0        :remaining  91
                :used-period      {:y 0 :m 0 :d 0}
                :remaining-period {:y 0 :m 3 :d 0}
                :delays           0        :requests   0
                :deadline         (deadstamp "8.7.2024")}
               (:processing-days result)))))

    (testing "Applicant can query processing days"
      (let [result (query pena :processing-days :id app-id)]
        (is (ok? result))
        (is (= :running (-> result :processing-days :status)))))

    (testing "Processing days includes request count"
      ;; Create some requests
      (with-ts "9.4.2024 9.00"
        (command sonja :new-processing-request
                 :id app-id
                 :delay-start (date/timestamp "10.4.2024")
                 :created-text "First request"))
      (with-ts "9.4.2024 10.00"
        (command sonja :new-processing-request
                 :id app-id
                 :delay-start (date/timestamp "10.4.2024")
                 :created-text "Second request"))
      (let [result (query sonja :processing-days :id app-id)]
        (is (= 2 (-> result :processing-days :requests)))))

    (testing "Answered requests are not counted as open"
      (let [{:keys [entries]} (get-entries sonja app-id)
            entry-id          (-> entries first :id)]
        (with-ts "10.4.2024 9.00"
          (command pena :answer-processing-entry
                   :id app-id
                   :entry-id entry-id
                   :answered-text "Here is my answer")))
      (let [result (query sonja :processing-days :id app-id)]
        (is (= 1 (-> result :processing-days :requests)))))))

;; ==========================================
;; Set Processing Complexity Tests
;; ==========================================

(deftest set-processing-complexity-test
  (let [app-id (create-submitted-app pena "11.4.2024 9.00")]

    (testing "Authority can set processing complexity"
      (let [result (with-ts "11.4.2024 10.00"
                     (command sonja :set-processing-complexity
                              :id app-id
                              :complexity "medium"))]
        (is (ok? result))
        (is (:processing-days result))
        (is (= :medium (-> result :processing-days :complexity)))))

    (testing "Authority can change processing complexity"
      (let [result (with-ts "11.4.2024 11.00"
                     (command sonja :set-processing-complexity
                              :id app-id
                              :complexity "extra-large"))]
        (is (ok? result))
        (is (= :extra-large (-> result :processing-days :complexity)))))

    (testing "Applicant cannot set processing complexity"
      (let [result (with-ts "11.4.2024 12.00"
                     (command pena :set-processing-complexity
                              :id app-id
                              :complexity 1))]
        (is (fail? result))))

    (testing "Invalid complexity value is rejected"
      (let [result (with-ts "11.4.2024 13.00"
                     (command sonja :set-processing-complexity
                              :id app-id
                              :complexity -1))]
        (is (fail? result)))
      (let [result (with-ts "11.4.2024 14.00"
                     (command sonja :set-processing-complexity
                              :id app-id
                              :complexity 10))]
        (is (fail? result))))

    (testing "Supported complexity values give correct deadlines"
      ;; Remaining days are calendar days from query time to deadline (end of day).
      ;; Deadlines are adjusted forward to work days if they fall on weekends.
      ;; 11.5.2024 is Saturday -> adjusted to 13.5.2024 (Monday)
      ;; 11.7.2024 is Thursday -> no adjustment
      ;; 11.10.2024 is Friday -> no adjustment
      (doseq [{:keys [complexity]
               :as   m} [{:complexity       :special :remaining 31
                          :remaining-period {:y 0 :m 1 :d 1}
                          :deadline         (deadstamp "13.5.2024")}
                         {:complexity       :medium :remaining 90
                          :remaining-period {:y 0 :m 2 :d 29}
                          :deadline         (deadstamp "11.7.2024")}
                         {:complexity :extra-large :remaining 182
                          :remaining-period {:y 0 :m 5 :d 29}
                          :deadline   (deadstamp "11.10.2024")}]
              :let      [{proc :processing-days} (with-ts "12.4.2024 9.00"
                                                   (command sonja :set-processing-complexity
                                                            :id app-id
                                                            :complexity (name complexity)))]]
        (testing (str "Complexity: " (name complexity))
          (is (= m (select-keys proc [:complexity :remaining :remaining-period
                                      :deadline]))))))))

;; =================================================
;; Application Cancelling and return to draft Tests
;; =================================================

(defn has-stats? [app-id]
  (mongo/any? :applications {:_id app-id :_stats {$exists true}}))

(deftest application-cancelling-test
  (let [app-id (create-submitted-app pena "1.9.2025 9.00")]
    (testing "Processing logdata no-complexity status added"
      (let [result (query pena :processing-days :id app-id)]
        (is (ok? result))
        (is (= :no-complexity (-> result :processing-days :status)))
        (is (true? (has-stats? app-id)))))
    (testing "No stats data should be present after cancelling application"
      (let [result (command pena :cancel-application
                            :id app-id
                            :text "Muutin mieleni."
                            :lang "fi")
            processing-logdata (query pena :processing-logdata :id app-id)
            application-after-canceling (query pena :application :id app-id)]
        (is (:ok result))
        (is (false? (has-stats? app-id)))
        (is (= {:state "canceled" :ok false :text "error.command-illegal-state"}
               processing-logdata))
        (is (nil? (get-in application-after-canceling [:application :submitted])))))))

(deftest return-to-draft-test
  (let [app-id (create-submitted-app pena "28.12.2025 10.00")]
    (env/with-feature-value :return-to-draft true
      (testing "Statistics are removed when application is reverted to draft"
        (is (true? (has-stats? app-id)))
        (is (:ok (command sonja :return-to-draft :id app-id
                          :text "Takaisin lähtöruutuun."
                          :lang "fi")))
        (is (false? (has-stats? app-id)))))))

;; ==========================================
;; Processing Logdata Tests
;; ==========================================

(deftest processing-logdata-query-test
  (let [app-id (create-submitted-app pena "13.4.2024 9.00")]

    (testing "Processing logdata works with no-complexity status"
      (let [result (query pena :processing-logdata :id app-id)]
        (is (ok? result))
        (is (= :no-complexity (-> result :processing-logdata :processing :status)))))

    ;; Set processing complexity
    (with-ts "13.4.2024 10.00"
      (command sonja :set-processing-complexity :id app-id :complexity "medium"))

    (testing "Authority can query processing logdata"
      (let [result (query sonja :processing-logdata :id app-id)]
        (is (ok? result))
        (is (:processing-logdata result))
        (is (number? (-> result :processing-logdata :submitted-ts)))
        (is (map? (-> result :processing-logdata :processing)))
        (is (boolean? (:unseen? result)))))

    (testing "Applicant can query processing logdata"
      (let [result (query pena :processing-logdata :id app-id)]
        (is (ok? result))
        (is (:processing-logdata result))))

    (testing "Logdata includes delay information"
      ;; Create a delay entry
      (with-ts "14.4.2024 9.00"
        (command sonja :new-processing-delay
                 :id app-id
                 :delay-start (date/timestamp "14.4.2024 9.00")
                 :created-text "Delay for testing"
                 :explanation "Testing delay in logdata"))
      (let [result (query sonja :processing-logdata :id app-id)
            delays (-> result :processing-logdata :delays)]
        (is (= 1 (count delays)))
        (is (number? (-> delays first :start-ts)))
        (is (= "Delay for testing" (-> delays first :text)))
        (is (= "Testing delay in logdata" (-> delays first :explanation)))
        (is (nil? (-> delays first :end-ts)))))

    (testing "Closed delay has end-ts in logdata"
      (let [{:keys [entries]} (get-entries sonja app-id)
            delay-id          (->> entries (filter #(= :delay (:type %))) first :id)]
        (with-ts "15.4.2024 9.00"
          (command sonja :close-processing-entry
                   :id app-id
                   :entry-id delay-id
                   :closed-ts (date/timestamp "15.4.2024 9.00"))))
      (let [result (query sonja :processing-logdata :id app-id)
            delays (-> result :processing-logdata :delays)]
        (is (number? (-> delays first :end-ts)))))

    (testing "Unseen flag reflects user's seen status"
      ;; Create a new entry that pena hasn't seen
      (with-ts "16.4.2024 9.00"
        (command sonja :new-processing-request
                 :id app-id
                 :delay-start (date/timestamp "17.4.2024")
                 :created-text "Unseen entry"))
      (let [result (query pena :processing-logdata :id app-id)]
        (is (true? (:unseen? result))))
      ;; Mark as seen
      (let [{:keys [entries]} (get-entries pena app-id)
            entry-ids         (mapv :id entries)]
        (with-ts "16.4.2024 10.00"
          (command pena :mark-entries-seen
                   :id app-id
                   :entry-ids entry-ids)))
      (let [result (query pena :processing-logdata :id app-id)]
        (is (false? (:unseen? result)))))))

;; ==========================================
;; Processing Log PDF Tests
;; ==========================================

(deftest processing-log-pdf-test
  (let [app-id (create-submitted-app pena "18.4.2024 9.00")]

    (testing "PDF requires processing to be configured"
      (let [result (local-raw sonja :processing-log-pdf :id app-id :lang "fi")]
        (is (not= 200 (:status result)))))

    ;; Set processing complexity
    (with-ts "18.4.2024 10.00"
      (command sonja :set-processing-complexity :id app-id :complexity "medium"))

    (testing "Authority can download processing log PDF"
      (let [result (local-raw sonja :processing-log-pdf :id app-id :lang "fi")]
        (is (= 200 (:status result)))
        (is (= "application/pdf" (get-in result [:headers "Content-Type"])))))

    (testing "Applicant can download processing log PDF"
      (let [result (local-raw pena :processing-log-pdf :id app-id :lang "fi")]
        (is (= 200 (:status result)))))

    (testing "PDF supports different languages"
      (doseq [lang ["fi" "sv" "en"]]
        (let [result (local-raw sonja :processing-log-pdf :id app-id :lang lang)]
          (is (= 200 (:status result))))))

    (testing "Invalid language is rejected"
      (let [result (local-raw sonja :processing-log-pdf :id app-id :lang "de")]
        (is (not= 200 (:status result)))))))

;; ==========================================
;; Feature Flag Tests
;; ==========================================

(deftest feature-flag-disabled-test
  (env/with-feature-value :statistics-processing false
    (let [app-id (create-submitted-app pena "10.3.2024 10.00")]

      (testing "Processing days query fails when feature is disabled"
        (let [result (query pena :processing-days :id app-id)]
          (is (fail? result))))

      (testing "New processing request fails when feature is disabled"
        (let [result (with-ts "11.3.2024 9.00"
                       (command sonja :new-processing-request
                                :id app-id
                                :delay-start (date/timestamp "12.3.2024")
                                :created-text "Should fail"))]
          (is (fail? result))))

      (testing "Processing entries query fails when feature is disabled"
        (let [result (query pena :processing-entries :id app-id)]
          (is (fail? result)))))))

;; ==========================================
;; Pre-submission State Tests
;; ==========================================

(deftest pre-submission-state-test
  (testing "Cannot access processing for draft application"
    (let [{:keys [id]} (with-ts "15.3.2024 10.00"
                         (create-local-app pena :operation "aita"
                                           :address "Draft App Street"))]
      (let [result (query pena :processing-days :id id)]
        (is (fail? result)))

      (is (= {:ok true :entries [] :auth-models nil}
             (query pena :processing-entries :id id)))

      (let [result (with-ts "15.3.2024 11.00"
                     (command sonja :new-processing-request
                              :id id
                              :delay-start (date/timestamp "16.3.2024")
                              :created-text "Should fail"))]
        (is (fail? result))))))

;; ==========================================
;; Verdict Tests
;; ==========================================

(defn- give-local-verdict
  "Creates, fills and publishes a local legacy verdict. Returns verdict-id."
  [apikey app-id verdict-ts]
  (let [{:keys [verdict-id]} (local-command apikey :new-legacy-verdict-draft :id app-id)]
    (doseq [[k v] {:kuntalupatunnus "888-10-12"
                   :verdict-code    "1" ;; Granted
                   :verdict-text    "Lorem ipsum"
                   :handler         "Decider"
                   :giver           "lautakunta"
                   :giver-name      "Board"
                   :anto            verdict-ts
                   :julkipano       (date/timestamp (date/plus verdict-ts :months 2))
                   :lainvoimainen   (date/timestamp (date/plus verdict-ts :weeks 2))}]
      (local-command apikey :edit-pate-verdict
                     :id app-id
                     :verdict-id verdict-id
                     :path [k]
                     :value v))
    (local-command apikey :publish-legacy-verdict :id app-id :verdict-id verdict-id)
    verdict-id))

(deftest verdict-finishes-processing-test
  (let [app-id (create-submitted-app pena "1.5.2024 9.00")]

    ;; Set processing complexity
    (with-ts "1.5.2024 10.00"
      (command sonja :set-processing-complexity :id app-id :complexity "medium"))

    (testing "Processing is running before verdict"
      (let [proc (with-ts "2.5.2024 9.00"
                   (get-processing-days pena app-id))]
        (is (= :running (:status proc)))
        (is (= :medium (:complexity proc)))))

    ;; Create a delay entry
    (with-ts "3.5.2024 16.00"
      (is (:ok (command sonja :new-processing-delay
                        :id app-id
                        :delay-start (date/timestamp "3.5.2024 10.00")
                        :created-text "Waiting for documents"
                        :explanation "Applicant requested time"))))

    (testing "Processing is delayed with open entry"
      (let [proc (with-ts "4.5.2024 9.00"
                   (get-processing-days pena app-id))]
        (is (= :delayed (:status proc)))))

    ;; Give verdict
    (with-ts "6.5.2024 9.00"
      (give-local-verdict sonja app-id (date/timestamp "6.5.2024")))

    (testing "Processing status becomes finished after verdict"
      (let [proc (with-ts "7.5.2024 9.00"
                   (get-processing-days pena app-id))]
        (is (= :finished (:status proc)))
        (is (= :medium (:complexity proc)))
        ;; Verdict timestamp is included
        (is (number? (:verdict-ts proc)))))

    (testing "Processing logdata includes verdict timestamp"
      (let [result (query sonja :processing-logdata :id app-id)
            logdata (:processing-logdata result)]
        (is (ok? result))
        (is (= :finished (-> logdata :processing :status)))))))

(deftest delete-verdict-resumes-processing-test
  (let [app-id (create-submitted-app pena "10.5.2024 9.00")]

    ;; Set processing complexity
    (with-ts "10.5.2024 10.00"
      (command sonja :set-processing-complexity :id app-id :complexity "medium"))

    (testing "Processing is running initially"
      (let [proc (with-ts "11.5.2024 9.00"
                   (get-processing-days pena app-id))]
        (is (= :running (:status proc)))))

    ;; Give verdict
    (let [verdict-id (with-ts "15.5.2024 9.00"
                       (give-local-verdict sonja app-id (date/timestamp "15.5.2024")))]

      (testing "Processing is finished after verdict"
        (let [proc (with-ts "16.5.2024 9.00"
                     (get-processing-days pena app-id))]
          (is (= :finished (:status proc)))))

      ;; Delete the verdict
      (with-ts "17.5.2024 9.00"
        (local-command sonja :delete-pate-verdict :id app-id :verdict-id verdict-id))

      (testing "Processing resumes after verdict deletion"
        (let [proc (with-ts "18.5.2024 9.00"
                     (get-processing-days pena app-id))]
          (is (= :running (:status proc)))
          (is (nil? (:verdict-ts proc)))
          ;; Used days should be calculated from submission
          (is (number? (:used proc)))
          (is (number? (:remaining proc))))))))

(deftest verdict-with-open-delays-test
  (let [app-id (create-submitted-app pena "20.5.2024 9.00")]

    ;; Set processing complexity
    (with-ts "20.5.2024 10.00"
      (command sonja :set-processing-complexity :id app-id :complexity "medium"))

    ;; Create delay entries (not closed)
    (with-ts "21.5.2024 9.00"
      (command sonja :new-processing-delay
               :id app-id
               :delay-start (date/timestamp "21.5.2024 9.00")
               :created-text "First delay"
               :explanation "Reason 1"))

    (with-ts "22.5.2024 9.00"
      (command sonja :new-processing-request
               :id app-id
               :delay-start (date/timestamp "22.5.2024 9.00")
               :created-text "Information request"))

    (testing "Processing is delayed before verdict"
      (let [proc (with-ts "23.5.2024 9.00"
                   (get-processing-days pena app-id))]
        (is (= :delayed (:status proc)))
        (is (= 2 (:requests proc)))))

    ;; Give verdict (even with open delay entries)
    (with-ts "24.5.2024 9.00"
      (give-local-verdict sonja app-id (date/timestamp "24.5.2024")))

    (testing "Processing becomes finished regardless of open entries"
      (let [proc (with-ts "25.5.2024 9.00"
                   (get-processing-days pena app-id))]
        (is (= :finished (:status proc)))))

    (testing "Open entries still exist but don't affect finished status"
      (let [{:keys [entries]} (get-entries sonja app-id)
            open-entries (filter #(nil? (:closed-ts %)) entries)]
        (is (= 2 (count open-entries)))))))

(deftest verdict-without-processing-test
  (testing "Verdict only updates existing and configured processing"
    (let [app-id     (create-submitted-app pena "10.5.2024 9.00")
          verdict-id (with-ts "15.5.2024 9.00"
                       (give-local-verdict sonja app-id (date/timestamp "15.5.2024")))]

      (testing "Processing is still unconfigured after verdict"
        (let [proc (with-ts "16.5.2024 9.00"
                     (get-processing-days pena app-id))]
          (is (= :no-complexity (:status proc)))))

      ;; Delete verdict
      (with-ts "17.5.2024 9.00"
        (local-command sonja :delete-pate-verdict :id app-id :verdict-id verdict-id))

      (testing "Processing is still unconfigured after verdict deletion"
        (let [proc (with-ts "16.5.2024 9.00"
                     (get-processing-days pena app-id))]
          (is (= :no-complexity (:status proc)))))

      ;; Delete processing
      (mongo/update-by-id :applications app-id {$unset {:_stats true}})

      (let [verdict-id (with-ts "18.5.2024 9.00"
                         (give-local-verdict sonja app-id (date/timestamp "18.5.2024")))]

        (testing "No processing after verdict"
          (let [proc (with-ts "19.5.2024 9.00"
                       (get-processing-days pena app-id))]
            (is (nil? proc))))

        (with-ts "19.5.2024 9.00"
          (local-command sonja :delete-pate-verdict :id app-id :verdict-id verdict-id))

        (testing "No processing after verdict deletion"
          (let [proc (with-ts "20.5.2024 9.00"
                       (get-processing-days pena app-id))]
            (is (nil? proc))))))))

;; ==========================================
;; Application Search Tests
;; ==========================================

(deftest processing-application-search-test
  (let [;; App with processing configured and running
        app-running   (create-submitted-app pena "1.6.2024 9.00")
        ;; App with processing configured but finished (verdict given)
        app-finished  (create-submitted-app pena "1.6.2024 10.00")
        ;; App with no processing complexity set (no-complexity status)
        app-no-complexity (create-submitted-app pena "1.6.2024 11.00")
        ;; App with processing delayed
        app-delayed   (create-submitted-app pena "1.6.2024 12.00")]

    ;; Setup running app
    (with-ts "2.6.2024 9.00"
      (command sonja :set-processing-complexity :id app-running :complexity "medium"))

    ;; Setup finished app (with verdict)
    (with-ts "2.6.2024 10.00"
      (command sonja :set-processing-complexity :id app-finished :complexity "medium"))
    (with-ts "3.6.2024 9.00"
      (give-local-verdict sonja app-finished (date/timestamp "3.6.2024")))

    ;; app-no-complexity stays without processing complexity

    ;; Setup delayed app
    (with-ts "2.6.2024 12.00"
      (command sonja :set-processing-complexity :id app-delayed :complexity "medium"))
    (with-ts "3.6.2024 12.00"
      (command sonja :new-processing-delay
               :id app-delayed
               :delay-start (date/timestamp "3.6.2024 12.00")
               :created-text "Causing delay"
               :explanation "Delay reason"))

    ;; Force update processing calculations
    (with-ts "4.6.2024 9.00"
      (stats-db/update-processing! (mongo/by-id :applications app-running))
      (stats-db/update-processing! (mongo/by-id :applications app-finished))
      (stats-db/update-processing! (mongo/by-id :applications app-delayed)))

    (testing "Verify processing statuses are correct"
      (is (= :running (:status (with-ts "4.6.2024 10.00"
                                 (get-processing-days sonja app-running)))))
      (is (= :finished (:status (with-ts "4.6.2024 10.00"
                                  (get-processing-days sonja app-finished)))))
      (is (= :no-complexity (:status (with-ts "4.6.2024 10.00"
                                       (get-processing-days sonja app-no-complexity)))))
      (is (= :delayed (:status (with-ts "4.6.2024 10.00"
                                 (get-processing-days sonja app-delayed))))))

    (testing "Processing search returns running, delayed, and no-complexity apps"
      (let [result       (with-ts "4.6.2024 11.00"
                           (query sonja :applications-search
                                  :applicationType "processing"
                                  :searchText ""
                                  :skip 0
                                  :limit 100
                                  :sort {:field "modified" :asc false}))
            applications (-> result :data :applications)
            app-ids      (set (map :id applications))]
        (is (ok? result))
        ;; Running and delayed should be included
        (is (contains? app-ids app-running))
        (is (contains? app-ids app-delayed))
        ;; no-complexity should also be included (it's in the query)
        (is (contains? app-ids app-no-complexity))
        ;; Finished should NOT be included
        (is (not (contains? app-ids app-finished)))
        (testing "Every application has :processing, but not :unseen?"
          (is (->> applications
                   (util/filter-by-key :id app-ids)
                   (every? (fn [{:keys [processing]}]
                             (and processing (not (:unseen? processing))))))))
        (testing "For Pena, the delayed-app's processing is unseen."
          (is (->> (with-ts "4.6.2024 11.00"
                     (query pena :applications-search
                            :applicationType "processing"
                            :searchText ""
                            :skip 0
                            :limit 100
                            :sort {:field "modified" :asc false}))
                   :data :applications
                   (util/filter-by-key :id app-ids)
                   (every? (fn [{:keys [id processing]}]
                             (if (= id (:id app-delayed))
                               (and processing (:unseen? processing))
                               (and processing (not (:unseen? processing)))))))))))

    (testing "Deleting verdict makes app appear in processing search again"
      ;; Get the verdict id
      (let [{:keys [pate-verdicts]} (mongo/by-id :applications app-finished)
            verdict-id              (-> pate-verdicts first :id)]
        ;; Delete verdict
        (with-ts "5.6.2024 9.00"
          (local-command sonja :delete-pate-verdict :id app-finished :verdict-id verdict-id))
        ;; Update processing
        (with-ts "5.6.2024 10.00"
          (stats-db/update-processing! (mongo/by-id :applications app-finished)))
        ;; Now it should appear in search
        (let [result  (with-ts "5.6.2024 11.00"
                        (local-query sonja :applications-search
                                     :applicationType "processing"
                                     :searchText ""
                                     :skip 0
                                     :limit 100
                                     :sort {:field "modified" :asc false}))
              app-ids (->> result :data :applications (map :id) set)]
          (is (contains? app-ids app-finished)))))))

(deftest processing-search-excludes-non-processing-apps-test
  (let [;; YA app (not R, so not processing-eligible)
        ya-app-id (with-ts "10.6.2024 9.00"
                    (let [{:keys [id]} (create-local-app pena
                                                         :operation "ya-katulupa-vesi-ja-telekaapelit"
                                                         :address "YA Street")]
                      (local-command pena :submit-application :id id)
                      id))
        ;; R app (processing-eligible)
        r-app-id (create-submitted-app pena "10.6.2024 10.00")]

    ;; Set processing complexity for R app
    (with-ts "11.6.2024 9.00"
      (command sonja :set-processing-complexity :id r-app-id :complexity "medium"))

    ;; Update processing
    (with-ts "11.6.2024 10.00"
      (stats-db/update-processing! (mongo/by-id :applications r-app-id)))

    (testing "YA apps are not included in processing search"
      (let [result (with-ts "12.6.2024 9.00"
                     (local-query sonja :applications-search
                                  :applicationType "processing"
                                  :searchText ""
                                  :skip 0
                                  :limit 100
                                  :sort {:field "modified" :asc false}))
            app-ids (->> result :data :applications (map :id) set)]
        (is (ok? result))
        (is (contains? app-ids r-app-id))
        (is (not (contains? app-ids ya-app-id)))))))

;; ==========================================
;; Processing Requests Status Tests
;; ==========================================

(deftest processing-requests-status-test
  (let [app-id (create-submitted-app pena "15.6.2024 9.00")]

    (testing "Initial status with no entries"
      (let [result (query sonja :processing-requests-status :id app-id)]
        (is (ok? result))
        (is (= 0 (:requests result)))
        (is (false? (:unseen? result)))))

    ;; Create request entries
    (with-ts "16.6.2024 9.00"
      (command sonja :new-processing-request
               :id app-id
               :delay-start (date/timestamp "17.6.2024")
               :created-text "First request"))

    (with-ts "16.6.2024 10.00"
      (command sonja :new-processing-request
               :id app-id
               :delay-start (date/timestamp "17.6.2024")
               :created-text "Second request"))

    (testing "Status shows open request count"
      (let [result (query sonja :processing-requests-status :id app-id)]
        (is (ok? result))
        (is (= 2 (:requests result)))
        (is (false? (:unseen? result)))))

    (testing "Applicant sees unseen entries"
      (let [result (query pena :processing-requests-status :id app-id)]
        (is (ok? result))
        (is (= 2 (:requests result)))
        (is (true? (:unseen? result)))))

    ;; Answer one request
    (let [{:keys [entries]} (get-entries sonja app-id)
          entry-id (-> entries first :id)]
      (with-ts "17.6.2024 9.00"
        (command pena :answer-processing-entry
                 :id app-id
                 :entry-id entry-id
                 :answered-text "Here is my answer")))

    (testing "Answered requests are not counted"
      (let [result (query sonja :processing-requests-status :id app-id)]
        (is (= 1 (:requests result)))))

    ;; Close the remaining request
    (let [{:keys [entries]} (get-entries sonja app-id)
          open-entry-id (->> entries (filter #(nil? (:answered-ts %))) first :id)]
      (with-ts "17.6.2024 10.00"
        (command sonja :close-processing-entry
                 :id app-id
                 :entry-id open-entry-id
                 :closed-ts (date/timestamp "17.6.2024 10.00")
                 :closed-text "Closed")))

    (testing "Closed requests are not counted"
      (let [result (query sonja :processing-requests-status :id app-id)]
        (is (= 0 (:requests result)))))

    ;; Mark entries as seen by applicant
    (let [{:keys [entries]} (get-entries pena app-id)
          entry-ids (mapv :id entries)]
      (with-ts "17.6.2024 11.00"
        (command pena :mark-entries-seen
                 :id app-id
                 :entry-ids entry-ids)))

    (testing "Applicant no longer sees unseen entries"
      (let [result (query pena :processing-requests-status :id app-id)]
        (is (false? (:unseen? result)))))))

;; ==========================================
;; Processing Dashboard Tab Tests
;; ==========================================

(deftest processing-dashboard-tab-test
  (testing "Authority in R organization can see dashboard tab"
    (let [result (query sonja :processing-dashboard-tab)]
      (is (ok? result))))

  (testing "Applicant can see dashboard tab"
    (let [result (query pena :processing-dashboard-tab)]
      (is (ok? result)))))

;; ==========================================
;; Overdue Processing Tests
;; ==========================================

(deftest overdue-processing-test
  ;; Submitted: 1.1.2024
  ;; With special complexity (1 month deadline): 1.2.2024
  ;; Query at 15.2.2024 - should show negative remaining days
  (let [app-id (create-submitted-app pena "1.1.2024 9.00")]

    ;; Set special complexity (1 month = shortest deadline)
    (with-ts "1.1.2024 10.00"
      (command sonja :set-processing-complexity :id app-id :complexity "special"))

    (testing "Processing starts running"
      (let [proc (with-ts "2.1.2024 9.00"
                   (get-processing-days sonja app-id))]
        (is (= :running (:status proc)))
        (is (pos? (:remaining proc)))))

    ;; Update processing after deadline has passed
    (with-ts "15.2.2024 9.00"
      (stats-db/update-processing! (mongo/by-id :applications app-id)))

    (testing "Processing shows negative remaining days when overdue"
      (let [proc (with-ts "15.2.2024 10.00"
                   (get-processing-days sonja app-id))]
        (is (= :running (:status proc)))
        ;; Remaining should be negative (overdue)
        (is (neg? (:remaining proc)))
        ;; Used should be positive and > remaining period
        (is (pos? (:used proc)))))

    (testing "Logdata shows overdue processing"
      (let [result (query sonja :processing-logdata :id app-id)
            processing (-> result :processing-logdata :processing)]
        (is (neg? (:remaining processing)))))))

;; ==========================================
;; Long Running Delay Tests
;; ==========================================

(deftest long-running-delay-test
  ;; Test a delay spanning multiple months
  (let [app-id (create-submitted-app pena "1.1.2024 9.00")]

    ;; Set processing complexity
    (with-ts "1.1.2024 10.00"
      (command sonja :set-processing-complexity :id app-id :complexity "medium"))

    ;; Create a delay that will span 2 months
    (with-ts "15.1.2024 9.00"
      (command sonja :new-processing-delay
               :id app-id
               :delay-start (date/timestamp "15.1.2024 9.00")
               :created-text "Long delay"
               :explanation "Extended waiting period"))

    ;; Close the delay 2 months later
    (let [{:keys [entries]} (get-entries sonja app-id)
          delay-id          (->> entries (filter #(= :delay (:type %))) first :id)]
      (with-ts "15.3.2024 9.00"
        (command sonja :close-processing-entry
                 :id app-id
                 :entry-id delay-id
                 :closed-ts (date/timestamp "15.3.2024 9.00")
                 :closed-text "Delay ended")))

    (testing "Long delay extends deadline significantly"
      (let [proc (with-ts "16.3.2024 9.00"
                   (get-processing-days sonja app-id))]
        (is (= {:delays           60
                :remaining        78
                :complexity       :medium
                :requests         0
                :status           :running
                :used-period      {:y 0 :m 0 :d 14}
                :remaining-period {:y 0 :m 2 :d 17}
                :deadline         (deadstamp "1.6.2024")
                :used             14} proc))))))

;; ==========================================
;; Multiple Verdict Scenarios Tests
;; ==========================================

(deftest multiple-verdicts-test
  (let [app-id (create-submitted-app pena "1.7.2024 9.00")]

    ;; Set processing complexity
    (with-ts "1.7.2024 10.00"
      (command sonja :set-processing-complexity :id app-id :complexity "medium"))

    (testing "Processing is running initially"
      (let [proc (with-ts "2.7.2024 9.00"
                   (get-processing-days sonja app-id))]
        (is (= :running (:status proc)))))

    ;; Give first verdict
    (let [verdict-id-1 (with-ts "5.7.2024 9.00"
                         (give-local-verdict sonja app-id (date/timestamp "5.7.2024")))]

      (testing "Processing is finished after first verdict"
        (let [proc (with-ts "6.7.2024 9.00"
                     (get-processing-days sonja app-id))]
          (is (= :finished (:status proc)))))

      ;; Delete first verdict
      (with-ts "7.7.2024 9.00"
        (local-command sonja :delete-pate-verdict :id app-id :verdict-id verdict-id-1))

      (testing "Processing resumes after deleting first verdict"
        ;; Force update after deletion
        (with-ts "7.7.2024 10.00"
          (stats-db/update-processing! (mongo/by-id :applications app-id)))
        (let [proc (with-ts "7.7.2024 11.00"
                     (get-processing-days sonja app-id))]
          (is (= :running (:status proc))))))

    ;; Give second verdict
    (let [verdict-id-2 (with-ts "10.7.2024 9.00"
                         (give-local-verdict sonja app-id (date/timestamp "10.7.2024")))]

      (testing "Processing is finished after second verdict"
        (let [proc (with-ts "11.7.2024 9.00"
                     (get-processing-days sonja app-id))]
          (is (= :finished (:status proc)))))

      ;; Delete second verdict
      (with-ts "12.7.2024 9.00"
        (local-command sonja :delete-pate-verdict :id app-id :verdict-id verdict-id-2))

      (testing "Processing resumes after deleting second verdict"
        (with-ts "12.7.2024 10.00"
          (stats-db/update-processing! (mongo/by-id :applications app-id)))
        (let [proc (with-ts "12.7.2024 11.00"
                     (get-processing-days sonja app-id))]
          (is (= :running (:status proc)))
          (is (nil? (:verdict-ts proc))))))))

;; ==========================================
;; Reader Authority Tests
;; ==========================================

(deftest reader-authority-test
  (let [app-id (create-submitted-app pena "20.6.2024 9.00")]

    ;; Set processing complexity
    (with-ts "20.6.2024 10.00"
      (command sonja :set-processing-complexity :id app-id :complexity "medium"))

    ;; Create an entry
    (with-ts "21.6.2024 9.00"
      (command sonja :new-processing-request
               :id app-id
               :delay-start (date/timestamp "22.6.2024")
               :created-text "Request for testing reader"))

    (let [{:keys [entries]} (get-entries sonja app-id)
          entry-id (-> entries first :id)]

      (testing "Reader authority can query processing days"
        (let [result (query luukas :processing-days :id app-id)]
          (is (ok? result))))

      (testing "Reader authority can query processing entries"
        (let [result (query luukas :processing-entries :id app-id)]
          (is (ok? result))))

      (testing "Reader authority cannot create entries"
        (let [result (with-ts "21.6.2024 10.00"
                       (command luukas :new-processing-request
                                :id app-id
                                :delay-start (date/timestamp "23.6.2024")
                                :created-text "Reader trying to create"))]
          (is (fail? result))))

      (testing "Reader authority cannot close entries"
        (let [result (with-ts "21.6.2024 11.00"
                       (command luukas :close-processing-entry
                                :id app-id
                                :entry-id entry-id
                                :closed-ts (date/timestamp "21.6.2024 11.00")
                                :closed-text "Reader trying to close"))]
          (is (fail? result))))

      (testing "Reader authority cannot set complexity"
        (let [result (with-ts "21.6.2024 12.00"
                       (command luukas :set-processing-complexity
                                :id app-id
                                :complexity "large"))]
          (is (fail? result)))))))

;; ==========================================
;; Leap Year Handling Tests
;; ==========================================

(deftest leap-year-processing-test
  ;; 2024 is a leap year, Feb 29 exists
  (let [app-id (create-submitted-app pena "15.2.2024 9.00")]

    ;; Set processing complexity
    (with-ts "15.2.2024 10.00"
      (command sonja :set-processing-complexity :id app-id :complexity "medium"))

    (testing "Processing calculation works across Feb 29"
      (let [proc (with-ts "1.3.2024 9.00"
                   (get-processing-days sonja app-id))]
        (is (= :running (:status proc)))
        ;; Used period should include the leap day
        (is (number? (:used proc)))))

    ;; Create a delay spanning Feb 29
    (with-ts "28.2.2024 9.00"
      (command sonja :new-processing-delay
               :id app-id
               :delay-start (date/timestamp "28.2.2024 9.00")
               :created-text "Delay across leap day"
               :explanation "Testing leap year"))

    ;; Close the delay after Feb 29
    (let [{:keys [entries]} (get-entries sonja app-id)
          delay-id (->> entries (filter #(= :delay (:type %))) first :id)]
      (with-ts "2.3.2024 9.00"
        (command sonja :close-processing-entry
                 :id app-id
                 :entry-id delay-id
                 :closed-ts (date/timestamp "1.3.2024 9.00")
                 :closed-text "Closed after leap day")))

    (testing "Delay across leap day calculates correctly"
      (let [proc (with-ts "2.3.2024 10.00"
                   (get-processing-days sonja app-id))]
        ;; Delay should be 2 days (28.2 to 1.3 in leap year)
        (is (= 2 (:delays proc)))))))

(deftest processing-post-reset-test
  (let [{app-id :id
         state  :state} (with-ts "22.2.2026 9.00"
                          (create-and-submit-local-application pena :operation "purkaminen"
                                                               :address "Demolition Drive"))
        second-op-id    #(-> (query-application pena app-id) :secondaryOperations first :id)
        processing?     #(:ok (query pena :processing-days :id app-id))
        not-processing? (complement processing?)]
    (testing "Demolition does not support processing"
      (is (= "submitted" state))
      (is (not-processing?)))
    (testing "Add secondary fence operation: processing supported"
      (is (:ok (command pena :add-operation :id app-id :operation "aita")))
      (is (processing?)))
    (testing "Replace fence with timber: no processing"
      (let [op-id (second-op-id)]
        (is (some? op-id))
        (is (:ok (command pena :replace-operation :id app-id
                          :opId op-id :operation "puun-kaataminen")))
        (is (not-processing?))))
    (testing "Replace timber with house: processing"
      (let [op-id (second-op-id)]
        (is (some? op-id))
        (is (:ok (command pena :replace-operation :id app-id
                          :opId op-id :operation "pientalo")))
        (is (processing?))))
    (testing "Remove the secondary operation: no processing"
      (let [op-id (second-op-id)]
        (is (some? op-id))
        (is (:ok (command pena :replace-operation :id app-id
                          :opId op-id :operation "pientalo")))
        (is (processing?))))
    (testing "Replace demolition with renovation: processing"
      (let [op-id (-> (query-application pena app-id) :primaryOperation :id)]
        (is (some? op-id))
        (is (:ok (command pena :replace-operation :id app-id
                          :opId op-id :operation "julkisivu-muutos")))
        (is (processing?))))))
