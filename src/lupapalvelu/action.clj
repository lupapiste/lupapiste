(ns lupapalvelu.action
  (:require [taoensso.timbre :as timbre :refer [trace tracef debug debugf info infof warn warnf error errorf fatal fatalf]]
            [clojure.set :as set]
            [clojure.string :as s]
            [slingshot.slingshot :refer [try+]]
            [monger.operators :refer [$set $push $pull]]
            [schema.core :as sc]
            [sade.env :as env]
            [sade.util :as util]
            [sade.strings :as ss]
            [sade.core :refer :all]
            [sade.validators :as v]
            [lupapalvelu.control-api :as control]
            [lupapalvelu.authorization :as auth]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.states :as states]
            [lupapalvelu.user :as user]
            [lupapalvelu.logging :as log]
            [lupapalvelu.notifications :as notifications]
            [lupapalvelu.domain :as domain]))

;;
;; construct command, query and raw
;;

(defn action [name & {:keys [user type data] :or {:user nil :type :action :data {}}}]
  {:action name
   :user user
   :type type
   :created (now)
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

(defn email-validator
  "Reads email key from action parameters and checks that it is valid email address.
   Blank address passes the validation."
  ([command] (email-validator :email command))
  ([email-param-name command]
    (let [email (get-in command [:data email-param-name])]
      (when-not (v/email-and-domain-valid? email)
        (fail :error.email)))))


;; Notificator

(defn notify [notification]
  (fn [command status]
    (notifications/notify! notification command)))

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
  (filter-params-of-command params command #(or (not (string? %)) (s/blank? %)) :error.missing-parameters))

(defn vector-parameters [params command]
  (filter-params-of-command params command (complement vector?) :error.non-vector-parameters))

(defn vector-parameters-with-non-blank-items [params command]
  (or
    (vector-parameters params command)
    (filter-params-of-command params command
      (partial some #(or (not (string? %)) (s/blank? %)))
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

(defn boolean-parameters [params command]
  (filter-params-of-command params command #(not (instance? Boolean %)) :error.non-boolean-parameters))

(defn number-parameters [params command]
  (filter-params-of-command params command (complement number?) :error.illegal-number))

(defn string-parameters [params command]
  (filter-params-of-command params command (complement string?) "error.illegal-value:not-a-string"))

(defn property-id-parameters [params command]
  (when-let [invalid (seq (filter #(not (v/kiinteistotunnus? (get-in command [:data %]))) params))]
    (trace "invalid property id parameters:" (s/join ", " invalid))
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

(defn update-application
  "Get current application from command (or fail) and run changes into it.
   Optionally returns the number of updated applications."
  ([command changes]
    (update-application command {} changes false))
  ([command mongo-query changes]
    (update-application command mongo-query changes false))
  ([command mongo-query changes return-count?]

    (when-let [new-state (get-in changes [$set :state])]
      (assert
        (or
          ; Require history entry
          (seq (get-in changes [$push :history]))
          ; Inforequest state chenges don't require logging
          (states/all-inforequest-states new-state)
          ; delete-verdict commands sets state back, but no logging is required (LPK-917)
          (seq (get-in changes [$pull :verdicts])))
        "event must be pushed to history array when state is set"))

    (with-application command
      (fn [{:keys [id]}]
        (let [n (mongo/update-by-query :applications (assoc mongo-query :_id id) changes)]
          (if return-count? n nil))))))

(defn application->command
  "Creates a command data structure that is suitable for update-application and with-application functions"
  [{id :id :as application}]
  {:data {:id id}
   :application application})

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
                  (dissoc :handler :pre-checks :input-validators :on-success)
                  (assoc :name k))])))

;;
;; Command router
;;

(defn missing-fields [{data :data} {parameters :parameters}]
  (map name (set/difference (set parameters) (set (keys data)))))

(defn- has-required-user-role [command {user-roles :user-roles :as meta-data}]
  (let [allowed-roles (or user-roles #{})
        user-role (-> command :user :role keyword)]
    (or (allowed-roles :anonymous) (allowed-roles user-role))))

(defn meta-data [{command :action}]
  ((get-actions) (keyword command)))

(defn check-lockdown [command]
  (let [{:keys [type allowed-in-lockdown]} (meta-data command)]
    (when (and (control/lockdown?) (= :command type) (not allowed-in-lockdown))
      (fail :error.service-lockdown))))

(defn missing-command [command]
  (when-not (meta-data command)
    (errorf "command '%s' not found" (log/sanitize 50 (:action command)))
    (fail :error.invalid-command)))

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
  (when (and (= :command (:type (meta-data command))) (get-in command [:user :impersonating]))
    unauthorized))

(defn disallow-impersonation [command _]
  (when (get-in command [:user :impersonating]) unauthorized))

(defn missing-parameters [command]
  (when-let [missing (seq (missing-fields command (meta-data command)))]
    (info "missing parameters:" (s/join ", " missing))
    (fail :error.missing-parameters :parameters (vec missing))))

(defn input-validators-fail [command]
  (when-let [validators (:input-validators (meta-data command))]
    (when (seq validators)
      (reduce #(or %1 (%2 command)) nil validators))))

(defn invalid-state-in-application [command {state :state}]
  (when-let [valid-states (:states (meta-data command))]
    (when-not (.contains valid-states (keyword state))
      (fail :error.command-illegal-state :state state))))

(defn pre-checks-fail [command application]
  {:post [(or (nil? %) (contains? % :ok))]}
  (when-let [pre-checks (:pre-checks (meta-data command))]
    (reduce #(or %1 (%2 command application)) nil pre-checks)))

(defn masked [command]
  (letfn [(strip-field [command field]
            (if (get-in command [:data field])
              (assoc-in command [:data field] "*****")
              command))]
    (reduce strip-field command [:password :newPassword :oldPassword])))

(defn get-meta [name]
  ((keyword name) (get-actions)))

(defn executed
  ([command] (executed (:action command) command))
  ([name command]
    (let [meta-data (get-meta name)]
      (or
        (if-let [handler (:handler meta-data)]
          (let [result (handler command)
                masked-command (masked command)]
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

(defn requires-application? [{data :data}]
  (contains? data :id))

(defn- get-application
  "if :id parameter is present read application from command
   (pre-loaded) or load application for user."
  [{{id :id} :data user :user application :application}]
  (and id user (or application (domain/get-application-as id user :include-canceled-apps? true))))

(defn- user-authz? [command-meta-data application user]
  (let [allowed-roles (get command-meta-data :user-authz-roles #{})]
    (auth/user-authz? allowed-roles application user)))

(defn- organization-authz? [command-meta-data application user]
  (let [required-authz (get command-meta-data :org-authz-roles #{})]
    (auth/has-organization-authz-roles? required-authz application user)))

(defn- company-authz? [command-meta-data application user]
  (auth/has-auth? application (get-in user [:company :id])))

(defn- user-is-not-allowed-to-access?
  "Current user must have correct role in application.auth, work in the organization or company that has been invited"
  [{user :user :as command} application]
  (let [meta-data (meta-data command)]
    (when-not (or
                (user-authz? meta-data application user)
                (organization-authz? meta-data application user)
                (company-authz? meta-data application user))

     unauthorized)))

(defn- not-authorized-to-application [command application]
  (when (-> command :data :id)
    (if-not application
      (fail :error.application-not-accessible)
      (or
        (invalid-state-in-application command application)
        (user-is-not-allowed-to-access? command application)))))

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
        (error e "post fn fail")))))

(defn- run [command validators execute?]
  (try+
    (or
      (some #(% command) validators)
      (let [application (get-application command)]
        (or
          (not-authorized-to-application command application)
          (pre-checks-fail command application)
          (when execute?
            (let [command  (assoc command :application application) ;; cache the app
                  status   (executed command)
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
        (when execute? (log/log-event :error command))
        (fail text (dissoc all :sade.core/type :sade.core/file :sade.core/line))))
    (catch response? resp
      (do
        (warnf "%s -> proxy fail: %s" (:action command) resp)
        (fail :error.unknown)))
    (catch Object e
      (do
        (error e "exception while processing action:" (:action command) (class e) (str e))
        (when execute? (log/log-event :error command))
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
   :user-roles (subset-of auth/all-user-roles)
   ; Parameters can be keywords or symbols. Symbols will be available in the action body.
   ; If a parameter is missing from request, an error will be raised.
   (sc/optional-key :parameters)  [(sc/either sc/Keyword sc/Symbol)]
   (sc/optional-key :optional-parameters)  [(sc/either sc/Keyword sc/Symbol)]
   ; Set of application context role keywords.
   (sc/optional-key :user-authz-roles)  (subset-of auth/all-authz-roles)
   ; Set of application organization context role keywords
   (sc/optional-key :org-authz-roles) (subset-of auth/all-org-authz-roles)
   ; Documentation string.
   (sc/optional-key :description) sc/Str
   ; Documents that the action will be sending (email) notifications.
   (sc/optional-key :notified)    sc/Bool
   ; Prechecks take two parameters: the command and the application.
   ; Command does not have :data when pre-check is called on validation phase (allowed-actions)
   ; but has :data when pre-check is called during action execution.
   (sc/optional-key :pre-checks)  [(sc/either util/Fn sc/Symbol)]
   ; Input validators take one parameter, the command. Application is not yet available.
   (sc/optional-key :input-validators)  [(sc/either util/Fn sc/Symbol)]
   ; Application state keywords
   (sc/optional-key :states)      (subset-of states/all-states)
   (sc/optional-key :on-complete) (sc/either util/Fn [util/Fn])
   (sc/optional-key :on-success)  (sc/either util/Fn [util/Fn])
   (sc/optional-key :on-fail)     (sc/either util/Fn [util/Fn])
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

      (throw (AssertionError. (str "Action '" action-name "' has invalid meta data: " invalid-meta)))))

  (assert (or (ss/ends-with ns-str "-api") (ss/ends-with ns-str "-test") (ss/starts-with (ss/suffix ns-str ".") "dummy"))
    (str "Please define actions in *-api namespaces. Offending action: " action-name " at " ns-str ":" line))

  (assert
    (if (some #(= % :id) (:parameters meta-data))
      (or (seq (:states meta-data)) (seq (:pre-checks meta-data)))
      true)
    (str "You must define :states or :pre-checks meta data for " action-name " if action has the :id parameter (i.e. application is attached to the action)."))

  (assert (or (seq (:input-validators meta-data))
              (empty? (:parameters meta-data))
              (= [:id] (:parameters meta-data)))
    (str "Input validators must be defined for " action-name))

  (let [action-keyword (keyword action-name)
        {:keys [user-roles user-authz-roles org-authz-roles]} meta-data]

    (tracef "registering %s: '%s' (%s:%s)" (name action-type) action-name ns-str line)
    (swap! actions assoc
      action-keyword
      (merge
        {:user-authz-roles (if (= #{:authority} user-roles)
                             #{} ; By default, authority gets authorization fron organization role
                             (auth/default-user-authz action-type))
         :org-authz-roles (cond
                            (some user-roles [:authority :oirAuthority]) auth/default-org-authz-roles
                            (user-roles :anonymous) auth/all-org-authz-roles)}
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
                         (let [{{:keys ~letkeys} :data} request#]
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
