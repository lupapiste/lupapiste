(ns lupapalvelu.verdict-api
  (:require [taoensso.timbre :as timbre :refer [trace debug debugf info infof warn warnf error fatal]]
            [pandect.core :as pandect]
            [monger.operators :refer :all]
            [sade.http :as http]
            [sade.strings :as ss]
            [sade.util :as util]
            [sade.core :refer [ok fail fail! ok?]]
            [lupapalvelu.action :refer [defquery defcommand update-application notify boolean-parameters] :as action]
            [lupapalvelu.attachment :as attachment]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.document.tools :as tools]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.notifications :as notifications]
            [lupapalvelu.verdict :as verdict]
            [lupapalvelu.user :as user]
            [lupapalvelu.application :as application]
            [lupapalvelu.application-meta-fields :as meta-fields]
            [lupapalvelu.xml.krysp.application-from-krysp :as krysp-fetch]
            [lupapalvelu.xml.krysp.reader :as krysp-reader]))

;;
;; KRYSP verdicts
;;

(defn do-check-for-verdict [{application :application :as command}]
  {:pre [(every? command [:application :user :created])]}
  (if-let [app-xml (krysp-fetch/get-application-xml application :application-id)]

    (or
      (let [validator-fn (permit/get-verdict-validator (permit/permit-type application))]
        (validator-fn app-xml))
      (let [updates (verdict/find-verdicts-from-xml command app-xml)]
        (update-application command updates)
        (ok :verdicts (get-in updates [$set :verdicts]) :tasks (get-in updates [$set :tasks]))))

    ;; Trimble writes verdict for tyonjohtaja/suunnittelija applications to their link permits.
    (let [application-op-name (-> application :primaryOperation :name)]
      (when (#{"tyonjohtajan-nimeaminen-v2" "tyonjohtajan-nimeaminen" "suunnittelijan-nimeaminen"} application-op-name)
        (let [application (meta-fields/enrich-with-link-permit-data application)
              link-permit (application/get-link-permit-app application)
              link-permit-xml (krysp-fetch/get-application-xml link-permit :application-id)
              osapuoli-type (cond
                              (or (= "tyonjohtajan-nimeaminen" application-op-name) (= "tyonjohtajan-nimeaminen-v2" application-op-name)) "tyonjohtaja"
                              (= "suunnittelijan-nimeaminen" application-op-name) "suunnittelija")
              doc-name-mapping {"tyonjohtajan-nimeaminen"    "tyonjohtaja"
                                "tyonjohtajan-nimeaminen-v2" "tyonjohtaja-v2"
                                "suunnittelijan-nimeaminen"  "suunnittelija"}
              doc-name (doc-name-mapping application-op-name)
              doc (tools/unwrapped (domain/get-document-by-name application doc-name))
              target-kuntaRoolikoodi (get-in doc [:data :kuntaRoolikoodi])]
          (when (and link-permit-xml osapuoli-type doc target-kuntaRoolikoodi)
            (or
              (krysp-reader/tj-suunnittelija-verdicts-validator doc link-permit-xml osapuoli-type target-kuntaRoolikoodi)
              (let [updates (verdict/find-tj-suunnittelija-verdicts-from-xml command doc link-permit-xml osapuoli-type target-kuntaRoolikoodi)]
                (update-application command updates)
                (ok :verdicts (get-in updates [$set :verdicts]))))))))))

(notifications/defemail :application-verdict
  {:subject-key    "verdict"
   :tab            "/verdict"})

(defcommand check-for-verdict
  {:description "Fetches verdicts from municipality backend system.
                 If the command is run more than once, existing verdicts are
                 replaced by the new ones."
   :parameters [:id]
   :states     [:submitted :complement-needed :sent :verdictGiven :constructionStarted] ; states reviewed 2015-07-13
   :user-roles #{:authority}
   :notified   true
   :on-success (notify :application-verdict)}
  [command]
  (let [result (do-check-for-verdict command)]
    (cond
      (nil? result) (fail :info.no-verdicts-found-from-backend)
      (ok? result) (ok :verdictCount (count (:verdicts result)) :taskCount (count (:tasks result)))
      :else result)))

;;
;; Manual verdicts
;;

(defn- validate-status [{{:keys [status]} :data}]
  (when (and status (or (< status 1) (> status 42)))
    (fail :error.false.status.out.of.range.when.giving.verdict)))

(defcommand new-verdict-draft
  {:parameters [:id]
   :states     [:submitted :complement-needed :sent :verdictGiven]
   :user-roles #{:authority}}
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
   :user-roles #{:authority}
   :pre-checks [(fn [{{:keys [verdictId]} :data} application]
                  (when verdictId
                    (when-not (:draft (find-verdict application verdictId))
                      (fail :error.verdict.not-draft))))]}
  [{:keys [application created data] :as command}]
  (let [verdict (domain/->paatos
                  (merge
                    (select-keys data [:verdictId :backendId :status :name :section :agreement :text :given :official])
                    {:timestamp created, :draft true}))]
    (update-application command
      {:verdicts {$elemMatch {:id verdictId}}}
      {$set {(str "verdicts.$") verdict}})))

(defn- publish-verdict [{timestamp :created :as command} {:keys [id kuntalupatunnus]}]
  (if-not (ss/blank? kuntalupatunnus)
    (do
      (update-application command
        {:verdicts {$elemMatch {:id id}}}
        {$set {:modified timestamp
               :state    :verdictGiven
               :verdicts.$.draft false}})
      (ok))
    (fail :error.no-verdict-municipality-id)))

(defcommand publish-verdict
  {:parameters [id verdictId]
   :states     [:submitted :complement-needed :sent :verdictGiven]
   :notified   true
   :on-success (notify :application-verdict)
   :user-roles #{:authority}}
  [{:keys [application] :as command}]
  (if-let [verdict (find-verdict application verdictId)]
    (publish-verdict command verdict)
    (fail :error.unknown)))

(defcommand delete-verdict
  {:parameters [id verdictId]
   :input-validators [(partial action/non-blank-parameters [:verdictId])]
   :states     [:submitted :complement-needed :sent :verdictGiven]
   :user-roles #{:authority}}
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
   :user-roles #{:applicant :authority}}
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
