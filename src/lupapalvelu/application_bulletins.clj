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
    #{:consideration}                      :consideration
    (difference states/post-verdict-states
                states/terminal-states)    :verdictGiven
    #{:final}                              :final))

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
