(ns lupapalvelu.statistics.schemas-test
  (:require [clojure.test :refer :all]
            [lupapalvelu.statistics.schemas :as schemas]
            [schema.core :as sc]))

(use-fixtures :once (fn [f]
                      (sc/with-fn-validation
                        (f))))

;; ----------------------------------
;; Status tests
;; ----------------------------------

(deftest status-test
  (testing "Status enum accepts valid values"
    (is (nil? (sc/check schemas/Status :no-complexity)))
    (is (nil? (sc/check schemas/Status :running)))
    (is (nil? (sc/check schemas/Status :delayed)))
    (is (nil? (sc/check schemas/Status :finished))))

  (testing "Status enum rejects invalid values"
    (is (some? (sc/check schemas/Status :invalid)))
    (is (some? (sc/check schemas/Status "running")))
    (is (some? (sc/check schemas/Status nil)))))

;; ----------------------------------
;; Complexity tests
;; ----------------------------------

(deftest complexity-test
  (testing "Complexity enum accepts valid values"
    (doseq [c (map :kw schemas/complexities)]
      (is (nil? (sc/check schemas/Complexity c)))))

  (testing "Complexity enum rejects invalid values"
    (is (some? (sc/check schemas/Complexity -1)))
    (is (some? (sc/check schemas/Complexity 6)))))

(deftest complexity->months-test
  (testing "Maps complexity to months"
    (is (= 1 (schemas/complexity->months :special)))
    (is (= 3 (schemas/complexity->months :small)))
    (is (= 3 (schemas/complexity->months :medium)))
    (is (= 3 (schemas/complexity->months :large)))
    (is (= 6 (schemas/complexity->months :very-large)))
    (is (= 6 (schemas/complexity->months :extra-large))))

  (testing "Throws on unsupported complexity"
    (is (thrown? Exception (schemas/complexity->months -1)))))

;; ----------------------------------
;; Processing schema tests
;; ----------------------------------

;; ----------------------------------
;; Period schema tests
;; ----------------------------------

(deftest period-schema-test
  (testing "Valid Period maps"
    (is (nil? (sc/check schemas/Period {:y 0 :m 0 :d 0})))
    (is (nil? (sc/check schemas/Period {:y 0 :m 1 :d 15})))
    (is (nil? (sc/check schemas/Period {:y 1 :m 6 :d 30}))))

  (testing "Negative values are valid (e.g., overdue remaining)"
    (is (nil? (sc/check schemas/Period {:y 0 :m 0 :d -7})))
    (is (nil? (sc/check schemas/Period {:y 0 :m -1 :d 0})))
    (is (nil? (sc/check schemas/Period {:y -1 :m -2 :d -3}))))

  (testing "Missing keys are invalid"
    (is (some? (sc/check schemas/Period {:y 0 :m 0})))
    (is (some? (sc/check schemas/Period {:y 0 :d 0})))
    (is (some? (sc/check schemas/Period {:m 0 :d 0})))
    (is (some? (sc/check schemas/Period {}))))

  (testing "Wrong types are invalid"
    (is (some? (sc/check schemas/Period {:y "0" :m 0 :d 0})))
    (is (some? (sc/check schemas/Period {:y 0 :m 1.5 :d 0})))
    (is (some? (sc/check schemas/Period {:y 0 :m 0 :d nil})))))

(deftest processing-schema-test
  (testing "Empty map is valid (all keys optional)"
    (is (nil? (sc/check schemas/Processing {}))))

  (testing "Valid processing with all fields"
    (is (nil? (sc/check schemas/Processing
                        {:complexity :large
                         :status     :running
                         :used       10
                         :remaining  50
                         :delays     2
                         :requests   1
                         :deadline   1704697200000}))))

  (testing "Valid processing with period breakdowns"
    (is (nil? (sc/check schemas/Processing
                        {:complexity       :medium
                         :status           :running
                         :used             10
                         :used-period      {:y 0 :m 0 :d 10}
                         :remaining        19
                         :remaining-period {:y 0 :m 0 :d 19}
                         :delays           0
                         :requests         1
                         :deadline         1704697200000}))))

  (testing "Valid processing with only some fields"
    (is (nil? (sc/check schemas/Processing {:status :no-complexity})))
    (is (nil? (sc/check schemas/Processing {:complexity :special})))
    (is (nil? (sc/check schemas/Processing {:complexity :extra-large :status :finished}))))

  (testing "Remaining can be negative"
    (is (nil? (sc/check schemas/Processing {:remaining -10}))))

  (testing "Remaining period can be negative (overdue)"
    (is (nil? (sc/check schemas/Processing {:remaining        -7
                                            :remaining-period {:y 0 :m 0 :d -7}}))))

  (testing "Invalid complexity"
    (is (some? (sc/check schemas/Processing {:complexity -1}))))

  (testing "Invalid used/delays (must be non-negative)"
    (is (some? (sc/check schemas/Processing {:used -1})))
    (is (some? (sc/check schemas/Processing {:delays -1})))
    (is (some? (sc/check schemas/Processing {:requests -1}))))

  (testing "Invalid status"
    (is (some? (sc/check schemas/Processing {:status :invalid}))))

  (testing "Invalid period structure"
    (is (some? (sc/check schemas/Processing {:used-period {:y 0 :m 0}})))
    (is (some? (sc/check schemas/Processing {:remaining-period "invalid"})))))

;; ----------------------------------
;; ->processing coercer tests
;; ----------------------------------

(deftest ->processing-coercer-test
  (testing "Coerces empty map"
    (is (= {} (schemas/->processing {}))))

  (testing "Coerces string status to keyword"
    (is (= {:status :running} (schemas/->processing {:status "running"})))
    (is (= {:status :delayed} (schemas/->processing {:status "delayed"})))
    (is (= {:status :finished} (schemas/->processing {:status "finished"})))
    (is (= {:status :no-complexity} (schemas/->processing {:status "no-complexity"}))))

  (testing "Preserves keyword status"
    (is (= {:status :running} (schemas/->processing {:status :running}))))

  (testing "Coerces full processing data"
    (is (= {:complexity :large
            :status     :running
            :used       10
            :remaining  -5
            :delays     2
            :requests   1
            :deadline   1704697200000}
           (schemas/->processing {:complexity :large
                                  :status     "running"
                                  :used       10
                                  :remaining  -5
                                  :delays     2
                                  :requests   1
                                  :deadline   1704697200000}))))

  (testing "Coerces processing data with period breakdowns"
    (is (= {:complexity       :medium
            :status           :running
            :used             10
            :used-period      {:y 0 :m 0 :d 10}
            :remaining        19
            :remaining-period {:y 0 :m 0 :d 19}
            :delays           0
            :requests         1
            :deadline         1704697200000}
           (schemas/->processing {:complexity       :medium
                                  :status           "running"
                                  :used             10
                                  :used-period      {:y 0 :m 0 :d 10}
                                  :remaining        19
                                  :remaining-period {:y 0 :m 0 :d 19}
                                  :delays           0
                                  :requests         1
                                  :deadline         1704697200000}))))

  (testing "Throws on invalid status"
    (is (thrown? Exception (schemas/->processing {:status "invalid"}))))

  (testing "Throws on invalid complexity"
    (is (thrown? Exception (schemas/->processing {:complexity -1})))))

;; ----------------------------------
;; ->front-processing coercer tests
;; ----------------------------------

(deftest ->front-processing-test
  (testing "Coerces empty map and with extras"
    (is (= {} (schemas/->front-processing {})))
    (is (= {:n 10 :foo "bar"}
           (schemas/->front-processing {:n 10 :foo "bar"}))))

  (testing "Coerces string status to keyword with extras"
    (is (= {:status :running :n 10 :hello "world"}
           (schemas/->front-processing {:status "running" :n 10 :hello "world"})))
    (is (= {:status :delayed :n 10 :hello "world"}
           (schemas/->front-processing {:status "delayed" :n 10 :hello "world"})))
    (is (= {:status :finished :n 10 :hello "world"}
           (schemas/->front-processing {:status "finished" :n 10 :hello "world"})))
    (is (= {:status :no-complexity :n 10 :hello "world"}
           (schemas/->front-processing {:status "no-complexity" :n 10 :hello "world"}))))

  (testing "Preserves keyword status"
    (is (= {:status :running :n 10 :hello "world"}
           (schemas/->front-processing {:status :running :n 10 :hello "world"}))))

  (testing "Coerces full processing data"
    (is (= {:complexity :large :status    :running
            :used       10     :remaining -5
            :delays     2      :requests  1
            :deadline   1704697200000
            :n          10     :hello     "world"}
           (schemas/->front-processing {:complexity :large
                                        :status     "running"
                                        :used       10
                                        :n          10
                                        :hello      "world"
                                        :remaining  -5
                                        :delays     2
                                        :requests   1
                                        :deadline   1704697200000}))))

  (testing "Coerces processing data with period breakdowns"
    (is (= {:complexity       :medium
            :status           :running
            :used             10
            :used-period      {:y 0 :m 0 :d 10}
            :remaining        19
            :remaining-period {:y 0 :m 0 :d 19}
            :delays           0
            :requests         1
            :deadline         1704697200000
            :extra-field      "preserved"}
           (schemas/->front-processing {:complexity       :medium
                                        :status           "running"
                                        :used             10
                                        :used-period      {:y 0 :m 0 :d 10}
                                        :remaining        19
                                        :remaining-period {:y 0 :m 0 :d 19}
                                        :delays           0
                                        :requests         1
                                        :deadline         1704697200000
                                        :extra-field      "preserved"}))))

  (testing "Throws on invalid status"
    (is (thrown? Exception (schemas/->front-processing {:status "invalid" :n 10 :hello "world"}))))

  (testing "Throws on invalid complexity"
    (is (thrown? Exception (schemas/->front-processing {:complexity -1 :n 10 :hello "world"})))))

;; ----------------------------------
;; Coercer interaction tests
;; ----------------------------------

(deftest coercer-interaction-test
  (testing "->db-processing after ->processing equals ->db-processing on original input"
    (let [input           {:status "running" :complexity "large" :used     10 :remaining -5
                           :delays 2         :requests   1       :deadline 1704697200000}
          db-from-coerced (schemas/->db-processing (schemas/->processing input))
          db-direct       (schemas/->db-processing input)]
      (is (= db-from-coerced db-direct))
      (is (integer? (:status db-direct)))
      (is (integer? (:complexity db-direct)))))

  (testing "->processing after ->db-processing equals ->processing on original input"
    (let [input     {:status "running" :complexity "large"}
          p-from-db (schemas/->processing (schemas/->db-processing input))
          p-direct  (schemas/->processing input)]
      (is (= p-from-db p-direct))
      (is (= :running (:status p-from-db)))
      (is (keyword? (:complexity p-from-db)))))

  (testing "Roundtrip with numeric DB values"
    (let [db-input  {:status 1 :complexity 3}
          processed (schemas/->processing db-input)
          back      (schemas/->db-processing processed)]
      (is (= processed {:status :running :complexity :large}))
      (is (= back db-input)))))

;; ----------------------------------
;; Schema utility tests
;; ----------------------------------

(deftest schema-utility-test
  (testing "->db-statuses accepts keywords, strings and numbers"
    (is (= (schemas/->db-statuses :running "delayed" 3)
           #{1 2 3})))

  (testing "->db-complexity returns numeric db code for keyword complexity"
    (is (= 3 (schemas/->db-complexity :large)))
    (is (= 0 (schemas/->db-complexity :special))))

  (testing "StrComplexity accepts string names and rejects keywords"
    (is (nil? (sc/check schemas/StrComplexity "large")))
    (is (some? (sc/check schemas/StrComplexity :large))))

  (testing "DbComplexity accepts numeric db values"
    (is (nil? (sc/check schemas/DbComplexity 3)))
    (is (some? (sc/check schemas/DbComplexity 10)))))

;; ----------------------------------
;; ProcessingLogData schema tests
;; ----------------------------------

(deftest processing-log-data-schema-test
  (testing "Valid minimal log data"
    (is (nil? (sc/check schemas/ProcessingLogData
                        {:submitted-ts 1704697200000
                         :processing   {}}))))

  (testing "Valid log data with verdict"
    (is (nil? (sc/check schemas/ProcessingLogData
                        {:submitted-ts 1704697200000
                         :verdict-ts   1704783600000
                         :processing   {:status :finished}}))))

  (testing "Valid log data with delays"
    (is (nil? (sc/check schemas/ProcessingLogData
                        {:submitted-ts 1704697200000
                         :processing   {:status :delayed}
                         :delays       [{:start-ts 1704700000000
                                         :text     "Waiting for documents"}]}))))

  (testing "Valid log data with complete delay entry"
    (is (nil? (sc/check schemas/ProcessingLogData
                        {:submitted-ts 1704697200000
                         :processing   {}
                         :delays       [{:start-ts    1704700000000
                                         :end-ts      1704800000000
                                         :explanation "Customer requested extension"
                                         :text        "Waiting for documents"}]}))))

  (testing "Invalid - missing submitted-ts"
    (is (some? (sc/check schemas/ProcessingLogData
                         {:processing {}}))))

  (testing "Invalid - missing processing"
    (is (some? (sc/check schemas/ProcessingLogData
                         {:submitted-ts 1704697200000}))))

  (testing "Invalid delay - missing text"
    (is (some? (sc/check schemas/ProcessingLogData
                         {:submitted-ts 1704697200000
                          :processing   {}
                          :delays       [{:start-ts 1704700000000}]})))))

;; ----------------------------------
;; ->logdata coercer tests
;; ----------------------------------

(deftest ->logdata-coercer-test
  (testing "Coerces valid log data"
    (let [result (schemas/->logdata {:submitted-ts 1704697200000
                                     :processing   {:status "running"}})]
      (is (= 1704697200000 (:submitted-ts result)))
      (is (= :running (get-in result [:processing :status])))))

  (testing "Coerces log data with delays"
    (let [result (schemas/->logdata {:submitted-ts 1704697200000
                                     :processing   {}
                                     :delays       [{:start-ts 1704700000000
                                                     :text     "Delay reason"}]})]
      (is (= 1 (count (:delays result))))
      (is (= "Delay reason" (get-in result [:delays 0 :text]))))))

;; ----------------------------------
;; EntryType tests
;; ----------------------------------

(deftest entry-type-test
  (testing "EntryType accepts valid values"
    (is (nil? (sc/check schemas/EntryType :request)))
    (is (nil? (sc/check schemas/EntryType :delay))))

  (testing "EntryType rejects invalid values"
    (is (some? (sc/check schemas/EntryType :invalid)))
    (is (some? (sc/check schemas/EntryType "request")))
    (is (some? (sc/check schemas/EntryType nil)))))

;; ----------------------------------
;; EntryUpdate schema tests
;; ----------------------------------

(deftest entry-update-schema-test
  (testing "Empty map is valid (all keys optional)"
    (is (nil? (sc/check schemas/EntryUpdate {}))))

  (testing "Valid entry update with type"
    (is (nil? (sc/check schemas/EntryUpdate {:type :request})))
    (is (nil? (sc/check schemas/EntryUpdate {:type :delay}))))

  (testing "Valid entry update with multiple fields"
    (is (nil? (sc/check schemas/EntryUpdate
                        {:type         :request
                         :delay-start  1704697200000
                         :deleted?     false
                         :created-text "Please provide documents"
                         :answered?    true
                         :answered-ts  1704783600000
                         :answered-text "Done"
                         :explanation  "Reason"
                         :closed?      false
                         :closed-ts    1704870000000
                         :closed-text  "Closing"}))))

  (testing "Invalid type"
    (is (some? (sc/check schemas/EntryUpdate {:type :invalid}))))

  (testing "Invalid deleted? (must be boolean)"
    (is (some? (sc/check schemas/EntryUpdate {:deleted? "true"})))))

;; ----------------------------------
;; FrontendEntry schema tests
;; ----------------------------------

(deftest frontend-entry-schema-test
  (testing "Valid minimal frontend entry"
    (is (nil? (sc/check schemas/FrontendEntry
                        {:id           "692012b805939c1a59a510db"
                         :created-name "Test User"
                         :created-ts   1704697200000
                         :modified     1704697200000}))))

  (testing "Valid frontend entry with all fields"
    (is (nil? (sc/check schemas/FrontendEntry
                        {:id            "692012b805939c1a59a510db"
                         :type          :request
                         :delay-start   1704697200000
                         :deleted?      false
                         :created-text  "Please provide"
                         :created-name  "Test User"
                         :created-ts    1704697200000
                         :modified      1704697200000
                         :answered?     true
                         :answered-ts   1704783600000
                         :answered-text "Done"
                         :answered-name "Other User"
                         :closed?       true
                         :closed-ts     1704870000000
                         :closed-text   "Closed"
                         :closed-name   "Admin"
                         :seen-by?      true}))))

  (testing "Invalid - missing required id"
    (is (some? (sc/check schemas/FrontendEntry
                         {:created-name "Test User"
                          :modified     1704697200000}))))

  (testing "Invalid - missing required created-name"
    (is (some? (sc/check schemas/FrontendEntry
                         {:id       "692012b805939c1a59a510db"
                          :modified 1704697200000}))))

  (testing "Invalid - missing required modified"
    (is (some? (sc/check schemas/FrontendEntry
                         {:id           "692012b805939c1a59a510db"
                          :created-name "Test User"})))))

;; ----------------------------------
;; diff-map tests
;; ----------------------------------

(deftest diff-map-test
  (testing "Returns empty map for identical maps"
    (is (= {} (schemas/diff-map {:a 1 :b 2} {:a 1 :b 2}))))

  (testing "Returns entries only in first map"
    (is (= {:a 1} (schemas/diff-map {:a 1 :b 2} {:b 2}))))

  (testing "Returns entries with different values"
    (is (= {:a 1} (schemas/diff-map {:a 1 :b 2} {:a 2 :b 2}))))

  (testing "Returns all entries when second map is empty"
    (is (= {:a 1 :b 2} (schemas/diff-map {:a 1 :b 2} {}))))

  (testing "Returns empty map when first map is empty"
    (is (= {} (schemas/diff-map {} {:a 1 :b 2}))))

  (testing "Works with complex values"
    (is (= {:a {:nested 1}} (schemas/diff-map {:a {:nested 1} :b 2}
                                              {:a {:nested 2} :b 2})))))

;; ----------------------------------
;; ->entry-update tests
;; ----------------------------------

(deftest ->entry-update-test
  (testing "Returns nil for empty map"
    (is (nil? (schemas/->entry-update {}))))

  (testing "Returns nil for map with only nil values"
    (is (nil? (schemas/->entry-update {:type nil :delay-start nil}))))

  (testing "Strips nil values"
    (is (= {:type :request}
           (schemas/->entry-update {:type :request :delay-start nil}))))

  (testing "Coerces string type to keyword"
    (is (= {:type :request}
           (schemas/->entry-update {:type "request"}))))

  (testing "Sets closed? to false when closed-ts is explicitly nil"
    (is (= {:closed? false}
           (schemas/->entry-update {:closed-ts nil}))))

  (testing "Sets closed? to true when closed-ts is present"
    (is (= {:closed?   true
            :closed-ts 1704697200000}
           (schemas/->entry-update {:closed-ts 1704697200000}))))

  (testing "Trims whitespace from text fields"
    (is (= {:created-text "Hello"}
           (schemas/->entry-update {:created-text "  Hello  "}))))

  (testing "Preserves valid entry update"
    (is (= {:type        :delay
            :delay-start 1704697200000
            :deleted?    true}
           (schemas/->entry-update {:type        :delay
                                    :delay-start 1704697200000
                                    :deleted?    true}))))

  (testing "Filters out unknown keys"
    (is (= {:type :request}
           (schemas/->entry-update {:type        :request
                                    :unknown-key "value"})))))

;; ----------------------------------
;; ->fronted-entry tests
;; ----------------------------------

(deftest ->frontend-entry-test
  (testing "Selects only FrontendEntry fields"
    (let [input  {:id           "692012b805939c1a59a510db"
                  :created-name "Test User"
                  :created-ts   1704697200000
                  :modified     1704697200000
                  :unknown-key  "should be removed"
                  :another      123}
          result (schemas/->fronted-entry input)]
      (is (= "692012b805939c1a59a510db" (:id result)))
      (is (= "Test User" (:created-name result)))
      (is (= 1704697200000 (:modified result)))
      (is (not (contains? result :unknown-key)))
      (is (not (contains? result :another)))))

  (testing "Coerces string type to keyword"
    (let [result (schemas/->fronted-entry {:id           "692012b805939c1a59a510db"
                                           :type         "request"
                                           :created-ts   1704697200000
                                           :created-name "Test User"
                                           :modified     1704697200000})]
      (is (= :request (:type result)))))

  (testing "Preserves all valid FrontendEntry fields"
    (let [input  {:id            "692012b805939c1a59a510db"
                  :type          :delay
                  :delay-start   1704600000000
                  :deleted?      true
                  :created-text  "Created"
                  :created-name  "Creator"
                  :created-ts    1704697200000
                  :modified      1704697200000
                  :answered?     true
                  :answered-ts   1704700000000
                  :answered-text "Answered"
                  :answered-name "Answerer"
                  :closed?       true
                  :closed-ts     1704800000000
                  :closed-text   "Closed"
                  :closed-name   "Closer"
                  :explanation   "Reason"
                  :seen-by?      false}
          result (schemas/->fronted-entry input)]
      (is (= :delay (:type result)))
      (is (= true (:deleted? result)))
      (is (= "Creator" (:created-name result)))
      (is (= "Answerer" (:answered-name result)))
      (is (= "Closer" (:closed-name result)))
      (is (= false (:seen-by? result))))))
