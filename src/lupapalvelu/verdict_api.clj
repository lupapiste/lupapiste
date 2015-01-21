(ns lupapalvelu.verdict-api
  (:require [taoensso.timbre :as timbre :refer [trace debug debugf info infof warn error fatal]]
            [pandect.core :as pandect]
            [monger.operators :refer :all]
            [sade.http :as http]
            [sade.strings :as ss]
            [sade.util :as util]
            [sade.core :refer [ok fail fail!]]
            [sade.email :as email]
            [lupapalvelu.action :refer [defquery defcommand update-application notify boolean-parameters] :as action]
            [lupapalvelu.attachment :as attachment]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.notifications :as notifications]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.tasks :as tasks]
            [lupapalvelu.user :as user]
            [lupapalvelu.i18n :refer [with-lang loc]]
            [lupapalvelu.xml.krysp.application-from-krysp :as krysp-fetch-api]
            [lupapalvelu.xml.krysp.reader :as krysp-reader])
  (:import [java.net URL]))

;;
;; KRYSP verdicts
;;

(defn- get-poytakirja [application user timestamp verdict-id pk]
  (if-let [url (get-in pk [:liite :linkkiliitteeseen])]
    (do
      (debug "Download" url)
      (let [filename        (-> url (URL.) (.getPath) (ss/suffix "/"))
            resp            (try
                              (http/get url :as :stream :throw-exceptions false)
                              (catch Exception e {:status -1 :body (str e)}))
            header-filename  (when (get (:headers resp) "content-disposition")
                               (clojure.string/replace (get (:headers resp) "content-disposition") #"attachment;filename=" ""))

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

(defn verdict-attachments [application user timestamp verdict]
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

(defn- get-verdicts-with-attachments [application user timestamp xml]
  (let [permit-type (:permitType application)
        reader (permit/get-verdict-reader permit-type)
        verdicts (krysp-reader/->verdicts xml reader)]
    (filter seq (map (partial verdict-attachments application user timestamp) verdicts))))

(defn find-verdicts-from-xml [{:keys [application user created] :as command} app-xml]
  {:pre [(every? command [:application :user :created]) app-xml]}
  (let [extras-reader (permit/get-verdict-extras-reader (:permitType application))]
    (when-let [verdicts-with-attachments (seq (get-verdicts-with-attachments application user created app-xml))]
      (let [has-old-verdict-tasks (some #(= "verdict" (get-in % [:source :type]))  (:tasks application))
            tasks (tasks/verdicts->tasks (assoc application :verdicts verdicts-with-attachments) created)
            updates {$set (merge {:verdicts verdicts-with-attachments
                                  :modified created
                                  :state    :verdictGiven}
                            (when-not has-old-verdict-tasks {:tasks tasks})
                            (when extras-reader (extras-reader app-xml)))}]
        (update-application command updates)
        {:verdicts verdicts-with-attachments :tasks (get-in updates [$set :tasks])}))))

(defn do-check-for-verdict [command]
  {:pre [(every? command [:application :user :created])]}
  (when-let [app-xml (krysp-fetch-api/get-application-xml (:application command))]
    (find-verdicts-from-xml command app-xml)))

(notifications/defemail :application-verdict
  {:subject-key    "verdict"
   :tab            "/verdict"})

(defcommand check-for-verdict
  {:description "Fetches verdicts from municipality backend system.
                 If the command is run more than once, existing verdicts are
                 replaced by the new ones."
   :parameters [:id]
   :states     [:submitted :complement-needed :sent :verdictGiven] ; states reviewed 2013-09-17
   :roles      [:authority]
   :notified   true
   :on-success (notify :application-verdict)}
  [command]
  (if-let [result (do-check-for-verdict command)]
    (ok :verdictCount (count (:verdicts result)) :taskCount (count (:tasks result)))
    (fail :info.no-verdicts-found-from-backend)))


;;
;; Manual verdicts
;;

(defn- validate-status [{{:keys [status]} :data}]
  (when (and status (or (< status 1) (> status 42)))
    (fail :error.false.status.out.of.range.when.giving.verdict)))

(defcommand new-verdict-draft
  {:parameters [:id]
   :states     [:submitted :complement-needed :sent :verdictGiven]
   :roles      [:authority]}
  [command]
  (let [blank-verdict (domain/->paatos {:draft true})]
    (update-application command {$push {:verdicts blank-verdict}})
    (ok :verdictId (:id blank-verdict))))

(defn- find-verdict [{verdicts :verdicts} id]
  (some #(when (= id (:id %)) %) verdicts))

(defcommand save-verdict-draft
  {:parameters [:id verdictId :backendId #_:status :name :section :agreement :text :given :official]
   :description  "backendId = Kuntalupatunnus, status = poytakirjat[] / paatoskoodi,
                  name = poytakirjat[] / paatoksentekija, section = poytakirjat[] / pykala
                  agreement = (sopimus), text =  poytakirjat[] / paatos
                  given, official = paivamaarat / antoPvm, lainvoimainenPvm"
   :input-validators [validate-status
                      (partial action/non-blank-parameters [:verdictId])
                      (partial action/boolean-parameters [:agreement])]
   :states     [:submitted :complement-needed :sent :verdictGiven]
   :roles      [:authority]}
  [{:keys [application created data] :as command}]
  (let [old-verdict (find-verdict application verdictId)
        verdict (domain/->paatos
                  (merge
                    (select-keys data [:verdictId :backendId :status :name :section :agreement :text :given :official])
                    {:timestamp created, :draft true}))]

    (when-not (:draft old-verdict) (fail! :error.unknown)) ; TODO error message

    (update-application command
      {:verdicts {$elemMatch {:id verdictId}}}
      {$set {(str "verdicts.$") verdict}})))

(defcommand publish-verdict
  {:parameters [id verdictId]
   :states     [:submitted :complement-needed :sent :verdictGiven]
   :notified   true
   :on-success (notify :application-verdict)
   :roles      [:authority]}
  [{:keys [application created] :as command}]
  (if-let [verdict (find-verdict application verdictId)]
    (update-application command
      {:verdicts {$elemMatch {:id verdictId}}}
      {$set {:modified created
             :state    :verdictGiven
             :verdicts.$.draft false}})
    (fail :error.unknown)))

(defcommand delete-verdict
  {:parameters [id verdictId]
   :input-validators [(partial action/non-blank-parameters [:verdictId])]
   :states     [:submitted :complement-needed :sent :verdictGiven]
   :roles      [:authority]}
  [{:keys [application created] :as command}]
  (when-let [verdict (find-verdict application verdictId)]
    (let [target {:type "verdict", :id verdictId} ; key order seems to be significant!
          is-verdict-attachment? #(= (select-keys (:target %) [:id :type]) target)
          attachments (filter is-verdict-attachment? (:attachments application))
          {:keys [sent state verdicts]} application
          ; Deleting the only given verdict? Return sent or submitted state.
          step-back? (and (= 1 (count verdicts)) (= "verdictGiven" state))
          updates (merge {$pull {:verdicts {:id verdictId}
                                :comments {:target target}
                                :tasks {:source target}}}
                    (when step-back? {$set {:state (if sent :sent :submitted)}}))]
      (update-application command updates)
      (doseq [{attachment-id :id} attachments]
        (attachment/delete-attachment application attachment-id))
      (when step-back?
        (notifications/notify! :application-state-change command)))))

(defcommand sign-verdict
  {:description "Applicant/application owner can sign an application's verdict"
   :parameters [id verdictId password]
   :states     [:verdictGiven :constructionStarted]
   :pre-checks [domain/validate-owner-or-write-access]
   :roles      [:applicant :authority]}
  [{:keys [application created user] :as command}]
  (if (user/get-user-with-password (:username user) password)
    (when (find-verdict application verdictId)
      (update-application command
                          {:verdicts {$elemMatch {:id verdictId}}}
                          {$set  {:modified              created}
                           $push {:verdicts.$.signatures {:created created
                                                          :user (user/summary user)}}
                          }))
    (do
      ; Throttle giving information about incorrect password
      (Thread/sleep 2000)
      (fail :error.password))))

(defn- send-kopiolaitos-email [lang email-address attachments orderInfo]

  (println "\n send-kopiolaitos-email, lang: ")
  (clojure.pprint/pprint lang)
  (println "\n send-kopiolaitos-email, email-address: ")
  (clojure.pprint/pprint email-address)
  (println "\n send-kopiolaitos-email, attachments: ")
  (clojure.pprint/pprint attachments)
  (println "\n send-kopiolaitos-email, orderInfo: ")
  (clojure.pprint/pprint orderInfo)
  (println "\n")

  (let [zip (attachment/get-all-attachments attachments)
        email-subject (str (with-lang lang (loc :kopiolaitos-email-subject)) \space (:ordererOrganization orderInfo))
        _ (println "\n send-kopiolaitos-email, email-subject: " email-subject "\n")
        ]
    ;; from email/send-email-message false = success, true = failure -> turn it other way around
    (try
      (not (email/send-email-message
           email-address
           email-subject
           ["Test message content"]
           attachments))
      (catch Exception e
        (fail! :kopiolaitos-email-sending-failed)))))

;; TODO: Siirra tama organization-namespaceen
(defn- get-kopiolaitos-email-address [{:keys [organization] :as application}]
  ;; TODO: Hae organisaatiolta "kopiolaitos-email-address"
  "pasiesko@example.com"  ;; (organization/get-kopiolaitos-email organization)
  )

(defn- do-order-verdict-attachment-prints [{{:keys [lang attachmentIds orderInfo]} :data application :application}]
  ;;
  ;; TODO: Hae organisaation kopiolaitoksen email-osoite.
  ;; TODO: Laheta tilaus email.
  ;;
  (if-let [email-address (get-kopiolaitos-email-address application)]
    (let [attachments (filterv #((set attachmentIds) (:id %)) (:attachments application))]
      (if (send-kopiolaitos-email lang email-address attachments orderInfo)
        (ok)
       (fail! :kopiolaitos-email-sending-failed)))
    (fail! :no-kopiolaitos-email-defined)))

(defcommand order-verdict-attachment-prints
  {:description "Orders prints of marked verdict attachments from copy institute.
                 If the command is run more than once, the already ordered attachment copies are ordered again."
   :parameters [:id :lang :attachmentIds :orderInfo]
   :states     [:verdictGiven :constructionStarted]   ;; TODO: nama tilat ok?
   :roles      [:authority]
   :input-validators [(partial action/non-blank-parameters [:lang])
                      (partial action/vector-parameters [:attachmentIds])
                      (partial action/map-parameters [:orderInfo])]}
  [command]
  (do-order-verdict-attachment-prints command)
  (ok))

