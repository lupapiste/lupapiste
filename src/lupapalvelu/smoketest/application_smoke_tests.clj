(ns lupapalvelu.smoketest.application-smoke-tests
  (:require [lupapalvelu.smoketest.core :refer [defmonster]]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.action :as action]
            [lupapalvelu.document.model :as model]
            [lupapalvelu.application :refer [get-operations]]
            [lupapalvelu.server] ; ensure all namespaces are loaded
            ))

(def applications (delay (mongo/select :applications)))
(def submitted-applications (delay (mongo/select :submitted-applications)))
(def organizations (delay (mongo/select :organizations)))

(defn- validate-doc [ignored-errors application {id :id schema-info :schema-info :as doc}]
  (if (and (:name schema-info) (:version schema-info))
    (let [ignored (set ignored-errors)
          results (filter
                    (fn [{result :result}]
                      (and (= :err (first result)) (not (ignored (second result)))))
                    (model/validate application doc))]
      (when (seq results)
        {:document-id id :schema-info schema-info :results results}))
    {:document-id id :schema-info schema-info :results "Schema name or version missing"}))

(defn- validate-documents [ignored-errors {id :id state :state documents :documents :as application}]
  (let [results (filter seq (map (partial validate-doc ignored-errors application) documents))]
    (when (seq results)
      {:id id
       :state state
       :results results})))

(defn- documents-are-valid [applications & ignored-errors]
  (if-let [validation-results (seq (filter seq (map (partial validate-documents ignored-errors) applications)))]
    {:ok false :results validation-results}
    {:ok true}))

;; Every document is valid.

(defmonster applications-documents-are-valid
  (documents-are-valid @applications))

(defmonster submitted-applications-documents-are-valid
  (documents-are-valid @submitted-applications "illegal-hetu"))

;; Latest attachment version and latestVersion match

(defn latest-version-mismatch [application]
  (when-not (every? (fn [a] (or (empty? (:versions a)) (= (:latestVersion a) (last (:versions a))))) (:attachments application))
    (:id application)))

(defmonster attachment-latest-version-in-sycn
  (if-let [results (seq (remove nil? (map latest-version-mismatch @applications)))]
    {:ok false :results results}
    {:ok true}))

;; Documents have operation information

(defn- application-schemas-have-ops [{documents :documents operations :operations :as application}]
  (when-not (:infoRequest application)
    (let [docs-with-op (count (filter #(get-in % [:schema-info :op]) documents))
          ops          (count operations)]
      (when-not (= docs-with-op ops)
        (:id application)))))

(defn- schemas-have-ops [apps]
  (let [app-ids-with-invalid-docs (remove nil? (map application-schemas-have-ops apps))]
    (when (seq app-ids-with-invalid-docs)
      {:ok false :results (into [] app-ids-with-invalid-docs)})))

(defmonster applications-schemas-have-ops
  (schemas-have-ops @applications))

(defmonster submitted-applications-schemas-have-ops
  (schemas-have-ops @submitted-applications))

;; not null
(defn nil-property [property]
  (if-let [results (seq (remove nil? (map #(when (nil? (property %)) (:id %)) @applications)))]
    {:ok false :results results}
    {:ok true}))

(defmonster organization-is-set
  (nil-property :organization))

(defmonster property-id-is-set
  (nil-property :propertyId))

(defmonster location-is-set
  (nil-property :location))

(defmonster municipality-is-set
  (nil-property :municipality))

(defn timestamp-is-set [ts-key states]
  (if-let [results (seq (remove nil? (map #(when (and (states (keyword (:state %))) (nil? (ts-key %))) (:id %)) @applications)))]
    {:ok false :results results}
    {:ok true}))

(defmonster opened-timestamp
  (timestamp-is-set :opened (action/all-states-but [:draft :canceled])))

;;
;; Skips applications with operation "aiemmalla-luvalla-hakeminen" (previous permit aka paperilupa)
;;
(defmonster submitted-timestamp
 (if-let [results (seq (remove nil? (map
                                      (fn [app]
                                        (when (and
                                                ((action/all-application-states-but [:canceled :draft :open]) (keyword (:state app)))
                                                (when-not (some #(#{"aiemmalla-luvalla-hakeminen"} (:name %)) (get-operations app))
                                                  (nil? (:submitted app))))
                                          (:id app)))
                                      @applications)))]
   {:ok false :results results}
   {:ok true}))

; Fails with 255 applications
;(defmonster canceled-timestamp
;  (timestamp-is-set :canceled #{:canceled}))

(defmonster sent-timestamp
  (timestamp-is-set :sent #{:sent :complement-needed}))

(defmonster closed-timestamp
  (timestamp-is-set :closed #{:closed}))

(defmonster permit-type-only-in-single-municipality-scope
  (let [results (->> @organizations
                     (mapcat :scope)
                     (group-by :municipality)
                     (map (fn [[muni scopes]] [muni (map :permitType scopes)]))
                     (remove #(apply distinct? (second %))))]
    (if (seq results)
      {:ok false :results results}
      {:ok true})))


;; task source is set

(defn every-task-refers-verdict [{:keys [verdicts tasks id]}]
  (let [verdict-ids (set (map :id verdicts))]
     (when-not (every? (fn [{:keys [source]}] (or (not= "verdict" (:type source)) (verdict-ids (:id source)))) tasks)
       id)))

; Not a valid test anymore. Fails if a verdict is replaced.
(comment
  (defmonster task-source-refers-verdict
       (if-let [results (seq (remove nil? (map every-task-refers-verdict @applications)))]
         {:ok false :results results}
         {:ok true})))
