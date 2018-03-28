(ns lupapalvelu.document.approval
  (:require [monger.operators :refer :all]
            [sade.core :refer :all]
            [sade.strings :as ss]
            [lupapalvelu.action :refer [update-application]]
            [lupapalvelu.document.model :as model]
            [lupapalvelu.document.persistence :as doc-persistence]))

(defn- validate-approvability [{{:keys [doc path collection]} :data application :application}]
  (let [path-v (if (ss/blank? path) [] (ss/split path #"\."))
        document (doc-persistence/by-id application collection doc)]
    (if document
      (when-not (model/approvable? document path-v)
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
  (update-approval command (model/with-timestamp created (model/->approved status user))))

(defn set-rejection-note [command note]
  (update-approval command {:note note}))
