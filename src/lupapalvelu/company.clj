(ns lupapalvelu.company
  (:require [taoensso.timbre :as timbre :refer [trace debug info infof warn warnf error fatal]]
            [monger.operators :refer :all]
            [monger.query :as q]
            [schema.core :as sc]
            [sade.util :refer [min-length-string max-length-string y? ovt? fn-> fn->>]]
            [sade.env :as env]
            [lupapalvelu.core :refer [fail! fail]]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.action :refer [update-application application->command]]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.core :refer [now ok fail!]]
            [lupapalvelu.token :as token]
            [lupapalvelu.notifications :as notif]
            [lupapalvelu.user-api :as uapi]
            [lupapalvelu.user :as u])
  (:import [java.util Date]))

;;
;; Company schema:
;;

(def ^:private max-64-or-nil (sc/either (max-length-string 64) (sc/pred nil?)))

(def Company {:name                          (sc/both (min-length-string 1) (max-length-string 64))
              :y                             (sc/pred y? "Not valid Y code")
              (sc/optional-key :reference)   max-64-or-nil
              (sc/optional-key :address1)    max-64-or-nil
              (sc/optional-key :address2)    max-64-or-nil
              (sc/optional-key :po)          max-64-or-nil
              (sc/optional-key :zip)         max-64-or-nil
              (sc/optional-key :country)     max-64-or-nil
              (sc/optional-key :ovt)         (sc/pred ovt? "Not valid OVT code")
              (sc/optional-key :pop)         (sc/pred ovt? "Not valid OVT code")
              (sc/optional-key :process-id)  sc/Str
              (sc/optional-key :created)     sc/Int})

(def company-updateable-keys (->> (keys Company)
                                  (map (fn [k] (if (sc/optional-key? k) (:k k) k)))
                                  (remove #{:y})))

;;
;; API:
;;

(defn create-company
  "Create a new company. Returns the created company data. Throws if given company data is not valid."
  [company]
  (sc/validate Company company)
  (let [company (assoc company
                       :id      (mongo/create-id)
                       :created (now))]
    (mongo/insert :companies company)
    company))

(defn find-company
  "Returns company mathing the provided query, or nil"
  [q]
  (some->> q (mongo/with-_id) (mongo/select-one :companies))) ; mongo/select-one return ANY FUCKING doc if query is nil. ANY...FUCKING....DOC...!!!!

(defn find-company!
  "Returns company mathing the provided query. Throws if not found."
  [q]
  (or (find-company q) (fail! :company.not-found)))

(defn find-company-by-id
  "Returns company by given ID, or nil"
  [id]
  (find-company {:id id}))

(defn find-company-by-id!
  "Returns company by given ID, throws if not found"
  [id]
  (or (find-company-by-id id) (fail! :company.not-found)))

(defn find-companies []
  (q/with-collection "companies"
    (q/sort {:name 1})
    (q/fields [:name :address1 :po])))

(defn find-company-users [company-id]
  (u/get-users {:company.id company-id}))

(defn find-company-admins [company-id]
  (u/get-users {:company.id company-id, :company.role "admin"}))

(defn update-company!
  "Update company. Throws if company is not found, or if provided updates would make company invalid.
   Retuens the updated company."
  [id updates]
  (if (some #{:id :y} (keys updates)) (fail! :bad-request))
  (let [updated (merge (dissoc (find-company-by-id! id) :id) updates)]
    (sc/validate Company updated)
    (mongo/update :companies {:_id id} updated)
    updated))

(defn update-user!
  "Update company user."
  [user-id op value]
  (condp = op
    :admin  (mongo/update-by-id :users user-id {$set {:company.role (if value "admin" "user")}})
    :enabled (mongo/update-by-id :users user-id {$set {:enabled (if value true false)}})
    :delete (mongo/update-by-id :users user-id {$set {:enabled false :company nil}})
    (fail! :bad-request)))

;;
;; Add/invite new company user:
;;

(defn add-user! [user company role]
  (let [token-id (token/make-token :new-company-user nil {:user user, :company company, :role role} :auto-consume false)]
    (notif/notify! :new-company-user {:user       user
                                      :company    company
                                      :link-fi    (str (env/value :host) "/app/fi/welcome#!/new-company-user/" token-id)
                                      :link-sv    (str (env/value :host) "/app/sv/welcome#!/new-company-user/" token-id)})
    token-id))

(notif/defemail :new-company-user {:subject-key   "new-company-user.subject"
                                   :recipients-fn (fn-> :user :email vector)
                                   :model-fn      (fn [model _] model)})

(defmethod token/handle-token :new-company-user [{{:keys [user company role]} :data} {password :password}]
  (find-company-by-id! (:id company)) ; make sure company still exists
  (uapi/create-new-user nil
                     {:email       (:email user)
                      :username    (:email user)
                      :firstName   (:firstName user)
                      :lastName    (:lastName user)
                      :company     {:id     (:id company)
                                    :role   role}
                      :password    password
                      :role        :applicant
                      :architect   true
                      :enabled     true}
                     :send-email false)
  (ok))

(defn invite-user! [user-email company-id]
  (let [company   (find-company! {:id company-id})
        user      (u/get-user-by-email user-email)
        token-id  (token/make-token :invite-company-user nil {:user user, :company company, :role :user} :auto-consume false)]
    (notif/notify! :invite-company-user {:user       user
                                         :company    company
                                         :ok-fi    (str (env/value :host) "/app/fi/welcome#!/invite-company-user/ok/" token-id)
                                         :ok-sv    (str (env/value :host) "/app/sv/welcome#!/invite-company-user/ok/" token-id)
                                         :cancel-fi    (str (env/value :host) "/app/fi/welcome#!/invite-company-user/cancel/" token-id)
                                         :cancel-sv    (str (env/value :host) "/app/sv/welcome#!/invite-company-user/cancel/" token-id)})
    token-id))

(notif/defemail :invite-company-user {:subject-key   "invite-company-user.subject"
                                      :recipients-fn (fn-> :user :email vector)
                                      :model-fn      (fn [model _] model)})

(defmethod token/handle-token :invite-company-user [{{:keys [user company role]} :data} {accept :ok}]
  (infof "user %s (%s) %s invitation to company %s (%s)"
         (:username user)
         (:id user)
         (if accept "ACCEPTED" "CANCELLED")
         (:name company)
         (:id company))
  (if accept
    (u/link-user-to-company! (:id user) (:id company) role))
  (ok))

(defn company->auth [company]
  (some-> company
          (select-keys [:id :name :y])
          (assoc :role      "writer"
                 :type      "company"
                 :username  (:y company)
                 :firstName (:name company)
                 :lastName  "")))

(defn company-invite [caller application-id company-id]
  (let [admins    (find-company-admins company-id)
        token-id  (token/make-token :accept-company-invitation nil {:caller caller, :company-id company-id, :application-id application-id} :auto-consume false)]
    (notif/notify! :accept-company-invitation {:admins     admins
                                               :caller     caller
                                               :link-fi    (str (env/value :host) "/app/fi/welcome#!/accept-company-invitation/" token-id)
                                               :link-sv    (str (env/value :host) "/app/sv/welcome#!/accept-company-invitation/" token-id)})
    token-id))

(notif/defemail :accept-company-invitation {:subject-key   "accept-company-invitation.subject"
                                            :recipients-fn (fn->> :admins (map :email))
                                            :model-fn      (fn [model _] model)})

(defmethod token/handle-token :accept-company-invitation [{{:keys [company-id application-id]} :data} _]
  (infof "comnpany %s accepted application %s" company-id application-id)
  (if-let [application (domain/get-application-no-access-checking application-id)]
    (do
      (update-application (application->command application)
       {$push {:auth (-> (find-company! {:id company-id}) (company->auth))}})
      (ok))
    (fail :error.unknown)))
