(ns lupapalvelu.company
  (:require [taoensso.timbre :as timbre :refer [trace debug info infof warn warnf error fatal]]
            [monger.operators :refer :all]
            [schema.core :as sc]
            [sade.util :refer [min-length-string max-length-string account-type? fn-> fn->>] :as util]
            [sade.env :as env]
            [sade.strings :as ss]
            [sade.core :refer :all]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.action :refer [update-application application->command]]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.token :as token]
            [lupapalvelu.ttl :as ttl]
            [lupapalvelu.notifications :as notif]
            [lupapalvelu.user :as user]
            [lupapalvelu.user :as u]
            [lupapalvelu.document.schemas :as schema])
  (:import [java.util Date]))

;;
;; Company schema:
;;

(def account-types [{:name :account5
                     :limit 5}
                    {:name :account15
                     :limit 15}
                    {:name :account30
                     :limit 30}])

(defn user-limit-for-account-type [account-name]
  (let [account-type (some #(if (= (:name %) account-name) %) account-types)]
    (:limit account-type)))

(def- max-64-or-nil (sc/either (max-length-string 64) (sc/pred nil?)))

(defn supported-invoice-operator? [op]
  (let [supported-ops (map :name schema/e-invoice-operators)]
    (some #(= op %) supported-ops)))

(def Company {:name                          (sc/both (min-length-string 1) (max-length-string 64))
              :y                             (sc/pred util/finnish-y? "Not valid Y code")
              :accountType                   (sc/pred account-type? "Not valid account type")
              :customAccountLimit            (sc/maybe sc/Int)
              (sc/optional-key :reference)   max-64-or-nil
              :address1                      max-64-or-nil
              :po                            max-64-or-nil
              :zip                           (sc/either (sc/pred util/finnish-zip? "Not a valid zip code")
                                                         (sc/pred ss/blank?))
              (sc/optional-key :country)     max-64-or-nil
              (sc/optional-key :ovt)         (sc/either (sc/pred util/finnish-ovt? "Not a valid OVT code")
                                                        (sc/pred ss/blank?))
              (sc/optional-key :pop)         (sc/either (sc/pred supported-invoice-operator? "Not a supported invoice operator")
                                                        (sc/pred ss/blank?))
              (sc/optional-key :document)    sc/Str
              (sc/optional-key :process-id)  sc/Str
              (sc/optional-key :created)     sc/Int
              })

(def company-skeleton ; required keys
  {:name nil
   :y    nil
   :accountType nil
   :customAccountLimit nil
   :address1 nil
   :po nil
   :zip nil})

(def company-updateable-keys (->> (keys Company)
                                  (map (fn [k] (if (sc/optional-key? k) (:k k) k)))
                                  (remove #{:y})))

(defn- fail-property! [prop]
  (case prop
    :address1 (fail! :error.illegal-address)
    :y        (fail! :error.illegal-y-tunnus)
    :zip      (fail! :error.illegal-zip)
    :ovt      (fail! :error.illegal-ovt-tunnus)
    :pop      (fail! "error.illegal-value:select")
    :accountType (fail! :error.illegal-company-account)
    (fail! :error.unknown)))

(defn validate! [company]
  (when-let [errors (sc/check Company company)]
    (fail-property! (first (keys errors)))))

(defn custom-account? [{type :accountType}]
  (= :custom (keyword type)))

;;
;; API:
;;

(defn create-company
  "Create a new company. Returns the created company data. Throws if given company data is not valid."
  [company]
  (validate! company)
  (let [company (assoc company
                       :id      (mongo/create-id)
                       :created (now))]
    (mongo/insert :companies company)
    company))

(defn find-company
  "Returns company mathing the provided query, or nil"
  [q]
  (when (seq q)
    (some->> q (mongo/with-_id) (mongo/select-one :companies))))

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
  (mongo/select :companies {} [:name :y :address1 :zip :po :accountType :customAccountLimit] (array-map :name 1)))

(defn find-company-users [company-id]
  (u/get-users {:company.id company-id}))

(defn company-users-count [company-id]
  (mongo/count :users {:company.id company-id}))

(defn- map-token-to-user-invitation [token]
  (-> {}
      (assoc :firstName (get-in token [:data :user :firstName]))
      (assoc :lastName  (get-in token [:data :user :lastName]))
      (assoc :email     (get-in token [:data :user :email]))
      (assoc :role      (get-in token [:data :role]))
      (assoc :tokenId (:id token))
      (assoc :expires (:expires token))))

(defn find-user-invitations [company-id]
  (let [tokens (mongo/select :token {$and [{:token-type {$in ["invite-company-user" "new-company-user"]}}
                                           {:data.company.id company-id}
                                           {:expires {$gt (now)}}
                                           {:used nil}]}
                             {:data 1 :tokenId 1}
                             {:data.user.firstName 1})
        data (map map-token-to-user-invitation tokens)]
    data))

(defn find-company-admins [company-id]
  (u/get-users {:company.id company-id, :company.role "admin"}))

(defn ensure-custom-limit [company-id {account-type :accountType custom-limit :customAccountLimit :as data}]
  "Checks that custom account's customAccountLimit is set and allowed. Nullifies customAcconutLimit with normal accounts."
  (if (= :custom (keyword account-type))
   (if-not (ss/blank? custom-limit)
     (if (<= (company-users-count company-id) (util/->int custom-limit))
       (assoc data :customAccountLimit (util/->int custom-limit))
       (fail! :company.limit-too-small))
     (fail! :company.missing.custom-limit))
   (assoc data :customAccountLimit nil)))

(defn account-type-changing-with-custom? [{old-type :accountType} {new-type :accountType}]
  (and (not= old-type new-type)
       (or (= :custom (keyword old-type))
           (= :custom (keyword new-type)))))

(defn update-company!
  "Update company. Throws if company is not found, or if provided updates would make company invalid.
   Returns the updated company."
  [id updates admin?]
  (if (some #{:id :y} (keys updates)) (fail! :bad-request))
  (let [company (dissoc (find-company-by-id! id) :id)
        updated (->> (merge company updates)
                  (ensure-custom-limit id))
        old-limit (user-limit-for-account-type (keyword (:accountType company)))
        limit     (user-limit-for-account-type (keyword (:accountType updated)))]
    (validate! updated)
    (when (and (not admin?)
               (account-type-changing-with-custom? company updates)) ; only admins are allowed to change account type to/from 'custom'
      (fail! :error.unauthorized))
    (when (and (not admin?) (< limit old-limit))
      (fail! :company.account-type-not-downgradable))
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

(notif/defemail :new-company-admin-user {:subject-key   "new-company-admin-user.subject"
                                         :recipients-fn notif/from-user
                                         :model-fn      (fn [model _ __] model)})

(notif/defemail :new-company-user {:subject-key   "new-company-user.subject"
                                   :recipients-fn notif/from-user
                                   :model-fn      (fn [model _ __] model)})

(defn add-user-after-company-creation! [user company role]
  (let [user (update-in user [:email] u/canonize-email)
        token-id (token/make-token :new-company-user nil {:user user, :company company, :role role} :auto-consume false)]
    (notif/notify! :new-company-admin-user {:user       user
                                            :company    company
                                            :link-fi    (str (env/value :host) "/app/fi/welcome#!/new-company-user/" token-id)
                                            :link-sv    (str (env/value :host) "/app/sv/welcome#!/new-company-user/" token-id)})
    token-id))

(defn add-user! [user company role]
  (let [user (update-in user [:email] u/canonize-email)
        token-id (token/make-token :new-company-user nil {:user user, :company company, :role role} :auto-consume false)]
    (notif/notify! :new-company-user {:user       user
                                      :company    company
                                      :link-fi    (str (env/value :host) "/app/fi/welcome#!/new-company-user/" token-id)
                                      :link-sv    (str (env/value :host) "/app/sv/welcome#!/new-company-user/" token-id)})
    token-id))

(defmethod token/handle-token :new-company-user [{{:keys [user company role]} :data} {password :password}]
  (find-company-by-id! (:id company)) ; make sure company still exists
  (user/create-new-user nil {:email       (:email user)
                             :username    (:email user)
                             :firstName   (:firstName user)
                             :lastName    (:lastName user)
                             :company     {:id (:id company) :role role}
                             :personId    (:personId user)
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
                                      :recipients-fn notif/from-user
                                      :model-fn      (fn [model _ __] model)})

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
                 :username  (-> company :y ss/trim ss/lower-case) ; usernames are always in lower case
                 :firstName (:name company)
                 :lastName  "")))

(defn company-invite [caller application company-id]
  {:pre [(map? caller) (map? application) (string? company-id)]}
  (let [company   (find-company! {:id company-id})
        auth      (assoc (company->auth company)
                    :id "" ; prevents access to application before accepting invite
                    :role ""
                    :invite {:user {:id company-id}})
        admins    (find-company-admins company-id)
        application-id (:id application)
        token-id  (token/make-token :accept-company-invitation nil {:caller caller, :company-id company-id, :application-id application-id} :auto-consume false :ttl ttl/company-invite-ttl)
        update-count (update-application
                       (application->command application)
                       {:auth {$not {$elemMatch {:invite.user.id company-id}}}}
                       {$push {:auth auth}, $set  {:modified (now)}}
                       true)]
    (when (pos? update-count)
      (notif/notify! :accept-company-invitation {:admins     admins
                                                 :caller     caller
                                                 :link-fi    (str (env/value :host) "/app/fi/welcome#!/accept-company-invitation/" token-id)
                                                 :link-sv    (str (env/value :host) "/app/sv/welcome#!/accept-company-invitation/" token-id)})
      token-id)))

(notif/defemail :accept-company-invitation {:subject-key   "accept-company-invitation.subject"
                                            :recipients-fn :admins
                                            :model-fn      (fn [model _ __] model)})

(defmethod token/handle-token :accept-company-invitation [{{:keys [company-id application-id]} :data} _]
  (infof "company %s accepted application %s" company-id application-id)
  (when-let [application (domain/get-application-no-access-checking application-id)]
    (update-application
      (application->command application)
      {:auth {$elemMatch {:invite.user.id company-id}}}
      {$set  {:auth.$  (-> (find-company! {:id company-id}) (company->auth) (assoc :inviteAccepted (now)))}})
    (ok)))
