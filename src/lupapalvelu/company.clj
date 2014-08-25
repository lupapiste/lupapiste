(ns lupapalvelu.company
  (:require [monger.operators :refer :all]
            [schema.core :as sc]
            [sade.util :refer [max-length max-length-string y? fn->]]
            [lupapalvelu.core :refer [fail!]]
            [sade.env :as env]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.core :refer [now ok fail!]]
            [lupapalvelu.token :as token]
            [lupapalvelu.notifications :as notif]
            [lupapalvelu.user-api :as u])
  (:import [java.util Date]))

;;
;; Company schema:
;;

(def Company {:id       #"^\w{24}$"
              :name     (max-length-string 64)
              :y        (sc/pred y? "Not valid Y number")
              :created  sc/Int
              (sc/optional-key :process-id) sc/Str})

(def company-updateable-keys [:name])

;;
;; API:
;;

(defn create-company
  "Create a new company. Returns the created company data. Throws if given company data is not valid."
  [company]
  (let [company (assoc company
                       :id      (mongo/create-id)
                       :created (now))]
    (sc/validate Company company)
    (mongo/insert :companies company)
    company))

(defn find-company
  "Returns company mathing the provided query, or nil"
  [q]
  (mongo/select-one :companies (mongo/with-_id q)))

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
  (or (find-company-by-id! id) (fail! :company.not-found)))

(defn update-company!
  "Update company. Throws if comoany is not found, or if updates would make company data invalid.
   Retuens the updated company data."
  [id updates]
  (let [q       {:id id}
        company (find-company! q)
        updated (merge company (select-keys updates company-updateable-keys))]
    (sc/validate Company updated)
    (mongo/update :companies q updated)
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
;; Add new company user:
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
  (u/create-new-user nil
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
