(ns lupapalvelu.document.approval
  (:require [clojure.walk :refer [keywordize-keys]]
            [monger.operators :refer :all]
            [sade.core :refer :all]
            [sade.strings :as ss]
            [lupapalvelu.action :refer [update-application]]
            [lupapalvelu.document.model :as model]
            [lupapalvelu.document.persistence :as doc-persistence]))

(defn get-approval
  "Returns document's approval data. Nil if not found."
  [document]
  (not-empty (get-in document [:meta :_approved])))


(defn ->approved
  "Approval meta data model. To be used within with-timestamp."
  [status user]
  {:value status
   :user (select-keys user [:id :firstName :lastName])
   :timestamp (model/current-timestamp)})

(defn approvable?
  ([document] (approvable? document nil nil))
  ([document path] (approvable? document nil path))
  ([document schema path]
   (if (seq path)
     (let [schema      (or schema (model/get-document-schema document))
           schema-body (:body schema)
           str-path    (map #(if (keyword? %) (name %) %) path)
           element     (keywordize-keys (model/find-by-name schema-body str-path))]
       (true? (:approvable element)))
     (true? (get-in document [:schema-info :approvable])))))

(defn- validate-approvability [{{:keys [doc path collection]} :data application :application}]
  (let [path-v (if (ss/blank? path) [] (ss/split path #"\."))
        document (doc-persistence/by-id application collection doc)]
    (if document
      (when-not (approvable? document path-v)
        (fail :error.document-not-approvable))
      (fail :error.document-not-found))))

(defn- ->approval-mongo-model
  "Creates a mongo update map of approval data.
   To be used within model/with-timestamp. Does not overwrite the rejection note."
  [path approval]
  (let [mongo-path (if (ss/blank? path) "documents.$.meta._approved" (str "documents.$.meta." path "._approved"))
        approval-pairs (map (fn [[k v]]
                              [(format "%s.%s" mongo-path (name k)) v])
                            approval)]
    {$set (into {:modified (model/current-timestamp)} approval-pairs)}))

(defn- update-approval [{{:keys [doc path]} :data created :created :as command} approval-data]
  (or
    (validate-approvability command)
    (model/with-timestamp created
                          (update-application
                            command
                            {:documents {$elemMatch {:id doc}}}
                            (->approval-mongo-model path approval-data))
                          approval-data)))

(defn approve [{:keys [user created] :as command} status]
  (update-approval command (model/with-timestamp created (->approved status user))))

(defn set-rejection-note [command note]
  (update-approval command {:note note}))
