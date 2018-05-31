(ns lupapalvelu.action
  (:require [taoensso.timbre :as timbre :refer [trace tracef debug debugf info infof warn warnf error errorf fatal fatalf]]
            [clojure.set :as set]
            [slingshot.slingshot :refer [try+]]
            [monger.operators :refer [$set $push $pull $ne]]
            [schema.core :as sc]
            [sade.core :refer :all]
            [sade.env :as env]
            [sade.strings :as ss]
            [sade.util :as util]
            [sade.validators :as v]
            [lupapalvelu.authorization :as auth]
            [lupapalvelu.control-api :as control]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.integrations.state-change :as state-change]
            [lupapalvelu.logging :as log]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.notifications :as notifications]
            [lupapalvelu.organization :as org]
            [lupapalvelu.permissions :as permissions]
            [lupapalvelu.roles :as roles]
            [lupapalvelu.states :as states]
            [lupapalvelu.user :as usr]
            [sade.dns :as dns]))

;;
;; construct command, query and raw
;;

(def ^:dynamic *created-timestamp-for-test-actions* nil)

(defn action [name & {:keys [user type data] :or {user nil type :action data {}}}]
  {:action name
   :user user
   :type type
   :created (or *created-timestamp-for-test-actions* (now))
   :data data})


(defn make-command
  ([name data]      (make-command name nil data))
  ([name user data] (action name :user user :data data :type :command)))

(defn make-query  [name data] (action name :type :query :data data))
(defn make-raw    [name data] (action name :type :raw :data data))
(defn make-export [name data] (action name :type :export :data data))

;;
;; some utils
;;

(defn- and-2-pre-check [check1 check2]
  (fn [x]
    (let [result1 (check1 x)]
      (if (nil? result1)
        (check2 x)
        result1))))


(defn and-pre-check
  "Returns a pre-check that fails if any of the given pre-checks fail.
   The returned pre-check return the first failing pre-checks failure,
   or nil in case all succeed."
  [& pre-checks]
  (reduce and-2-pre-check
          (constantly nil)
          pre-checks))

(defn not-pre-check
  "Pre-check fails if given pre-check succeeds."
  [pre-check & fail-args]
  (let [fail-args (or fail-args
                      [:error.pre-check])]
    (fn [command]
      (when-not (pre-check command)
        (apply fail fail-args)))))

(defn some-pre-check
  "Return pre-check that fails if none of the given pre-checks succeeds.
  Pre-check returns nil if some succees, or result of the last error returning pre-check."
  [& pre-checks]
  (fn [command]
    (reduce (fn [res check]
              (if (nil? res)
                (reduced nil)
                (check command)))
            ((first pre-checks) command)
            (rest pre-checks))))

(defn email-validator
  "Reads email key from action parameters and checks that it is valid email address.
   Blank address passes the validation."
  ([command] (email-validator :email command))
  ([email-param-name command]
    (let [email (get-in command [:data email-param-name])]
      (when-not (dns/email-and-domain-valid? (ss/canonize-email email))
        (fail :error.email)))))

(defn validate-url [url]
  (when-not (v/http-url? url)
    (fail :error.invalid.url)))

(defn validate-optional-url [param command]
  (let [url (ss/trim (get-in command [:data param]))]
    (when-not (ss/blank? url)
      (validate-url url))))

(defn validate-optional-https-url [param command]
  (let [url (ss/trim (get-in command [:data param]))]
    (when-not (ss/blank? url)
      (or (validate-url url)
          (when-not (ss/starts-with-i url "https://")
            (fail :error.only-https-allowed))))))

;; Notificator

(defn notify
  [notification]
   (fn [command status]
     (notifications/notify! notification command status)))

(defn with-application [command function]
  (if-let [id (-> command :data :id)]
    (if-let [application (:application command)]
      (function application)
      (fail :error.application-not-found :id id))
    (fail :error.application-not-found :id nil)))

(defn- filter-params-of-command [params command filter-fn error-message & [extra-error-data]]
  {:pre [(or (nil? extra-error-data) (map? extra-error-data))]}
  (when-let [non-matching-params (seq (filter #(filter-fn (get-in command [:data %])) params))]
    (merge
      (fail error-message :parameters (vec non-matching-params))
      extra-error-data)))

(defn non-blank-parameters [params command]
  (filter-params-of-command params command #(or (not (string? %)) (ss/blank? %)) :error.missing-parameters))

(defn vector-parameters [params command]
  (filter-params-of-command params command (complement vector?) :error.non-vector-parameters))

(defn vector-parameters-with-non-blank-items [params command]
  (or
    (vector-parameters params command)
    (filter-params-of-command params command
      (partial some #(or (not (string? %)) (ss/blank? %)))
      :error.vector-parameters-with-blank-items )))

(defn vector-parameters-with-at-least-n-non-blank-items [n params command]
  (or
    (vector-parameters-with-non-blank-items params command)
    (filter-params-of-command params command
      #(> n (count %))
      :error.vector-parameters-with-items-missing-required-keys)))

(defn vector-parameters-with-map-items-with-required-keys [params required-keys command]
  (or
    (vector-parameters params command)
    (filter-params-of-command params command
      (partial some #(not (and (map? %) (util/every-key-in-map? % required-keys))))
      :error.vector-parameters-with-items-missing-required-keys
      {:required-keys required-keys})))

(defn vector-parameter-of [param pred command]
  (or
    (vector-parameters [param] command)
    (when-not (every? pred (get-in command [:data param]))
      (fail :error.unknown-type :parameters param))))

(defn optional-parameter-of [param pred command]
  (let [val (get-in command [:data param])]
    (when-not (or (nil? val) (pred val))
      (fail :error.unknown-type :parameters param))))

(defn boolean-parameters [params command]
  (filter-params-of-command params command #(not (instance? Boolean %)) :error.non-boolean-parameters))

(defn number-parameters [params command]
  (filter-params-of-command params command (complement number?) :error.illegal-number))

(defn positive-number-parameters [params command]
  (filter-params-of-command params command (complement pos?) :error.illegal-number))

(defn positive-integer-parameters [params command]
  (filter-params-of-command params command (complement #(and (pos? %) (integer? %))) :error.illegal-number))

(defn string-parameters [params command]
  (filter-params-of-command params command (complement string?) "error.illegal-value:not-a-string"))

(defn ascii-parameters [params command]
  (filter-params-of-command params command (complement ss/ascii?) "error.illegal-value:not-ascii-string"))

(defn numeric-parameters [params command]
  (filter-params-of-command params command (complement ss/numeric?) :error.illegal-number))

(defn supported-lang [param-key {data :data}]
  (when-not (i18n/supported-lang? (get data (keyword param-key)))
    (fail :error.unsupported-language)))

(defn select-parameters
  "Parameters are valid if each of them belong to the value-set"
  [params value-set command]
  (filter-params-of-command params command (complement value-set) "error.illegal-value:not-in-set"))

(defn property-id-parameters [params command]
  (when-let [invalid (seq (filter #(not (v/kiinteistotunnus? (get-in command [:data %]))) params))]
    (trace "invalid property id parameters:" (ss/join ", " invalid))
    (fail :error.invalid-property-id)))

(defn map-parameters [params command]
  (filter-params-of-command params command (complement map?) :error.unknown-type))

(defn map-parameters-with-required-keys [params required-keys command]
  (or
    (map-parameters params command)
    (filter-params-of-command params command
      #(not (util/every-key-in-map? % required-keys))
      :error.map-parameters-with-required-keys
      {:required-keys required-keys})))

(defn non-empty-map-parameters [params command]
  (or
    (map-parameters params command)
    (filter-params-of-command params
                              command
                              empty?
                              :error.empty-map-parameters)))

(defn parameters-matching-schema
  ([params schema command]
   (parameters-matching-schema params schema "error.illegal-value:schema-validation" command))
  ([params schema error-msg command]
   (filter-params-of-command params command
                             (partial sc/check schema)
                             error-msg)))

(defn valid-db-key
  "Input-validator to check that given parameter is valid db key"
  [param]
  (fn [{:keys [data]}]
    (when-not (mongo/valid-key? (get data param))
      (fail :error.invalid-db-key))))

(defn- localization? [mode maybe-localization]
  (and (map? maybe-localization)
       (case mode
         :partial    (every? i18n/languages (keys maybe-localization))
         :all        (= i18n/languages (set (keys maybe-localization)))
         :supported  (every? (set (keys maybe-localization)) i18n/languages))
       (every? string? (vals maybe-localization))))

(defn partial-localization-parameters
  "Validates that the given parameters are maps with supported
  language keywords (such as :fi) as keys and strings as values. Does
  not require that all supported languages are included."
  [params command]
  (filter-params-of-command params command
                            (complement (partial localization? :partial))
                            "error.illegal-localization-value"))

(defn localization-parameters
  "Validates that the given parameters are maps with supported
  language keywords (such as :fi) as keys and strings as values. All
  supported languages must be included."
  [params command]
  (filter-params-of-command params command
                            (complement (partial localization? :all))
                            "error.illegal-localization-value"))

(defn supported-localization-parameters
  "Validates that the given parameters are maps with supported
  language keywords (such as :fi) as keys and strings as values. All
  supported languages must be included, but can have extra languages."
  [params command]
  (filter-params-of-command params command
                            (complement (partial localization? :supported))
                            "error.illegal-localization-value"))

(defn update-application
  "Get current application from command (or fail) and run changes into it.
   Optionally returns the number of updated applications."
  ([command changes]
    (update-application command {} changes))
  ([command mongo-query changes & {:keys [return-count?]}]

    (when-let [new-state (get-in changes [$set :state])]
      (assert
        (or
          ; Require history entry
          (seq (get-in changes [$push :history]))
          ; Inforequest state chenges don't require logging
          (states/all-inforequest-states new-state)
          ; delete-verdict commands sets state back, but no logging is required (LPK-917)
          (seq (get-in changes [$pull :verdicts])))
        "event must be pushed to history array when state is set")
      (if (env/dev-mode?)
        (when-not (map? (:user command))
          (fatalf "no user defined in command '%s' for update-application call, new state was %s" (:action command) new-state))
        (when-not (map? (:user command))
          (warnf "no user defined in command '%s' for update-application call, new state was %s" (:action command) new-state))))

    (with-application command
      (fn [{:keys [id organization]}]
        (let [n (mongo/update-by-query :applications (assoc mongo-query :_id id) changes)]
          (when-let [new-state (get-in changes [$set :state])]
            (when (and (env/feature? :pate-json) organization (org/pate-org? organization))
              (util/future*
                (state-change/trigger-state-change command new-state))))
          (if return-count? n nil))))))

(defn application->command
  "Creates a command data structure that is suitable for update-application and with-application functions.
   Optionally takes also a user map."
  ([application]
   (application->command application nil))
  ([{id :id :as application} user]
   (util/assoc-when
     {:data {:id id}
      :application application}
     :user user)))

(defn without-system-keys [application]
  (into {} (filter (fn [[k v]] (not (.startsWith (name k) "_"))) application)))

;;
;; Actions
;;

(defonce actions (atom {}))

(defn get-actions [] @actions)

(defn serializable-actions []
  (into {} (for [[k v] (get-actions)]
             [k (-> v
                  (dissoc :handler :pre-checks :input-validators :on-success
                          :contexts :permissions)           ; FIXME serialize permissions
                  (assoc :name k))])))

;;
;; Command router
;;

; FIXME This is bit ugly hack to get needed rights for financial authority without adding
; it into all queries. Should be removed when user-roles handling is updated.
(defn- allowed-financial-authority [allowed-roles user-role parameters]
  (and (= user-role :financialAuthority)
       (allowed-roles :applicant)
       (contains? (set (map keyword parameters)) :id)))

(defn missing-fields [{data :data} {parameters :parameters}]
  (map name (set/difference (set parameters) (set (keys data)))))

(defn- has-required-user-role [command {user-roles :user-roles parameters :parameters :as meta-data}]
  (let [allowed-roles (or user-roles #{})
        user-role (-> command :user :role keyword)]
    (or (nil? user-roles) (allowed-roles :anonymous) (allowed-roles user-role) (allowed-financial-authority allowed-roles user-role parameters))))

(defn meta-data [{command :action}]
  ((get-actions) (keyword command)))

(defn check-lockdown [command]
  (let [{:keys [type allowed-in-lockdown]} (meta-data command)]
    (when (and (control/lockdown?) (= :command type) (not allowed-in-lockdown))
      (fail :error.service-lockdown))))

(defn missing-command [command]
  (when-not (meta-data command)
    (let [{:keys [action web]} command
          {:keys [user-agent client-ip]} web]
      (errorf "action '%s' not found. User agent '%s' from %s"
             (log/sanitize 50 action) (log/sanitize 100 user-agent) client-ip)
      (fail :error.invalid-command))))

(defn missing-feature [command]
  (when-let [feature (:feature (meta-data command))]
    (when-not (env/feature? feature)
      (fail :error.missing-feature))))

(defn invalid-type [{type :type :as command}]
  (when (and type (not= type (:type (meta-data command))))
    (info "invalid type:" (name type))
    (fail :error.invalid-type)))

(defn missing-roles [command]
  (when-not (has-required-user-role command (meta-data command))
    (tracef "command '%s' is unauthorized for role '%s'" (:action command) (-> command :user :role))
    unauthorized))

(defn- impersonation [command]
  (when (and (let [md (meta-data command)]
               (case (:type md)
                 :command true
                 :raw     (= :post (get-in command [:web :method]))
                 false))
             (get-in command [:user :impersonating]))
    unauthorized))

(defn disallow-impersonation [command]
  (when (get-in command [:user :impersonating]) unauthorized))

(defn missing-parameters [command]
  (when-let [missing (seq (missing-fields command (meta-data command)))]
    (info "missing parameters:" (ss/join ", " missing))
    (fail :error.missing-parameters :parameters (vec missing))))

(defn input-validators-fail [command]
  (when-let [validators (:input-validators (meta-data command))]
    (when (seq validators)
      (reduce #(or %1 (%2 command)) nil validators))))

(defn invalid-state-in-application [{{role :role} :user :as command} {state :state}]
  (when-let [valid-states (util/pcond-> (:states (meta-data command))
                                        map? (get (keyword role)))]
    (when-not (valid-states (keyword state))
      (fail :error.command-illegal-state :state state))))

(defn pre-checks-fail [command]
  {:post [(or (nil? %) (contains? % :ok))]}
  (when-let [pre-checks (:pre-checks (meta-data command))]
    (reduce #(or %1 (%2 command)) nil pre-checks)))

(defn masked [command]
  (letfn [(strip-field [command field]
            (if (get-in command [:data field])
              (assoc-in command [:data field] "*****")
              command))]
    (reduce strip-field command [:password :newPassword :oldPassword])))

(defn get-meta [name]
  ; Using wrapper function to enable mock actions it test
  (get (get-actions) (keyword name)))

(defn executed
  ([command] (executed (:action command) command))
  ([name command]
    (let [meta-data (get-meta name)]
      (or
        (if-let [handler (:handler meta-data)]
          (let [result (handler command)
                masked-command (assoc (masked command) :ns (:ns meta-data))]
            (if (or (= :raw (:type command)) (nil? result) (ok? result))
              (log/log-event :info masked-command)
              (log/log-event :warning masked-command))
            result)
          (infof "no handler for action '%s'" name))
        (ok)))))

(def authorize-validators [check-lockdown
                           missing-feature
                           missing-roles
                           impersonation])

(def execute-validators (conj authorize-validators
                          invalid-type
                          missing-parameters
                          input-validators-fail))

(defn access-denied-by-insufficient-permissions [{user-permissions :permissions :as command}]
  (let [permissions (permissions/get-required-permissions (meta-data command) command)]
    (when-not (set/subset? (:required permissions) user-permissions)
      unauthorized)))

(defn requires-application? [{data :data}]
  (contains? data :id))

(defn- get-application
  "if :id parameter is present read application from command
   (pre-loaded) or load application for user."
  [{{id :id} :data user :user application :application}]
  (and id
       user
       (or application
           (domain/get-application-as id user :include-canceled-apps? true))))

(defn- user-authz? [command-meta-data application user]
  (let [allowed-roles (get command-meta-data :user-authz-roles #{})]
    (auth/user-authz? allowed-roles application user)))

(defn- organization-authz? [command-meta-data application user]
  (let [allowed-roles (get command-meta-data :org-authz-roles #{})]
    (auth/has-organization-authz-roles? allowed-roles (:organization application) user)))

(defn- company-authz? [command-meta-data application user]
  (-> (get command-meta-data :user-authz-roles #{})
      (auth/company-authz? application user)))

(defn user-is-allowed-to-access?
  [{user :user :as command} application]
  (let [meta-data (meta-data command)]
    (or
      (user-authz? meta-data application user)
      (organization-authz? meta-data application user)
      (company-authz? meta-data application user))))

(defn- user-is-not-allowed-to-access?
  "Current user must have correct role in application.auth, work in
  the organization or company that has been invited"
  [command application]
  (when-not (user-is-allowed-to-access? command application)
    unauthorized))

(defn- not-authorized-to-application [{:keys [application] :as command}]
  (when (-> command :data :id)
    (if-not application
      (fail :error.application-not-accessible)
      (or
        (invalid-state-in-application command application)
        (user-is-not-allowed-to-access? command application)))))

(defn- update-user-application-role [{org-authz :orgAuthz role :role :as user} {app-org :organization :as application}]
  (assoc user :role (cond
                      (nil? app-org) role
                      (contains? org-authz (keyword app-org)) role
                      :else "applicant")))

(defn response? [r]
  (and (map? r) (:status r)))

(defn get-post-fns [{ok :ok} {:keys [on-complete on-success on-fail]}]
  (letfn [(->vec [v]
            (cond
              (nil? v)         nil
              (sequential? v)  v
              :else            [v]))]
    (concat (->vec on-complete) (->vec (if ok on-success on-fail)))))

(defn invoke-post-fns! [fns command status]
  (doseq [f fns]
    (try
      (f command status)
      (catch Throwable e
        (error "post fn fail after command " (:action command) ": " e)))))

(defn- enrich-default-permissions [command]
  (->> (set/union (permissions/get-global-permissions command)
                  (permissions/get-application-permissions command)
                  (permissions/get-organization-permissions command)
                  (permissions/get-company-permissions command))
       (assoc command :permissions)))

(defn- enrich-action-contexts [command]
  (reduce (fn [cmd ctx-fn] (ctx-fn cmd)) command (:contexts (meta-data command))))

(defn- run [command validators execute?]
  (try+
    (or
      (some #(% command) validators)
      (let [application (get-application command)
            ^{:doc "Organization as delay"}
            organization (when application
                           (delay (org/get-organization (:organization application))))
            ^{:doc "Application assignments as delay"}
            assignments (when application
                          (delay (mongo/select :assignments
                                               {:application.id (:id application)
                                                :status {$ne "canceled"}})))
            application-bulletins (delay
                                   (when application
                                     (mongo/select :application-bulletins
                                                   ;; TODO: proper query for all application bulletins
                                                   {:_id (:id application)})))
            user-organizations (lazy-seq (usr/get-organizations (:user command)))
            company (when-let [company-id (get-in command [:user :company :id])]
                      (delay (mongo/by-id :companies company-id)))
            command (-> {:application application
                         :application-bulletins application-bulletins
                         :organization organization
                         :user-organizations user-organizations
                         :company company
                         :application-assignments assignments}
                        (merge command)
                        (update :user update-user-application-role application)
                        enrich-default-permissions
                        enrich-action-contexts)]
        (or
          (not-authorized-to-application command)
          (access-denied-by-insufficient-permissions command)
          (pre-checks-fail command)
          (when execute?
            (let [status   (executed command)
                  post-fns (get-post-fns status (get-meta (:action command)))]
              (invoke-post-fns! post-fns command status)
              status))
          (ok))))
    (catch [:sade.core/type :sade.core/fail] {:keys [text] :as all}
      (do
        (errorf "fail! in action: \"%s\" [%s:%s]: %s (%s)"
          (:action command)
          (:sade.core/file all)
          (:sade.core/line all)
          text
          (dissoc all :text :sade.core/type :sade.core/file :sade.core/line))
        (when execute? (log/log-event :error (masked command)))
        (fail text (dissoc all :sade.core/type :sade.core/file :sade.core/line))))
    (catch response? resp
      (do
        (warnf "%s -> proxy fail: %s" (:action command) resp)
        (fail :error.unknown)))
    (catch [:type :schema.core/error] {:keys [schema value error]}
      (errorf "schema.core error processing action: %s, error: '%s' with schema (containing keys): %s"
              (:action command)
              (pr-str error)
              (pr-str (if (map? schema)
                        (map pr-str (take 5 (keys schema)))
                        schema)))
      (when execute? (log/log-event :error (masked command)))
      (fail :error.illegal-value:schema-validation))
    (catch Object e
      (do
        (error e "exception while processing action:" (:action command) (class e) (str e))
        (when execute? (log/log-event :error (masked command)))
        (fail :error.unknown)))))

(defn execute [{action :action :as command}]
  (or
    ; Invalid commands should have as little side effect as possible:
    ; call must not be counted (don't put the invalid command in actions atom)
    ; and there is no point logging exec time.
    (missing-command command)
    (let [before   (System/currentTimeMillis)
          response (run command execute-validators true)
          after    (System/currentTimeMillis)]
      (debug action "->" (:ok response) "(took" (- after before) "ms)")
      (swap! actions update-in [(keyword action) :call-count] #(if % (inc %) 1))
      response)))

(defn validate [command]
  (run command authorize-validators false))

;;
;; Register actions
;;

(defn- subset-of [reference-set]
  {:pre [(set? reference-set)]}
  (sc/pred (fn [x] (and (set? x) (every? reference-set x)))))

(def ActionMetaData
  {
   ; Set of user role keywords. Use :user-roles #{:anonymous} to grant access to anyone.
   (sc/optional-key :user-roles) (subset-of roles/all-user-roles)
   ; Parameters can be keywords or symbols. Symbols will be available in the action body.
   ; If a parameter is missing from request, an error will be raised.
   (sc/optional-key :parameters)  [(sc/cond-pre sc/Keyword sc/Symbol)]
   (sc/optional-key :optional-parameters)  [(sc/cond-pre sc/Keyword sc/Symbol)]
   ; Set of categories for use of allowed-actions-for-cateory
   (sc/optional-key :categories)   #{sc/Keyword}
   ; Set of application context role keywords.
   (sc/optional-key :user-authz-roles)  (subset-of roles/all-authz-roles)
   ; Set of application organization context role keywords
   (sc/optional-key :org-authz-roles) (subset-of roles/all-org-authz-roles)
   ; Documentation string.
   (sc/optional-key :description) sc/Str
   ; Documents that the action will be sending (email) notifications.
   (sc/optional-key :notified)    sc/Bool
   ; Prechecks one parameter: the command, which has :application associated.
   ; Command does not have :data when pre-check is called on validation phase (allowed-actions)
   ; but has :data when pre-check is called during action execution.
   (sc/optional-key :pre-checks)  [(sc/cond-pre util/IFn sc/Symbol)]
   ; Input validators take one parameter, the command. Application is not yet available.
   (sc/optional-key :input-validators)  [(sc/cond-pre util/Fn sc/Symbol)]
   ; Application state keywords
   (sc/optional-key :states)      (sc/if map?
                                    {(apply sc/enum roles/all-user-roles) (subset-of states/all-states)}
                                    (subset-of states/all-states))
   (sc/optional-key :contexts)    [(sc/pred fn? "context extender function")]
   (sc/optional-key :permissions) (sc/constrained [{(sc/optional-key :description) sc/Str
                                                    (sc/optional-key :context) permissions/ContextMatcher
                                                    :required [permissions/RequiredPermission]}]
                                                  (util/fn->> butlast (every? :context))
                                                  "key :context is required for all but last element of permissions")
   (sc/optional-key :on-complete) (sc/cond-pre util/Fn [util/Fn])
   (sc/optional-key :on-success)  (sc/cond-pre util/Fn [util/Fn])
   (sc/optional-key :on-fail)     (sc/cond-pre util/Fn [util/Fn])
   ; Allow command execution even if the system is in readonly mode.
   (sc/optional-key :allowed-in-lockdown)    sc/Bool
   ; Feature flag name. Action is run only if the feature flag is true.
   ; If you have feature.some-feature properties file, use :feature :some-feature in action meta data
   (sc/optional-key :feature)     sc/Keyword})

(defn register-action [action-type action-name meta-data line ns-str handler]
  {:pre [(keyword? action-type)
         (not (ss/blank? (name action-name)))
         (number? line)
         (string? ns-str)
         (fn? handler)]}

  (when-let [res (sc/check ActionMetaData meta-data)]
    (let [invalid-meta (merge-with
                         (fn [val-in-result val-in-latter]
                           (if-not (nil? val-in-latter)
                             val-in-latter
                             val-in-result))
                         res
                         (select-keys meta-data (keys res)))]

      (throw (AssertionError. (str "Action '" action-name "' has invalid meta data: " invalid-meta ", schema-error: " res)))))

  (assert (or (seq (:user-roles meta-data)) (seq (:permissions meta-data)))
          (str "You must define :user-roles or :permissions meta data for " action-name))

  (assert (or (ss/ends-with ns-str "-api") (ss/ends-with ns-str "-test") (ss/starts-with (ss/suffix ns-str ".") "dummy"))
    (str "Please define actions in *-api namespaces. Offending action: " action-name " at " ns-str ":" line))

  (assert
    (if (some #(= % :id) (:parameters meta-data))
      (or (seq (:permissions meta-data)) (seq (:states meta-data)) (seq (:pre-checks meta-data)))
      true)
    (str "You must define :permissions, :states or :pre-checks meta data for " action-name " if action has the :id parameter (i.e. application is attached to the action)."))

  (assert (or  (nil? (:states meta-data)) (set? (:states meta-data)) (-> meta-data :states keys set (= (:user-roles meta-data))))
    (str "Keys of :states should match :user-roles for " action-name " when :states is defined as a map."))

  (assert (or (seq (:input-validators meta-data))
              (empty? (:parameters meta-data))
              (some #{:id} (:parameters meta-data)))
    (str "Input validators must be defined for " action-name))

  (assert (or (empty? (:org-authz-roles meta-data))
              (some #{:id} (:parameters meta-data)))
          (str "org-authz-roles depends on application, can't be used outside application context - " action-name))

  (let [action-keyword (keyword action-name)
        {:keys [user-roles user-authz-roles org-authz-roles]} meta-data]

    (tracef "registering %s: '%s' (%s:%s)" (name action-type) action-name ns-str line)
    (swap! actions assoc
      action-keyword
      (merge
       {:user-authz-roles (cond
                            (nil? user-roles) roles/all-authz-roles
                            (= #{:authority} user-roles) #{} ;; By default, authority gets authorization fron organization role
                            :else (roles/default-user-authz action-type))
        :org-authz-roles (cond
                           (nil? user-roles) roles/all-org-authz-roles
                           (some user-roles [:authority :oirAuthority]) roles/default-org-authz-roles
                           (user-roles :anonymous) roles/all-org-authz-roles)
        :permissions     [{:required []}]} ; no permissions required by default
        meta-data
        {:type action-type
         :ns ns-str
         :line line
         :handler handler
         :call-count 0}))))

(defmacro defaction [form-meta action-type action-name & args]
  (let [doc-string  (when (string? (first args)) (first args))
        args        (if doc-string (rest args) args)
        meta-data   (when (map? (first args)) (first args))
        args        (if meta-data (rest args) args)
        bindings    (when (vector? (first args)) (first args))
        body        (if bindings (rest args) args)
        bindings    (or bindings ['_])
        letkeys     (->> (util/select-values meta-data [:parameters :optional-parameters])
                         (apply concat)
                         (filter symbol?))
        keywordize  (comp keyword name)
        meta-data   (assoc meta-data :parameters (mapv keywordize (:parameters meta-data))
                                     :optional-parameters (mapv keywordize (:optional-parameters meta-data)))
        line-number (:line form-meta)
        ns-str      (str *ns*)
        defname     (symbol (str (name action-type) "-" action-name))
        handler     (eval
                      `(fn [request#]
                         (let [{{:keys ~(vec letkeys)} :data} request#]
                           ((fn ~bindings (do ~@body)) request#))))]
    `(do
       (register-action ~action-type ~(str action-name) ~meta-data ~line-number ~ns-str ~handler)
       (defn ~defname
         ([] (~defname {}))
         ([request#] (~handler request#))))))

(defmacro defcommand [& args] `(defaction ~(meta &form) :command ~@args))
(defmacro defquery   [& args] `(defaction ~(meta &form) :query ~@args))
(defmacro defraw     [& args] `(defaction ~(meta &form) :raw ~@args))
(defmacro defexport  [& args] `(defaction ~(meta &form) :export ~@args))

(sc/defschema ActionType
  (sc/enum [:command
            :raw
            :action
            :query
            :export]))

(sc/defschema ActionData
  ;;TODO: needs an actual schema
  sc/Any)

(sc/defschema ActionBase
  "Has the bare minimum fields of an action that allow it to be run without
   executing."
  {:user usr/User
   :type ActionType
   :data ActionData
   sc/Any sc/Any})

(sc/defschema ActionSkeleton
  {:user usr/User
   :data ActionData
   sc/Any sc/Any})

(sc/defn build-action :- ActionBase
  [action-name :- sc/Str
   {:keys [user data] :as skeleton} :- ActionSkeleton]
  (let [meta (get-meta action-name)]
    (when meta
      (merge skeleton
             (action action-name
                     :type (:type meta)
                     :data data
                     :user user)
             {:categories (:categories meta)}))))

(defn foreach-action [command-skeleton]
  (for [action-name (remove nil? (keys @actions))
        :let [result (build-action action-name command-skeleton)]
        :when result]
    result))

(defn- validated [command]
  {(:action command) (validate command)})

(def validate-actions
  (if (env/dev-mode?)
    (util/fn->> (map validated) (into {}))
    (util/fn->> (map validated) (filter (comp :ok first vals)) (into {}))))

(defn filter-actions-by-category [category actions]
  {:pre [(keyword? category)]}
  (filter #(some-> % :categories category) actions))

(defmulti allowed-actions-for-category (util/fn-> :data :category keyword))

(defmethod allowed-actions-for-category :default
  [_]
  nil)

(defn allowed-category-actions-for-command [category command]
  (->> (foreach-action command)
       (filter-actions-by-category category)
       validate-actions))

(defn allowed-actions-for-collection
  "The collection-key must be the same as the category key."
  [collection-key command-builder {:keys [application] :as command}]
  (let [coll (get application collection-key)]
    (->> (map (partial command-builder application) coll)
         (map (partial assoc command :data))
         (map foreach-action)
         (map (partial filter-actions-by-category collection-key))
         (map validate-actions)
         (zipmap (map :id coll)))))
