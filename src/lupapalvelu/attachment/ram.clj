(ns lupapalvelu.attachment.ram
  (:require [sade.core :refer :all]
            [sade.util :as util]
            [sade.strings :as ss]
            [monger.operators :refer :all]
            [lupapalvelu.application-utils :as app-utils]
            [lupapalvelu.action :refer [update-application application->command]]
            [lupapalvelu.attachment :as att]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.notifications :refer [defemail] :as notifications]
            [lupapalvelu.user :as usr]
            [lupapalvelu.tiedonohjaus :as tos]))


(defn- new-ram-email-model [{app :application {attachment-id :attachment-id created-date :created-date} :data} _ recipient]
  (let [link-fn (fn [lang] (notifications/get-subpage-link {:id (:id app) :subpage-id attachment-id} "attachment" lang recipient))]
    {:link-fi      (link-fn "fi")
     :link-sv      (link-fn "sv")
     :address      (:address app)
     :operation-fi (app-utils/operation-description app :fi)
     :operation-sv (app-utils/operation-description app :sv)
     :created-date created-date}))

(def- new-ram-email-conf
  {:recipients-fn  :recipients
   :subject-key    "new-ram-attachment"
   :model-fn       new-ram-email-model})

(defemail :new-ram-notification new-ram-email-conf)

(defn notify-new-ram-attachment! [application attachment-id created]
  (notifications/notify! :new-ram-notification {:application application
                                                :recipients  (->> (get-in application [:authority :id]) (mongo/by-id :users) vector)
                                                :data        {:attachment-id attachment-id
                                                              :created-date  (util/to-local-date created)}}))


(defn attachment-type-allows-ram
  "Pre-checker that fails if the attachment type does not support RAMs."
  [{{attachment-id :attachmentId} :data app :application}]
  (when-not (contains? #{:paapiirustus :suunnitelmat :erityissuunnitelmat
                         :pelastusviranomaiselle_esitettavat_suunnitelmat
                         :tietomallit}
                       (some->> app
                                :attachments
                                (util/find-by-id attachment-id)
                                :type
                                :type-group
                                keyword))
    (fail :error.ram-not-allowed)))

(defn attachment-status-ok
  "Pre-checker that fails only if the attachment is not approved"
  [{{attachment-id :attachmentId} :data app :application}]
  (when (util/not=as-kw (att/attachment-state (util/find-by-id attachment-id (:attachments app))) :ok)
    (fail :error.attachment-not-approved)))

(defn ram-status-not-ok
  "Pre-checker that fails only if the attachment is approved RAM attachment."
  [{{attachment-id :attachmentId} :data app :application}]
  (let [{:keys [ramLink] :as attachment} (util/find-by-id attachment-id (:attachments app))]
    (when (and (ss/not-blank? ramLink)
               (util/=as-kw (att/attachment-state attachment) :ok))
      (fail :error.ram-approved))))

(defn- find-by-ram-link [link attachments]
  (util/find-first (comp #{link} :ramLink) attachments))

(defn ram-not-linked
  "Pre-checker that fails if the attachment is linked by a RAM
  attachment. In other words, the attachment is either root or in the
  middle of the link chain."
  [{{attachment-id :attachmentId} :data {:keys [attachments]} :application}]
  (when (find-by-ram-link attachment-id attachments)
    (fail :error.ram-linked)))

(defn- make-ram-attachment [{:keys [id op target type contents scale size] :as base-attachment} application created]
  (->> (att/create-attachment-data application {:created created
                                                :target target
                                                :group op
                                                :attachment-type type
                                                :contents contents})
       (#(merge {:ramLink id}
                %
                (when scale    {:scale scale})
                (when size     {:size size})))))

(defn create-ram-attachment! [{attachments :attachments :as application} attachment-id created]
  {:pre [(map? application)]}
  (let [ram-attachment (make-ram-attachment (util/find-by-id attachment-id attachments) application created)]
    (update-application
      (application->command application)
      {$set {:modified created}
       $push {:attachments ram-attachment}})
    (tos/update-process-retention-period (:id application) created)
    (:id ram-attachment)))

(defn resolve-ram-links [attachments attachment-id]
  (-> []
      (#(loop [res % id attachment-id] ; Backward linking
         (if-let [attachment (and id (not (util/find-by-id id res)) (util/find-by-id id attachments))]
           (recur (cons attachment res) (:ramLink attachment))
           (vec res))))
      (#(loop [res % id attachment-id] ; Forward linking
         (if-let [attachment (and id (not (find-by-ram-link id res)) (find-by-ram-link id attachments))]
           (recur (conj res attachment) (:id attachment))
           (vec res))))))
