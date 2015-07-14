(ns lupapalvelu.verdict
  (:require [taoensso.timbre :as timbre :refer [debug debugf info infof warn warnf error]]
            [monger.operators :refer :all]
            [pandect.core :as pandect]
            [sade.http :as http]
            [sade.strings :as ss]
            [sade.util :as util]
            [lupapalvelu.action :as action]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.attachment :as attachment]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.tasks :as tasks]
            [lupapalvelu.xml.krysp.reader :as krysp-reader])
  (:import [java.net URL]))

(defn- get-poytakirja [application user timestamp verdict-id pk]
  (if-let [url (get-in pk [:liite :linkkiliitteeseen])]
    (do
      (debug "Download" url)
      (let [filename        (-> url (URL.) (.getPath) (ss/suffix "/"))
            resp            (try
                              (http/get url :as :stream :throw-exceptions false)
                              (catch Exception e {:status -1 :body (str e)}))
            header-filename  (when-let [content-disposition (get-in resp [:headers "content-disposition"])]
                               (ss/replace content-disposition #"attachment;filename=" ""))
            content-length  (util/->int (get-in resp [:headers "content-length"] 0))
            urlhash         (pandect/sha1 url)
            attachment-id   urlhash
            attachment-type {:type-group "muut" :type-id "muu"}
            target          {:type "verdict" :id verdict-id :urlHash urlhash}
            attachment-time (get-in pk [:liite :muokkausHetki] timestamp)]
        ; If the attachment-id, i.e., hash of the URL matches
        ; any old attachment, a new version will be added
        (if (= 200 (:status resp))
          (attachment/attach-file! {:application application
                                    :filename (or header-filename filename)
                                    :size content-length
                                    :content (:body resp)
                                    :attachment-id attachment-id
                                    :attachment-type attachment-type
                                    :target target
                                    :required false
                                    :locked true
                                    :user user
                                    :created attachment-time
                                    :state :ok})
          (error (str (:status resp) " - unable to download " url ": " resp)))
        (-> pk (assoc :urlHash urlhash) (dissoc :liite))))
    pk))

(defn- verdict-attachments [application user timestamp verdict]
  {:pre [application]}
  (when (:paatokset verdict)
    (let [verdict-id (mongo/create-id)]
      (->
        (assoc verdict :id verdict-id, :timestamp timestamp)
        (update-in [:paatokset]
          (fn [paatokset]
            (filter seq
              (map (fn [paatos]
                     (update-in paatos [:poytakirjat] #(map (partial get-poytakirja application user timestamp verdict-id) %)))
                paatokset))))))))

(defn- get-verdicts-with-attachments [application user timestamp xml reader]  ;; TODO: reader-parametri tullut lisaa -> korjaa testit
  (->> (krysp-reader/->verdicts xml reader)
    (map (partial verdict-attachments application user timestamp))
    (filter seq)))

(defn find-verdicts-from-xml
  "Returns a monger update map"
  [{:keys [application user created] :as command} app-xml]
  {:pre [(every? command [:application :user :created]) app-xml]}
  (let [verdict-reader (permit/get-verdict-reader (:permitType application))
        extras-reader (permit/get-verdict-extras-reader (:permitType application))]
    (when-let [verdicts-with-attachments (seq (get-verdicts-with-attachments application user created app-xml verdict-reader))]
      (let [has-old-verdict-tasks (some #(= "verdict" (get-in % [:source :type]))  (:tasks application))
            tasks (tasks/verdicts->tasks (assoc application :verdicts verdicts-with-attachments) created)]
        {$set (merge {:verdicts verdicts-with-attachments
                      :modified created}
                (when-not (action/post-verdict-states (keyword (:state application)))
                  {:state :verdictGiven})
                (when-not has-old-verdict-tasks {:tasks tasks})
                (when extras-reader (extras-reader app-xml)))}))))

;;
;; TODO:
;;  - yhdista tama funktio jotenkin ylla olevan find-verdicts-from-xml:n kanssa?
;;  - Kayta testaamiseen "verdict - 2.1.8 - Tekla.xml" -tiedoston sisaltoa (esim kopioi verdict.xml:n paalle)
;;
(defn find-tj-suunnittelija-verdicts-from-xml
  [{:keys [application user created] :as command} app-xml osapuoli-type target-kuntaRoolikoodi]
  {:pre [(every? command [:application :user :created]) app-xml]}
  (let [verdict-reader (partial
                         (permit/get-tj-suunnittelija-verdict-reader (:permitType application))
                         osapuoli-type target-kuntaRoolikoodi)]
    (when-let [verdicts-with-attachments (seq (get-verdicts-with-attachments application user created app-xml verdict-reader))]
      {$set {:verdicts verdicts-with-attachments
             :modified created
             :state    :verdictGiven}})))

