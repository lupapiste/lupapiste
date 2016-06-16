(ns lupapalvelu.company
  (:require [taoensso.timbre :as timbre :refer [trace debug info infof warn warnf error fatal]]
            [monger.operators :refer :all]
            [schema.core :as sc]
            [sade.util :refer [fn-> fn->>] :as util]
            [sade.schemas :as ssc]
            [sade.env :as env]
            [sade.strings :as ss]
            [sade.core :refer :all]
            [sade.validators :as v]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.action :refer [update-application application->command]]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.token :as token]
            [lupapalvelu.ttl :as ttl]
            [lupapalvelu.notifications :as notif]
            [lupapalvelu.user :as usr]
            [lupapalvelu.password-reset :as pw-reset]
            [lupapalvelu.document.schemas :as schema]
            [lupapalvelu.authorization :as auth])
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

(def Company {:name                          (ssc/min-max-length-string 1 64)
              :y                             ssc/FinnishY
              :accountType                   (apply sc/enum "custom" (map (comp name :name) account-types))
              :customAccountLimit            (sc/maybe sc/Int)
              (sc/optional-key :reference)   (sc/maybe (ssc/max-length-string 64))
              :address1                      (sc/maybe (ssc/max-length-string 64))
              :po                            (sc/maybe (ssc/max-length-string 64))
              :zip                           (sc/if ss/blank? ssc/BlankStr ssc/Zipcode)
              (sc/optional-key :country)     (sc/maybe (ssc/max-length-string 64))
              (sc/optional-key :ovt)         (sc/if ss/blank? ssc/BlankStr ssc/FinnishOVTid)
              (sc/optional-key :netbill)     (sc/maybe sc/Str)
              (sc/optional-key :pop)         (sc/maybe (apply sc/enum "" (map :name schema/e-invoice-operators)))
              (sc/optional-key :document)    sc/Str
              (sc/optional-key :process-id)  sc/Str
              (sc/optional-key :created)     ssc/Timestamp
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

(defn find-companies
  "Returns all data off all companies"
  []
  (mongo/select :companies {} [:name :y :address1 :zip :po :accountType :customAccountLimit :created :pop :ovt :reference :document] (array-map :name 1)))

(defn find-company-users [company-id]
  (usr/get-users {:company.id company-id} {:lastName 1}))

(defn company-users-count [company-id]
  (mongo/count :users {:company.id company-id}))

(defn- map-token-to-user-invitation [token]
  (-> {}
      (assoc :firstName (get-in token [:data :user :firstName]))
      (assoc :lastName  (get-in token [:data :user :lastName]))
      (assoc :email     (get-in token [:data :user :email]))
      (assoc :role      (get-in token [:data :role]))
      (assoc :submit    (get-in token [:data :submit]))
      (assoc :tokenId (:id token))
      (assoc :expires (:expires token))))

(defn find-user-invitations [company-id]
  (let [tokens (mongo/select :token
                             {$and [{:token-type {$in ["invite-company-user" "new-company-user"]}}
                                    {:data.company.id company-id}
                                    {:expires {$gt (now)}}
                                    {:used nil}]}
                             {:data 1 :tokenId 1}
                             {:data.user.lastName 1})
        data (map map-token-to-user-invitation tokens)]
    data))

(defn find-company-admins [company-id]
  (usr/get-users {:company.id company-id, :company.role "admin"}))

(defn search-result
  "Convenience handler for the most common search results. The
  following results are automatically
  handled/responded: :already-invited, :not-found
  and :already-in-company. For other results (user is found but not
  related to the company), the given fun is called.
    caller: Calling user
    email: Email to be searched
    fun: Function that takes (found) user as argument."
  [caller email fun]
  (let [user (usr/find-user {:email email})
        tokens (find-user-invitations (-> caller :company :id))]
    (cond
      (some #(= email (:email %)) tokens)
      (ok :result :already-invited)

      (nil? user)
      (ok :result :not-found)

      (get-in user [:company :id])
      (ok :result :already-in-company)

      :else
      (fun user))))

(defn ensure-custom-limit
  "Checks that custom account's customAccountLimit is set and allowed. Nullifies customAcconutLimit with normal accounts."
  [company-id {account-type :accountType custom-limit :customAccountLimit :as data}]
  (if (= :custom (keyword account-type))
   (if custom-limit
     (if (<= (company-users-count company-id) custom-limit)
       (assoc data :customAccountLimit custom-limit)
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
  [id updates caller]
  (if (some #{:id :y} (keys updates)) (fail! :bad-request))
  (let [company (dissoc (find-company-by-id! id) :id)
        updated (->> (merge company updates)
                  (ensure-custom-limit id))
        old-limit (user-limit-for-account-type (keyword (:accountType company)))
        limit     (user-limit-for-account-type (keyword (:accountType updated)))]
    (validate! updated)
    (when (and (not (usr/admin? caller))
               (account-type-changing-with-custom? company updates)) ; only admins are allowed to change account type to/from 'custom'
      (fail! :error.unauthorized))
    (when (and (not (usr/admin? caller)) (not (custom-account? company)) (< limit old-limit))
      (fail! :company.account-type-not-downgradable))
    (mongo/update :companies {:_id id} updated)
    updated))

(defn company-user-edit-allowed
  "Pre-check enforcing that the caller has sufficient credentials to
  edit company member."
  [{caller :user :as cmd} app]
  (when-let [target-user (some-> cmd :data :user-id usr/get-user-by-id)]
    (when-not (or (= (:role caller) "admin")
                  (and (= (get-in caller [:company :role])
                          "admin")
                       (= (get-in caller [:company :id])
                          (get-in target-user [:company :id]))))
      unauthorized)))

(defn delete-user!
  [user-id]
  (mongo/update-by-id :users user-id {$set {:enabled false}, $unset {:company 1}}))

(defn update-user! [user-id role submit]
  (mongo/update-by-id :users user-id {$set {:company.role role
                                            :company.submit submit}}))
;;
;; Add/invite new company user:
;;

(notif/defemail :new-company-admin-user {:subject-key   "new-company-admin-user.subject"
                                         :recipients-fn notif/from-user
                                         :model-fn      (fn [model _ __] model)})

(notif/defemail :new-company-user {:subject-key   "new-company-user.subject"
                                   :recipients-fn notif/from-user
                                   :model-fn      (fn [model _ __] model)})

(defn add-user-after-company-creation! [user company role submit]
  (let [user (update-in user [:email] usr/canonize-email)
        token-id (token/make-token :new-company-user nil {:user user, :company company, :role role, :submit submit} :auto-consume false)]
    (notif/notify! :new-company-admin-user {:user       user
                                            :company    company
                                            :link-fi    (str (env/value :host) "/app/fi/welcome#!/new-company-user/" token-id)
                                            :link-sv    (str (env/value :host) "/app/sv/welcome#!/new-company-user/" token-id)})
    token-id))

(defn add-user! [user company role submit]
  (let [user (update-in user [:email] usr/canonize-email)
        token-id (token/make-token :new-company-user nil {:user user
                                                          :company company
                                                          :role role
                                                          :submit submit} :auto-consume false)]
    (notif/notify! :new-company-user {:user       user
                                      :company    company
                                      :link-fi    (str (env/value :host) "/app/fi/welcome#!/new-company-user/" token-id)
                                      :link-sv    (str (env/value :host) "/app/sv/welcome#!/new-company-user/" token-id)})
    token-id))

(defmethod token/handle-token :new-company-user [{{:keys [user company role submit]} :data} {password :password}]
  (find-company-by-id! (:id company)) ; make sure company still exists
  (usr/create-new-user nil {:email       (:email user)
                            :username    (:email user)
                            :firstName   (:firstName user)
                            :lastName    (:lastName user)
                            :company     {:id (:id company) :role role :submit (if (nil? submit) true submit)}
                            :personId    (:personId user)
                            :password    password
                            :role        :applicant
                            :architect   true
                            :enabled     true}
    :send-email false)
  (ok))

(defn invite-user! [user-email company-id role submit]
  (let [company   (find-company! {:id company-id})
        user      (usr/get-user-by-email user-email)
        token-id  (token/make-token :invite-company-user nil {:user user
                                                              :company company
                                                              :role role
                                                              :submit submit} :auto-consume false)]
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


;;
;; Link user to company:
;;

(defn link-user-to-company! [user-id company-id role submit]
  (if-let [user (usr/get-user-by-id user-id)]
    (let [updates (merge {:company {:id company-id, :role role :submit submit}}
                    (when (usr/dummy? user) {:role :applicant})
                    (when-not (:enabled user) {:enabled true}))]
      (mongo/update :users {:_id user-id} {$set updates})
      (when (usr/dummy? user)
        (pw-reset/reset-password (assoc user :role "applicant"))))
    (fail! :error.user-not-found)))

(defmethod token/handle-token :invite-company-user [{{:keys [user company role submit]} :data} {accept :ok}]
  (infof "user %s (%s) %s invitation to company %s (%s)"
         (:username user)
         (:id user)
         (if accept "ACCEPTED" "CANCELLED")
         (:name company)
         (:id company))
  (if accept
    (link-user-to-company! (:id user) (:id company) role submit))
  (ok))

(defn company->auth [company]
  (some-> company
          (select-keys [:id :name :y])
          (assoc :role      "writer"
                 :type      "company"
                 :username  (-> company :y ss/trim ss/lower-case) ; usernames are always in lower case
                 :firstName (:name company)
                 :lastName  "")))

(defn company-invitation-token [caller company-id application-id]
  (token/make-token
    :accept-company-invitation
    nil
    {:caller caller, :company-id company-id, :application-id application-id}
    :auto-consume false
    :ttl ttl/company-invite-ttl))

(defn company-not-already-invited
  "Pre-checker for company-invite command. Fails if the company is
  already authorized to the application."
  [{{:keys [company-id]} :data} {:keys [auth]}]
  ;; We identify companies by their y (Y-tunnus), since the id
  ;; is not available in the auth for not yet accepted invites.
  ;; See company-invite function below.
  (when-let [y (some-> company-id find-company-by-id :y)]
    (when (some #(= y (:y %)) auth)
      (fail :company.already-invited))))


(defn company-invite [caller application company-id]
  {:pre [(map? caller) (map? application) (string? company-id)]}
  (let [company   (find-company! {:id company-id})
        auth      (assoc (company->auth company)
                    :id      "" ; prevents access to application before accepting invite
                    :role    "reader"
                    :inviter (usr/summary caller)
                    :invite  {:user {:id company-id}})
        admins    (find-company-admins company-id)
        application-id (:id application)
        token-id  (company-invitation-token caller company-id application-id)
        update-count (update-application
                       (application->command application)
                       {:auth {$not {$elemMatch {:invite.user.id company-id}}}}
                       {$push {:auth auth}, $set  {:modified (now)}}
                       true)]
    (when (pos? update-count)
      (notif/notify! :accept-company-invitation {:admins     admins
                                                 :caller     caller
                                                 :company    company
                                                 :link-fi    (str (env/value :host) "/app/fi/welcome#!/accept-company-invitation/" token-id)
                                                 :link-sv    (str (env/value :host) "/app/sv/welcome#!/accept-company-invitation/" token-id)})
      token-id)))

(notif/defemail :accept-company-invitation {:subject-key   "accept-company-invitation.subject"
                                            :recipients-fn :admins
                                            :model-fn      (fn [model _ __] model)})

(defmethod token/handle-token :accept-company-invitation [{{:keys [company-id application-id]} :data} _]
  (infof "company %s accepted application %s" company-id application-id)
  (when-let [application (domain/get-application-no-access-checking application-id)]
    (let [company           (find-company! {:id company-id})
          {:keys [inviter]} (some #(when (= (:y company) (:y %)) %) (:auth application))]
      (update-application
       (application->command application)
       {:auth {$elemMatch {:invite.user.id company-id}}}
       {$set  {:auth.$ (-> company company->auth (util/assoc-when :inviter inviter :inviteAccepted (now)))}}))
    (ok)))

(defn cannot-submit
  "Pre-check that succeeds only if a company user does not have submit
  rights"
  [{{company :company} :user} application]
  (when-not (and company  (not (:submit company)))
    (fail :error.authorized)))
