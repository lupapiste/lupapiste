(ns lupapalvelu.conversion.section-test
  (:require [clojure.test :refer :all]
            [lupapalvelu.conversion.section :as conv-sec]
            [lupapalvelu.mongo :as mongo]))

(deftest section-query-or-nil-test
  (testing "good"
    (is (= {:LP-id       "LP-123"
            :sections.id "my-id"}
           (conv-sec/section-query-or-nil {:application {:id "LP-123"}
                                           :data        {:section-id " my-id "}}))))
  (testing "nil"
    (doseq [data [{:section-id "  "} {:hello "foo"} {}]]
      (is (nil? (conv-sec/section-query-or-nil {:application {:id "LP-123"}
                                                :data        data}))))
    (is (nil? (conv-sec/section-query-or-nil {:data {:section-id "my-id"}})))))

(deftest no-duplicate-names!-test
  (testing "good"
    (doseq [xs [[] [{}] [{:name 1} {:name 2}] [{:name "a"} {:name "A"}]]]
      (is (= xs (conv-sec/no-duplicate-names! xs)))))
  (testing "bad"
    (doseq [xs [[{} nil "hello"] [{:name 1} {:name 2} {:name 1}]]]
      (is (thrown-with-msg? Exception #"Duplicate name"
                            (conv-sec/no-duplicate-names! xs))))))

(def process-sections #'conv-sec/process-sections)

(def mongo-id "662bb83731a70c06f922ea02")
(def ts 1714141270311)

(deftest process-sections-test
  (testing "Section imports must have unique targets"
    (is (thrown-with-msg? Exception #"Duplicate name what in imports"
                          (process-sections nil [{:op :delete :name "what"}
                                                 {:op :delete :name "what"}]))))
  (with-redefs
    [sade.core/now    (constantly ts)
     mongo/create-id  (constantly mongo-id)
     mongo/select-one (fn [coll q]
                        {:pre [(= coll :conversion-section-schemas)
                               (:name q)]}
                        (case (:name q)
                          "order"
                          {:id         "661fe14b08d6bc6fb07ec542"
                           :name       "order"
                           :dictionary {:color {:select {:items ["red" "green" "blue"]}}
                                        :straw {:toggle {}}
                                        :note  {:text {}}}
                           :rows       []}
                          "todo"
                          {:id         "661fe14b08d6bc6fb07ec543"
                           :name       "todo"
                           :dictionary {:task     {:text {:lines 10}}
                                        :deadline {:date {:time-of-day "end"}}}
                           :rows       []}
                          nil))]
    (testing "add: ok (merge/replace fallback to add, if the target section is not found)"
      (doseq [op [:add :merge :replace]]
        (is (= [{:id          mongo-id :name     "table"
                 :schema-name "order"  :modified ts
                 :data        {:color {:_user     "batchrun-user"
                                       :_value    "red"
                                       :_modified ts}}}]
               (process-sections nil [{:op          op
                                       :name        "table"
                                       :schema-name "order"
                                       :data        {:color "red"}}])))
        (is (= [{:id          "661fe14b08d6bc6fb07ec546"
                 :name        "hi"
                 :schema-name "todo"
                 :data        {:task {:_value    "Do something!"
                                      :_user     "sonja"
                                      :_modified 123456}}
                 :modified    1714078800000}
                {:id          mongo-id
                 :name        "table"
                 :schema-name "order"
                 :title       :my-title
                 :data        {:color {:_value    "green"
                                       :_modified ts
                                       :_user     "batchrun-user"}
                               :straw {:_value    true
                                       :_modified ts
                                       :_user     "batchrun-user"}}
                 :modified    ts}]
               (process-sections [{:id          "661fe14b08d6bc6fb07ec546"
                                   :name        "hi"
                                   :schema-name "todo"
                                   :data        {:task {:_value    "Do something!"
                                                        :_user     "sonja"
                                                        :_modified 123456}}
                                   :modified    1714078800000}]
                                 [{:op          op
                                   :name        "table"
                                   :title       :my-title
                                   :schema-name "order"
                                   :data        {:color "green"
                                                 :straw true}}])))))
    (testing "add: schema missing"
      (is (thrown-with-msg? Exception #"Schema nowhere not found"
                            (process-sections nil [{:op          :add
                                                    :name        "table"
                                                    :schema-name "nowhere"
                                                    :data        {:color "red"}}]))))
    (testing "add: bad data"
      (is (thrown-with-msg? Exception #"Bad data in section table"
                            (process-sections nil [{:op          :add
                                                    :name        "table"
                                                    :schema-name "order"
                                                    :data        {:color "gray"}}]))))
    (testing "add: duplicate name"
      (is (thrown-with-msg? Exception #"Section table already exists."
                            (process-sections [{:id          "661fe14b08d6bc6fb07ec546"
                                                :name        "table"
                                                :schema-name "todo"
                                                :data        {}
                                                :modified    1714078800000}]
                                              [{:op          :add
                                                :name        "table"
                                                :schema-name "order"
                                                :data        {:color "green"}}]))))
    (testing "delete: do nothing"
      (doseq [oldies [[] nil [{:id          "661fe14b08d6bc6fb07ec546"
                               :name        "hi"
                               :schema-name "todo"
                               :data        {:task {:_value    "Do something!"
                                                    :_user     "sonja"
                                                    :_modified 123456}}
                               :modified    1714078800000}]]]
        (is (= (seq oldies)
               (process-sections oldies [{:op   :delete
                                          :name "not-here"}])))))

    (testing "delete: success"
      (is (nil? (process-sections [{:id          "661fe14b08d6bc6fb07ec546"
                                    :name        "hi"
                                    :schema-name "todo"
                                    :data        {:task {:_value    "Do something!"
                                                         :_user     "sonja"
                                                         :_modified 123456}}
                                    :modified    1714078800000}]
                                  [{:op :delete :name "hi"}])))
      (is (= [{:id          "661fe14b08d6bc6fb07ec547"
               :name        "table"
               :schema-name "order"
               :modified    9988776655
               :data        {:color {:_user     "batchrun-user"
                                     :_value    "red"
                                     :_modified ts}}}]
             (process-sections [{:id          "661fe14b08d6bc6fb07ec546"
                                 :name        "hi"
                                 :schema-name "todo"
                                 :data        {:task {:_value    "Do something!"
                                                      :_user     "sonja"
                                                      :_modified 123456}}
                                 :modified    1714078800000}
                                {:id          "661fe14b08d6bc6fb07ec547"
                                 :name        "table"
                                 :schema-name "order"
                                 :modified    9988776655
                                 :data        {:color {:_user     "batchrun-user"
                                                       :_value    "red"
                                                       :_modified ts}}}]
                               [{:op :delete :name "hi"}]))))

    (testing "merge"
      (is (= [{:id          "661fe14b08d6bc6fb07ec547"
               :name        "table"
               :schema-name "order"
               :modified    ts
               :data        {:color {:_user     "batchrun-user"
                                     :_value    "blue"
                                     :_modified ts}
                             :note  {:_user     "sonja"
                                     :_value    "hello"
                                     :_modified 1234567}}
               :title       :warn
               :l10n        {:foo-bar {:fi "Kuppila"
                                       :en "Canteen"}
                             :what    {:fi "Häh?"
                                       :en "Huh?"}}}]
             (process-sections [{:id          "661fe14b08d6bc6fb07ec547"
                                 :name        "table"
                                 :schema-name "order"
                                 :modified    9988776655
                                 :data        {:color {:_user     "batchrun-user"
                                                       :_value    "red"
                                                       :_modified 9988776655}
                                               :note  {:_user     "sonja"
                                                       :_value    "hello"
                                                       :_modified 1234567}}
                                 :title       :info
                                 :l10n        {:foo-bar {:fi "Kuppila"}}}]
                               [{:op    :merge
                                 :name  "table"
                                 :data  {:color "blue"}
                                 :title :warn
                                 :l10n  {:foo-bar {:en "Canteen"}
                                         :what    {:fi "Häh?"
                                                   :en "Huh?"}}
                                 }]))))
    (testing "replace"
      (is (= [{:id          "661fe14b08d6bc6fb07ec547"
               :name        "table"
               :schema-name "order"
               :modified    ts
               :data        {:color {:_user     "batchrun-user"
                                     :_value    "blue"
                                     :_modified ts}}
               :title       :warn
               :l10n        {:foo-bar {:en "Canteen"}
                             :what    {:fi "Häh?"
                                       :en "Huh?"}}}]
             (process-sections [{:id          "661fe14b08d6bc6fb07ec547"
                                 :name        "table"
                                 :schema-name "order"
                                 :modified    9988776655
                                 :data        {:color {:_user     "batchrun-user"
                                                       :_value    "red"
                                                       :_modified 9988776655}
                                               :note  {:_user     "sonja"
                                                       :_value    "hello"
                                                       :_modified 1234567}}
                                 :title       :info
                                 :l10n        {:foo-bar {:fi "Kuppila"}}}]
                               [{:op    :replace
                                 :name  "table"
                                 :data  {:color "blue"}
                                 :title :warn
                                 :l10n  {:foo-bar {:en "Canteen"}
                                         :what    {:fi "Häh?"
                                                   :en "Huh?"}}}]))))

    (testing "Change schema: failure"
      (is (thrown-with-msg? Exception #"Bad data in section table"
                            (process-sections [{:id          "661fe14b08d6bc6fb07ec547"
                                                :name        "table"
                                                :schema-name "order"
                                                :modified    9988776655
                                                :data        {:color {:_user     "batchrun-user"
                                                                      :_value    "red"
                                                                      :_modified 9988776655}
                                                              :note  {:_user     "sonja"
                                                                      :_value    "hello"
                                                                      :_modified 1234567}}
                                                :title       :info
                                                :l10n        {:foo-bar {:fi "Kuppila"}}}]
                                              [{:op          :merge
                                                :name        "table"
                                                :schema-name "todo"}]))))
    (testing "Change schema: success"
      (is (= [{:id          "661fe14b08d6bc6fb07ec547"
               :name        "table"
               :schema-name "todo"
               :modified    ts
               :data        {}
               :title       :info
               :l10n        {:foo-bar {:fi "Kuppila"}}}]
             (process-sections [{:id          "661fe14b08d6bc6fb07ec547"
                                 :name        "table"
                                 :schema-name "order"
                                 :modified    9988776655
                                 :data        {:color {:_user     "batchrun-user"
                                                       :_value    "red"
                                                       :_modified 9988776655}
                                               :note  {:_user     "sonja"
                                                       :_value    "hello"
                                                       :_modified 1234567}}
                                 :title       :info
                                 :l10n        {:foo-bar {:fi "Kuppila"}}}]
                               [{:op          :replace
                                 :name        "table"
                                 :schema-name "todo"
                                 :data        {}}]))))))
