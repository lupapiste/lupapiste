(ns lupapalvelu.conversion.import.statement-i-test
  (:require [clojure.test :refer :all]
            [lupapalvelu.conversion.import.itest-util :refer :all]
            [lupapalvelu.conversion.import.shared :refer [import--items]]
            [lupapalvelu.conversion.import.statement]
            [lupapalvelu.conversion.schemas :as conv-sc]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.statement-schemas :as statement-sc]
            [monger.operators :refer :all]
            [sade.date :as date]
            [schema.core :as sc]))

(use-fixtures :once (local-fixture-factory :db-name "test_conversion-import-statement-itest"))

(defn make-cfg [& items]
  (sc/validate conv-sc/ImportStatementsConfig
               {:organization-id "753-R"
                :op              :statements
                :items           items}))

(defn statement=? [check statement]
  (-> statement
      (select-keys (keys check))
      (update :person select-keys (keys (:person check)))
      (= check)))

(deftest import-statement-test
  (let [[app-id1 app-id2 app-id3 app-id4
         :as app-ids] (for [i (range 4)]
                        (->> (str "Statement Square " (inc i))
                             (create-and-open-application pena :address)
                             :id))
        stat1         {:status "palautettu" :text " Good enough "
                       :given  "1.2.2023"
                       :person {:text " Comment section "
                                :name " Talkie "}}
        stat2         {:given  "5.2.2024" :text "Ugh"
                       :status "ei-huomautettavaa"
                       :person {:name "Wookie"}}
        stat3         {:text "Not added"}
        stat4s        (map #(hash-map :backend-id "back4"
                                      :given (date/plus "1.1.2023" :days (rand-int 365))
                                      :status (rand-nth statement-sc/statement-statuses)
                                      :text (format "Comment %03d" (inc %))
                                      :person {:name (format "Mx %s" (inc %))})
                           (range 100))
        cfg           (apply make-cfg
                             (assoc stat1 :backend-id "back1")
                             (assoc stat2 :backend-id "back2")
                             (assoc stat3 :backend-id "back3")
                             stat4s)]
    (doseq [[app-id i] (zipmap app-ids (range))]
      (make-conversion-document app-id (str "back" (inc i)) (not= i 2)))
    (mongo/update-by-id :applications app-id2 {$push {:statements {:hello "world"}}})
    (testing "Import statements"
      (is (nil? (import--items cfg)))
      (let [[app1 app2 app3 app4] (map #(mongo/by-id :applications %)
                                       [app-id1 app-id2 app-id3 app-id4])]
        (testing "App1: imported"
          (let [{:keys [statements]
                 notes :_sheriff-notes} app1
                statement               (last statements)]
            (is (= {:note "Import statement" :statement-id (:id statement)}
                   (dissoc (last notes) :created)))
            (is (some? (statement-sc/valid-statement! statement)))
            (is (statement=? {:status "palautettu"
                              :text   "Good enough"
                              :id     (:id statement)
                              :person {:text   "Comment section"
                                       :name   "Talkie"
                                       :email  "eraajo@lupapiste.fi"
                                       :userId "batchrun-user"}}
                             statement))))
        (testing "App2: imported"
          (let [{:keys [statements]
                 notes :_sheriff-notes} app2
                [hello statement]       statements]
            (is (= {:note "Import statement" :statement-id (:id statement)}
                   (dissoc (last notes) :created)))
            (is (= {:hello "world"} hello))
            (is (some? (statement-sc/valid-statement! statement)))
            (is (statement=? {:given  (date/timestamp "5.2.2024")
                              :text   "Ugh"
                              :id     (:id statement)
                              :person {:name   "Wookie"
                                       :email  "eraajo@lupapiste.fi"
                                       :userId "batchrun-user"}}
                             statement))))
        (testing "App3: not converter, not imported"
          (let [{:keys [statements]
                 notes :_sheriff-notes} app3]
            (is (empty? statements))
            (is (empty? notes))))
        (testing "App4: imported in the same order"
          (let [{:keys [statements]} app4]
            (is (= 100 (count statements)))
            (is (-> statements first :text (= "Comment 001")))
            (is (-> statements last :text (= "Comment 100")))
            (is (= statements (sort-by :text statements)))))
        (testing "Re-import does nothing"
          (is (nil? (import--items cfg)))
          (is (= #{app1 app2 app3 app4}
                 (set (mongo/select :applications {})))))))))
