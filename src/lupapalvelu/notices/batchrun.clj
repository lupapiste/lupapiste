(ns lupapalvelu.notices.batchrun
  (:require [lupapalvelu.api-common :as api]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.logging :as log]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.notices.notices-api] ;; Needed for `api/execute-command`
            [lupapalvelu.user :as usr]
            [monger.operators :refer :all]
            [sade.core :refer [ok?]]
            [sade.date :as date]))

(def ^:private automatic-params
  {:state "approved"
   :text  [{:insert (i18n/localize :fi :notice.automatic-ready.text)}]
   :lang  "fi"})

(defn- publish-automatic-notice
  [system organization-id application-id]
  (let [user (usr/batchrun-user [organization-id])]
    (log/with-logging-context {:applicationId application-id
                               :userId        (:id user)}
      (try
        (let [resp (api/execute-command
                     "publish-notice-resolution"
                     (assoc automatic-params :id application-id)
                     {:user        user
                      :system      system
                      :scheme      "cronjob"
                      :remote-addr "localhost"
                      :headers     {"user-agent" "Lupapiste Eräajo"
                                    "host"       "localhost"}})]
          (or (ok? resp)
              (log/error "Publish notice resolution failed:" resp)))
        (catch Exception e
          (log/error e "Publish notice resolution failed:"
                     (ex-message e)))))))

(defn process-due-demolition-notices
  "Publishes default approval resolution for demolition notices that have not been processed
  in thirty days. Returns the number of approved notices."
  [system]
  {:pre [(map? system)]}
  (let [ts (date/timestamp (date/minus (date/now) :days 30))]
    (->> (mongo/select :applications
                       {:primaryOperation.name :purkamisilmoitus
                        :state                 :submitted
                        :submitted             {$lte ts}}
                       [:organization])
         (keep (fn [{:keys [organization id]}]
                 (publish-automatic-notice system organization id)))
         count)))
