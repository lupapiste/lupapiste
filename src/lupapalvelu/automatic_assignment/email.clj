(ns lupapalvelu.automatic-assignment.email
  "Email notifications for automatic assignments."
  (:require [lupapalvelu.application-utils :refer [application-operation-buildings]]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.user :as usr]
            [sade.schemas :as ssc]
            [sade.strings :as ss]
            [sade.util :as util]
            [lupapalvelu.html-email.core :refer [send-template-email]]
            [schema.core :as sc]))

(def EVENT-KEYS #{:attachment-type :notice-form :foreman-role :review-request})

(defn- event-key [options]
  (some EVENT-KEYS (keys options)))

(defmulti email-context event-key)

(defmethod email-context :attachment-type
  [options]
  (assoc options :tab "attachments"))

(defmethod email-context :notice-form
  [{:keys [notice-form application] :as options}]
  (let [bids (some-> notice-form :buildingIds set not-empty)
        buildings (when bids
                    (->>  (application-operation-buildings application)
                          (filter (comp bids :buildingId))
                          (map (fn [{:keys [opName description nationalId]}]
                                 #(ss/join-non-blanks " - "
                                                      [(i18n/localize % :operations opName)
                                                       description nationalId])))
                          seq))]
    (util/assoc-when options
                     :tab "tasks"
                     :notice-form-buildings buildings)))

(defmethod email-context :foreman-role
  [options]
  (assoc options :tab "tasks"))

(defmethod email-context :review-request
  [{:keys [review-request application] :as options}]
  (let [op-ids (some-> review-request :operation-ids set not-empty)
        buildings (when op-ids
                    (->>  (application-operation-buildings application)
                          (filter (comp op-ids :opId))
                          (map (fn [{:keys [opName description nationalId]}]
                                 #(ss/join-non-blanks ", "
                                                      [(i18n/localize % :operations opName)
                                                       description nationalId])))
                          seq))]
    (util/assoc-when options
                     :tab "tasks"
                     :review-request-buildings buildings)))

(sc/defschema FilterParam
  {:filter-id                      ssc/ObjectIdStr
   :filter-name                    ssc/NonBlankStr
   (sc/optional-key :filter-email) {:emails                    [ssc/Email]
                                    (sc/optional-key :message) ssc/NonBlankStr}
   sc/Keyword                      sc/Any})

(sc/defn ^:always-validate send-email-notification
  [fltr-base    :- FilterParam
   options]
  (when-let [recipients (some-> fltr-base :filter-email :emails seq)]
    (let [ctx (email-context (merge options fltr-base))]
      (doseq [addr recipients
              :let [user (usr/get-user-by-email addr)]]
        (send-template-email {:to (or user addr)
                              :template-id :automatic-assignment}
                             (util/assoc-when ctx :user user))))))
