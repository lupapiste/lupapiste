(ns lupapalvelu.smoketest.application-smoke-tests
  (:require [lupapalvelu.smoketest.core :refer [defmonster]]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.document.model :as model]
            [lupapalvelu.server] ; ensure all namespaces are loaded
            ))

(def applications (delay (mongo/select :applications)))
(def submitted-applications (delay (mongo/select :submitted-applications)))

(defn- validate-doc [{id :id schema-info :schema-info :as doc}]
  (if (and (:name schema-info) (:version schema-info))
    (let [results (filter (fn [{result :result}] (= :err (first result))) (model/validate doc))]
      (when (seq results)
        {:document-id id :schema-info schema-info :results results}))
    {:document-id id :schema-info schema-info :results "Schema name or version missing"}))

(defn- validate-documents [{id :id state :state documents :documents }]
  (let [results (filter seq (map validate-doc documents))]
    (when (seq results)
      {:id id
       :state state
       :results results})))

(defn- documents-are-valid [applications]
  (if-let [validation-results (seq (filter seq (map validate-documents applications)))]
    {:ok false :results validation-results}
    {:ok true}))

;; Every document is valid.

(defmonster applications-documents-are-valid
  (documents-are-valid @applications))

(defmonster submitted-applications-documents-are-valid
  (documents-are-valid @submitted-applications))

;; Documents have operation information

(defn- application-schemas-have-ops [{documents :documents operations :operations :as application}]
  (when-not (:infoRequest application)
    (let [docs-with-op (count (filter #(get-in % [:schema-info :op]) documents))
          ops          (count operations)]
      (when-not (= docs-with-op ops)
        (:id application)))))

(defn- schemas-have-ops [apps]
  (let [app-ids-with-invalid-docs (filter identity (map application-schemas-have-ops apps))]
    (when (seq app-ids-with-invalid-docs)
      {:ok false :results (into [] app-ids-with-invalid-docs)})))

(defmonster applications-schemas-have-ops
  (schemas-have-ops @applications))

(defmonster submitted-applications-schemas-have-ops
  (schemas-have-ops @submitted-applications))
