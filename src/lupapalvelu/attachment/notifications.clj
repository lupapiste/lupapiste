(ns lupapalvelu.attachment.notifications
  (:require [lupapalvelu.notifications :refer [defemail] :as notifications]
            [lupapalvelu.mongo :as mongo]
            [sade.util :as util]
            [lupapalvelu.i18n :as i18n]
            [sade.core :refer :all]))

(defn- new-ram-email-model [{app :application {attachment-id :attachment-id created-date :created-date} :data} _ recipient]
  (let [link-fn (fn [lang] (notifications/get-subpage-link {:id (:id app) :subpage-id attachment-id} "attachment" lang recipient))]
    {:link-fi (link-fn "fi")
     :link-sv (link-fn "sv")
     :address (:title app)
     :operation-fi (i18n/localize "fi" "operations" (get-in app [:primaryOperation :name]))
     :operation-sv (i18n/localize "sv" "operations" (get-in app [:primaryOperation :name]))
     :created-date created-date}))

(def- new-ram-email-conf
  {:recipients-fn  :recipients
   :subject-key    "new-ram-attachment"
   :model-fn       new-ram-email-model})

(defemail :new-ram-notification new-ram-email-conf)

(defn notify-new-ram-attachment! [application attachment-id created]
  (notifications/notify! :new-ram-notification {:application application
                                                :recipients (->> (get-in application [:authority :id]) (mongo/by-id :users) vector)
                                                :data {:attachment-id attachment-id
                                                       :created-date  (util/to-local-date created)}}))
