(ns lupapalvelu.permissions
  (:require [sade.util :as util]))

(defonce permission-tree (atom {}))

(defmacro defpermission [context-type permissions]
  (assert (map? permissions)
          "Permissions should be defnied as a map.")
  (assert (every? map? (vals permissions))
          "Permissions should be defnied as a map of maps.")
  (assert (->> (vals permissions)
               (mapcat vals)
               (every? set?))
          "Permissions should be defnied as a map of maps of sets.")
  (assert (->> (vals permissions)
               (mapcat vals)
               (apply concat)
               (map namespace)
               (every? #{(name context-type)}))
          "Namespace of the permission keyword for all permissions should match context type")
  (swap! permission-tree #(merge-with (partial merge-with into) % permissions)))

(defn get-permissions-by-role [scope role]
  (get-in @permission-tree [(keyword scope) (keyword role)] #{}))

(defn get-global-permissions [{{role :role} :user}]
  (get-permissions-by-role :global (keyword role)))

(defn get-organization-permissions [{{org-authz :orgAuthz} :user {org-id :organization} :application}]
  (->> (get org-authz (keyword org-id))
       (map (partial get-permissions-by-role :organization))
       (reduce into #{})))

(defn get-application-permissions [{{user-id :id} :user {auth :auth} :application}]
  (->> (util/find-by-id user-id auth)
       (:role)
       (get-permissions-by-role :application)))
