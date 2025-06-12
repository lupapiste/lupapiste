(ns lupapalvelu.permissions
  (:require [clojure.set :as set]
            [lupapalvelu.restrictions :as restrictions]
            [lupapalvelu.roles :as roles]
            [sade.util :as util]
            [schema.core :as sc]))

(set! *warn-on-reflection* true)

(def scopes
  "Scopes represent a source or reason for granting authorizations for a role.

  Note that some scopes are checked automatically (`:global`, `:organization`..),
  while others require that the target command lists the appropriate context functions
  (see `:contexts` and `defcontext`)."
  #{;; Permissions are granted to the role/user when:
    :global        ; always
    :organization  ; the user is a member of an organization
    :integration   ; the user is an external integration assigned to an organization
    :application   ; the user is authorized to an application
    :authorization ; some special authorization? see `auth-entry-context`
    :canceled-app  ; the user cancelled the target application, see `canceled-app-context`
    :document      ; the user has one of the `user-authz-roles` specified on the target document, see `document-context`
    :foreman-app   ; the user is authorized to a foreman application, see `foreman-app-context`
    :inforequest   ; the user is authorized to a info request application, see `inforequest-context`
    })

(def contexts
  "Correspond to the various `resources/permissions/<context>.edn` files."
  #{:application :attachment :comment :document :global
    :invoice :organization :task :users :verdict :processing})

;; TODO: we could leverage these schemas elsewhere when we refer to roles, scopes etc.
(def ^:private Scope (apply sc/enum scopes))
(def ^:private Context sc/Keyword)

(defn- permission-schema [context]
  (let [context-ns (name context)]
    (sc/constrained Context (fn [permission] (= context-ns (namespace permission)))
                    "Permission namespace must equal context")))

(defn- load-permissions!
  []
  (reduce (fn [permissions context]
            (let [path    (format "permissions/%s.edn" (name context))
                  content (util/read-edn-resource path)]
              (sc/validate {Scope {roles/Role #{(permission-schema context)}}} content)
              (merge-with (partial merge-with into) permissions content)))
          {}
          contexts))

(def permission-tree (load-permissions!))

(def known-permissions (->> (vals permission-tree)
                            (mapcat vals)
                            (reduce into #{:global/not-allowed})))

(defn- known-permission? [permission]
  (contains? known-permissions permission))

(defn restriction [& restrictions]
  {:pre [(every? known-permission? restrictions)]}
  (fn [permissions] (apply disj permissions restrictions)))

(defmacro permissions?
  "Macro returns a checks that required permissions are included in a command.
  Validation for required-permissions is run in compile time for developing
  convenience."
  [command required-permissions]
  {:pre [(every? known-permission? required-permissions)]}
  `(every? (get ~command :permissions #{}) ~required-permissions))

(defn roles-in-scope-with-permissions [scope required-permissions]
  {:pre [(every? known-permission? required-permissions)]}
  (->> (get permission-tree (keyword scope))
       (keep (fn [[role permissions]]
               (when (every? (set permissions) required-permissions) role)))
       set))

(defn get-permissions-by-role [scope role]
  (get-in permission-tree [(keyword scope) (keyword role)] #{}))

(defn get-global-permissions [{{role :role} :user}]
  (get-permissions-by-role :global (keyword role)))

(defn get-organization-permissions [{:keys [application data] {org-authz :orgAuthz} :user}]
  ;; NOTE: If the command DOES NOT target an application nor an organization (in other words,
  ;; it does not have an `:id` or `:organizationId` parameter), the user is granted the union
  ;; of ALL permissions in any of their organizations!
  (let [org-id (keyword (or (:organization application) (:organizationId data)))
        roles (if org-id
                (get org-authz org-id)
                (mapcat val org-authz))]
    (into #{}
          (comp (mapcat (partial get-permissions-by-role :organization))
                (filter (fn [permission] (or (not= permission :organization/admin)
                                             (some #(= % :authorityAdmin) (get org-authz org-id))))))
          (set roles))))

(def ^:private submit-restriction (restriction :application/submit))

(defn apply-company-restrictions [{company-submitter :submit} permissions]
  (cond-> permissions
          (not company-submitter) submit-restriction))

(defn get-company-permissions [{{{company-id :id company-role :role :as company} :company} :user
                                {auth :auth}                                               :application :as command}]
  (when company-id
    (->> auth
         (filter (every-pred (comp #{company-id} :id)
                             (comp (some-fn #{(keyword company-role)} nil?) keyword :company-role)))
         (map :role)
         (map (partial get-permissions-by-role :application))
         (reduce into #{})
         (apply-company-restrictions company)
         (restrictions/apply-auth-restrictions command))))

(defn get-application-permissions [{{user-id :id} :user {auth :auth} :application :as command}]
  (->> (filter (comp #{user-id} :id) auth)
       (map :role)
       (map (partial get-permissions-by-role :application))
       (reduce into #{})
       (restrictions/apply-auth-restrictions command)))

(defn get-integration-organization-permissions
  "Organization permissions for a AsiointiAPI user"
  [{:keys [user org-id]}]
  (when (and user
             org-id
             (util/=as-kw :asiointi-api (:role user)))
    (let [org-authz (:orgAuthz user)
          roles     (get org-authz (keyword org-id))]
      (->> (set roles)
           (mapcat (partial get-permissions-by-role :integration))
           (into #{})))))

(defn get-integration-app-permissions
  "Application permissions for a AsiointiAPI user"
  [{:keys [user application]}]
  (when (and user
             application
             (util/=as-kw :asiointi-api (:role user)))
    (let [org-id (keyword (:organization application))]
      (get-integration-organization-permissions (util/mmap user org-id)))))

(defmacro defcontext
  "Creates an extender function for action context. Function takes only command parameter
  which is passed through and extended with output of the extender function. Function
  should return a map, where :context-scope and :context-roles keys are handled as special
  keys.  Command :permissions are extended by resolving permissions from permission tree
  by :context-scope and :context-roles. :context-scope and :context-roles are not merged
  into command.

  example:
  (defcontext thing-context [{{user-id :id} :user app :application}]
    (let [thing (app/find-user-thing user-id app)]
      {:context-scope  :thing
       :context-roles  [(:role thing)]
       :thing          thing}))

  returns a command extended with :thing and :permissions. :permissions is union of
  original command permissions and permissions for :context-role in :context-scope.

  {:user {...}
   :application {...}
   ...
   :thing {...}
   :permissions #{:global/permissions :application/permissions ... :thing/permissions}}"
  [context-name [command-param] & body]
  (let [cmd (cond
              (:as command-param) command-param
              (map? command-param) (assoc command-param :as (gensym "cmd"))
              :else {:as command-param})]
    `(defn ~context-name [~cmd]
       (let [ctx#   (do ~@body)
             perms# (->> (:context-roles ctx#)
                         (map (partial get-permissions-by-role (:context-scope ctx#)))
                         (reduce into #{}))]
         (-> (merge ~(:as cmd) (dissoc ctx# :context-scope :context-roles :permissions))
             (update :permissions set/union perms#))))))

(def ContextMatcher
  (sc/conditional
    set? #{sc/Keyword}
    fn? sc/Any
    map? {sc/Keyword (sc/recursive #'ContextMatcher)}))

(def RequiredPermission
  (sc/constrained sc/Keyword known-permission?))

(defn matching-context? [ctx-matcher context]
  (cond
    (map? ctx-matcher) (every? (fn [[k v]] (matching-context? v (k context))) ctx-matcher)
    (nil? ctx-matcher) true
    (set? ctx-matcher) (contains? ctx-matcher (keyword context))
    (fn? ctx-matcher) (ctx-matcher context)))

(defn get-required-permissions [{permissions :permissions} command]
  (or (util/find-first (util/fn-> :context (matching-context? command)) permissions)
      {:required [:global/not-allowed]}))
