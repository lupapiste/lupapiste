(ns lupapalvelu.action
  (:require [taoensso.timbre :as timbre :refer [trace tracef debug debugf info infof warn warnf error errorf fatal fatalf]]
            [clojure.set :as set]
            [clojure.string :as s]
            [slingshot.slingshot :refer [try+]]
            [sade.env :as env]
            [lupapalvelu.core :refer :all]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.logging :as log]
            [lupapalvelu.notifications :as notifications]
            [lupapalvelu.domain :as domain]))

;;
;; construct command, query and raw
;;

(defn- action [name & {:keys [user type data] :or {:user nil :type :action :data {}}}]
  {:action name
   :user user
   :type type
   :created (now)
   :data data})

(defn make-query [name data] (action name :type :query :data data))
(defn make-raw   [name data] (action name :type :raw :data data))

(defn make-command
  ([name data]      (make-command name nil data))
  ([name user data] (action name :user user :data data :type :command)))

;;
;; some utils
;;

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

(defn non-blank-parameters [params command]
  (when-let [missing (seq (filter
                            #(let [s (get-in command [:data %])]
                               (or (nil? s) (and (string? s) (s/blank? s))))
                            params))]
    (info "blank parameters:" (s/join ", " missing))
    (fail :error.missing-parameters :parameters (vec missing))))

(defn get-applicant-name [_ app]
  (if (:infoRequest app)
    (let [{first-name :firstName last-name :lastName} (first (domain/get-auths-by-role app :owner))]
      (str first-name \space last-name))
    (when-let [body (:data (domain/get-applicant-document app))]
      (if (= (get-in body [:_selected :value]) "yritys")
        (get-in body [:yritys :yritysnimi :value])
        (let [{first-name :etunimi last-name :sukunimi} (get-in body [:henkilo :henkilotiedot])]
          (str (:value first-name) \space (:value last-name)))))))

(defn get-application-operation [app]
  (first (:operations app)))

(defn update-application
  "get current application from command (or fail) and run changes into it."
  ([command changes]
    (update-application command {} changes))
  ([command mongo-query changes]
    (with-application command
      (fn [{:keys [id]}]
        (mongo/update :applications (assoc mongo-query :_id id) changes)))))

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
                  (dissoc :handler :validators :input-validators :on-success)
                  (assoc :name k))])))

;;
;; Command router
;;

(defn missing-fields [{data :data} {parameters :parameters}]
  (map name (set/difference (set parameters) (set (keys data)))))

(defn- has-required-role [command meta-data]
  (let [user-role      (-> command :user :role keyword)
        roles-required (-> meta-data :roles)]
    (or (empty? roles-required) (some #{user-role} roles-required))))

(defn meta-data [{command :action}]
  ((get-actions) (keyword command)))

(defn missing-command [command]
  (when (not (meta-data command))
    (warnf "command '%s' not found" (:action command))
    (fail :error.invalid-command)))

(defn not-authenticated [{user :user :as command}]
  (when (and (nil? user) (:authenticated (meta-data command)))
    (fail :error.unauthorized)))

(defn missing-feature [command]
  (when-let [feature (:feature (meta-data command))]
    (when-not (apply env/feature? feature)
      (fail :error.missing-feature))))

(defn invalid-type [{type :type :as command}]
  (when (and type (not (= type (:type (meta-data command)))))
    (info "invalid type:" (name type))
    (fail :error.invalid-type)))

(defn missing-roles [command]
  (when (not (has-required-role command (meta-data command)))
    (tracef "command '%s' is unauthorized for role '%s'" (-> command :action) (-> command :user :role))
    (fail :error.unauthorized)))

(defn impersonation [command]
  (when (and (= :command (:type (meta-data command))) (get-in command [:user :impersonating]))
    (fail :error.unauthorized)))

(defn missing-parameters [command]
  (when-let [missing (seq (missing-fields command (meta-data command)))]
    (info "missing parameters:" (s/join ", " missing))
    (fail :error.missing-parameters :parameters (vec missing))))

(defn input-validators-fail [command]
  (when-let [validators (:input-validators (meta-data command))]
    (when (seq validators)
      (reduce #(or %1 (%2 command)) nil validators))))

(defn invalid-state-in-application [command application]
  (when-let [valid-states (:states (meta-data command))]
    (let [state (:state application)]
      (when-not (.contains valid-states (keyword state))
        (fail :error.command-illegal-state)))))

(defn validators-fail [command application]
  (when-let [validators (:validators (meta-data command))]
    (reduce #(or %1 (%2 command application)) nil validators)))

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

(def authorize-validators [missing-command
                           missing-feature
                           not-authenticated
                           missing-roles
                           impersonation])

(def execute-validators (conj authorize-validators
                          invalid-type
                          missing-parameters
                          input-validators-fail))

(defn requires-application? [{data :data}]
  (contains? data :id))

(defn get-application
  "if :id parameter is present read application from command
   (pre-loaded) or load application for user."
  [{{id :id} :data user :user application :application}]
  (and id (or application (domain/get-application-as id user))))

(defn- authorized-to-application [command application]
  (when-let [id (-> command :data :id)]
    (if-not application
      (fail :error.unauthorized)
      (or
        (invalid-state-in-application command application)
        (validators-fail command application)))))

(defn- response? [r]
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
          (authorized-to-application command application)
          (when execute?
            (let [command  (assoc command :application application) ;; cache the app
                  status   (executed command)
                  post-fns (get-post-fns status (get-meta (:action command)))]
              (invoke-post-fns! post-fns command status)
              status))
          (ok))))
    (catch [:lupapalvelu.core/type :lupapalvelu.core/fail] {:keys [text] :as all}
      (do
        (errorf "fail! in action: \"%s\" [%s:%d]: %s (%s)"
          (:action command)
          (:lupapalvelu.core/file all)
          (:lupapalvelu.core/line all)
          text
          (dissoc all :text :lupapalvelu.core/type :lupapalvelu.core/file :lupapalvelu.core/line))
        (when execute? (log/log-event :error command))
        (fail text (dissoc all :lupapalvelu.core/type :lupapalvelu.core/file :lupapalvelu.core/line))))
    (catch response? resp
      resp)
    (catch Object e
      (do
        (error e "exception while processing action:" (:action command) (class e) (str e))
        (when execute? (log/log-event :error command))
        (fail :error.unknown)))))

(defmacro logged [command & body]
  `(let [response# (do ~@body)]
     (debug (:action ~command) "->" (:ok response#))
     response#))

(defn execute [command]
  (logged command
    (run command execute-validators true)))

(defn validate [command]
  (run command authorize-validators false))

;;
;; Register actions
;;

(defn register-action [action-type action-name params line ns-str handler]
  (let [action-keyword (keyword action-name)]
    (tracef "registering %s: '%s' (%s:%s)" (name action-type) action-name ns-str line)
    (swap! actions assoc
      action-keyword
      (merge params {:type action-type
                     :ns ns-str
                     :line line
                     :verified (or (:verified params) (contains? (set (:parameters params)) :id))
                     :handler handler}))))

(defmacro defaction [atype fun & args]
  (let [doc-string  (when (string? (first args)) (first args))
        args        (if doc-string (rest args) args)
        meta-data   (when (map? (first args)) (first args))
        args        (if meta-data (rest args) args)
        meta-data   (or meta-data {})
        bindings    (when (vector? (first args)) (first args))
        body        (if bindings (rest args) args)
        bindings    (or bindings ['_])
        parameters  (:parameters meta-data)
        letkeys     (filter symbol? parameters)
        parameters  (map (comp keyword name) parameters)
        meta-data   (assoc meta-data :parameters (vec parameters))
        line-number (:line (meta &form))
        ns-str      (str *ns*)
        defname     (symbol (str (name atype) "-" fun))
        action-name (str fun)
        handler     (eval
                      `(fn [request#]
                         (let [{{:keys ~letkeys} :data} request#]
                           ((fn ~bindings (do ~@body)) request#))))]
    `(do
       (register-action ~atype ~action-name ~meta-data ~line-number ~ns-str ~handler)
       (defn ~defname
         ([] (~defname {}))
         ([request#] (~handler request#))))))

(defmacro defcommand [& args] `(defaction :command ~@args))
(defmacro defquery   [& args] `(defaction :query ~@args))
(defmacro defraw     [& args] `(defaction :raw ~@args))

