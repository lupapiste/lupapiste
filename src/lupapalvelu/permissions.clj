(ns lupapalvelu.permissions
  (:require [clojure.set :as set]
            [schema.core :as sc]
            [sade.util :refer [fn->] :as util]))

(defonce permission-tree (atom {}))

(def ^:private Scope sc/Keyword)
(def ^:private Role  sc/Keyword)
(defn- permission-schema [context-type]
  (sc/constrained sc/Keyword (fn-> namespace name (= (name context-type)))
                  "Permission namespace equals context-type"))

(defn defpermissions [context-type permissions]
  (sc/validate {Scope {Role #{(permission-schema context-type)}}} permissions)
  (swap! permission-tree #(merge-with (partial merge-with into) % permissions)))

(defn get-permissions-by-role [scope role]
  (set/union
   (get-in @permission-tree [(keyword scope) (keyword role)] #{})
   (when role
     (get-in @permission-tree [(keyword scope) :roles/any]))))


(defn get-global-permissions [{{role :role} :user}]
  (get-permissions-by-role :global (keyword role)))

(defn get-organization-permissions [{{org-authz :orgAuthz} :user {org-id :organization} :application}]
  (->> (get org-authz (keyword org-id))
       (map (partial get-permissions-by-role :organization))
       (reduce into #{})))

(defn get-application-permissions [{{user-id :id} :user {auth :auth} :application}]
  (->> (filter (comp #{user-id} :id) auth)
       (map :role)
       (map (partial get-permissions-by-role :application))
       (reduce into #{})))

(defmacro defcontext
  "Creates an extender function for action context. Function takes only command parameter
  which is passed trough and exteded with output of the extender function. Function should
  return a map, where :context-scope and :context-role keys are handled as special keys.
  Command :permissions are extended by resolving permissions from permission tree by
  :context-scope and :context-role. :context-scope and :context-role are not merged into
  command.

  example:
  (defcontext thing-context [{{user-id :id} :user app :application}]
    (let [thing (app/find-user-thing user-id app)]
      {:context-scope  :thing
       :context-role   (:role thing})
       :thing          thing))

  returns a command extended with :thing and :permissions. :permissions is union of
  original command permissions and permissions for :context-role in :context-scope.

  {:user {...}
   :application {...}
   ...
   :thing {...}
   :permissions #{:global/permissions :application/permissions ... :thing/permissions}}"
  [context-name [command-param] & body]
  (let [cmd (cond
              (:as command-param)  command-param
              (map? command-param) (assoc command-param :as (gensym "cmd"))
              :else                {:as command-param})]
    `(defn ~context-name [~cmd]
       (let [ctx#   (do ~@body)
             perms# (->> (:context-roles ctx#)
                         (map (partial get-permissions-by-role (:context-scope ctx#)))
                         (reduce into #{}))]
         (-> (merge ~(:as cmd) (dissoc ctx# :context-scope :context-roles :permissions))
             (update :permissions set/union perms#))))))

(def ContextMatcher
  (sc/conditional
   set?     #{sc/Keyword}
   fn?      sc/Any
   map?     {sc/Keyword (sc/recursive #'ContextMatcher)}))

(defn- known-permission? [permission]
  (contains? (->> (vals @permission-tree)
                  (mapcat vals)
                  (reduce into #{:global/not-allowed}))
             permission))

(def RequiredPermission
  (sc/constrained sc/Keyword known-permission?))

(defn- matching-context? [ctx-matcher context]
  (cond
    (map? ctx-matcher) (every? (fn [[k v]] (matching-context? v (k context))) ctx-matcher)
    (nil? ctx-matcher) true
    (set? ctx-matcher) (contains? ctx-matcher (keyword context))
    (fn? ctx-matcher)  (ctx-matcher context)))

(defn get-required-permissions [{permissions :permissions :as command-meta} command]
  (util/find-first (util/fn-> :context (matching-context? command)) permissions))
