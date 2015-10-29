(ns lupapalvelu.application-bulletins
  (:require [monger.operators :refer :all]
            [lupapalvelu.states :as states]
            [sade.util :refer [fn->]]))


(defn bulletin-state [app-state] ; TODO state machine for bulletins
  (condp contains? app-state
    states/pre-verdict-states :proclaimed
    states/post-verdict-states :verdictGiven
    :proclaimed))

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
                       :attachments attachments
                       :bulletinState (bulletin-state (:state app-snapshot)))]
    app-snapshot))

(defn snapshot-updates [snapshot search-fields ts]
  {$push {:versions snapshot}
   $set  (merge {:modified ts} search-fields)})
