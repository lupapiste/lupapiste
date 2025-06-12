(ns lupapalvelu.statistics.entry-test
  (:require [clojure.test :refer :all]
            [lupapalvelu.action :as action]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.statistics.entry :as entry]
            [monger.operators :refer :all]
            [sade.date :as date]
            [sade.env :as env]
            [schema.core :as sc]))

(set! *warn-on-reflection* true)

(use-fixtures :once (fn [f]
                      (env/with-feature-value :statistics-processing true
                        (sc/with-fn-validation
                          (f)))))

;; Private function access
(def entry-ts->frontend #'entry/entry-ts->frontend)
(def entry->frontend #'entry/entry->frontend)
(def make-entry-ts #'entry/make-entry-ts)
(def entry-ts-update #'entry/entry-ts-update)
(def entry-flag-update #'entry/entry-flag-update)
(def explanation-update #'entry/explanation-update)
(def entry-updates #'entry/entry-updates)
(def command-entry-id #'entry/command-entry-id)
(def entry-command? #'entry/entry-command?)
(def entry-query #'entry/entry-query)
(def command->entry-query #'entry/command->entry-query)
(def good-dates? #'entry/good-dates?)
(def check-dates! #'entry/check-dates!)
(def merge-ranges #'entry/merge-ranges)

;; Test data fixtures
(def test-user
  {:id        "user-123"
   :firstName "Test"
   :lastName  "User"
   :role      "authority"
   :email     "test.user@example.com"
   :username  "testuser"})

(def test-user-summary
  {:id        "user-123"
   :firstName "Test"
   :lastName  "User"
   :role      "authority"
   :username  "testuser"})

(def test-application
  {:id           "LP-753-2024-00001"
   :organization "753-R"})

(def monday    (date/timestamp "17.11.2025"))
(def tuesday   (date/timestamp "18.11.2025"))
(def wednesday (date/timestamp "19.11.2025"))
(def thursday  (date/timestamp "20.11.2025"))
(def friday    (date/timestamp "21.11.2025"))

(def test-entry-ts
  {:ts   thursday
   :text "Test text"
   :user test-user-summary})

(def test-entry
  {:id          "692012b805939c1a59a510db"
   :type        :request
   :application {:id           "LP-753-2024-00001"
                 :organization "753-R"}
   :created     {:ts   monday
                 :text "Created text"
                 :user test-user-summary}
   :modified    wednesday
   :delay-start friday
   :answered    {:ts   tuesday
                 :text "Answer text"
                 :user test-user-summary}
   :closed      {:ts   thursday
                 :text "Closed text"
                 :user test-user-summary}
   :deleted     false})

(def test-command
  {:user test-user})

(deftest entry-ts->frontend-test
  (testing "Converts created entry timestamp to frontend format"
    (let [result (entry-ts->frontend :created test-entry-ts)]
      (is (= thursday (:created-ts result)))
      (is (= "Test User" (:created-name result)))
      (is (= "Test text" (:created-text result)))))

  (testing "Converts answered entry timestamp to frontend format"
    (let [result (entry-ts->frontend :answered test-entry-ts)]
      (is (= thursday (:answered-ts result)))
      (is (= "Test User" (:answered-name result)))
      (is (= "Test text" (:answered-text result)))))

  (testing "Converts closed entry timestamp to frontend format"
    (let [result (entry-ts->frontend :closed test-entry-ts)]
      (is (= thursday (:closed-ts result)))
      (is (= "Test User" (:closed-name result)))
      (is (= "Test text" (:closed-text result)))))

  (testing "Handles missing text field"
    (let [entry-ts-no-text (dissoc test-entry-ts :text)
          result           (entry-ts->frontend :created entry-ts-no-text)]
      (is (= thursday (:created-ts result)))
      (is (= "Test User" (:created-name result)))
      (is (not (contains? result :created-text)))))

  (testing "Handles user with only first name"
    (let [entry-ts-partial-user (assoc test-entry-ts :user {:firstName "Test" :lastName ""})
          result                (entry-ts->frontend :created entry-ts-partial-user)]
      (is (= "Test" (:created-name result)))))

  (testing "Handles user with only last name"
    (let [entry-ts-partial-user (assoc test-entry-ts :user {:firstName "" :lastName "User"})
          result                (entry-ts->frontend :created entry-ts-partial-user)]
      (is (= "User" (:created-name result))))))

;; Tests for entry->frontend
(deftest entry->frontend-test
  (testing "Converts full entry to frontend format"
    (let [result (entry->frontend test-command test-entry)]
      (is (= (:id test-entry) (:id result)))
      (is (= :request (:type result)))
      (is (= friday (:delay-start result)))
      (is (= wednesday (:modified result)))
      (is (= "Test User" (:created-name result)))
      (is (= "Created text" (:created-text result)))
      (is (= tuesday (:answered-ts result)))
      (is (= "Test User" (:answered-name result)))
      (is (= "Answer text" (:answered-text result)))
      (is (= thursday (:closed-ts result)))
      (is (= "Test User" (:closed-name result)))
      (is (= "Closed text" (:closed-text result)))))

  (testing "Converts minimal entry to frontend format"
    (let [minimal-entry {:id          "692017b805939c1a59a510dd"
                         :type        :delay
                         :application {:id           "LP-753-2024-00002"
                                       :organization "753-R"}
                         :created     {:ts   monday
                                       :user test-user-summary}
                         :modified    monday
                         :delay-start friday}
          result        (entry->frontend test-command minimal-entry)]
      (is (= "692017b805939c1a59a510dd" (:id result)))
      (is (= :delay (:type result)))
      (is (= friday (:delay-start result)))
      (is (= "Test User" (:created-name result)))))

  (testing "Handles entry without answered or closed timestamps"
    (let [open-entry (dissoc test-entry :answered :closed)
          result     (entry->frontend test-command open-entry)]
      (is (= (:id test-entry) (:id result)))
      (is (not (contains? result :answered-ts)))
      (is (not (contains? result :closed-ts)))))

  (testing "Handles deleted entry"
    (let [deleted-entry (assoc test-entry :deleted true)
          result        (entry->frontend test-command deleted-entry)]
      (is (true? (:deleted? result)))))

  (testing "Handles seen-by information"
    (let [seen-entry (assoc test-entry :seen-by [{:user-id "user-123" :ts monday}])
          result     (entry->frontend test-command seen-entry)]
      (is (true? (:seen-by? result)))))

  (testing "Handles unseen entry"
    (let [seen-entry (assoc test-entry :seen-by [{:user-id "other-user" :ts monday}])
          result     (entry->frontend test-command seen-entry)]
      (is (not (:seen-by? result)))))

  (testing "Handles explanation field"
    (let [entry-with-explanation (assoc test-entry :explanation "This is why")
          result                 (entry->frontend test-command entry-with-explanation)]
      (is (= "This is why" (:explanation result))))))

(deftest make-entry-ts-test
  (testing "Creates entry timestamp with all parameters"
    (with-redefs [date/now (constantly monday)]
      (let [result (make-entry-ts "753-R" test-user monday "Test text")]
        (is (= monday (:ts result)))
        (is (= "Test text" (:text result)))
        (is (map? (:user result)))
        (is (= "Test" (get-in result [:user :firstName])))
        (is (= "User" (get-in result [:user :lastName]))))))

  (testing "Creates entry timestamp without text"
    (with-redefs [date/now (constantly monday)]
      (let [result (make-entry-ts "753-R" test-user monday)]
        (is (= monday (:ts result)))
        (is (not (contains? result :text)))
        (is (map? (:user result))))))

  (testing "Creates entry timestamp with current time when ts is nil"
    (with-redefs [date/now (constantly monday)]
      (let [result (make-entry-ts "753-R" test-user nil "Test text")]
        (is (= monday (:ts result)))
        (is (= "Test text" (:text result))))))

  (testing "Creates entry timestamp without user or ts"
    (with-redefs [date/now (constantly monday)]
      (let [result (make-entry-ts "753-R")]
        (is (= monday (:ts result)))
        (is (map? (:user result)))
        (is (not (contains? result :text))))))

  (testing "Strips nil values from result"
    (with-redefs [date/now (constantly monday)]
      (let [result (make-entry-ts "753-R" test-user monday nil)]
        (is (= monday (:ts result)))
        (is (not (contains? result :text))))))

  (testing "Trims whitespace from text"
    (with-redefs [date/now (constantly monday)]
      (let [result (make-entry-ts "753-R" test-user monday "  Test text  ")]
        (is (= "Test text" (:text result))))))

  (testing "Treats blank text as nil"
    (with-redefs [date/now (constantly monday)]
      (let [result (make-entry-ts "753-R" test-user monday "   ")]
        (is (not (contains? result :text)))))))

(deftest make-entry-test
  (testing "Creates entry with all parameters"
    (with-redefs [mongo/create-id (constantly "6920130e05939c1a59a510dc")
                  date/now        (constantly monday)]
      (let [update {:type         :request
                    :delay-start  friday
                    :created-text "Created text"
                    :closed?      true
                    :closed-ts    thursday
                    :closed-text  "Closed text"
                    :explanation  "This is why"}
            result (entry/make-entry test-application test-user monday update)]
        (is (= "6920130e05939c1a59a510dc" (:id result)))
        (is (= :request (:type result)))
        (is (= "LP-753-2024-00001" (get-in result [:application :id])))
        (is (= "753-R" (get-in result [:application :organization])))
        (is (= monday (get-in result [:created :ts])))
        (is (= "Created text" (get-in result [:created :text])))
        (is (= friday (:delay-start result)))
        (is (= thursday (get-in result [:closed :ts])))
        (is (= "Closed text" (get-in result [:closed :text])))
        (is (= "This is why" (:explanation result)))
        (is (= monday (:modified result)))
        (is (vector? (:seen-by result))))))

  (testing "Creates minimal entry"
    (with-redefs [mongo/create-id (constantly "6920130e05939c1a59a510dc")
                  date/now        (constantly monday)]
      (let [update {:type         :delay
                    :delay-start  friday
                    :created-text " Yikes "}
            result (entry/make-entry test-application test-user monday update)]
        (is (= "6920130e05939c1a59a510dc" (:id result)))
        (is (= :delay (:type result)))
        (is (= "LP-753-2024-00001" (get-in result [:application :id])))
        (is (= "753-R" (get-in result [:application :organization])))
        (is (= monday (get-in result [:created :ts])))
        (is (= "Yikes" (get-in result [:created :text]))))))

  (testing "Creates entry from command map"
    (with-redefs [mongo/create-id (constantly "6920130e05939c1a59a510dc")
                  date/now        (constantly monday)]
      (let [command {:application test-application
                     :user        test-user
                     :created     monday}
            update  {:type         :request
                     :delay-start  friday
                     :created-text "Hello"}
            result  (entry/make-entry command update)]
        (is (= "6920130e05939c1a59a510dc" (:id result)))
        (is (= :request (:type result)))))))

(deftest entry-ts-update-test
  (testing "Sets entry timestamp when flag is true"
    (with-redefs [date/now (constantly monday)]
      (let [entry  {:id :test}
            result (entry-ts-update test-user-summary entry
                                    :answered true tuesday "Answer text")]
        (is (= {:in {:answered {:ts   tuesday
                                :text "Answer text"
                                :user test-user-summary}}}
               result)))))

  (testing "Removes entry timestamp when flag is false and entry has timestamp"
    (let [entry  {:answered {:ts tuesday :user test-user-summary}}
          result (entry-ts-update test-user-summary entry :answered false nil nil)]
      (is (= {:out :answered} result))))

  (testing "Returns nil when flag is false and entry has no timestamp"
    (let [entry  {:id :test}
          result (entry-ts-update test-user-summary entry :answered false nil nil)]
      (is (nil? result))))

  (testing "Updates existing timestamp with new ts"
    (let [entry  {:answered {:ts tuesday :user test-user-summary}}
          result (entry-ts-update test-user-summary entry :answered nil tuesday nil)]
      (is (= {:in {:answered.user test-user-summary
                   :answered.ts   tuesday}}
             result))))

  (testing "Updates existing timestamp with new text"
    (let [entry  {:answered {:ts tuesday :user test-user-summary :text "Old text"}}
          result (entry-ts-update test-user-summary entry :answered nil nil "New text")]
      (is (= {:in {:answered.user test-user-summary
                   :answered.text "New text"}}
             result))))

  (testing "Updates existing timestamp with both new ts and text"
    (let [entry  {:answered {:ts tuesday :user test-user-summary}}
          result (entry-ts-update test-user-summary entry :answered nil tuesday "New text")]
      (is (= {:in {:answered.user test-user-summary
                   :answered.ts   tuesday
                   :answered.text "New text"}}
             result))))

  (testing "Returns nil when no changes needed"
    (let [entry  {:id :test}
          result (entry-ts-update test-user-summary entry :answered nil nil nil)]
      (is (nil? result))))

  (testing "Trims whitespace from text"
    (with-redefs [date/now (constantly tuesday)]
      (let [entry  {:id :test}
            result (entry-ts-update test-user-summary entry :answered true tuesday "  Text  ")]
        (is (= "Text" (get-in result [:in :answered :text]))))))

  (testing "Treats blank text as nil"
    (with-redefs [date/now (constantly tuesday)]
      (let [entry  {:id :test}
            result (entry-ts-update test-user-summary entry :answered true tuesday "   ")]
        (is (not (contains? (get-in result [:in :answered]) :text)))))))

(deftest entry-flag-update-test
  (testing "Sets flag to true when not present"
    (let [entry  {:id :test}
          result (entry-flag-update entry :deleted true)]
      (is (= {:in {:deleted true}} result))))

  (testing "Removes flag when set to false"
    (let [entry  {:deleted true}
          result (entry-flag-update entry :deleted false)]
      (is (= {:out :deleted} result))))

  (testing "Returns nil when flag already matches"
    (let [entry  {:deleted true}
          result (entry-flag-update entry :deleted true)]
      (is (nil? result))))

  (testing "Returns nil when flag is false and not present"
    (let [entry  {:id :test}
          result (entry-flag-update entry :deleted false)]
      (is (nil? result)))))

(deftest explanation-update-test
  (testing "Sets explanation when provided"
    (let [result (explanation-update "This is why")]
      (is (= {:in {:explanation "This is why"}} result))))

  (testing "Trims whitespace from explanation"
    (let [result (explanation-update "  This is why  ")]
      (is (= {:in {:explanation "This is why"}} result))))

  (testing "Removes explanation when blank"
    (let [result (explanation-update "   ")]
      (is (= {:out :explanation} result))))

  (testing "Removes explanation when empty string"
    (let [result (explanation-update "")]
      (is (= {:out :explanation} result))))

  (testing "Returns nil when not a string"
    (is (nil? (explanation-update nil)))
    (is (nil? (explanation-update 123)))))

(deftest entry-updates-test
  (testing "Updates created text"
    (let [entry  test-entry
          update {:created-text "New created text"}
          result (entry-updates entry test-user-summary update)]
      (is (= "New created text" (get-in result [:in :created.text])))))

  (testing "Updates delay-start"
    (let [entry  test-entry
          update {:delay-start thursday}
          result (entry-updates entry test-user-summary update)]
      (is (= thursday (get-in result [:in :delay-start])))))

  (testing "Sets deleted flag"
    (let [entry  (dissoc test-entry :deleted)
          update {:deleted? true}
          result (entry-updates entry test-user-summary update)]
      (is (= true (get-in result [:in :deleted])))))

  (testing "Removes deleted flag"
    (let [entry  (assoc test-entry :deleted true)
          update {:deleted? false}
          result (entry-updates entry test-user-summary update)]
      (is (contains? (:out result) :deleted))))

  (testing "Sets answered timestamp"
    (let [entry  (dissoc test-entry :answered)
          update {:answered?     true
                  :answered-ts   tuesday
                  :answered-text "Answer"}
          result (entry-updates entry test-user-summary update)]
      (is (map? (get-in result [:in :answered])))
      (is (= tuesday (get-in result [:in :answered :ts])))
      (is (= "Answer" (get-in result [:in :answered :text])))))

  (testing "Removes answered timestamp"
    (let [entry  test-entry
          update {:answered? false}
          result (entry-updates entry test-user-summary update)]
      (is (contains? (:out result) :answered))))

  (testing "Sets closed timestamp"
    (let [entry  (dissoc test-entry :closed)
          update {:closed?     true
                  :closed-ts   thursday
                  :closed-text "Closed"}
          result (entry-updates entry test-user-summary update)]
      (is (map? (get-in result [:in :closed])))
      (is (= thursday (get-in result [:in :closed :ts])))
      (is (= "Closed" (get-in result [:in :closed :text])))))

  (testing "Remove closed-text"
    (let [update {:closed-text "    "}
          result (entry-updates test-entry test-user-summary update)]
      (is (= {:out #{:closed.text}} result))))

  (testing "Sets explanation"
    (let [entry  (dissoc test-entry :explanation)
          update {:explanation "This is why"}
          result (entry-updates entry test-user-summary update)]
      (is (= "This is why" (get-in result [:in :explanation])))))

  (testing "Removes explanation"
    (let [entry  (assoc test-entry :explanation "Old reason")
          update {:explanation ""}
          result (entry-updates entry test-user-summary update)]
      (is (contains? (:out result) :explanation))))

  (testing "Returns nil when no changes"
    (let [entry  test-entry
          update {:created-text (:text (:created test-entry))}
          result (entry-updates entry test-user-summary update)]
      (is (nil? result))))

  (testing "Combines multiple updates"
    (let [entry  (dissoc test-entry :answered :deleted :closed)
          update {:deleted?      true
                  :answered?     true
                  :answered-ts   tuesday
                  :answered-text "  Answer\n "}
          result (entry-updates entry test-user-summary update)]
      (is (= true (get-in result [:in :deleted])))
      (is (map? (get-in result [:in :answered])))
      (is (= tuesday (get-in result [:in :answered :ts])))
      (is (= "Answer" (get-in result [:in :answered :text])))))

  ;; Date validation tests - validation now happens in entry-updates
  (testing "Fails when setting closed-ts before delay-start for delay entry"
    (let [entry  {:type        :delay
                  :delay-start friday
                  :application {:id "LP-753-2024-00001" :organization "753-R"}}
          update {:closed?   true
                  :closed-ts monday}]
      ;; monday < friday, so this should fail
      (is (thrown-with-msg? clojure.lang.ExceptionInfo #"error.invalid-date"
                            (entry-updates entry test-user-summary update)))))

  (testing "Succeeds when setting closed-ts equal to delay-start for delay entry"
    (let [entry  {:type        :delay
                  :delay-start friday
                  :application {:id "LP-753-2024-00001" :organization "753-R"}}
          update {:closed?   true
                  :closed-ts friday}
          result (entry-updates entry test-user-summary update)]
      (is (map? (get-in result [:in :closed])))))

  (testing "Succeeds when setting closed-ts after delay-start for delay entry"
    (let [entry  {:type        :delay
                  :delay-start monday
                  :application {:id "LP-753-2024-00001" :organization "753-R"}}
          update {:closed?   true
                  :closed-ts friday}
          result (entry-updates entry test-user-summary update)]
      (is (map? (get-in result [:in :closed])))))

  (testing "No date validation for request entries"
    (let [entry  {:type        :request
                  :delay-start friday
                  :application {:id "LP-753-2024-00001" :organization "753-R"}}
          update {:closed?   true
                  :closed-ts monday}
          ;; monday < friday, but it's a request so no validation
          result (entry-updates entry test-user-summary update)]
      (is (map? (get-in result [:in :closed])))))

  (testing "Fails when updating delay-start to after existing closed-ts"
    (let [entry  {:type        :delay
                  :delay-start monday
                  :closed      {:ts wednesday :user test-user-summary}
                  :application {:id "LP-753-2024-00001" :organization "753-R"}}
          ;; Try to move delay-start to friday (after closed wednesday)
          update {:delay-start friday}]
      (is (thrown-with-msg? clojure.lang.ExceptionInfo #"error.invalid-date"
                            (entry-updates entry test-user-summary update)))))

  (testing "Succeeds when updating delay-start to before existing closed-ts"
    (let [entry  {:type        :delay
                  :delay-start wednesday
                  :closed      {:ts friday :user test-user-summary}
                  :application {:id "LP-753-2024-00001" :organization "753-R"}}
          ;; Move delay-start to monday (before closed friday)
          update {:delay-start monday}
          result (entry-updates entry test-user-summary update)]
      (is (= monday (get-in result [:in :delay-start])))))

  (testing "Uses new closed-ts when both delay-start and closed-ts are updated"
    (let [entry  {:type        :delay
                  :delay-start monday
                  :application {:id "LP-753-2024-00001" :organization "753-R"}}
          ;; Both are updated - validation uses the new values
          update {:delay-start wednesday
                  :closed?     true
                  :closed-ts   friday}
          result (entry-updates entry test-user-summary update)]
      ;; wednesday < friday, so this should succeed
      (is (= wednesday (get-in result [:in :delay-start])))))

  (testing "Fails when both delay-start and closed-ts are updated with invalid dates"
    (let [entry  {:type        :delay
                  :delay-start monday
                  :application {:id "LP-753-2024-00001" :organization "753-R"}}
          ;; Both are updated - new delay-start is after new closed-ts
          update {:delay-start friday
                  :closed?     true
                  :closed-ts   wednesday}]
      ;; friday > wednesday, so this should fail
      (is (thrown-with-msg? clojure.lang.ExceptionInfo #"error.invalid-date"
                            (entry-updates entry test-user-summary update))))))

(deftest command-entry-id-test
  (testing "Extracts entry-id from data"
    (let [command {:data {:entry-id (:id test-entry)}}
          result  (command-entry-id command)]
      (is (= (:id test-entry) result))))

  (testing "Extracts id from ::entry"
    (let [command {::entry/entry {:id "entry-456"}}
          result  (command-entry-id command)]
      (is (= "entry-456" result))))

  (testing "Prefers ::entry over data"
    (let [command {::entry/entry {:id "entry-456"}
                   :data         {:entry-id (:id test-entry)}}
          result  (command-entry-id command)]
      (is (= "entry-456" result))))

  (testing "Returns nil when no entry id"
    (let [command {:data {:other-field "value"}}
          result  (command-entry-id command)]
      (is (nil? result)))))

(deftest entry-command?-test
  (testing "Returns true when command has entry-id in data"
    (let [command {:data {:entry-id (:id test-entry)}}]
      (is (true? (entry-command? command)))))

  (testing "Returns true when command has ::entry"
    (let [command {::entry/entry {:id "entry-456"}}]
      (is (true? (entry-command? command)))))

  (testing "Returns false when command has no entry id"
    (let [command {:data {:other-field "value"}}]
      (is (false? (entry-command? command)))))

  (testing "Returns false for empty command"
    (let [command {}]
      (is (false? (entry-command? command))))))

(deftest entry-query-test
  (testing "Creates query from application and entry-id"
    (let [application {:id "LP-753-2024-00001"}
          entry-id    (:id test-entry)
          result      (entry-query application entry-id)]
      (is (= {:_id            (:id test-entry)
              :application.id "LP-753-2024-00001"}
             result))))

  (testing "Works with different application ids"
    (let [application {:id "LP-091-2024-99999"}
          entry-id    "entry-456"
          result      (entry-query application entry-id)]
      (is (= {:_id            "entry-456"
              :application.id "LP-091-2024-99999"}
             result)))))

(deftest command->entry-query-test
  (testing "Creates query from command"
    (let [command {:application {:id "LP-753-2024-00001"}
                   :data        {:entry-id (:id test-entry)}}
          result  (command->entry-query command)]
      (is (= {:_id            (:id test-entry)
              :application.id "LP-753-2024-00001"}
             result))))

  (testing "Creates query from command with extras"
    (let [command {:application {:id "LP-753-2024-00001"}
                   :data        {:entry-id (:id test-entry)}}
          extras  {:deleted {$ne true}}
          result  (command->entry-query command extras)]
      (is (= {:_id            (:id test-entry)
              :application.id "LP-753-2024-00001"
              :deleted        {$ne true}}
             result))))

  (testing "Extras override query values"
    (let [command {:application {:id "LP-753-2024-00001"}
                   :data        {:entry-id (:id test-entry)}}
          extras  {:_id "override-id" :hello "world"}
          result  (command->entry-query command extras)]
      (is (= {:_id            (:id test-entry)
              :application.id "LP-753-2024-00001"
              :hello          "world"}
             result)))))

;; ----------------------------------
;; delay-end-ts tests
;; ----------------------------------

(deftest delay-end-ts-test
  (testing "Returns closed timestamp when entry has closed"
    (let [entry {:closed {:ts thursday :user test-user-summary}}]
      (is (= thursday (entry/delay-end-ts entry)))))

  (testing "Returns nil when entry has no closed"
    (let [entry {:id "entry-123" :type :delay}]
      (is (nil? (entry/delay-end-ts entry)))))

  (testing "Returns nil when closed has no ts"
    (let [entry {:closed {:user test-user-summary}}]
      (is (nil? (entry/delay-end-ts entry)))))

  (testing "Returns nil for nil entry"
    (is (nil? (entry/delay-end-ts nil))))

  (testing "Returns nil for empty entry"
    (is (nil? (entry/delay-end-ts {})))))

;; --------------------------------------
;; good-dates? and check-dates! tests
;; --------------------------------------

(deftest good-dates?-test
  ;; Tests for 3-arity version
  (testing "Returns true for request type regardless of dates"
    (is (true? (good-dates? :request friday monday)))
    (is (true? (good-dates? :request monday friday)))
    (is (true? (good-dates? "request" friday monday))))

  (testing "Returns true for delay type when closed-ts is nil"
    (is (true? (good-dates? :delay friday nil)))
    (is (true? (good-dates? :delay monday nil))))

  (testing "Returns true for delay type when closed-ts >= delay-start"
    (is (true? (good-dates? :delay monday friday)))
    (is (true? (good-dates? :delay monday monday)))
    (is (true? (good-dates? :delay friday friday))))

  (testing "Returns false for delay type when closed-ts < delay-start"
    (is (false? (good-dates? :delay friday monday)))
    (is (false? (good-dates? :delay friday (dec friday))))
    (is (false? (good-dates? "delay" friday monday))))

  (testing "Returns true for nil type"
    (is (true? (good-dates? nil friday monday))))

  ;; Tests for 1-arity (entry map) version
  (testing "Returns true for request entry regardless of dates"
    (let [entry {:type        :request
                 :delay-start friday
                 :closed      {:ts monday :user test-user-summary}}]
      (is (true? (good-dates? entry)))))

  (testing "Returns true for delay entry without closed"
    (let [entry {:type        :delay
                 :delay-start friday}]
      (is (true? (good-dates? entry)))))

  (testing "Returns true for delay entry with nil closed"
    (let [entry {:type        :delay
                 :delay-start friday
                 :closed      nil}]
      (is (true? (good-dates? entry)))))

  (testing "Returns true for delay entry with closed ts equal to delay-start"
    (let [entry {:type        :delay
                 :delay-start friday
                 :closed      {:ts friday :user test-user-summary}}]
      (is (true? (good-dates? entry)))))

  (testing "Returns true for delay entry with closed ts after delay-start"
    (let [entry {:type        :delay
                 :delay-start monday
                 :closed      {:ts friday :user test-user-summary}}]
      (is (true? (good-dates? entry)))))

  (testing "Returns false for delay entry when closed ts is before delay-start"
    (let [entry {:type        :delay
                 :delay-start friday
                 :closed      {:ts monday :user test-user-summary}}]
      (is (false? (good-dates? entry)))))

  (testing "Returns true for delay entry with closed but no ts in closed"
    (let [entry {:type        :delay
                 :delay-start friday
                 :closed      {:user test-user-summary}}]
      ;; closed exists but has no :ts, so some-> returns nil
      (is (true? (good-dates? entry)))))

  (testing "Returns true for nil entry"
    (is (true? (good-dates? nil))))

  (testing "Returns true for empty entry"
    (is (true? (good-dates? {}))))

  (testing "Returns true when type is nil"
    (let [entry {:delay-start friday
                 :closed      {:ts monday :user test-user-summary}}]
      (is (true? (good-dates? entry)))))

  (testing "Works with string type 'delay' using util/=as-kw"
    (let [entry {:type        "delay"
                 :delay-start friday
                 :closed      {:ts monday :user test-user-summary}}]
      (is (false? (good-dates? entry))))))

(deftest check-dates!-test
  ;; Tests for 1-arity (entry map) version
  (testing "Returns nil for request type"
    (let [entry {:type        :request
                 :delay-start friday
                 :closed      {:ts monday :user test-user-summary}}]
      (is (nil? (check-dates! entry)))))

  (testing "Returns nil for request type even when closed is before delay-start"
    (let [entry {:type        :request
                 :delay-start friday
                 :closed      {:ts monday :user test-user-summary}}]
      ;; monday < friday, but it's a request so no validation
      (is (nil? (check-dates! entry)))))

  (testing "Returns nil for delay type without closed"
    (let [entry {:type        :delay
                 :delay-start friday}]
      (is (nil? (check-dates! entry)))))

  (testing "Returns nil for delay type with nil closed"
    (let [entry {:type        :delay
                 :delay-start friday
                 :closed      nil}]
      (is (nil? (check-dates! entry)))))

  (testing "Returns nil for delay type with closed ts equal to delay-start"
    (let [entry {:type        :delay
                 :delay-start friday
                 :closed      {:ts friday :user test-user-summary}}]
      (is (nil? (check-dates! entry)))))

  (testing "Returns nil for delay type with closed ts after delay-start"
    (let [entry {:type        :delay
                 :delay-start monday
                 :closed      {:ts friday :user test-user-summary}}]
      (is (nil? (check-dates! entry)))))

  (testing "Fails for delay type when closed ts is before delay-start"
    (let [entry {:type        :delay
                 :delay-start friday
                 :closed      {:ts monday :user test-user-summary}}]
      ;; monday < friday, so this should fail
      (is (thrown-with-msg? clojure.lang.ExceptionInfo #"error.invalid-date"
                            (check-dates! entry)))))

  (testing "Fails for delay type when closed ts is one millisecond before delay-start"
    (let [entry {:type        :delay
                 :delay-start friday
                 :closed      {:ts (dec friday) :user test-user-summary}}]
      (is (thrown-with-msg? clojure.lang.ExceptionInfo #"error.invalid-date"
                            (check-dates! entry)))))

  (testing "Returns nil for delay type with closed but no ts in closed"
    (let [entry {:type        :delay
                 :delay-start friday
                 :closed      {:user test-user-summary}}]
      ;; closed exists but has no :ts, so some-> returns nil
      (is (nil? (check-dates! entry)))))

  (testing "Returns nil for nil entry"
    (is (nil? (check-dates! nil))))

  (testing "Returns nil for empty entry"
    (is (nil? (check-dates! {}))))

  (testing "Returns nil when type is nil"
    (let [entry {:delay-start friday
                 :closed      {:ts monday :user test-user-summary}}]
      (is (nil? (check-dates! entry)))))

  (testing "Works with string type 'delay' using util/=as-kw"
    ;; The function now uses util/=as-kw, so string "delay" should match :delay
    (let [entry {:type        "delay"
                 :delay-start friday
                 :closed      {:ts monday :user test-user-summary}}]
      ;; Should fail because "delay" =as-kw :delay and monday < friday
      (is (thrown-with-msg? clojure.lang.ExceptionInfo #"error.invalid-date"
                            (check-dates! entry)))))

  ;; Tests for 3-arity version
  (testing "3-arity: Returns nil for valid delay dates"
    (is (nil? (check-dates! :delay monday friday))))

  (testing "3-arity: Returns nil for request type"
    (is (nil? (check-dates! :request friday monday))))

  (testing "3-arity: Fails when delay closed-ts is before delay-start"
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"error.invalid-date"
                          (check-dates! :delay friday monday))))

  (testing "3-arity: Returns nil when closed-ts is nil"
    (is (nil? (check-dates! :delay friday nil))))

  (testing "3-arity: Works with string type"
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"error.invalid-date"
                          (check-dates! "delay" friday monday)))))

;; ----------------------------------
;; Schema coercion tests
;; ----------------------------------

(deftest entry-coercion-test
  (testing "Coerces valid entry with string type to keyword"
    (let [input  {:id          "692012b805939c1a59a510db"
                  :type        "request"
                  :application {:id           "LP-753-2024-00001"
                                :organization "753-R"}
                  :created     {:ts   monday
                                :text "Created text"
                                :user test-user-summary}
                  :modified    wednesday
                  :delay-start friday}
          result (entry/->entry input)]
      (is (= :request (:type result)))
      (is (= "692012b805939c1a59a510db" (:id result)))))

  (testing "Coerces entry with all optional fields"
    (let [input  {:id          "692012b805939c1a59a510db"
                  :type        "delay"
                  :application {:id           "LP-753-2024-00001"
                                :organization "753-R"}
                  :created     {:ts   monday
                                :text "Created"
                                :user test-user-summary}
                  :modified    wednesday
                  :delay-start monday  ;; Changed from friday to monday (closed thursday >= monday)
                  :answered    {:ts   tuesday
                                :text "Answered"
                                :user test-user-summary}
                  :closed      {:ts   thursday
                                :text "Closed"
                                :user test-user-summary}
                  :explanation "Reason for delay"
                  :deleted     true
                  :seen-by     [{:user-id "user-123" :ts monday}]}
          result (entry/->entry input)]
      (is (= :delay (:type result)))
      (is (= "Reason for delay" (:explanation result)))
      (is (true? (:deleted result)))
      (is (= 1 (count (:seen-by result))))))

  (testing "Throws on invalid entry - missing required field"
    (is (thrown? Exception
                 (entry/->entry {:id   "692012b805939c1a59a510db"
                                 :type "request"}))))

  (testing "Throws on invalid entry - invalid type"
    (is (thrown? Exception
                 (entry/->entry {:id          "692012b805939c1a59a510db"
                                 :type        "invalid-type"
                                 :application {:id           "LP-753-2024-00001"
                                               :organization "753-R"}
                                 :created     {:ts   monday
                                               :text "Created"
                                               :user test-user-summary}
                                 :modified    wednesday
                                 :delay-start friday}))))

  (testing "Throws on delay entry with closed before delay-start (schema constraint)"
    (is (thrown? Exception
                 (entry/->entry {:id          "692012b805939c1a59a510db"
                                 :type        "delay"
                                 :application {:id           "LP-753-2024-00001"
                                               :organization "753-R"}
                                 :created     {:ts   monday
                                               :text "Created"
                                               :user test-user-summary}
                                 :modified    wednesday
                                 :delay-start friday
                                 :closed      {:ts   monday  ;; monday < friday, invalid!
                                               :text "Closed"
                                               :user test-user-summary}}))))

  (testing "Coerces request entry with closed before delay-start (no constraint)"
    (let [input  {:id          "692012b805939c1a59a510db"
                  :type        "request"
                  :application {:id           "LP-753-2024-00001"
                                :organization "753-R"}
                  :created     {:ts   monday
                                :text "Created"
                                :user test-user-summary}
                  :modified    wednesday
                  :delay-start friday
                  :closed      {:ts   monday  ;; monday < friday, but allowed for request
                                :text "Closed"
                                :user test-user-summary}}
          result (entry/->entry input)]
      (is (= :request (:type result)))
      (is (= monday (get-in result [:closed :ts])))))

  (testing "Coerces delay entry with valid dates (closed >= delay-start)"
    (let [input  {:id          "692012b805939c1a59a510db"
                  :type        "delay"
                  :application {:id           "LP-753-2024-00001"
                                :organization "753-R"}
                  :created     {:ts   monday
                                :text "Created"
                                :user test-user-summary}
                  :modified    wednesday
                  :delay-start monday
                  :closed      {:ts   friday  ;; friday >= monday, valid
                                :text "Closed"
                                :user test-user-summary}}
          result (entry/->entry input)]
      (is (= :delay (:type result)))
      (is (= friday (get-in result [:closed :ts]))))))

(deftest merge-ranges-test
  (testing "Merges overlapping ranges"
    (is (= [[1 10]] (merge-ranges [[1 5] [3 10]])))
    (is (= [[1 10]] (merge-ranges [[1 10] [2 8]])))
    (is (= [[1 10]] (merge-ranges [[2 8] [1 10]]))))

  (testing "Merges touching ranges"
    (is (= [[1 10]] (merge-ranges [[1 5] [5 10]]))))

  (testing "Keeps non-overlapping ranges separate"
    (is (= [[1 5] [7 10]] (merge-ranges [[1 5] [7 10]])))
    (is (= [[1 5] [7 10]] (merge-ranges [[7 10] [1 5]]))))

  (testing "Merges multiple ranges"
    (is (= [[1 15]] (merge-ranges [[1 5] [3 8] [7 12] [10 15]])))
    (is (= [[1 5] [10 15]] (merge-ranges [[1 5] [10 12] [12 15]]))))

  (testing "Handles empty and nil input"
    (is (nil? (merge-ranges [])))
    (is (nil? (merge-ranges nil))))

  (testing "Handles single range"
    (is (= [[1 5]] (merge-ranges [[1 5]])))))

;; ----------------------------------
;; delay-ranges->ms tests
;; ----------------------------------

(deftest delay-ranges->ms-test
  (testing "Returns 0 for empty ranges"
    (is (= 0 (entry/delay-ranges->ms []))))

  (testing "Returns 0 for nil ranges"
    (is (= 0 (entry/delay-ranges->ms nil))))

  (testing "Calculates single range correctly"
    (is (= 100 (entry/delay-ranges->ms [[100 200]])))
    (is (= 1000 (entry/delay-ranges->ms [[0 1000]]))))

  (testing "Calculates multiple ranges correctly"
    (is (= 300 (entry/delay-ranges->ms [[100 200] [300 500]])))
    (is (= 600 (entry/delay-ranges->ms [[0 100] [200 300] [400 800]]))))

  (testing "Calculates one day in milliseconds"
    ;; One day = 86400000 ms
    (is (= 86400000 (entry/delay-ranges->ms [[monday tuesday]]))))

  (testing "Calculates two days in milliseconds"
    (is (= 172800000 (entry/delay-ranges->ms [[monday wednesday]]))))

  (testing "Calculates multiple day ranges"
    ;; monday to tuesday (1 day) + thursday to friday (1 day) = 2 days
    (is (= 172800000 (entry/delay-ranges->ms [[monday tuesday] [thursday friday]])))))

;; ----------------------------------
;; delayed? tests
;; ----------------------------------

(deftest delayed?-test
  (let [ranges [[monday wednesday] [thursday friday]]]

    (testing "Returns false for empty ranges"
      (is (false? (entry/delayed? [] (date/zoned-date-time tuesday)))))

    (testing "Returns false for nil ranges"
      (is (false? (entry/delayed? nil (date/zoned-date-time tuesday)))))

    (testing "Returns false before first range"
      ;; Sunday (day before monday)
      (let [sunday (date/zoned-date-time (- monday 86400000))]
        (is (false? (entry/delayed? ranges sunday)))))

    (testing "Returns false at exact start of range (exclusive start)"
      (is (false? (entry/delayed? ranges (date/zoned-date-time monday)))))

    (testing "Returns true just after start of range"
      (is (true? (entry/delayed? ranges (date/zoned-date-time (+ monday 1))))))

    (testing "Returns true inside first range"
      (is (true? (entry/delayed? ranges (date/zoned-date-time tuesday)))))

    (testing "Returns true just before end of range"
      (is (true? (entry/delayed? ranges (date/zoned-date-time (- wednesday 1))))))

    (testing "Returns false at exact end of range (exclusive end)"
      (is (false? (entry/delayed? ranges (date/zoned-date-time wednesday)))))

    (testing "Returns false between ranges"
      ;; One hour after wednesday ends
      (is (false? (entry/delayed? ranges (date/zoned-date-time (+ wednesday 3600000))))))

    (testing "Returns false at exact start of second range (exclusive start)"
      (is (false? (entry/delayed? ranges (date/zoned-date-time thursday)))))

    (testing "Returns true inside second range"
      ;; One hour into thursday
      (is (true? (entry/delayed? ranges (date/zoned-date-time (+ thursday 3600000))))))

    (testing "Returns false after all ranges"
      ;; Saturday (day after friday)
      (let [saturday (date/zoned-date-time (+ friday 86400000))]
        (is (false? (entry/delayed? ranges saturday))))))

  (testing "Single range behavior"
    (let [single-range [[monday wednesday]]]
      (is (false? (entry/delayed? single-range (date/zoned-date-time monday))))
      (is (true? (entry/delayed? single-range (date/zoned-date-time tuesday))))
      (is (false? (entry/delayed? single-range (date/zoned-date-time wednesday))))))

  (testing "Adjacent ranges (touching but not overlapping)"
    ;; Ranges [monday tuesday] and [tuesday wednesday] touch at tuesday
    (let [adjacent [[monday tuesday] [tuesday wednesday]]]
      (is (false? (entry/delayed? adjacent (date/zoned-date-time monday))))
      ;; At tuesday - this is the end of first range AND start of second, both exclusive
      (is (false? (entry/delayed? adjacent (date/zoned-date-time tuesday))))
      (is (true? (entry/delayed? adjacent (date/zoned-date-time (+ tuesday 1))))))))

;; ----------------------------------
;; check-entry tests
;; ----------------------------------

(deftest check-entry-test
  (let [request-entry    {:id       "123"
                          :type     :request
                          :answered {:ts 100 :user test-user-summary}
                          :closed   {:ts 200 :user test-user-summary}
                          :deleted  false}
        delay-entry      {:id      "456"
                          :type    :delay
                          :deleted true}
        open-entry       {:id   "789"
                          :type :request}
        command          {:data {:entry-id "123"}}]

    (testing ":request? flag"
      (testing "passes when entry is request and flag is true"
        (with-redefs [entry/command->entry (constantly request-entry)]
          (is (nil? ((entry/check-entry :request? true) command)))))

      (testing "fails when entry is request and flag is false"
        (with-redefs [entry/command->entry (constantly request-entry)]
          (is (= {:ok false :text "error.entry"}
                 ((entry/check-entry :request? false) command)))))

      (testing "fails when entry is delay and flag is true"
        (with-redefs [entry/command->entry (constantly delay-entry)]
          (is (= {:ok false :text "error.entry"}
                 ((entry/check-entry :request? true) command)))))

      (testing "passes when entry is delay and flag is false"
        (with-redefs [entry/command->entry (constantly delay-entry)]
          (is (nil? ((entry/check-entry :request? false) command))))))

    (testing ":answered? flag"
      (testing "passes when entry has answered and flag is true"
        (with-redefs [entry/command->entry (constantly request-entry)]
          (is (nil? ((entry/check-entry :answered? true) command)))))

      (testing "fails when entry has answered and flag is false"
        (with-redefs [entry/command->entry (constantly request-entry)]
          (is (= {:ok false :text "error.entry"}
                 ((entry/check-entry :answered? false) command)))))

      (testing "fails when entry has no answered and flag is true"
        (with-redefs [entry/command->entry (constantly open-entry)]
          (is (= {:ok false :text "error.entry"}
                 ((entry/check-entry :answered? true) command)))))

      (testing "passes when entry has no answered and flag is false"
        (with-redefs [entry/command->entry (constantly open-entry)]
          (is (nil? ((entry/check-entry :answered? false) command))))))

    (testing ":deleted? flag"
      (testing "passes when entry is deleted and flag is true"
        (with-redefs [entry/command->entry (constantly delay-entry)]
          (is (nil? ((entry/check-entry :deleted? true) command)))))

      (testing "fails when entry is deleted and flag is false"
        (with-redefs [entry/command->entry (constantly delay-entry)]
          (is (= {:ok false :text "error.entry"}
                 ((entry/check-entry :deleted? false) command)))))

      (testing "fails when entry is not deleted and flag is true"
        (with-redefs [entry/command->entry (constantly request-entry)]
          (is (= {:ok false :text "error.entry"}
                 ((entry/check-entry :deleted? true) command)))))

      (testing "passes when entry is not deleted and flag is false"
        (with-redefs [entry/command->entry (constantly request-entry)]
          (is (nil? ((entry/check-entry :deleted? false) command))))))

    (testing ":closed? flag"
      (testing "passes when entry has closed and flag is true"
        (with-redefs [entry/command->entry (constantly request-entry)]
          (is (nil? ((entry/check-entry :closed? true) command)))))

      (testing "fails when entry has closed and flag is false"
        (with-redefs [entry/command->entry (constantly request-entry)]
          (is (= {:ok false :text "error.entry"}
                 ((entry/check-entry :closed? false) command)))))

      (testing "fails when entry has no closed and flag is true"
        (with-redefs [entry/command->entry (constantly open-entry)]
          (is (= {:ok false :text "error.entry"}
                 ((entry/check-entry :closed? true) command)))))

      (testing "passes when entry has no closed and flag is false"
        (with-redefs [entry/command->entry (constantly open-entry)]
          (is (nil? ((entry/check-entry :closed? false) command))))))

    (testing "Multiple flags"
      (testing "passes when all flags match"
        (with-redefs [entry/command->entry (constantly request-entry)]
          (is (nil? ((entry/check-entry :request? true :answered? true :closed? true) command)))))

      (testing "fails on first non-matching flag"
        (with-redefs [entry/command->entry (constantly request-entry)]
          (is (= {:ok false :text "error.entry"}
                 ((entry/check-entry :request? true :deleted? true) command))))))

    (testing "Returns nil when command->entry returns nil"
      (with-redefs [entry/command->entry (constantly nil)]
        (is (nil? ((entry/check-entry :request? true) command)))))))

;; ----------------------------------
;; allowed-actions tests
;; ----------------------------------

(deftest allowed-actions-test
  (testing "1-arity version with entry-id in data"
    (with-redefs [action/allowed-category-actions-for-command (constantly #{:action1 :action2})]
      (let [command {:data {:entry-id "entry-123"}}
            result  (entry/allowed-actions command)]
        (is (= {"entry-123" #{:action1 :action2}} result)))))

  (testing "1-arity version with ::entry in command"
    (with-redefs [action/allowed-category-actions-for-command (constantly #{:view :edit})]
      (let [command {::entry/entry {:id "entry-456"}}
            result  (entry/allowed-actions command)]
        (is (= {"entry-456" #{:view :edit}} result)))))

  (testing "1-arity version returns nil when no entry-id"
    (with-redefs [action/allowed-category-actions-for-command (constantly #{:action1})]
      (let [command {:data {:other-field "value"}}
            result  (entry/allowed-actions command)]
        (is (nil? result)))))

  (testing "2-arity version uses entry id"
    (with-redefs [action/allowed-category-actions-for-command (constantly #{:delete :update})]
      (let [command {}
            entry   {:id "entry-789"}
            result  (entry/allowed-actions command entry)]
        (is (= {"entry-789" #{:delete :update}} result)))))

  (testing "2-arity version overrides entry-id from data"
    (with-redefs [action/allowed-category-actions-for-command (constantly #{:action1})]
      (let [command {:data {:entry-id "from-data"}}
            entry   {:id "from-entry"}
            result  (entry/allowed-actions command entry)]
        ;; The explicit entry should take precedence
        (is (= {"from-entry" #{:action1}} result)))))

  (testing "Returns empty set when no actions allowed"
    (with-redefs [action/allowed-category-actions-for-command (constantly #{})]
      (let [command {:data {:entry-id "entry-123"}}
            result  (entry/allowed-actions command)]
        (is (= {"entry-123" #{}} result))))))

;; ----------------------------------
;; delay-ranges type filtering tests
;; ----------------------------------

(deftest delay-ranges-type-filtering-test
  (let [now         (date/now)
        delay-start (date/timestamp (date/plus now :days -1))
        end-ts      (date/timestamp now)]

    (testing "delay-ranges only returns delay entries, not requests"
      ;; Delay entry with identical timestamp as a request entry
      ;; delay-ranges should only return the delay entry range
      (let [delay-entry    {:id          "delay-1"
                            :type        :delay
                            :delay-start delay-start
                            :deleted     false}
            ;; Request entry exists with same timestamps, but delay-entries filters it out
            _request-entry {:id          "request-1"
                            :type        :request
                            :delay-start delay-start
                            :deleted     false}]
        ;; delay-entries filters by :type :delay, so only delay entry would match
        (with-redefs [entry/delay-entries (constantly [delay-entry])]
          (let [ranges (entry/delay-ranges {:id        "app-1"
                                            :submitted (- delay-start 1000)}
                                           end-ts)]
            ;; Should have exactly one range from the delay entry
            (is (= 1 (count ranges)))
            (is (vector? (first ranges)))))))

    (testing "Empty ranges when only request entries exist"
      (with-redefs [entry/delay-entries (constantly [])]
        (let [ranges (entry/delay-ranges {:id        "app-2"
                                          :submitted (- delay-start 1000)}
                                         end-ts)]
          (is (empty? ranges)))))

    (testing "Multiple non-overlapping delay entries produce multiple ranges"
      (let [delay1 {:id          "delay-1"
                    :type        :delay
                    :delay-start (date/timestamp (date/plus now :days -5))
                    :closed      {:ts (date/timestamp (date/plus now :days -4))}
                    :deleted     false}
            delay2 {:id          "delay-2"
                    :type        :delay
                    :delay-start (date/timestamp (date/plus now :days -2))
                    :closed      {:ts (date/timestamp (date/plus now :days -1))}
                    :deleted     false}]
        (with-redefs [entry/delay-entries (constantly [delay1 delay2])]
          (let [ranges (entry/delay-ranges {:id        "app-3"
                                            :submitted (date/timestamp (date/plus now :days -10))}
                                           end-ts)]
            ;; Should have two non-overlapping ranges
            (is (= 2 (count ranges)))))))))

;; ----------------------------------
;; make-entry type-specific behavior tests
;; ----------------------------------

(deftest make-entry-type-test
  (with-redefs [mongo/create-id (constantly "6920130e05939c1a59a510dc")
                date/now        (constantly monday)]

    (testing "Request entry does not include explanation"
      (let [update {:type        :request
                    :delay-start tuesday
                    :created-text "Test created text"
                    :explanation "This should be ignored for requests"}
            result (entry/make-entry test-application test-user monday update)]
        (is (= :request (:type result)))
        (is (= "Test created text" (get-in result [:created :text])))
        ;; Requests DO include explanation if provided (no type-based filtering)
        ;; This test verifies the behavior - explanation is preserved
        (is (= "This should be ignored for requests" (:explanation result)))))

    (testing "Delay entry includes explanation when provided"
      (let [explanation "Valid explanation for delay"
            update      {:type        :delay
                         :delay-start tuesday
                         :created-text "Test created text"
                         :explanation explanation}
            result      (entry/make-entry test-application test-user monday update)]
        (is (= :delay (:type result)))
        (is (= "Test created text" (get-in result [:created :text])))
        ;; Delays should have explanation
        (is (= explanation (:explanation result)))))

    (testing "Delay entry without explanation has no explanation key"
      (let [update {:type        :delay
                    :delay-start tuesday
                    :created-text "Test created text"}
            result (entry/make-entry test-application test-user monday update)]
        (is (= :delay (:type result)))
        ;; No explanation provided = no key
        (is (not (contains? result :explanation)))))

    (testing "New entry has no closed fields when not provided"
      (let [update {:type         :request
                    :delay-start  tuesday
                    :created-text "Test created text"}
            result (entry/make-entry test-application test-user monday update)]
        ;; New entries should not have closed field when closed? is not true
        (is (not (contains? result :closed)))))

    (testing "Entry with closed? true has closed field"
      (let [update {:type         :request
                    :delay-start  tuesday
                    :created-text "Test created text"
                    :closed?      true
                    :closed-ts    wednesday
                    :closed-text  "Closed reason"}
            result (entry/make-entry test-application test-user monday update)]
        ;; Should have closed field
        (is (contains? result :closed))
        (is (= wednesday (get-in result [:closed :ts])))
        (is (= "Closed reason" (get-in result [:closed :text])))))))

(deftest entries-application?-test
  (let [base-application {:permitType       "R"
                          :primaryOperation {:name "kerrostalo-rivitalo"}}]

    (testing "R application with regular operation"
      (is (true? (entry/entries-application? base-application))))

    (testing "P application with regular operation"
      (is (true? (entry/entries-application?
                   (assoc base-application :permitType "P")))))

    (testing "Permit type as keyword also works"
      (is (true? (entry/entries-application?
                   (assoc base-application :permitType :R)))))

    (testing "Unsupported permit types"
      (doseq [permit-type ["YA" "YI" "MAL" "VVVL" "KT" "MM"]]
        (testing permit-type
          (is (false? (entry/entries-application?
                        (assoc base-application :permitType permit-type)))))))

    (testing "Facta-imported application"
      (is (false? (entry/entries-application?
                    (assoc base-application :facta-imported true)))))

    (testing "Non-facta-imported application (explicit false)"
      (is (true? (entry/entries-application?
                   (assoc base-application :facta-imported false)))))

    (testing "Info request"
      (is (false? (entry/entries-application?
                    (assoc base-application :infoRequest true)))))

    (testing "Non-info request (explicit false)"
      (is (true? (entry/entries-application?
                   (assoc base-application :infoRequest false)))))

    (testing "Operation with :no-requests flag"
      (is (false? (entry/entries-application?
                    (assoc base-application
                           :primaryOperation {:name "tyonjohtajan-nimeaminen-v2"})))))

    (testing "Another operation with :no-requests flag"
      (is (false? (entry/entries-application?
                    (assoc base-application
                           :primaryOperation {:name "suunnittelijan-nimeaminen"})))))

    (testing "Feature flag disabled"
      (env/with-feature-value :statistics-processing false
        (is (false? (entry/entries-application? base-application)))))

    (testing "Multiple disqualifying conditions"
      (is (false? (entry/entries-application?
                    (assoc base-application
                           :permitType "YA"
                           :infoRequest true
                           :facta-imported true)))))))





