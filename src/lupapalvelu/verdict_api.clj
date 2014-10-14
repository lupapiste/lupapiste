(ns lupapalvelu.verdict-api
  (:require [taoensso.timbre :as timbre :refer [trace debug debugf info infof warn error fatal]]
            [pandect.core :as pandect]
            [monger.operators :refer :all]
            [sade.http :as http]
            [sade.strings :as ss]
            [sade.util :as util]
            [lupapalvelu.core :refer [ok fail fail!]]
            [lupapalvelu.action :refer [defquery defcommand update-application notify boolean-parameters] :as action]
            [lupapalvelu.attachment :as attachment]
            [lupapalvelu.application :as application]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.tasks :as tasks]
            [lupapalvelu.user :as user]
            [lupapalvelu.xml.krysp.reader :as krysp])
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
            locked          true
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
                                    :locked locked
                                    :user user
                                    :created attachment-time})
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
        verdicts (krysp/->verdicts xml reader)]
    (filter seq (map (partial verdict-attachments application user timestamp) verdicts))))

(defn do-check-for-verdict [command user created application]
  (if-let [xml (application/get-application-xml application)]
    (let [extras-reader (permit/get-verdict-extras-reader (:permitType application))]
      (if-let [verdicts-with-attachments (seq (get-verdicts-with-attachments application user created xml))]
        (let [has-old-verdict-tasks (some #(= "verdict" (get-in % [:source :type]))  (:tasks application))
              tasks (tasks/verdicts->tasks (assoc application :verdicts verdicts-with-attachments) created)
              updates {$set (merge {:verdicts verdicts-with-attachments
                                    :modified created
                                    :state    :verdictGiven}
                              (when-not has-old-verdict-tasks {:tasks tasks})
                              (when extras-reader (extras-reader xml)))}]
          (update-application command updates)
          (ok :verdictCount (count verdicts-with-attachments) :taskCount (count (get-in updates [$set :tasks]))))
        (fail :info.no-verdicts-found-from-backend)))
    (fail :info.no-verdicts-found-from-backend)))

(defcommand check-for-verdict
  {:description "Fetches verdicts from municipality backend system.
                 If the command is run more than once, existing verdicts are
                 replaced by the new ones."
   :parameters [:id]
   :states     [:submitted :complement-needed :sent :verdictGiven] ; states reviewed 2013-09-17
   :roles      [:authority]
   :notified   true
   :on-success  (notify :application-verdict)}
  [{:keys [user created application] :as command}]
  (do-check-for-verdict command user created application))

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
    (do
      (debug verdictId)
      (debug (map :id (:verdicts application)))

      (fail :error.unknown)) ; TODO
    ))

(defcommand delete-verdict
  {:parameters [id verdictId]
   :input-validators [(partial action/non-blank-parameters [:verdictId])]
   :states     [:submitted :complement-needed :sent :verdictGiven]
   :roles      [:authority]}
  [{:keys [application created] :as command}]
  (when-let [verdict (find-verdict application verdictId)]
    (let [is-verdict-attachment? #(= (select-keys (:target %) [:id :type]) {:type "verdict" :id (:id verdict)})
          attachments (filter is-verdict-attachment? (:attachments application))]
      (update-application command {$pull {:verdicts {:id verdictId}}})
      ; TODO pull from tasks, auth-comments
      (doseq [{attachment-id :id} attachments]
        (attachment/delete-attachment application attachment-id)))))

(defcommand sign-verdict
  {:description "Applicant/application owner can sign an application's verdict"
   :parameters [id verdictId password]
   :states     [:verdictGiven :constructionStarted]
   :pre-checks [domain/validate-owner-or-writer]
   :roles      [:applicant :authority]}
  [{:keys [application created user] :as command}]
  (if (user/get-user-with-password (:username user) password)
    (when (find-verdict application verdictId)
      (update-application command
                          {:verdicts {$elemMatch {:id verdictId}}}
                          {$set {:modified                     created
                                 :verdicts.$.signature.created created
                                 :verdicts.$.signature.user    (select-keys user [:id :username :firstName :lastName :role])
                                }}))
    (do
      ; Throttle giving information about incorrect password
      (Thread/sleep 2000)
      (fail :error.password))))
