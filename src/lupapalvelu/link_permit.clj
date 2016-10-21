(ns lupapalvelu.link-permit
  (:require [taoensso.timbre :as timbre :refer [infof info error errorf]]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.foreman :as foreman]
            [lupapalvelu.states :as states]
            [sade.core :refer :all]))

;; TODO needs refactoring
(defn update-link-permit-data-with-kuntalupatunnus-from-verdict [application]
  (let [link-permit-app-id (-> application :linkPermitData first :id) ; TODO: search trough all link permits
        link-permit-app (domain/get-application-no-access-checking link-permit-app-id)
        kuntalupatunnus (-> link-permit-app :verdicts first :kuntalupatunnus)] ; TODO: search trough all verdicts
    ;; TODO why we check only link permit data on index 0?
    (if kuntalupatunnus
      (-> application
         (assoc-in [:linkPermitData 0 :lupapisteId] link-permit-app-id)
         (assoc-in [:linkPermitData 0 :id] kuntalupatunnus)
         (assoc-in [:linkPermitData 0 :type] "kuntalupatunnus"))
      (if (and (foreman/foreman-app? application) (some #{(keyword (:state link-permit-app))} states/post-submitted-states))
        application
        (do
          (info "Not able to get a kuntalupatunnus for the application  " (:id application) " from it's link permit's (" link-permit-app-id ") verdict."
                 " Associated Link-permit data: " (:linkPermitData application))
          (if (foreman/foreman-app? application)
            (fail! :error.link-permit-app-not-in-post-sent-state)
            (fail! :error.kuntalupatunnus-not-available-from-verdict)))))))
