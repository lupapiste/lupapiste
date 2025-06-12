(ns lupapalvelu.conversion.import.statement-test
  (:require [clojure.test :refer :all]
            [lupapalvelu.conversion.import.statement :as im-stat]
            [lupapalvelu.mongo :as mongo]
            [sade.date :as date]))

(def pack #'im-stat/pack-statement)

(deftest pack-statement-test
  (testing "Pack success"
    (is {:given 12345 :status "hello" :person-name "bob"}
        (pack {:backend-id "aa"  :text   "howdy"
               :given      12345 :status " hello "
               :person     {:name " BOB "}})))
  (testing "Pack failure"
    (is (nil? (pack {:backend-id "aa" :text "howdy"
                     :status     " hello "
                     :person     {:name " BOB "}})))
    (is (nil? (pack {:backend-id "aa" :text "howdy"
                     :given      12345
                     :person     {:name " BOB "}})))
    (is (nil? (pack {:backend-id "aa"  :text   "howdy"
                     :given      12345 :status " hello "
                     :person     {:text " BOB "}})))))

(def se? #'im-stat/statement-exists?)

(deftest statement-exists?-test
  (let [item {:backend-id "hello" :given  12345
              :status     "yeah"  :person {:name "Bob"}}]
    (testing "Does not exist"
      (is (false? (se? item [{} (update item :given inc)
                             (dissoc item :given)
                             (assoc-in item [:person :name] "dot")]))))
    (testing "Exists"
      (is (true? (se? item [item])))
      (is (true? (se? item [{} {:hello "world"}
                            {:given 12345 :status " yeah "
                             :person {:name "  BOB  "}}]))))))

(def make #'im-stat/make-statement)
(def now (date/now))
(def now-ts (date/timestamp now))
(def runeberg-ts (date/timestamp "5.2.2024"))
(def may-ts (date/timestamp "1.5.2024"))
(def mongo-id "673f5a87a2bdfa6f49f34ffc")

(deftest make-statement-test
  (with-redefs [date/now        (constantly now)
                mongo/create-id (constantly mongo-id)]
    (testing "Default/minimal is valid"
      (is (= {:id       mongo-id
              :state    :given
              :modified now-ts
              :person   {:userId "batchrun-user"
                         :email  "eraajo@lupapiste.fi"
                         :name   "Lupapiste Eräajo"}}
             (make {}))))
    (testing "Valid full merge"
      (is (= {:id        mongo-id
              :state     :given
              :requested runeberg-ts
              :given     may-ts
              :modified  now-ts
              :status    "poydalle"
              :text      "Lorem ipsum"
              :person    {:userId "batchrun-user"
                          :email  "state.ment@example.net"
                          :id     "ID"
                          :name   "Sun State"
                          :text   "Soothsayer"}}
             (make {:given     may-ts
                    :requested runeberg-ts
                    :status    "poydalle"
                    :text      "Lorem ipsum"
                    :person    {:email "state.ment@example.net"
                                :id    "ID"
                                :name  "Sun State"
                                :text  "Soothsayer"}}))))
    (testing "Bad merge"
      (is (nil? (make {:status "yikes"}))))))
