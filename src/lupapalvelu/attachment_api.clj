(ns lupapalvelu.attachment-api
  (:require [taoensso.timbre :as timbre :refer [trace debug debugf info infof warn warnf error errorf fatal]]
            [monger.operators :refer :all]
            [lupapalvelu.core :refer [ok fail fail!]]
            [lupapalvelu.action :refer [defquery defcommand defraw update-application]]
            [lupapalvelu.attachment :refer [if-not-authority-states-must-match]]
            [lupapalvelu.organization :as organization]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.xml.krysp.application-as-krysp-to-backing-system :as mapping-to-krysp]))

(defn- get-data-argument-for-attachments-mongo-update [timestamp attachments]
  (reduce
    (fn [data-map attachment]
      (conj data-map {(keyword (str "attachments." (count data-map) ".sent")) timestamp}))
    {}
    attachments))

(defcommand move-attachments-to-backing-system
  {:parameters [id lang]
   :roles      [:authority]
   :validators [(partial if-not-authority-states-must-match #{:verdictGiven})
                (permit/validate-permit-type-is permit/R)]
   :states     [:verdictGiven]
   :description "Sends such attachments to backing system that are not yet sent."}
  [{:keys [created application] :as command}]

  (let [attachments-wo-sent-timestamp (filter
                                        #(and
                                           (pos? (-> % :versions count))
                                           (or
                                             (not (:sent %))
                                             (> (-> % :versions last :created) (:sent %)))
                                           (not (= "statement" (-> % :target :type)))
                                           (not (= "verdict" (-> % :target :type))))
                                        (:attachments application))]
    (if (pos? (count attachments-wo-sent-timestamp))

      (let [organization (organization/get-organization (:organization application))]
        (mapping-to-krysp/save-unsent-attachments-as-krysp
          (-> application
            (dissoc :attachments)
            (assoc :attachments attachments-wo-sent-timestamp))
          lang
          organization)

        (update-application command
          {$set (get-data-argument-for-attachments-mongo-update created (:attachments application))})
        (ok))

      (fail :error.sending-unsent-attachments-failed))))
