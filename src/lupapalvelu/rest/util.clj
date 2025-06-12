(ns lupapalvelu.rest.util
  (:require [clojure.set :as set]
            [lupapalvelu.action :as action]
            [lupapalvelu.company :as company]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.organization :as org]
            [lupapalvelu.permissions :as permissions]
            [lupapalvelu.rest.docstore :as docstore]
            [lupapalvelu.roles :as roles]
            [lupapalvelu.user :as usr]
            [ring.util.http-status :as http-status]
            [sade.schema-errors :as se]
            [sade.util :as util]))

(set! *warn-on-reflection* true)

(defn client-org-oauth-roles [{:keys [client-type oauth-roles]}]
  (filter #(and (:organization %)
                (= (:client-type %) (name client-type)))
          oauth-roles))

(defmulti get-organization-roles
  "Get client-specific organization roles. Roles may be resolved both from user's oauth-roles and
  implicitly from orgAuthz."
  (fn [client-type _user]
    client-type))

(defmethod get-organization-roles :docdepartmental
  [_client-type user]
  (->> (docstore/departmental-organizations user)
       (map (fn [[{:keys [id]} role]]
              {:id    id
               :roles [(name role)]}))
       distinct))

(defn- reporting-enabled-for-client? [client-version {:keys [id]}]
  (-> id
      (org/get-organization [:reporting-system])
      :reporting-system
      (= client-version)))

(defn implicit-reporting-roles [user]
  (let [qualified-reporting-roles (set (map #(keyword "organization" (name %)) roles/reporting-roles))]
    (->> user
         :orgAuthz
         (map (fn [[org-id _]]
                [org-id (->> (lupapalvelu.permissions/get-organization-permissions
                               {:user user
                                :data {:organizationId org-id}})
                             (filter qualified-reporting-roles)
                             (map (comp keyword name))
                             seq)]))
         (into {})
         util/strip-nils)))

(defmethod get-organization-roles :bi-app
  [_client-type user]
  (let [implicit-roles (implicit-reporting-roles user)
        oauth-roles    (->> (client-org-oauth-roles user)
                            (group-by (comp keyword :organization))
                            (util/map-values (partial map (comp keyword :role))))]

    (->> (util/deep-merge-with concat implicit-roles oauth-roles)
         (map (fn [[org-id roles]]
                {:id    (name org-id)
                 :roles (->> roles distinct (map name))}))
         (filter (partial reporting-enabled-for-client? (:client-version user)))
         seq)))

(defn- flattened-org-roles [{:keys [orgAuthz]}]
  (mapcat (fn [[org roles]]
            (map (fn [role] {:organization (name org)
                             :role         role}) roles))
          orgAuthz))

;; If some other client is queried, return orgAuthz roles also.
(defmethod get-organization-roles :default
  [_client-type user]
  (->> (concat (flattened-org-roles user)
               (client-org-oauth-roles user))
       distinct
       (group-by :organization)
       (util/map-values #(map :role %))
       (map (fn [[org-id roles]]
              {:id    org-id
               :roles roles}))))

(defn get-non-org-roles [{:keys [client-type oauth-roles]}]
  (filter #(and (-> % :organization nil?)
                (= (-> % :client-type keyword) client-type)) oauth-roles))

(defn rest-user
  "Basic information for REST endpoints (e.g, '/rest/user').
  User can have a single non-organization client role, e.g. admin,
  and several organization-specific client roles."
  [{:keys [client-type] :as user}]
  (let [company-id    (get-in user [:company :id])
        company       (some-> company-id company/find-company-by-id)
        organizations (get-organization-roles client-type user)
        non-org-roles (get-non-org-roles user)]
    (util/assoc-when (select-keys user [:id :role :email :firstName :lastName])
                     :role (-> non-org-roles first :role)
                     :company (when company {:id company-id :name (:name company)})
                     :organizations (seq organizations))))

(defn make-error
  [err & fields]
  (assoc err :field (se/make-field fields)))

;;; Pipeline helpers

;; Updating an entity is a multi-step process with lots of potential errors on
;; which we have to stop the process. Here each step is modelled as a function
;; `context -> context`, i.e. a function that takes a context map (the state of
;; the process), does something to move to process forward, and returns an
;; updated context map. If a step fails, it adds the `:error` key to the
;; context, possibly with some other keys that contain the details of the
;; failure.

;; The following helpers are used to compose the steps into a single pipeline,
;; similar to how one might compose them with Clojure's threading macros (->,
;; ->>, etc.), but with error checking.

(defn do-all
  "Composes the given steps (context -> context) into a single step , short-circuiting on error."
  [& steps]
  (fn [context]
    (reduce (fn [context step]
              (let [result (step context)]
                (if (contains? result :error)
                  ;; Step failed, short-circuiting.
                  (reduced result)
                  ;; Step succeeded, move on to the next one.
                  result)))
            context steps)))

(defn do-one
  "Composes the given steps (context -> context), short-circuiting on success."
  [& steps]
  (fn [context]
    (reduce (fn [context step]
              ;; Clear the error set by the previous step.
              (let [result (step (dissoc context :error))]
                (if (contains? result :error)
                  ;; Step failed, try the next one.
                  result
                  ;; Step succeeded, short-circuiting.
                  (reduced result))))
            context steps)))

;;; Common pipeline steps

(defn- get-key
  [ctx k]
  (get-in ctx (if (keyword? k) [k] k)))

(defn- assoc-key
  [ctx k v]
  (assoc-in ctx (if (keyword? k) [k] k) v))

(defn check-authentication-step
  "Verify that we have a user"
  [{:keys [user] :as ctx}]
  (if-not user
    (assoc ctx :error {:title  "Unauthenticated"
                       :status http-status/forbidden})
    ctx))

(defn check-authorization-step
  "Verify that the user is authorized to access this API"
  [{:keys [user] :as ctx}]
  (if-not (usr/rest-user? user)
    (assoc ctx :error {:title  "Unauthorized"
                       :status http-status/unauthorized})
    ctx))

(defn check-data-step
  "Produces a step which checks data in `src-key` with `checker`."
  [{:keys [src-key checker err-title]}]
  (fn [ctx]
    (if-let [errors (checker (get-key ctx src-key))]
      (assoc ctx
             :error {:title  err-title
                     :errors (se/error-or-errors->data errors)})
      ctx)))

(defn coerce-data-step
  "Produces a step which coerces context data from `src-key` to `dst-key` with `coercer`."
  [{:keys [src-key dst-key coercer err-title err-path]}]
  (fn [ctx]
    (try
      (assoc-key ctx dst-key (coercer (get-key ctx src-key)))
      (catch Exception e
        (if-some [errors (-> e ex-data :error)]
          (assoc ctx
                 :error {:title  err-title
                         :errors (apply se/error-or-errors->data errors err-path)})
          (assoc ctx
                 :error {:title  err-title
                         :errors [(apply make-error {:error "unknown-error"} err-path)]}))))))

(defn not-found-error
  [application-id]
  {:title  "Application error"
   :errors [(make-error
              {:error "application-not-found"
               :value application-id}
              :application-id)]})

(defn find-application-from-mongo-step
  "Produces a step which tries to find the application and merges it into the context.
   Can also optionally fetch the organization and create a command."
  [{:keys [application-id-key user-key timestamp-key
           dst-application-key dst-org-key dst-command-key]}]
  (fn [ctx]
    {:pre [(map? (:system ctx))]}
    (let [application-id (get-key ctx application-id-key)
          user           (get-key ctx user-key)
          timestamp      (get-key ctx timestamp-key)]
      (if-some [app (mongo/by-id :applications application-id)]
        (let [*org (delay (org/get-organization (:organization app)))]
          (cond-> ctx
            true (assoc-key dst-application-key app)
            dst-org-key (assoc-key dst-org-key (force *org))
            dst-command-key (assoc-key dst-command-key (-> (action/application->command app user)
                                                           (assoc :created timestamp
                                                                  :organization *org
                                                                  :system (:system ctx))))))
        (assoc ctx :error (not-found-error application-id))))))

(defn check-application-authorization-step
  "Produces a step which checks that the user has access to the application (and organization et al.)"
  [{:keys [application-id-key command-key required-permissions]}]
  (fn [ctx]
    (let [application-id (get-key ctx application-id-key)
          command        (get-key ctx command-key)]
      (if (set/subset? required-permissions (permissions/get-organization-permissions command))
        ctx
        ;; Don't leak details about the application
        (assoc ctx :error (not-found-error application-id))))))

(defn check-application-state-step
  "Produces a step which checks that the application is in a valid state"
  [{:keys [command-key permitted-app-states]}]
  (fn [ctx]
    (let [state (-> ctx (get-key command-key) :application :state keyword)]
      (cond-> ctx
        (-> permitted-app-states set (contains? state) not)
        (assoc :error {:title  "Illegal application state"
                       :status http-status/forbidden})))))

(defn commit-queued-updates-step!
  "Produces a step which persist all the queued application changes to mongo"
  [{:keys [command-key updates-key]}]
  (fn [ctx]
    (let [updates (get-key ctx updates-key)]
      (when (seq updates)
        (action/update-application (get-key ctx command-key) updates)))
    ctx))
