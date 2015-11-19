(ns lupapalvelu.application-bulletins
  (:require [monger.operators :refer :all]
            [clojure.set :refer [difference]]
            [lupapalvelu.state-machine :as sm]
            [lupapalvelu.states :as states]
            [sade.util :refer [fn->]]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.mime :as mime]))

(def bulletin-state-seq (sm/state-seq states/bulletin-version-states))

(defn bulletin-state [app-state]
  (condp contains? (keyword app-state)
    states/pre-verdict-states              :proclaimed
    (difference states/post-verdict-states
                states/terminal-states)    :verdictGiven
    #{:final}                              :final))

;; Query/Projection fields


(def bulletins-fields
  {:versions {$slice -1} :versions.bulletinState 1
   :versions.state 1 :versions.municipality 1
   :versions.address 1 :versions.location 1
   :versions.primaryOperation 1 :versions.propertyId 1
   :versions.applicant 1 :versions.modified 1
   :versions.proclamationEndsAt 1
   :modified 1})

(def bulletin-fields
  (merge bulletins-fields
         {:versions._applicantIndex 1
          :versions.documents 1
          :versions.id 1
          :versions.attachments 1}))

;; Snapshot

(def app-snapshot-fields
  [:_applicantIndex :address :applicant :created :documents :location
   :modified :municipality :organization :permitType
   :primaryOperation :propertyId :state :verdicts])

(def remove-party-docs-fn
  (partial remove (fn-> :schema-info :type keyword (= :party))))

(defn create-bulletin-snapshot [application]
  (let [app-snapshot (select-keys application app-snapshot-fields)
        app-snapshot (update-in
                       app-snapshot
                       [:documents]
                       remove-party-docs-fn)
        attachments (->> (:attachments application)
                         (filter :latestVersion)
                         (map #(dissoc % :versions)))
        app-snapshot (assoc app-snapshot
                       :id (mongo/create-id)
                       :attachments attachments
                       :bulletinState (bulletin-state (:state app-snapshot)))]
    app-snapshot))

(defn snapshot-updates [snapshot search-fields ts]
  {$push {:versions snapshot}
   $set  (merge {:modified ts} search-fields)})

(defn create-comment [comment contact-info created]
  (let [id          (mongo/create-id)
        new-comment {:id           id
                     :comment      comment
                     :created      created
                     :contact-info contact-info}]
    new-comment))

(defn store-files [bulletin-id comment-id files]
  (let [store-file-fn (fn [file] (let [file-id (mongo/create-id)
                                       sanitized-filename (mime/sanitize-filename (:filename file))]
                                   (mongo/upload file-id sanitized-filename (:content-type file) (:tempfile file) :bulletinId bulletin-id :commentId comment-id)
                                   {:id file-id
                                    :filename sanitized-filename
                                    :size (:size file)
                                    :contentType (:content-type file)}))]
    (map store-file-fn files)))

(defn get-bulletin
  ([bulletinId]
    (get-bulletin bulletinId bulletin-fields))
  ([bulletinId projection]
   (mongo/with-id (mongo/by-id :application-bulletins bulletinId projection))))

(defn get-bulletin-attachment [attachment-id]
  (when-let [attachment-file (mongo/download attachment-id)]
    (when-let [bulletin (get-bulletin (:application attachment-file))]
      (when (seq bulletin) attachment-file))))

