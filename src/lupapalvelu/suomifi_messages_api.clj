(ns lupapalvelu.suomifi-messages-api
  (:require [clojure.string :as s]
            [lupapalvelu.action :refer [defcommand defquery some-pre-check] :as action]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.pate.verdict :refer [verdict-exists backing-system-verdict]]
            [lupapalvelu.states :as states]
            [lupapalvelu.suomifi-messages :as suomifi :refer [suomifi-enabled]]
            [lupapalvelu.user :as usr]
            [sade.core :refer :all]
            [sade.env :as env]
            [sade.schemas :as ssc]
            [taoensso.timbre :refer [trace tracef debug debugf info infof warn warnf error errorf fatal fatalf]]))

;; Helpers

(defrecord Recipient [first-name last-name address zipcode city country person-id])

(defn- extract-recipient-data [app]
  (let [recipient (->> app
                       :documents
                       (filter #(s/includes? (get-in % [:schema-info :name]) "paatoksen-toimitus"))
                       first
                       :data)]
    (->Recipient (get-in recipient [:henkilotiedot :etunimi :value] "")
                 (get-in recipient [:henkilotiedot :sukunimi :value] "")
                 (get-in recipient [:osoite :katu :value] "")
                 (get-in recipient [:osoite :postinumero :value] "")
                 (get-in recipient [:osoite :postitoimipaikannimi :value] "")
                 "FI" ;; Suomi.fi-messages can only be sent to Finnish addresses.
                 ""   ;; We don't have hetu (= personal id number) for recipients
                 )))

(defn- ensure-org-present [application organization]
  (if (delay? organization)
    @organization
    (mongo/by-id :organizations (:organization application))))

;; Pre-checkers

(defn- can-send-suomifi-verdict
  "Pre-check for whether the user can send the verdict as Suomi.fi-message"
  [{:keys [verdicts organization]} user verdictId]
  (let [app-has-verdict? (some? (map #(= verdictId (:id %)) verdicts))
        user-can-send-verdict? (->> user :orgAuthz ((keyword organization)) :approver)]
    (if-not (and app-has-verdict? user-can-send-verdict?)
      (fail :error.suomifi-messages.not-enabled)
      true)))

;; ------------------------------------------
;; Suomi.fi-messages API
;; ------------------------------------------

(defcommand send-suomifi-verdict
  {:description      "Send a verdict to the recipient via Suomi.fi-messages"
   :parameters       [id verdictId firstName lastName personId address zipCode city country]
   :user-roles       #{:authority}
   :states           states/post-verdict-states
   :input-validators [{:id        ssc/NonBlankStr
                       :verdictId ssc/NonBlankStr
                       :zipCode   ssc/Zipcode
                       :firstName ssc/NonBlankStr
                       :lastName  ssc/NonBlankStr
                       :personId  ssc/Hetu
                       :city      ssc/NonBlankStr
                       :address   ssc/NonBlankStr
                       :country   ssc/ISO-3166-alpha-2}]
   :pre-checks       [suomifi-enabled
                      (some-pre-check (verdict-exists :published?) backing-system-verdict)]}
  [{:keys [application organization user] :as command}]
  (when (can-send-suomifi-verdict application user verdictId)
    ;; Here we merge the global configuration for Suomi.fi-messages with the organizational one for easier handling.
    (let [suomifi-settings (-> (if (delay? organization)
                                 @organization
                                 (mongo/by-id :organizations (:organization application)))
                               :suomifi-messages
                               (merge (:suomifi-messages (env/get-config))))]
      (if-let [message (suomifi/build-verdict-message application
                                                      verdictId
                                                      user
                                                      (->Recipient firstName lastName
                                                                   address zipCode
                                                                   city country (usr/coerce-person-id personId))
                                                      suomifi-settings)]
        (let [res (suomifi/send-suomifi-message! message application user verdictId suomifi-settings)]
          (if (ok? res)
            (ok)
            (fail (:message res))))
        (fail :error.suomifi-messages.msg-error)))))

(defquery get-verdict-recipient
  {:description      "Fetch details for the recipient of the verdict"
   :user-roles       #{:authority}
   :parameters       [id]
   :input-validators [(partial action/non-blank-parameters [:id])]
   :pre-checks       [suomifi-enabled
                      (some-pre-check (verdict-exists :published?) backing-system-verdict)]}
  [{:keys [application] :as command}]
  (ok :recipient (extract-recipient-data application)))

(defquery fetch-recipient-suomifi-data
  {:description      "Fetch user data from suomi.fi backend.
                      PersonId can be either the social security number of the recipient or the Lupapiste user id."
   :user-roles       #{:authority}
   :parameters       [id personId]
   :input-validators [(partial action/non-blank-parameters [:id :personId])]
   :pre-checks       [suomifi-enabled]}
  [{:keys [application organization user] :as command}]
  (let [org                           (ensure-org-present application organization)
        person-id                     (usr/coerce-person-id personId)
        {:keys [status] :as response} (when person-id (suomifi/query-person-data-from-suomifi person-id user org))]
    (if (and (some? response) (= 200 (:status response)))
      (ok :allows-suomifi-messages (suomifi/allows-suomifi-messages? (:body response)))
      (do
        (error "HaeAsiakkaita request failed. The response was: " response)
        (fail (get {404 :error.suomifi-messages.server-not-responding
                    502 :error.suomifi-messages.server-not-responding}
                   status :error.suomifi-messages.sending-failed))))))
