(ns lupapalvelu.permissions
  (:require [clojure.set :as set]
            [clojure.java.io :as io]
            [schema.core :as sc]
            [sade.strings :as ss]
            [sade.util :refer [fn->] :as util]
            [lupapalvelu.restrictions :as restrictions]))

(defonce permission-tree (atom {}))

(def ^:private Scope sc/Keyword)
(def ^:private Role sc/Keyword)
(defn- permission-schema [context-type]
  (sc/constrained sc/Keyword (fn-> namespace name (= (name context-type)))
                  "Permission namespace equals context-type"))

(defn- defpermissions [context-type permissions]
  (sc/validate {Scope {Role #{(permission-schema context-type)}}} permissions)
  (swap! permission-tree #(merge-with (partial merge-with into) % permissions)))

(defn load-permissions! []
  (let [this-path (util/this-jar lupapalvelu.main)
        files     (if (ss/ends-with this-path ".jar")       ; are we inside jar
                    (filter #(ss/ends-with % ".edn") (util/list-jar this-path "permissions/"))
                    (map #(.getName %) (util/get-files-by-regex "resources/permissions/" #".+\.edn$")))]
    (reset! permission-tree {})
    (doseq [file files]
      (defpermissions (->> file (re-find #"(.*)(.edn)") second)
                      (util/read-edn-resource (str "permissions/" file))))))

(load-permissions!)

(defn- known-permission? [permission]
  (contains? (->> (vals @permission-tree)
                  (mapcat vals)
                  (reduce into #{:global/not-allowed}))
             permission))

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
  (->> (get @permission-tree (keyword scope))
       (keep (fn [[role permissions]]
               (when (every? (set permissions) required-permissions) role)))
       set))

(defn get-permissions-by-role [scope role]
  (get-in @permission-tree [(keyword scope) (keyword role)] #{}))

(defn get-global-permissions [{{role :role} :user}]
  (get-permissions-by-role :global (keyword role)))

(defn get-organization-permissions [{{org-authz :orgAuthz} :user {org-id :organization} :application}]
  (->> (if org-id
         (get org-authz (keyword org-id))
         (mapcat val org-authz)) ; FIXME should figure out org from session
       (map (partial get-permissions-by-role :organization))
       (reduce into #{})))

(def ^:private submit-restriction (restriction :application/submit))

(defn- apply-company-restrictions [{company-submitter :submit} permissions]
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

(defmacro defcontext
  "Creates an extender function for action context. Function takes only command parameter
  which is passed trough and exteded with output of the extender function. Function should
  return a map, where :context-scope and :context-roles keys are handled as special keys.
  Command :permissions are extended by resolving permissions from permission tree by
  :context-scope and :context-roles. :context-scope and :context-roles are not merged into
  command.

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

(defn- matching-context? [ctx-matcher context]
  (cond
    (map? ctx-matcher) (every? (fn [[k v]] (matching-context? v (k context))) ctx-matcher)
    (nil? ctx-matcher) true
    (set? ctx-matcher) (contains? ctx-matcher (keyword context))
    (fn? ctx-matcher) (ctx-matcher context)))

(defn get-required-permissions [{permissions :permissions} command]
  (or (util/find-first (util/fn-> :context (matching-context? command)) permissions)
      {:required [:global/not-allowed]}))
