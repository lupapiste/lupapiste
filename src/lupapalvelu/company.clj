(ns lupapalvelu.company
  (:require [clojure.data :refer [diff]]
            [clojure.set :as set]
            [lupapalvelu.action :refer [application->command update-application]]
            [lupapalvelu.authorization :as auth]
            [lupapalvelu.document.document :as doc]
            [lupapalvelu.document.schemas :as schema]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.logging :as log :refer [debug error info infof]]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.notifications :as notif]
            [lupapalvelu.password-reset :as pw-reset]
            [lupapalvelu.security :as security]
            [lupapalvelu.system.util-components :as components]
            [lupapalvelu.token :as token]
            [lupapalvelu.ttl :as ttl]
            [lupapalvelu.user :as usr]
            [monger.operators :refer :all]
            [sade.core :refer :all]
            [sade.env :as env]
            [sade.schemas :as ssc]
            [sade.strings :as ss]
            [sade.util :as util]
            [schema.core :as sc]))

;;
;; Company schema:
;;

(def account-types [{:name  :account5
                     :limit 5
                     :price {:monthly 79
                             :yearly  (* 12 75)}}
                    {:name  :account15
                     :limit 15
                     :price {:monthly 99
                             :yearly  (* 12 94)}}
                    {:name  :account30
                     :limit 30
                     :price {:monthly 120
                             :yearly  (* 12 114)}}])

(def billing-types (->> account-types
                        (mapcat (comp keys :price))         ; all keys under :price
                        (distinct)))

(defn user-limit-for-account-type [account-name]
  (let [account-type (some #(when (= (:name %) account-name) %) account-types)]
    (:limit account-type)))

(def Company {(sc/optional-key :id)               sc/Str
              :name                               (ssc/min-max-length-string 1 255)
              :y                                  ssc/FinnishY
              :accountType                        (apply sc/enum "custom" (map (comp name :name) account-types))
              :billingType                        (apply sc/enum (map name billing-types))
              :customAccountLimit                 (sc/maybe sc/Int)
              (sc/optional-key :reference)        (sc/maybe ssc/default-max-length-string)
              :address1                           (sc/maybe ssc/default-max-length-string)
              :po                                 (sc/maybe ssc/default-max-length-string)
              :zip                                (sc/if ss/blank? ssc/BlankStr ssc/Zipcode)
              (sc/optional-key :country)          (sc/maybe ssc/default-max-length-string)
              (sc/optional-key :ovt)              (sc/if ss/blank? ssc/BlankStr ssc/FinnishOVTid)
              (sc/optional-key :netbill)          (sc/maybe sc/Str)
              (sc/optional-key :pop)              (sc/maybe (apply sc/enum "" (map :name schema/e-invoice-operators)))
              (sc/optional-key :document)         sc/Str
              (sc/optional-key :process-id)       sc/Str
              (sc/optional-key :created)          ssc/Timestamp
              (sc/optional-key :campaign)         sc/Str ; Deprecated as of LPK-7485
              (sc/optional-key :locked)           ssc/Timestamp
              (sc/optional-key :tags)             [{:id ssc/ObjectIdStr :label sc/Str}]
              (sc/optional-key :contactAddress)   (sc/maybe ssc/default-max-length-string)
              (sc/optional-key :contactPo)        (sc/maybe ssc/default-max-length-string)
              (sc/optional-key :contactZip)       (sc/if ss/blank? ssc/BlankStr ssc/Zipcode)
              (sc/optional-key :contactCountry)   (sc/maybe ssc/default-max-length-string)
              ;; Can the company user restrict other auths submission rights.
              (sc/optional-key :submitRestrictor) sc/Bool
              (sc/optional-key :invitationDenied) sc/Bool})

(def company-skeleton ; required keys
  {:name               nil
   :y                  nil
   :accountType        nil
   :customAccountLimit nil
   :address1           nil
   :po                 nil
   :zip                nil})

(def company-updateable-keys (->> (keys Company)
                                  (map (fn [k] (if (sc/optional-key? k) (:k k) k)))
                                  (remove #{:y})))

(defn- fail-property! [prop]
  (case prop
    :address1    (fail! :error.illegal-address)
    :y           (fail! :error.illegal-y-tunnus)
    :zip         (fail! :error.illegal-zip)
    :ovt         (fail! :error.illegal-ovt-tunnus)
    :pop         (fail! "error.illegal-value:select")
    :accountType (fail! :error.illegal-company-account)
    :tags        (fail! :error.illegal-tags)
    (fail! :error.unknown)))

(def company-checker (sc/checker Company))

(defn validate! [company]
  (when-let [errors (company-checker company)]
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

(def ^:private company-subscribers-id "company-notification-subscribers")

(defn- sanitize-email
  [email]
  (some-> email ss/canonize-email ss/blank-as-nil))

(defn company-subscribers-emails
  "Collection of emails registered as subscribers to company updates."
  []
  (:emails (mongo/by-id :admin-config company-subscribers-id)))

(defn add-company-subscriber!
  "Register a new email to the company update subscribers."
  [email]
  (when-let [email (sanitize-email email)]
    (mongo/update-by-id :admin-config
                        company-subscribers-id
                        {$push {:emails email}}
                        :upsert true)))

(defn remove-company-subscriber!
  "Unregister an existing email from the company update subscribers."
  [email]
  (when-let [email (sanitize-email email)]
    (mongo/update-by-id :admin-config
                        company-subscribers-id
                        {$pull {:emails email}})))

(defn validate-existing-subscriber
  "Return a pre-checker for confirming if an email is (or is not) already registered."
  [should-be-existing?]
  (fn [command]
    (when-let [email (some-> command :data :email sanitize-email)]
      (let [subscribers (company-subscribers-emails)]
        (when-not (= should-be-existing? (contains? (set subscribers) email))
          (fail (if should-be-existing?
                  :error.not-yet-subscribed
                  :error.already-subscribed)))))))

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
   (find-company q nil))
  ([query projection]
   (when-let [query (some->> query not-empty mongo/with-_id)]
     (apply mongo/select-one :companies
            (cond-> [query]
              projection (conj projection))))))

(defn find-company!
  "Returns company matching the provided query. Throws if not found."
  ([q]
   (find-company! q nil))
  ([query projection]
   (or (find-company query projection) (fail! :company.not-found))))

(defn find-company-by-id
  "Returns company by given ID, or nil"
  [id]
  (some->> id ss/blank-as-nil (mongo/by-id :companies)))

(defn find-company-by-id!
  "Returns company by given ID, throws if not found"
  [id]
  (or (find-company-by-id id) (fail! :company.not-found)))

(defn find-companies
  "Makes a company query. If query is not given, returns every
  company."
  ([] (find-companies {}))
  ([query]
   (find-companies query [:name :y :address1 :zip :po :accountType :billingType
                          :contactAddress :contactZip :contactPo
                          :customAccountLimit :created :pop :ovt
                          :netbill :reference :document :locked :process-id
                          :submitRestrictor]))
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
        data   (map map-token-to-user-invitation tokens)]
    data))

(defn find-company-admins [company-id]
  (usr/get-users {:company.id company-id, :company.role "admin"}))

(defn search-sanity-check
  "Convenience handler for the most common errors:
  :already-invited, :not-found, :already-in-company,
  and :not-applicant. Returns either the error keyword or the user
  map.
    caller: Calling user
    email: Email to be searched"
  [caller email]
  (let [user   (usr/find-user {:email email})
        tokens (find-user-invitations (-> caller :company :id))]
    (cond
      (some #(= email (:email %)) tokens) :already-invited
      (nil? user)                         :not-found
      (get-in user [:company :id])        :already-in-company
      (not (or (usr/applicant? user)
               (usr/authority? user)
               (usr/dummy? user)))        :not-applicant
      :else                               user)))

(defn search-result
  "After `search-sanity-check` either wraps the error or calls fun for the user.
    caller: Calling user
    email: Email to be searched
    fun: Function that takes (found) user as argument."
  [caller email fun]
  (let [check (search-sanity-check caller email)]
    (if (keyword? check)
      (ok :result check)
      (fun check))))

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
  (and new-type
       (not= old-type new-type)
       (or (= :custom (keyword old-type))
           (= :custom (keyword new-type)))))

(defn changing-billing-type?
  "True if company :billingType was changed"
  [{old :billingType} {new :billingType}]
  (boolean (when new (not= old new))))

(defn changing-company-business-id?
  "True if the business id of the company was changed"
  [{old :y} {new :y}]
  (boolean (when new (not= old new))))

(notif/defemail :company-change-notification
  {:recipients-fn :subscribers
   :subject-key   "company-change-notification.subject"
   :model-fn      (fn [cmd _conf _recipient] cmd)})

(defn- notify-subscribers!
  [system user company changes]
  (when (env/feature? :company-change-notifications)
    (try
      (if-let [subscribers (seq (company-subscribers-emails))]
        (notif/notify! :company-change-notification {:system      system
                                                     :caller      (select-keys user [:email])
                                                     :company     (select-keys company [:name :y])
                                                     :subscribers (->> subscribers
                                                                       (mapv (fn [email] {:email    email
                                                                                          :language "fi"})))
                                                     :changes     changes})
        (info "No company subscribers"))
      (catch Exception e
        (error "Failed to notify subscribers" e)))))

(def ^:private ignored-change-keys #{:tags})

(defn- changes [old new]
  (let [[in-old in-new _] (diff old new)
        keyset            (set/difference (set/union (-> in-old keys set)
                                                     (-> in-new keys set))
                                          ignored-change-keys)
        fmt               #(str \" (log/sanitize 200 %) \")]
    (not-empty
      (reduce
        (fn [acc k]
          (conj acc (str (name k) " from " (-> in-old k fmt) " to " (-> in-new k fmt))))
        []
        (sort keyset)))))

(defn update-company!
  "Update company. Throws if company is not found, or if provided updates would make company invalid.
   Returns the updated company."
  [system id updates caller]
  {:pre [(map? system)]}
  (when (some #{:id} (keys updates))
    (fail! :bad-request))
  (let [company                   (dissoc (find-company-by-id! id) :id)
        updated                   (->> (merge company updates)
                                       (ensure-custom-limit id))
        company-name              (:name updates)
        company-business-id       (:y updates)
        company-updates-for-users (util/assoc-when {}
                                                   :companyName company-name
                                                   :companyId company-business-id)
        old-limit                 (user-limit-for-account-type (keyword (:accountType company)))
        limit                     (user-limit-for-account-type (keyword (:accountType updated)))]
    (validate! updated)
    (when (and (not (usr/admin? caller))
               (or (account-type-changing-with-custom? company updates)
                   ;; only admins are allowed to change account type to/from 'custom'
                   (changing-billing-type? company updates)))
      (fail! :error.unauthorized))
    (when (and (not (usr/admin? caller))
               (contains? updates :submitRestrictor))
      (fail! :error.unauthorized))
    (when (and (not (usr/admin? caller))
               (changing-company-business-id? company updates))
      (fail! :error.unauthorized))
    (when (and (not (usr/admin? caller)) (not (custom-account? company)) (< limit old-limit))
      (fail! :company.account-type-not-downgradable))
    (mongo/update :companies {:_id id} updated)
    (when-let [changes (changes company updated)]
      ;; Log the changes for later review
      (info "company-update for" (:name company) "/" (:y company) "- changes were:"
            (ss/join ", " changes))
      (notify-subscribers! system caller updated changes))
    (when (seq company-updates-for-users)
      (let [user-count (mongo/update-by-query :users
                                              {:company.id id}
                                              {$set company-updates-for-users})]
        (info "company details of " user-count " users were updated")))
    updated))

(defn company-user-edit-allowed
  "Pre-check enforcing that the caller has sufficient credentials to
  edit company member."
  [{:keys [user data]}]
  (when-let [target-user-id (:user-id data)]
    (let [{:keys [role id]} (:company user)]
      (when-not (or (= (:role user) "admin")
                    (and (= role "admin")
                         id
                         (mongo/any? :users {:_id        target-user-id
                                             :company.id id})))
        unauthorized))))

(defn locked? [{locked :locked} timestamp]
  (boolean (and locked (> timestamp locked 0))))

(defn- locked-check
  "Pre-checker helper that will `fail` with `:company.not-found` if `company-id` with
  locking status `locked?` is not found. Note: fails if the company exists but with
  different locking status. Nil on success."
  [{:keys [created]} company-id locked?]
  (when-not (and created
                 (ss/not-blank? company-id)
                 (mongo/any? :companies
                             {:_id    (ss/trim company-id)
                              ;; Company is locked if 0 < locked < created
                              :locked (cond->> {$lt created $gt 0}
                                        (not locked?) (hash-map $not))}))
    (fail :company.not-found)))

(defn company-not-locked
  "Returns pre-checker that fails! for a locked or non-existing company. `param-key` is the
  data parameter key (e.g., `:company`, `:company-id`)."
  [param-key]
  (fn [command]
    (when-let [company-id (some-> command :data param-key)]
      (locked-check command company-id false))))

(defn user-company-is-locked
  "Pre-check that fails the user-associated company _is not_ locked (or the user does not
  have a company)."
  [{user :user :as command}]
  (locked-check command (some-> user :company :id) true))

(defn user-company-not-locked
  "Pre-check that fails the user-associated company _is_ locked (or the user does not have a
  company)."
  [{user :user :as command}]
  (locked-check command (some-> user :company :id) false))

(defn check-company-authorized [{{auth :auth} :application {{company-id :id} :company} :user}]
  (when auth
    (let [company-auths (filter (comp #{company-id} :id) auth)]
      (when (or (empty? company-auths)
                (some :invite company-auths))
        (fail :error.company-has-not-accepted-invite)))))

(defn authorized-to-apply-submit-restriction-to-other-auths [{company :company}]
  (when-not (:submitRestrictor (and company @company))
    (fail :error.not-allowed-to-apply-submit-restriction)))

;;
;; Deletion
;;

(defn- register-link [system lang]
  (str (components/system->host system) "/app/" lang "/applicant#!/register"))

(notif/defemail :company-user-delete                        ; Only sent to dummy users, who are not yet authed via identification service
  {:subject-key   "company-user-delete.subject"
   :recipients-fn (fn [{:keys [result]}] [(:user result)])
   :model-fn      (fn [{:keys [system] :as model} _ _]
                    (assoc model :company (get-in model [:result :company])
                           :register-link (fn [lang] (register-link system lang))))
   :pred-fn       (fn [{:keys [result]}] (usr/dummy? (:user result)))})

(def- verified-user-removal   {$unset {:company 1 :defaultFilter 1 :companyName 1 :companyId 1}})
(def- unverified-user-removal {$set   {:role "dummy" :enabled false}
                               $unset {:company 1 :private 1 :defaultFilter 1 :companyName 1 :companyId 1}})

(defn- remove-invalid-user-from-document
  "Cleans application from user_id references that are invalid (if user does not have auth anymore)"
  [app]
  (when-let [updates (not-empty (doc/generate-remove-invalid-user-from-docs-updates app))]
    (mongo/update-by-id :applications (:id app) {$unset updates})))

(defn delete-user!
  "Removes user from company. If user is not yet identificated, role is set to dummy.
   Returns updated user for further processing."
  [user]
  (let [updated-user        (->> (if (usr/verified-person-id? user)
                            verified-user-removal
                            unverified-user-removal)
                                 (mongo/update-one-and-return :users {:_id (:id user)})
                          (mongo/with-id)
                          (usr/non-private))
        apps-with-documents (mongo/select :applications {:auth.id (:id user)} [:documents :auth])]
    (run! remove-invalid-user-from-document apps-with-documents)
    updated-user))

(defn delete-every-user! [company-id]
  (->> (usr/find-users {:company.id company-id})
       (run! delete-user!)))

(defn update-user! [user-id role submit]
  (mongo/update-by-id :users user-id {$set {:company.role   role
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

(defn add-user-after-company-creation! [system user company role submit]
  (let [user     (update-in user [:email] ss/canonize-email)
        token-id (token/make-token :new-company-user nil {:user user, :company company, :role role, :submit submit} :auto-consume false)
        host     (components/system->host system)]
    (notif/notify! :new-company-admin-user {:system  system
                                            :user    user
                                            :company company
                                            :link    #(str host "/app/" (name %) "/welcome#!/new-company-user/" token-id)})
    token-id))

(defn add-user!
  "Adds creates :new-company-user token and sends email to new user. Returns token-id."
  [system user company role submit]
  (let [user     (update-in user [:email] ss/canonize-email)
        token-id (token/make-token :new-company-user nil {:user    user
                                                          :company company
                                                          :role    role
                                                          :submit  submit} :auto-consume false)
        host     (components/system->host system)]
    (notif/notify! :new-company-user {:user          user
                                      :system        system
                                      :company       company
                                      :company-admin (str (get-in company [:admin :firstName]) " " (get-in company [:admin :lastName]))
                                      :link          #(str host "/app/" (name %) "/welcome#!/new-company-user/" token-id)})
    token-id))

(defmethod token/handle-token :new-company-user [{{:keys [user company role submit]} :data :as token-data} {password :password} system]
  (find-company-by-id! (:id company)) ; make sure company still exists
  (when (and password (not (security/valid-password-length? password)))
    (fail! :error.password.minlength))
  (when (and password (not (security/valid-password-score? password user)))
    (fail! :error.password.minscore))
  (if-let [existing-user (usr/get-user-by-email (:email user))]
    ;; LPK-3759:
    (token/handle-token (-> token-data
                            (assoc :token-type :invite-company-user)
                            (assoc-in [:data :user] existing-user))
                        {:ok true}
                        system)
    (usr/create-new-user nil (util/assoc-when
                               {:email     (:email user)
                                :username  (:email user)
                                :firstName (:firstName user)
                                :lastName  (:lastName user)
                                :company   {:id (:id company) :role role :submit (if (nil? submit) true submit)}
                                :password  password
                                :role      "applicant"
                                :architect true
                                :enabled   true}
                               :language    (:language user)
                               :personId    (:personId user)
                               :personIdSource (:personIdSource user))))
  (ok))

(defn invite-user! [system caller user-email company-id role submit firstname lastname]
  (let [company  (find-company! {:id company-id})
        user     (usr/get-user-by-email user-email)
        user     (if (usr/dummy? user)
                   (do                                     ; update firstname and lastname from frontend for dummy user
                     (usr/update-user-by-email user-email {:firstName firstname :lastName lastname})
                     (usr/get-user-by-email user-email))
                   user)
        token-id (token/make-token :invite-company-user nil {:user    user
                                                             :company company
                                                             :role    role
                                                             :submit  submit} :auto-consume false)
        host     (components/system->host system)]
    (notif/notify! :invite-company-user {:system  system
                                         :user    user
                                         :company (assoc company :admin caller)
                                         :ok      #(str host "/app/" % "/welcome#!/invite-company-user/ok/" token-id)
                                         :cancel  #(str host "/app/" % "/welcome#!/invite-company-user/cancel/" token-id)})
    token-id))

(notif/defemail :invite-company-user {:subject-key   "invite-company-user.subject"
                                      :recipients-fn notif/from-user
                                      :model-fn      (fn [model _ __] model)})


;;
;; Link user to company:
;;

(defn link-user-to-company! [system user-id company-id role submit]
  (if-let [user (usr/get-user-by-id user-id)]
    (let [company             (mongo/select-one :companies {:_id company-id} {:y 1 :name 1})
          company-name        (get company :name)
          company-business-id (get company :y)
          updates             (merge {:company     {:id company-id :role role :submit submit}
                                      :companyName company-name
                                      :companyId   company-business-id}
                                     (when (usr/dummy? user) {:role :applicant})
                                     (when-not (:enabled user) {:enabled true}))]
      (mongo/update :users {:_id user-id} {$set updates})
      (when (usr/dummy? user)
        (pw-reset/reset-password system (assoc user :role "applicant"))))
    (fail! :error.user-not-found)))

(defmethod token/handle-token :invite-company-user [{{:keys [user company role submit]} :data} {accept :ok} system]
  (infof "user %s (%s) %s invitation to company %s (%s)"
         (:username user)
         (:id user)
         (if accept "ACCEPTED" "CANCELLED")
         (:name company)
         (:id company))
  (if accept
    (link-user-to-company! system (:id user) (:id company) role submit))
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

(defn company-invite [system caller application company-id]
  {:pre [(map? caller) (map? application) (string? company-id)]}
  (let [company      (find-company! {:id company-id})
        auth         (assoc (company->auth company :writer)
                            :inviter (usr/summary caller))
        admins       (find-company-admins company-id)
        update-count (update-application
                       (application->command application)
                       {:auth {$not {$elemMatch {:invite.user.id company-id}}}}
                       {$push {:auth auth}, $set {:modified (now)}}
                       :return-count? true)]
    (when (pos? update-count)
      (notif/notify! :accept-company-invitation {:admins      admins
                                                 :inviter     caller
                                                 :company     company
                                                 :application application
                                                 :system      system}))))

(notif/defemail :accept-company-invitation {:subject-key   "accept-company-invitation.subject"
                                            :recipients-fn :admins
                                            :model-fn      (fn [model _ recipient]
                                                             (merge (notif/create-app-model model nil recipient)
                                                                    model))})

(defmethod auth/approve-invite-auth :company
  [{invite :invite :as auth} {{company-id :id} :company} accepted-ts]
  (when invite
    (some-> (find-company! {:id company-id})
            company->auth
            (util/assoc-when-pred util/not-empty-or-nil?
                                  :role (:role invite)
                                  :inviter (:inviter auth)
                                  :inviteAccepted accepted-ts))))

(defmethod token/handle-token :accept-company-invitation [{{:keys [company-id application-id]} :data} _ _system]
  (infof "company %s accepted application %s" company-id application-id)
  (when-let [application (domain/get-application-no-access-checking application-id)]
    (let [auth (-> (auth/get-auth application company-id)
                   (auth/approve-invite-auth {:company {:id company-id}} (now)))]
      (update-application
        (application->command application)
        {:auth {$elemMatch {:invite.user.id company-id}}}
        {$set {:auth.$ auth}}))
    (ok)))

(defn cannot-submit
  "Pre-check that succeeds only if a company user does not have submit
  rights"
  [{{company :company} :user}]
  (when-not (and company  (not (:submit company)))
    (fail :error.authorized)))

(defn company-denies-invitations? [application company-id]
  (and (ss/not-blank? company-id)
       (empty? (auth/get-auth application company-id))
       (let [{:keys [invitationDenied locked]} (find-company-by-id company-id)]
         (and (nil? locked)
              invitationDenied))))
