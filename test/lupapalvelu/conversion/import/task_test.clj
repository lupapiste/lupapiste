(ns lupapalvelu.conversion.import.task-test
  (:require [clojure.test :refer :all]
            [lupapalvelu.conversion.import.task :as conv-task]
            [sade.date :as date]
            [sade.strings :as ss]))

(def runeberg  (date/timestamp "5.2.2024"))
(def mayday  (date/timestamp "1.5.2024"))

(def make-text #'conv-task/make-text)

(deftest make-text-test
  (testing "blank"
    (is (ss/blank? (make-text nil)))
    (is (ss/blank? (make-text {})))
    (is (ss/blank? (make-text {:text "   "}))))
  (testing "text"
    (is (= "hello" (make-text {:text "  hello  "}))))
  (testing "received"
    (is (= "Saapunut 5.2.2024."
           (make-text {:text     "\n "
                       :received runeberg}))))
  (testing "processed"
    (is (= "Käsitelty 1.5.2024."
           (make-text {:text      "\n "
                       :processed mayday}))))
  (testing "mix"
    (is (= "Hello\n\nSaapunut 5.2.2024."
           (make-text {:text     " Hello "
                       :received runeberg})))
    (is (= "Hello\n\nKäsitelty 1.5.2024."
           (make-text {:text      " Hello "
                       :processed mayday})))
    (is (= "Saapunut 5.2.2024.\nKäsitelty 1.5.2024."
           (make-text {:processed mayday
                       :received  runeberg})))
    (is (= "All good\n\nSaapunut 5.2.2024.\nKäsitelty 1.5.2024."
           (make-text {:processed mayday
                       :received  runeberg
                       :text      "  All good  "})))))

(def plan-pred #'conv-task/plan-pred)

(deftest plan-pred-test
  (testing "no match"
    (doseq [m [nil {} {:taskname "hello"}
               {:taskname    " HELLO "
                :schema-info {:name "foobar"}}]]
      (is (false? ((plan-pred "Hello") m)))))

  (testing "match"
    (doseq [m [{:taskname "hello" :schema-info {:name "task-lupamaarays"}}
               {:taskname "  HeLlo  " :schema-info {:name :task-lupamaarays}}]]
      (is (true? ((plan-pred "Hello") m))))))

(def old-match-pred #'conv-task/old-match-pred)

(def target {:taskname " ÄläMÖlö "
             :received runeberg})

(def oldie? (old-match-pred target))

(def oldie-match {:taskname    "Älämölö"
                  :schema-info {:name "task-lupamaarays"}
                  :state       :ok
                  :data        {:kuvaus {:value (make-text target)}}})

(deftest old-match-pred-test
  (testing "matches"
    (doseq [m [oldie-match
               (assoc oldie-match :taskname "  ÄLÄMÖLÖ  ")
               (assoc oldie-match :state "ok")
               (assoc-in oldie-match [:schema-info :name] :task-lupamaarays)
               (update-in oldie-match [:data :kuvaus :value] (partial str "hello"))
               (update-in oldie-match [:data :kuvaus :value] str "\n  ")]]
      (is (true? (oldie? m)))))

  (testing "no match"
    (doseq [m [(assoc oldie-match :taskname "hello")
               (assoc oldie-match :state :requires_user_action)
               (assoc-in oldie-match [:schema-info :name] :task-foobar)
               (assoc-in oldie-match [:data :kuvaus :value] "")
               (assoc-in oldie-match [:data :kuvaus :value] "Hello")
               (update-in oldie-match [:data :kuvaus :value] str "\npostfix")]]
      (is (false? (oldie? m))))))
