(ns lupapalvelu.matti
  "Matti batchrun functionality."
  (:require [lupapalvelu.application :as app]
            [lupapalvelu.backing-system.krysp.application-as-krysp-to-backing-system :as bs]
            [lupapalvelu.backing-system.krysp.http :as krysp-http]
            [lupapalvelu.integrations.state-change :as state-change]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.pate.verdict-common :as vc]
            [lupapalvelu.pate.verdict-interface :as vif]
            [lupapalvelu.user :as usr]
            [monger.operators :refer :all]
            [sade.date :as date]
            [sade.strings :as ss]
            [sade.util :as util]
            [slingshot.slingshot :refer [try+]]))

(defn parse-params [{:keys [organizationId validateXml sendVerdicts sendStateChanges ids startDate endDate]}]
  (assoc (if ids
           {:ids (remove ss/blank? (ss/split ids  #"\s+"))}
           {:start-date (date/timestamp (date/start-of-day startDate))
            :end-date   (date/timestamp (date/end-of-day endDate))})
         :org-id organizationId
         :validate? validateXml
         :send-verdicts? sendVerdicts
         :send-state-changes? sendStateChanges))

(defn send-kuntagml [user organization application type kuntagml]
  (krysp-http/send-xml application user
                       type
                       kuntagml
                       (bs/http-conf organization
                                     (:permitType application)
                                     true)))

(defn send-state-changes [user organization
                          {history :history
                           app-id  :id
                           :as     application}
                          {:keys [validate? kuntagml?]}]
  (for [{:keys [state ts]} (->> history
                                (filter :state)
                                (drop-while #(util/includes-as-kw?  #{:draft :open :canceled :info}
                                                                    (:state %))))
        :let               [cmd {:user         user
                                 :application  application
                                 :organization (delay organization)
                                 :created      ts}]]
    (try+
      (let [kuntagml (when (and kuntagml? (util/=as-kw state :sent))
                       (bs/batchrun-kuntagml application
                                             organization
                                             {:validate? validate?}))]
       (Thread/sleep 200)
       (state-change/trigger-state-change cmd state)
       (when kuntagml
          (send-kuntagml user organization application :application kuntagml))
       {:applicationId app-id
        :state         state
        :timestamp     ts
        :kuntagml      (boolean kuntagml)})
     (catch map? err
       {:applicationId app-id
        :state         state
        :timestamp     ts
        :error         (:details err)})
     (catch Object err
       {:applicationId app-id
        :state         state
        :timestamp     ts
        :error         (.getMessage err)}))))

(defn send-verdicts
  [user organization application validate?]
  (for [verdict (vif/published-verdicts {:application application})
        :let    [timestamp (vc/verdict-published verdict)]]
    (try+
      (do
           (send-kuntagml user organization application :verdict
                          (bs/batchrun-kuntagml application organization
                                                {:verdict   verdict
                                                 :validate? validate?}))
           {:applicationId (:id application)
            :timestamp     timestamp
            :verdict       (:id verdict)})
      (catch map? err
        {:applicationId (:id application)
         :verdict       (:id verdict)
         :timestamp     timestamp
         :error         (:details err)})
      (catch Object err
        {:applicationId (:id application)
         :verdict       (:id verdict)
         :timestamp     timestamp
         :error         (.getMessage err)}))))

(defn batchrun [{:keys [org-id ids start-date end-date validate? send-verdicts? send-state-changes?]}]
  (let [user         (usr/batchrun-user [org-id])
        organization (mongo/by-id :organizations org-id)]
    (for [application (mongo/select :applications
                                    (cond-> {:organization org-id
                                             :permitType {$in ["R" "P" "KT" "MM" "YL" "YM" "VVVL" "MAL"]}}
                                      ids        (assoc :_id {$in ids})
                                      start-date (assoc $or [{:history {$elemMatch {:state {$in ["sent" "submitted"]}
                                                                                    :ts    {$gte start-date
                                                                                            $lte end-date}}}}
                                                             {:pate-verdicts.published.published {$gte start-date
                                                                                                  $lte end-date}}
                                                             {:verdicts.timestamp {$gte start-date
                                                                                   $lte end-date}}])))]
      (concat (when send-state-changes?
                (send-state-changes user organization application {:validate? true :kuntagml? true}))
              (when send-verdicts?
                (send-verdicts user organization application validate?))))))

(defn review-batchrun-targets [{:keys [org-id start-date end-date]}]
  (map :id (mongo/select :applications
                         {:organization            org-id
                          :permitType              "R"
                          :tasks
                          {$elemMatch {:created          {$gte start-date
                                                          $lte end-date}
                                       :state            "sent"
                                       :schema-info.name "task-katselmus"}}}
                         [:id])))

(defn review-batchrun [org-id application-ids]
  (let [user         (usr/batchrun-user [org-id])
        organization (assoc-in (mongo/by-id :organizations org-id)
                               [:krysp :R :http :enabled] true)]
    (apply concat
           (for [{tasks :tasks
                  :as   application} (mongo/select :applications {:organization org-id
                                                                  :permitType   "R"
                                                                  :tasks.state  "sent"
                                                                  :_id          {$in application-ids}})
                 :let                [command {:application  (app/post-process-app-for-krysp application organization)
                                               :organization (delay organization)
                                               :user         user}]]
             (->> tasks
                  (filter (fn [{:keys [schema-info state]}]
                            (and (util/=as-kw (:name schema-info) :task-katselmus)
                                 (util/=as-kw state :sent))))
                  (map (fn [task]
                         {:applicationId (:id application)
                          :taskId        (:id task)
                          :taskName      (:taskname task)
                          :result        (try+
                                          (if (bs/save-review-as-krysp command task "fi")
                                            "OK"
                                            "FAIL")
                                          (catch Object _
                                            "ERROR"))})))))))
