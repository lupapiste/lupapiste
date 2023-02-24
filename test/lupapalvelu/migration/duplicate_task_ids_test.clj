(ns lupapalvelu.migration.duplicate-task-ids-test
  (:require [midje.sweet :refer :all]
            [lupapalvelu.migration.task-duplicate-ids-fix :refer [update-attachment-task-ref tasks-by-id]]
            [clojure.java.io :as io])
  (:import [java.io BufferedReader StringReader]))


(def attachments [{:id "111"}
                  {:id "222" :source nil}
                  {:id "333" :source {:id "task-1"}}
                  {:id "444" :source {:id "task-2"}}
                  {:id "555" :target {:id "task-5"}}
                  {:id "666" :target {:id "task-6"}}
                  {:id "both" :source {:id "both-id"} :target {:id "both-id"}}])


(def new-ids {"task-1" "UUSI-1"
              "task-5" "UUSI-5"
              "both-id" "UUSI-both"})


(fact "updating-attachment-task-refs"
  (->> attachments
       (map (partial update-attachment-task-ref new-ids))
       (map (juxt :id (comp :id :source) (comp :id :target))))
  =>
  [["111" nil nil]
   ["222" nil nil]
   ["333" "UUSI-1" nil]
   ["444" "task-2" nil]
   ["555" nil "UUSI-5"]
   ["666" nil "task-6"]
   ["both" "UUSI-both" "UUSI-both"]])


(facts "migration"
  (against-background [(io/reader anything) => (BufferedReader. (StringReader. "123,123-1\n123,123-2\n321,"))])
  (fact "grouped by id (first column)"
    (tasks-by-id)
    =>
    {"123" ["123-1" "123-2"]
     "321" [nil]})

  (fact "zipmapped to ids"
    (let[[_ ids] (first (tasks-by-id))]
      (zipmap ids (iterate inc 1)))
    => {"123-1" 1 "123-2" 2}))
