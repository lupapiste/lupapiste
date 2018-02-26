(ns lupapalvelu.company
  (:require [taoensso.timbre :as timbre :refer [trace debug info infof warn warnf error fatal]]
            [clojure.data :refer [diff]]
            [clojure.set :as set]
            [monger.operators :refer :all]
            [schema.core :as sc]
            [sade.util :refer [fn-> fn->>] :as util]
            [sade.schemas :as ssc]
            [sade.env :as env]
            [sade.strings :as ss]
            [sade.core :refer :all]
            [sade.validators :as v]
            [lupapalvelu.action :refer [update-application application->command]]
            [lupapalvelu.authorization :as auth]
            [lupapalvelu.document.schemas :as schema]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.logging :as logging]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.notifications :as notif]
            [lupapalvelu.password-reset :as pw-reset]
            [lupapalvelu.token :as token]
            [lupapalvelu.ttl :as ttl]
            [lupapalvelu.user :as usr])
  (:import [java.util Date]))

;;
;; Company schema:
;;

(def account-types [{:name :account5
                     :limit 5
                     :price {:monthly 69
                             :yearly 780}}
                    {:name :account15
                     :limit 15
                     :price {:monthly 89
                             :yearly 1008}}
                    {:name :account30
                     :limit 30
                     :price {:monthly 109
                             :yearly 1236}}])

(def billing-types (->> account-types
                        (mapcat (comp keys :price))         ; all keys under :price
                        (distinct)))

(defn user-limit-for-account-type [account-name]
  (let [account-type (some #(if (= (:name %) account-name) %) account-types)]
    (:limit account-type)))

(def Company {(sc/optional-key :id)                sc/Str
              :name                                (ssc/min-max-length-string 1 64)
              :y                                   ssc/FinnishY
              :accountType                         (apply sc/enum "custom" (map (comp name :name) account-types))
              :billingType                         (apply sc/enum (map name billing-types))
              :customAccountLimit                  (sc/maybe sc/Int)
              (sc/optional-key :reference)         (sc/maybe (ssc/max-length-string 64))
              :address1                            (sc/maybe (ssc/max-length-string 64))
              :po                                  (sc/maybe (ssc/max-length-string 64))
              :zip                                 (sc/if ss/blank? ssc/BlankStr ssc/Zipcode)
              (sc/optional-key :country)           (sc/maybe (ssc/max-length-string 64))
              (sc/optional-key :ovt)               (sc/if ss/blank? ssc/BlankStr ssc/FinnishOVTid)
              (sc/optional-key :netbill)           (sc/maybe sc/Str)
              (sc/optional-key :pop)               (sc/maybe (apply sc/enum "" (map :name schema/e-invoice-operators)))
              (sc/optional-key :document)          sc/Str
              (sc/optional-key :process-id)        sc/Str
              (sc/optional-key :created)           ssc/Timestamp
              (sc/optional-key :campaign)          sc/Str
              (sc/optional-key :locked)            ssc/Timestamp
              (sc/optional-key :tags)              [{:id ssc/ObjectIdStr :label sc/Str}]
              (sc/optional-key :contactAddress)    (sc/maybe (ssc/max-length-string 64))
              (sc/optional-key :contactPo)         (sc/maybe (ssc/max-length-string 64))
              (sc/optional-key :contactZip)        (sc/if ss/blank? ssc/BlankStr ssc/Zipcode)
              (sc/optional-key :contactCountry)    (sc/maybe (ssc/max-length-string 64))
              (sc/optional-key :invitationDenied)  sc/Bool})

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
    :tags     (fail! :error.illegal-tags)
    (fail! :error.unknown)))

(defn validate! [company]
  (when-let [errors (sc/check Company company)]
    (debug "Company schema validation: " errors)
    (fail-property! (first (keys errors)))))

(defn custom-account? [{type :accountType}]
  (= :custom (keyword type)))

;;
;; Pre-checkers
;;

(defn validate-has-company-role
  "Role :any matches any role excluding nil. Thus, even with :any the
  user must have some company role."
  [role]
  (fn [{user :user}]
    (let [com-role (-> user :company :role)]
      (when-not (or (util/=as-kw role com-role)
                    (and (util/=as-kw role :any)
                         (ss/not-blank? com-role)))
        unauthorized))))

(defn validate-is-admin [{user :user}]
  (when-not (usr/admin? user)
    unauthorized))

(defn validate-belongs-to-company [{:keys [user data]}]
  (when-not (= (-> user :company :id)
               (:company data))
    unauthorized))

(defn validate-company-is-authorized [{{{company-id :id} :company} :user {auth :auth} :application}]
  (when-not (util/find-by-id company-id auth)
    unauthorized))

(defn validate-tag-ids [{company :company {tags :tags} :data}]
  (some->> (remove (set (map :id (:tags @company))) tags)
           not-empty
           (fail :error.unknown-tag :tags)))

(defn validate-is-financial-authority []
  (fn [{user :user}]
    (when-not (usr/financial-authority? user)
      unauthorized)))

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
  "Returns company matching the provided query, or nil"
  ([q]
   (when (seq q)
     (some->> q (mongo/with-_id) (mongo/select-one :companies))))
  ([query projection]
   (when (seq query)
     (mongo/select-one :companies (mongo/with-_id query) projection))))

(defn find-company!
  "Returns company matching the provided query. Throws if not found."
  ([q]
   (or (find-company q) (fail! :company.not-found)))
  ([query projection]
   (or (find-company query projection) (fail! :company.not-found))))

(defn find-company-by-id
  "Returns company by given ID, or nil"
  [id]
  (find-company {:id id}))

(defn find-company-by-id!
  "Returns company by given ID, throws if not found"
  [id]
  (or (find-company-by-id id) (fail! :company.not-found)))

(defn find-companies
  "Makes a company query. If query is not given, returns every
  company."
  ([] (find-companies {}))
  ([query]
    (find-companies query [:name :y :address1 :zip :po :accountType
                           :contactAddress :contactZip :contactPo
                           :customAccountLimit :created :pop :ovt
                           :netbill :reference :document :locked]))
  ([query projection]
   (mongo/select :companies query projection (array-map :name 1))))

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

      (usr/financial-authority? user)
      (ok :result :financial-authority)

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

(defn changing-billing-type?
  "True if company :billingType was changed"
  [{old :billingType} {new :billingType}]
  (boolean (when new (not= old new))))

(defn- changes [old new]
  (let [[in-old in-new _] (diff old new)
        keyset (set/union (-> in-old keys set) (-> in-new keys set))
        fmt #(str \" (logging/sanitize 200 %) \")]
    (reduce #(conj %1 (str (name %2) " from " (-> in-old %2 fmt) " to " (-> in-new %2 fmt))) [] (sort keyset))))

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
               (or (account-type-changing-with-custom? company updates)
                   ; only admins are allowed to change account type to/from 'custom'
                   (changing-billing-type? company updates)))
      (fail! :error.unauthorized))
    (when (and (not (usr/admin? caller)) (not (custom-account? company)) (< limit old-limit))
      (fail! :company.account-type-not-downgradable))
    (mongo/update :companies {:_id id} updated)
    ; Log the changes for later review
    (info "company-update for" (:name company) "/" (:y company) "- changes were:" (ss/join ", " (changes company updated)))
    updated))

(defn company-user-edit-allowed
  "Pre-check enforcing that the caller has sufficient credentials to
  edit company member."
  [{caller :user :as cmd}]
  (when-let [target-user (some-> cmd :data :user-id usr/get-user-by-id)]
    (when-not (or (= (:role caller) "admin")
                  (and (= (get-in caller [:company :role])
                          "admin")
                       (= (get-in caller [:company :id])
                          (get-in target-user [:company :id]))))
      unauthorized)))

(defn locked? [{locked :locked} timestamp]
  (boolean (and locked (> timestamp locked 0))))

(defn company-not-locked
  "Pre-check that fails for a locked company."
  [{{company :company} :data created :created}]
  (when (locked? (find-company-by-id! company) created)
    (fail :error.company-locked)))

(defn user-company-is-locked
  "Pre-check that fails the user-associated company _is not_ locked."
  [{user :user created :created}]
  (when-not (locked? (find-company-by-id! (some-> user :company :id))
                     created)
    (fail :error.company-not-locked)))

;;
;; Deletion
;;

(defn- register-link [lang]
  (str (env/value :host) "/app/" lang "/applicant#!/register"))

(notif/defemail :company-user-delete                        ; Only sent to dummy users, who are not yet authed via identification service
                {:subject-key   "company-user-delete.subject"
                 :recipients-fn (fn [{:keys [result]}] [(:user result)])
                 :model-fn      (fn [model _ _] (assoc model :company (get-in model [:result :company])
                                                             :register-link (fn [lang] (register-link lang))))
                 :pred-fn       (fn [{:keys [result]}] (usr/dummy? (:user result)))})

(def- verified-user-removal   {$unset {:company 1}})
(def- unverified-user-removal {$set   {:role "dummy" :enabled false}
                               $unset {:company 1, :private 1}})

(defn delete-user!
  "Removes user from company. If user is not yet identificated, role is set to dummy.
   Returns updated user for further processing."
  [user]
  (->> (if (usr/verified-person-id? user)
         verified-user-removal
         unverified-user-removal)
       (mongo/update-one-and-return :users {:_id (:id user)})
       (mongo/with-id)
       (usr/non-private)))

(defn delete-every-user! [company-id]
  (->> (usr/find-users {:company.id company-id})
       (run! delete-user!)))

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
  (let [user (update-in user [:email] ss/canonize-email)
        token-id (token/make-token :new-company-user nil {:user user, :company company, :role role, :submit submit} :auto-consume false)]
    (notif/notify! :new-company-admin-user {:user       user
                                            :company    company
                                            :link       #(str (env/value :host) "/app/" (name %) "/welcome#!/new-company-user/" token-id)})
    token-id))

(defn add-user!
  "Adds creates :new-company-user token and sends email to new user. Returns token-id."
  [user company role submit]
  (let [user (update-in user [:email] ss/canonize-email)
        token-id (token/make-token :new-company-user nil {:user user
                                                          :company company
                                                          :role role
                                                          :submit submit} :auto-consume false)]
    (notif/notify! :new-company-user {:user       user
                                      :company    company
                                      :company-admin  (str (get-in company [:admin :firstName]) " " (get-in company [:admin :lastName]))
                                      :link    #(str (env/value :host) "/app/" (name %) "/welcome#!/new-company-user/" token-id)})
    token-id))

(defmethod token/handle-token :new-company-user [{{:keys [user company role submit]} :data} {password :password}]
  (find-company-by-id! (:id company)) ; make sure company still exists
  (usr/create-new-user nil (util/assoc-when
                             {:email       (:email user)
                              :username    (:email user)
                              :firstName   (:firstName user)
                              :lastName    (:lastName user)
                              :company     {:id (:id company) :role role :submit (if (nil? submit) true submit)}
                              :password    password
                              :role        :applicant
                              :architect   true
                              :enabled     true}
                             :language    (:language user)
                             :personId    (:personId user)
                             :personIdSource (:personIdSource user))
    :send-email false)
  (ok))

(defn invite-user! [caller user-email company-id role submit firstname lastname]
  (let [company   (find-company! {:id company-id})
        user      (usr/get-user-by-email user-email)
        user      (if (usr/dummy? user)
                    (do                                     ; update firstname and lastname from frontend for dummy user
                      (usr/update-user-by-email user-email {:firstName firstname :lastName lastname})
                      (usr/get-user-by-email user-email))
                    user)
        token-id  (token/make-token :invite-company-user nil {:user user
                                                              :company company
                                                              :role role
                                                              :submit submit} :auto-consume false)]
    (notif/notify! :invite-company-user {:user       user
                                         :company    (assoc company :admin caller)
                                         :ok         #(str (env/value :host) "/app/" % "/welcome#!/invite-company-user/ok/" token-id)
                                         :cancel     #(str (env/value :host) "/app/" % "/welcome#!/invite-company-user/cancel/" token-id)})
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

(defn company->auth
  "No auth if company is currently locked. The default role is
  writer. However, if invite-role is given, the resulting auth is in
  the invite format: only company-admin can read the application and
  accept the invitation."
  ([company invite-role]
   (when-not (locked? company (now))
     (when-let [auth (some-> company
                             (select-keys [:id :name :y])
                             (assoc :type      "company"
                                    ;; usernames are always in lower case
                                    :username  (-> company :y ss/trim ss/lower-case)
                                    :firstName (:name company)
                                    :lastName  ""))]
       (if invite-role
         (assoc auth
                :role "reader"
                :company-role :admin
                :invite {:user    {:id (:id company)}
                         :created (now)
                         :role    invite-role})
         (assoc auth :role "writer")))))
  ([company]
   (company->auth company nil)))

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
  [{{:keys [company-id]} :data {:keys [auth]} :application}]
  (when (some #(or (= company-id (:id %))
                   (= company-id (some-> % :invite :user :id))) auth)
    (fail :company.already-invited)))

(defn company-info
  "Map with :id, :name, :y, :address1, :zip and :po. Contact information
  overrides billing info."
  [company]
  (letfn [(address [c] (reduce-kv (fn [acc k v]
                                    (cond-> acc
                                      (ss/not-blank? v) (assoc k v)))
                                  {}
                                  (select-keys c [:address1 :zip :po])))]
    (merge (select-keys company [:id :name :y])
           (address company)
           (address (set/rename-keys company
                                     {:contactAddress :address1
                                      :contactZip     :zip
                                      :contactPo      :po})))))

(defn company-invite [caller application company-id]
  {:pre [(map? caller) (map? application) (string? company-id)]}
  (let [company        (find-company! {:id company-id})
        auth           (assoc (company->auth company :writer)
                              :inviter (usr/summary caller))
        admins         (find-company-admins company-id)
        update-count   (update-application
                        (application->command application)
                        {:auth {$not {$elemMatch {:invite.user.id company-id}}}}
                        {$push {:auth auth}, $set {:modified (now)}}
                        :return-count? true)]
    (when (pos? update-count)
      (notif/notify! :accept-company-invitation {:admins      admins
                                                 :inviter     caller
                                                 :company     company
                                                 :application application}))))

(notif/defemail :accept-company-invitation {:subject-key   "accept-company-invitation.subject"
                                            :recipients-fn :admins
                                            :model-fn      (fn [model _ recipient]
                                                             (merge (notif/create-app-model model nil recipient)
                                                                    model))})

(defmethod auth/approve-invite-auth :company
  [{invite :invite :as auth} {{company-id :id} :company :as user} accepted-ts]
  (when invite
    (some-> (find-company! {:id company-id})
            company->auth
            (util/assoc-when-pred util/not-empty-or-nil?
              :role (:role invite)
              :inviter (:inviter auth)
              :inviteAccepted accepted-ts))))

(defmethod token/handle-token :accept-company-invitation [{{:keys [company-id application-id]} :data} _]
  (infof "company %s accepted application %s" company-id application-id)
  (when-let [application (domain/get-application-no-access-checking application-id)]
    (let [auth (-> (auth/get-auth application company-id)
                   (auth/approve-invite-auth {:company {:id company-id}} (now)))]
      (update-application
       (application->command application)
       {:auth {$elemMatch {:invite.user.id company-id}}}
       {$set  {:auth.$ auth}}))
    (ok)))

(defn cannot-submit
  "Pre-check that succeeds only if a company user does not have submit
  rights"
  [{{company :company} :user}]
  (when-not (and company  (not (:submit company)))
    (fail :error.authorized)))

(defn company-denies-invitations? [application user]
  (let [user-company-id (get-in user [:company :id])
        company (find-company-by-id user-company-id)
        invites-denied (:invitationDenied company)]
    (and invites-denied (empty? (auth/get-auth application user-company-id)))))
