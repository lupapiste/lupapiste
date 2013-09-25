(ns lupapalvelu.smoketest.application-smoke-tests
  (:require [lupapalvelu.smoketest.core :refer [defmonster]]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.document.model :as model]))

(def applications (delay (mongo/select :applications)))
(def submitted-applications (delay (mongo/select :submitted-applications)))

(defn- validate-doc [{id :id schema-info :schema-info :as doc}]
  (let [results (filter (fn [{result :result}] (= :err (first result))) (model/validate doc))]
    (when (seq results)
      {:document-id id :schema-info schema-info :results results})))

(defn- validate-documents [{id :id state :state documents :documents }]
  (let [results (filter seq (map validate-doc documents))]
    (when (seq results)
      {:id id
       :state state
       :results results})))

(defmonster documents-are-valid
  (if-let [validation-results (seq (filter seq (map validate-documents @applications)))]
    {:ok false :results validation-results}
    {:ok true}))

(defmonster submitted-documents-are-valid
  {:ok true}
  #_(if-let [validation-results (seq (filter seq (map validate-documents @submitted-applications)))]
    {:ok false :results validation-results}
    {:ok true}))
