(ns lupapalvelu.restrictions
  (:require [schema.core :refer [defschema] :as sc]
            [monger.operators :refer [$push $pull]]
            [sade.core :refer [fail]]
            [sade.schemas :as ssc]
            [sade.strings :as ss]))

(defschema AuthRestriction
  {:restriction  sc/Str
   :user         {:id       sc/Str
                  :username ssc/Username}
   :target       {:type (sc/enum "others")}})

(defn- restrict [permissions restriction]
  (disj (set permissions) restriction))

(defmulti apply-auth-restriction
  {:private true
   :arglists '([command permissions auth-restriction])}
  (fn [_ _ {{target-type :type} :target}]
    (keyword target-type)))

(defmethod apply-auth-restriction :others
  [{{user-id :id {company-id :id} :company} :user} permissions {{restrictor-id :id} :user restriction :restriction}]
  (cond-> permissions
    (not (#{user-id company-id} restrictor-id)) (restrict restriction)))

(defn apply-auth-restrictions
  ([command]
   (update command :permissions (partial apply-auth-restrictions command)))
  ([{{auth-restrictions :authRestrictions} :application :as command} permissions]
   (reduce (partial apply-auth-restriction command) permissions auth-restrictions)))

(defmulti check-auth-restriction-entry
  {:private true
   :arglists '([user restriction auth-restriction-entry])}
  (fn [_ _ {{target-type :type} :target}]
    (keyword target-type)))

(defmethod check-auth-restriction-entry :others
  [{user-id :id {company-id :id} :company} restriction auth-restriction-entry]
  (when (and (not (#{user-id company-id} (get-in auth-restriction-entry [:user :id])))
             (= (keyword restriction) (keyword (:restriction auth-restriction-entry))))
    (fail :error.permissions-restricted-by-another-user :restriction restriction)))

(defn check-auth-restriction
  "Pre check that fails if restriction is applied for user"
  [{user :user {auth-restrictions :authRestrictions} :application} restriction]
  (some (partial check-auth-restriction-entry user restriction) auth-restrictions))

(defn- restriction-as-string [restriction]
  (if (keyword? restriction)
    (ss/join "/" [(namespace restriction) (name restriction)])
    restriction))

(defn mongo-updates-for-restrict-other-auths [{user-id :id username :username} restriction]
  {$push {:authRestrictions {:restriction (restriction-as-string restriction)
                             :user        {:id user-id
                                           :username username}
                             :target      {:type "others"}}}})

(defn mongo-updates-for-remove-all-user-restrictions [{user-id :id}]
  {$pull {:authRestrictions {:user.id user-id}}})

(defn mongo-updates-for-remove-other-user-restrictions
  ([{user-id :id}]
   {$pull {:authRestrictions {:user.id user-id :target.type "others"}}})
  ([{user-id :id} restriction]
   {$pull {:authRestrictions {:user.id user-id :target.type "others" :restriction (restriction-as-string restriction)}}}))
