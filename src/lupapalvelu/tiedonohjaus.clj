(ns lupapalvelu.tiedonohjaus
  (:require [sade.http :as http]
            [sade.env :as env]
            [clojure.core.memoize :as memo]
            [lupapalvelu.organization :as o]))

(defn- build-url [& path-parts]
  (apply str (env/value :toj :host) path-parts))

(defn- get-tos-functions-from-toj [organization-id]
  (let [has-archive? (:permanent-archive-enabled (o/get-organization organization-id))]
    (if (and (env/feature? :tiedonohjaus) has-archive?)
      (try
        (let [url (build-url "/tiedonohjaus/api/org/" organization-id "/asiat")
              response (http/get url {:as :json
                                      :throw-exceptions false})]
          (if (= 200 (:status response))
            (:body response)
            []))
        (catch Exception _
          []))
      [])))

(def available-tos-functions
  (memo/ttl get-tos-functions-from-toj
            :ttl/threshold 10000))

(defn- get-metadata-for-document-from-toj [organization tos-function document-type]
  (if (env/feature? :tiedonohjaus)
    (when (and organization tos-function document-type)
      (try
        (let [doc-id (if (map? document-type) (str (name (:type-group document-type)) "." (name (:type-id document-type))) document-type)
              url (build-url "/tiedonohjaus/api/org/" organization "/asiat/" tos-function "/document/" doc-id)
              response (http/get url {:as :json
                                      :throw-exceptions false})]
          (if (= 200 (:status response))
            (:body response)
            {}))
        (catch Exception _
          {})))
    {}))

(def metadata-for-document
  (memo/ttl get-metadata-for-document-from-toj
            :ttl/threshold 10000))

(defn document-with-updated-metadata [document organization tos-function & [type]]
  (let [document-type (or type (:type document))]
    (->> (metadata-for-document organization tos-function document-type)
         (assoc document :metadata))))

(defn- get-tos-toimenpide-for-application-state-from-toj [organization tos-function state]
  (if (env/feature? :tiedonohjaus)
    (when (and organization tos-function state)
      (try
        (let [url (build-url "/tiedonohjaus/api/org/" organization "/asiat/" tos-function "/toimenpide-for-state/" state)
              response (http/get url {:as :json
                                      :throw-exceptions false})]
          (if (= 200 (:status response))
            (:body response)
            {}))
        (catch Exception _
          {})))
    {}))

(def toimenpide-for-state
  (memo/ttl get-tos-toimenpide-for-application-state-from-toj
    :ttl/threshold 10000))

(defn- get-documents-from-application [application]
  [{:type :hakemus
    :category :document
    :ts (:created application)}])

(defn- get-attachments-from-application [application]
  (reduce (fn [acc attachment]
            (if-let [versions (seq (:versions attachment))]
              (->> versions
                   (map (fn [ver]
                          {:type (:type attachment)
                           :category :attachment
                           :version (:version ver)
                           :ts (:created ver)}))
                  (concat acc))
              acc))
    []
    (:attachments application)))

(defn generate-case-file-data [application]
  (let [documents (get-documents-from-application application)
        attachments (get-attachments-from-application application)
        all-docs (sort-by :ts (concat documents attachments))]
    (map (fn [[{:keys [state ts]} next]]
           (let [api-response (toimenpide-for-state (:organization application) (:tosFunction application) state)
                 action-name (or (:name api-response) "Ei asetettu tiedonohjaussuunnitelmassa")]
             {:action action-name
              :start ts
              :documents (filter (fn [{doc-ts :ts}]
                                   (and (>= doc-ts ts) (or (nil? next) (< doc-ts (:ts next)))))
                           all-docs)}))
      (partition 2 1 nil (:history application)))))
