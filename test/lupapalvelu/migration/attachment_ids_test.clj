(ns lupapalvelu.migration.attachment-ids-test
  (:require [clojure.test :refer :all]
            [lupapalvelu.migration.attachment-ids :as att-ids]
            [monger.operators :refer :all]
            [sade.util :as util]))

(def find-duplicates-by-id #'att-ids/find-duplicates-by-id)
(def archived? #'att-ids/archived?)
(def versions? #'att-ids/versions?)
(def winner #'att-ids/winner)
(def remove-one #'att-ids/remove-one)
(def losers #'att-ids/losers)
(def winners-and-losers #'att-ids/winners-and-losers)
(def duplicate-purge-updates #'att-ids/duplicate-purge-updates)

;;; Test Fixtures

(def archived-with-versions
  {:id       "1"
   :metadata {:tila "arkistoitu"}
   :versions [{:version 1}]})

(def archived-no-versions
  {:id       "1"
   :metadata {:tila "arkistoitu"}
   :versions []})

(def not-archived-with-versions
  {:id       "1"
   :metadata {:tila "active"}
   :versions [{:version 1}]})

(def not-archived-no-versions
  {:id       "1"
   :metadata {:tila "active"}
   :versions []})

(def unique-attachment-1
  {:id       "unique-1"
   :metadata {:tila "active"}
   :versions []})

(def unique-attachment-2
  {:id       "unique-2"
   :metadata {:tila "active"}
   :versions []})

(def different-id-attachment
  {:id       "different"
   :metadata {:tila "active"}
   :versions []})

;;; Tests for find-duplicates-by-id

(deftest test-find-duplicates-by-id
  (testing "Returns nil when no duplicates"
    (is (nil? (find-duplicates-by-id [{:id "1"} {:id "2"}]))))

  (testing "Finds single group of duplicates"
    (let [[x & _ :as result] (find-duplicates-by-id [{:id "1"} {:id "1"} {:id "2"}])]
      (is (= 1 (count result)))
      (is (= 2 (count x)))
      (is (= [{:id "1"} {:id "1"}] x))))

  (testing "Finds multiple groups of duplicates"
    (let [[x y & _ :as result] (find-duplicates-by-id [{:id "1"} {:id "1"}
                                                       {:id "2"} {:id "2"}])]
      (is (= 2 (count result)))
      (is (every? #(= 2 (count %)) result))
      (is (= [{:id "1"} {:id "1"}] x))
      (is (= [{:id "2"} {:id "2"}] y))))

  (testing "Returns nil for empty input"
    (is (nil? (find-duplicates-by-id []))))

  (testing "Groups preserve all duplicate instances"
    (let [att1   {:id "x" :data "first"}
          att2   {:id "x" :data "second"}
          att3   {:id "x" :data "third"}
          result (find-duplicates-by-id [att1 att2 att3])]
      (is (= 1 (count result)))
      (is (= 3 (count (first result))))
      (is (= #{att1 att2 att3} (set (first result)))))))

;;; Tests for archived?

(deftest test-archived?
  (testing "Returns true for archived attachment"
    (is (true? (archived? {:metadata {:tila "arkistoitu"}}))))

  (testing "Returns false for non-archived attachment"
    (is (false? (archived? {:metadata {:tila "active"}}))))

  (testing "Returns false when tila is nil"
    (is (false? (archived? {:metadata {}}))))

  (testing "Returns false for different tila values"
    (is (false? (archived? {:metadata {:tila "poistettu"}})))
    (is (false? (archived? {:metadata {:tila "luonnos"}})))
    (is (false? (archived? {:metadata {:tila "valmis"}})))))

;;; Tests for versions?

(deftest test-versions?
  (testing "Returns true for non-empty versions"
    (is (true? (versions? {:versions [{:v 1}]})))
    (is (true? (versions? {:versions [1 2 3]})))
    (is (true? (versions? {:versions ["a"]}))))

  (testing "Returns false for empty versions"
    (is (false? (versions? {:versions []}))))

  (testing "Returns false for nil versions"
    (is (false? (versions? {:versions nil}))))

  (testing "Returns false when versions key is missing"
    (is (false? (versions? {})))
    (is (false? (versions? {:id "123"})))))

;;; Tests for winner - Precondition Validation

(deftest test-winner-preconditions
  (testing "Both attachments must have :id"
    (is (thrown? AssertionError
                 (winner {} archived-with-versions)))
    (is (thrown? AssertionError
                 (winner archived-with-versions {})))
    (is (thrown? AssertionError
                 (winner {} {}))))

  (testing "IDs must match"
    (is (thrown? AssertionError
                 (winner archived-with-versions different-id-attachment)))
    (is (thrown? AssertionError
                 (winner unique-attachment-1 unique-attachment-2))))

  (testing "Valid calls with matching IDs succeed"
    (is (= archived-with-versions
           (winner archived-with-versions archived-no-versions)))
    (is (= archived-with-versions
           (winner archived-no-versions archived-with-versions)))))

(deftest test-winner-priority-levels
  (testing "Archived+Versioned wins over all"
    (is (= archived-with-versions
           (winner archived-with-versions archived-no-versions)))
    (is (= archived-with-versions
           (winner archived-no-versions archived-with-versions)))
    (is (= archived-with-versions
           (winner archived-with-versions not-archived-with-versions)))
    (is (= archived-with-versions
           (winner archived-with-versions not-archived-no-versions))))

  (testing "Archived-only wins over versioned-only and neither"
    (is (= archived-no-versions
           (winner archived-no-versions not-archived-with-versions)))
    (is (= archived-no-versions
           (winner archived-no-versions not-archived-no-versions))))

  (testing "Versioned-only wins over neither"
    (is (= not-archived-with-versions
           (winner not-archived-with-versions not-archived-no-versions))))

  (testing "First wins when equal priority"
    (let [att1 {:id "same" :metadata {:tila "active"} :versions []}
          att2 {:id "same" :metadata {:tila "valmis"} :versions []}]
      (is (= att1 (winner att1 att2)))
      (is (= att2 (winner att2 att1))))))

(deftest test-winner-equality
  (testing "Returns same attachment when equal"
    (is (= archived-with-versions
           (winner archived-with-versions archived-with-versions)))))

;;; Tests for remove-one - New Error Handling

(deftest test-remove-one-success-cases
  (testing "Removes first occurrence of target"
    (is (= [2 3 1]
           (remove-one 1 [1 2 3 1]))))

  (testing "Removes only element"
    (is (= []
           (remove-one 1 [1]))))

  (testing "Removes from beginning"
    (is (= [2 3 4]
           (remove-one 1 [1 2 3 4]))))

  (testing "Removes from middle"
    (is (= [1 2 4 5]
           (remove-one 3 [1 2 3 4 5]))))

  (testing "Removes from end"
    (is (= [1 2 3]
           (remove-one 4 [1 2 3 4]))))

  (testing "Preserves order"
    (is (= [:a :b :c :d]
           (remove-one :x [:a :b :x :c :d]))))

  (testing "Works with maps"
    (is (= [{:id 2} {:id 3}]
           (remove-one {:id 1} [{:id 1} {:id 2} {:id 3}])))))

(deftest test-remove-one-error-cases
  (testing "Throws when target not found"
    (is (thrown-with-msg? Exception
                          #"Target not found"
                          (remove-one 5 [1 2 3 4]))))

  (testing "Throws with meaningful error data"
    (try
      (remove-one :missing [:a :b :c])
      (is false "Should have thrown")
      (catch Exception e
        (let [data (ex-data e)]
          (is (= :missing (:target data)))
          (is (= [:a :b :c] (:items data)))))))

  (testing "Throws when list is empty"
    (is (thrown-with-msg? Exception
                          #"Target not found"
                          (remove-one 1 [])))))

(deftest test-remove-one-with-attachments
  (testing "Removes attachment by equality"
    (let [items  [archived-with-versions
                  not-archived-no-versions
                  unique-attachment-1]
          result (remove-one not-archived-no-versions items)]
      (is (= 2 (count result)))
      (is (some #(= % archived-with-versions) result))
      (is (some #(= % unique-attachment-1) result))
      (is (not (some #(= % not-archived-no-versions) result)))))

  (testing "Throws when attachment not in list"
    (is (thrown? Exception
                 (remove-one different-id-attachment
                             [archived-with-versions
                              not-archived-no-versions])))))

;;; Tests for losers

(deftest test-losers-basic
  (testing "Returns all except winner"
    (let [duplicates [not-archived-no-versions
                      archived-with-versions
                      not-archived-with-versions]
          result     (losers duplicates)]
      (is (= 2 (count result)))
      (is (not (some #(= % archived-with-versions) result)))))

  (testing "Throws for single attachment (invariant)"
    (is (thrown? AssertionError (losers [archived-with-versions]))))

  (testing "Works with two attachments"
    (let [result (losers [archived-no-versions not-archived-no-versions])]
      (is (= 1 (count result)))
      (is (= not-archived-no-versions (first result))))))

(deftest test-losers-winner-priority
  (testing "Highest priority always excluded from losers"
    (let [duplicates [not-archived-no-versions
                      not-archived-with-versions
                      archived-no-versions
                      archived-with-versions]
          result     (losers duplicates)]
      (is (= 3 (count result)))
      (is (not (some #(= % archived-with-versions) result)))))

  (testing "Multiple attachments with same ID but different priority"
    (let [best   {:id "doc" :metadata {:tila "arkistoitu"} :versions [{:v 1}]}
          good   {:id "doc" :metadata {:tila "arkistoitu"} :versions []}
          okay   {:id "doc" :metadata {:tila "active"} :versions [{:v 1}]}
          poor   {:id "doc" :metadata {:tila "active"} :versions []}
          result (losers [poor okay good best])]
      (is (= 3 (count result)))
      (is (not (some #(= % best) result))))))

;;; Tests for winners-and-losers

(deftest test-winners-and-losers-no-duplicates
  (testing "Returns nil when no duplicates"
    (is (nil? (winners-and-losers
                {:attachments [unique-attachment-1 unique-attachment-2]}))))

  (testing "Returns nil for empty attachments"
    (is (nil? (winners-and-losers {:attachments []}))))

  (testing "Returns nil for single attachment"
    (is (nil? (winners-and-losers {:attachments [unique-attachment-1]})))))

(deftest test-winners-and-losers-single-group
  (testing "Correctly identifies winners and losers"
    (let [app    {:attachments [archived-with-versions
                                not-archived-no-versions
                                unique-attachment-1]}
          result (winners-and-losers app)]
      (is (some? result))
      (is (= 1 (count (:losers result))))
      (is (= 2 (count (:winners result))))
      (is (some #(= % archived-with-versions) (:winners result)))
      (is (some #(= % unique-attachment-1) (:winners result)))
      (is (= not-archived-no-versions (first (:losers result))))))

  (testing "All duplicates except winner become losers"
    (let [app    {:attachments [archived-with-versions
                                not-archived-no-versions
                                not-archived-with-versions
                                archived-no-versions]}
          result (winners-and-losers app)]
      (is (= 3 (count (:losers result))))
      (is (= 1 (count (:winners result)))))))

(deftest test-winners-and-losers-multiple-groups
  (testing "Handles multiple duplicate groups"
    (let [dup1a  {:id "group-1" :metadata {:tila "arkistoitu"} :versions []}
          dup1b  {:id "group-1" :metadata {:tila "active"} :versions []}
          dup2a  {:id "group-2" :metadata {:tila "active"} :versions [{:v 1}]}
          dup2b  {:id "group-2" :metadata {:tila "active"} :versions []}
          app    {:attachments [dup1a dup1b dup2a dup2b unique-attachment-1]}
          result (winners-and-losers app)]
      (is (= 2 (count (:losers result))))
      (is (= 3 (count (:winners result))))
      (is (some #(= % dup1a) (:winners result)))
      (is (some #(= % dup2a) (:winners result)))
      (is (some #(= % unique-attachment-1) (:winners result)))))

  (testing "Three groups with different sizes"
    (let [app    {:attachments [{:id "a" :metadata {:tila "arkistoitu"} :versions []}
                                {:id "a" :metadata {:tila "active"} :versions []}
                                {:id "b" :metadata {:tila "active"} :versions [{:v 1}]}
                                {:id "b" :metadata {:tila "active"} :versions []}
                                {:id "b" :metadata {:tila "active"} :versions []}
                                {:id "c" :metadata {:tila "active"} :versions []}]}
          result (winners-and-losers app)]
      (is (= 3 (count (:losers result))))
      (is (= 3 (count (:winners result)))))))

(deftest test-winners-and-losers-invariants
  (testing "Union of winners and losers equals original"
    (let [app                      {:attachments [archived-with-versions
                                                  not-archived-no-versions
                                                  not-archived-no-versions
                                                  unique-attachment-1]}
          {:keys [winners losers]} (winners-and-losers app)]
      (is (= (sort-by hash (:attachments app))
             (sort-by hash (concat winners losers))))
      (is (= [not-archived-no-versions not-archived-no-versions]
             losers))))

  (testing "No duplicate IDs in winners"
    (let [app        {:attachments [archived-with-versions
                                    not-archived-no-versions
                                    not-archived-with-versions
                                    unique-attachment-1]}
          result     (winners-and-losers app)
          winner-ids (map :id (:winners result))]
      (is (= (count winner-ids)
             (count (distinct winner-ids))))))

  (testing "All losers have duplicate IDs"
    (let [app       {:attachments [archived-with-versions
                                   not-archived-no-versions
                                   unique-attachment-1]}
          result    (winners-and-losers app)
          loser-ids (map :id (:losers result))]
      (doseq [loser-id loser-ids]
        (is (> (count (util/filter-by-key :id loser-id (:attachments app)))
               1))
        (is (true? (boolean (util/find-by-id loser-id (:winners result)))))))))

;;; Tests for duplicate-purge-updates

(deftest test-duplicate-purge-updates-no-duplicates
  (testing "Returns nil when no duplicates"
    (is (nil? (duplicate-purge-updates
                {:attachments [unique-attachment-1 unique-attachment-2]}))))

  (testing "Returns nil for empty attachments"
    (is (nil? (duplicate-purge-updates {:attachments []}))))

  (testing "Returns nil for single attachment"
    (is (nil? (duplicate-purge-updates {:attachments [unique-attachment-1]})))))

(deftest test-duplicate-purge-updates-structure
  (testing "Returns map with required keys"
    (let [app         {:attachments [archived-with-versions not-archived-no-versions]}
          result      (duplicate-purge-updates app)
          attachments (get-in result [$set :attachments])
          duplicates  (get-in result [$push :_sheriff-notes :duplicates])]
      (is (map? result))
      (is (seq attachments))
      (is (seq duplicates)))))

(deftest test-duplicate-purge-updates-content
  (testing "Winners in $set attachments"
    (let [app         {:attachments [archived-with-versions
                                     not-archived-no-versions
                                     unique-attachment-1]}
          result      (duplicate-purge-updates app)
          attachments (get-in result [$set :attachments])]
      (is (= 2 (count attachments)))
      (is (some #(= % archived-with-versions) attachments))
      (is (some #(= % unique-attachment-1) attachments))
      (is (not (some #(= % not-archived-no-versions) attachments)))
      (is (= [not-archived-no-versions]
             (get-in result [$push :_sheriff-notes :duplicates])))))

  (testing "Losers in :duplicates"
    (let [app        {:attachments [archived-with-versions
                                    not-archived-no-versions
                                    not-archived-with-versions]}
          result     (duplicate-purge-updates app)
          duplicates (get-in result [$push :_sheriff-notes :duplicates])]
      (is (= 2 (count duplicates)))
      (is (some #(= % not-archived-no-versions) duplicates))
      (is (some #(= % not-archived-with-versions) duplicates))
      (is (= [archived-with-versions]
             (get-in result [$set :attachments])))))

  (testing "All unique attachments preserved"
    (let [app         {:attachments [archived-with-versions
                                     not-archived-no-versions
                                     unique-attachment-1
                                     unique-attachment-2]}
          result      (duplicate-purge-updates app)
          attachments (get-in result [$set :attachments])]
      (is (some #(= % unique-attachment-1) attachments))
      (is (some #(= % unique-attachment-2) attachments))
      (is (some #(= % archived-with-versions) attachments))
      (is (= [not-archived-no-versions]
             (get-in result [$push :_sheriff-notes :duplicates]))))))

(deftest test-duplicate-purge-updates-multiple-groups
  (testing "Correctly handles multiple duplicate groups"
    (let [app         {:attachments [{:id "1" :metadata {:tila "arkistoitu"} :versions []}
                                     {:id "1" :metadata {:tila "active"} :versions []}
                                     {:id "2" :metadata {:tila "active"} :versions [{:v 1}]}
                                     {:id "2" :metadata {:tila "active"} :versions []}
                                     unique-attachment-1]}
          result      (duplicate-purge-updates app)
          attachments (get-in result [$set :attachments])
          duplicates  (get-in result [$push :_sheriff-notes :duplicates])]
      (is (= 3 (count attachments)))
      (is (= 2 (count duplicates)))
      (is (= (sort-by hash (:attachments app))
             (sort-by hash (concat attachments duplicates)))))))

;;; Integration and Regression Tests

(deftest test-full-workflow-with-preconditions
  (testing "End-to-end deduplication with validation"
    (let [app         {:attachments [{:id       "doc-1"
                                      :filename "report.pdf"
                                      :metadata {:tila "arkistoitu"}
                                      :versions [{:v 1}]}
                                     {:id       "doc-1"
                                      :filename "report.pdf"
                                      :metadata {:tila "active"}
                                      :versions []}
                                     {:id       "doc-2"
                                      :filename "contract.pdf"
                                      :metadata {:tila "active"}
                                      :versions []}]}
          result      (duplicate-purge-updates app)
          attachments (get-in result [$set :attachments])
          duplicates  (get-in result [$push :_sheriff-notes :duplicates])]

      (is (some? result))
      (is (= 2 (count attachments)))
      (is (= 1 (count duplicates)))

      ;; Verify best attachment kept
      (is (some #(and (= (:id %) "doc-1")
                      (= (get-in % [:metadata :tila]) "arkistoitu"))
                attachments))

      ;; Verify unique preserved
      (is (some #(= (:id %) "doc-2")
                attachments)))))

(deftest test-realistic-migration-with-validation
  (testing "Larger scenario with all edge cases"
    (let [app {:attachments
               [;; Perfect candidate: archived + versioned
                {:id       "invoice-2023"
                 :metadata {:tila "arkistoitu"}
                 :versions [{:v 1} {:v 2}]}
                ;; Duplicate: active, no versions
                {:id       "invoice-2023"
                 :metadata {:tila "active"}
                 :versions []}
                ;; Duplicate: archived, no versions
                {:id       "invoice-2023"
                 :metadata {:tila "arkistoitu"}
                 :versions []}

                ;; Versioned wins (no archived)
                {:id       "draft"
                 :metadata {:tila "luonnos"}
                 :versions [{:v 1}]}
                {:id       "draft"
                 :metadata {:tila "luonnos"}
                 :versions []}

                ;; Unique documents
                {:id "unique-1" :metadata {:tila "valmis"} :versions []}
                {:id "unique-2" :metadata {:tila "valmis"} :versions [{:v 1}]}]}
          result      (duplicate-purge-updates app)
          attachments (get-in result [$set :attachments])
          duplicates  (get-in result [$push :_sheriff-notes :duplicates])]

      (is (= 4 (count attachments)))
      (is (= 3 (count duplicates)))

      ;; Verify no duplicate IDs in winners
      (is (= 4 (count (distinct (map :id attachments)))))

      ;; Verify best invoice kept
      (is (some #(and (= (:id %) "invoice-2023")
                      (= (get-in % [:metadata :tila]) "arkistoitu")
                      (seq (:versions %)))
                attachments))
      (is (= (sort-by hash (:attachments app))
             (sort-by hash (concat attachments duplicates)))))))
