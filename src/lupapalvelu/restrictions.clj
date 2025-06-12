(ns lupapalvelu.restrictions
  (:require [schema.core :refer [defschema] :as sc]
            [monger.operators :refer [$push $pull]]
            [sade.core :refer [fail]]
            [sade.schemas :as ssc]
            [sade.strings :as ss]
            [sade.util :as util]))

(defschema AuthRestriction
  {:restriction  sc/Str
   :user         {:id       sc/Str
                  :username ssc/Username}
   ;; Currently there is only restrictions that are applied to other
   ;; users in application auth. Using different target restrictions
   ;; can be applied for example single users or companies.
   :target       {:type (sc/enum "others")}})

(defn- restrict
  "Applies single restriction in permissions."
  [permissions restriction]
  (disj (set permissions) (keyword restriction)))

(defmulti apply-auth-restriction
  "Applies single restriction in permissions if conditions apply."
  {:private true
   :arglists '([command permissions auth-restriction-entry])}
  (fn [_ _ {{target-type :type} :target}]
    (keyword target-type)))

(defmethod apply-auth-restriction :others
  [{{user-id :id {company-id :id} :company} :user} permissions {{restrictor-id :id} :user restriction :restriction}]
  (cond-> permissions
    (not (#{user-id company-id} restrictor-id)) (restrict restriction)))

(defn apply-auth-restrictions
  "Applies restrictions defined in application :authRestrictions permissions.
  Every restriction  that is applied removes corresponding permissions. Permissions
  can be given under command :permissions key or as separate array."
  ([command]
   (update command :permissions (partial apply-auth-restrictions command)))
  ([{{auth-restrictions :authRestrictions} :application :as command} permissions]
   (reduce (partial apply-auth-restriction command) permissions auth-restrictions)))

(defmulti check-auth-restriction-entry
  "Check that fails if restriction is applied with given conditions."
  {:private true
   :arglists '([user restriction auth-restriction-entry])}
  (fn [_ _ {{target-type :type} :target}]
    (keyword target-type)))

(defmethod check-auth-restriction-entry :others
  [{user-id :id {company-id :id} :company} restriction auth-restriction-entry]
  (when (and (not (#{user-id company-id} (get-in auth-restriction-entry [:user :id])))
             (= (keyword restriction) (keyword (:restriction auth-restriction-entry))))
    (fail :error.permissions-restricted-by-another-user
          :restriction restriction
          :user (:user auth-restriction-entry))))

(defn- enrich-with-auth-info [{:keys [application]} entry]
  (let [{auth-type :type :as auth} (util/find-by-id (get-in entry [:user :id]) (:auth application))]
    (cond-> entry
      (:user entry)    (assoc-in [:user :type] (if (= :company (keyword auth-type)) :company :person))
      (:lastName auth) (assoc-in [:user :name] (ss/join " " [(:firstName auth) (:lastName auth)]))
      (:name auth)     (assoc-in [:user :name] (:name auth)))))

(defn check-auth-restriction
  "Pre check that fails if restriction is applied for user."
  [{user :user {auth-restrictions :authRestrictions} :application permissions :permissions :as command} restriction]
  (when-not (get permissions (keyword restriction)) ; User can get permissions from different sources. Restriction is only applied to auth array.
    (->> (some (partial check-auth-restriction-entry user restriction) auth-restrictions)
         (enrich-with-auth-info command))))

(defn- restriction-as-string
  "Monger drops namespace part of keywords. To store them with namespace part,
  keywords must be converted as strings."
  [restriction]
  (if (keyword? restriction)
    (ss/join "/" [(namespace restriction) (name restriction)])
    restriction))

(defn mongo-updates-for-restrict-other-auths
  "Return mogno update for adding restriction that is applied to other users."
  [{user-id :id username :username} restriction]
  {$push {:authRestrictions {:restriction (restriction-as-string restriction)
                             :user        {:id user-id
                                           :username username}
                             :target      {:type "others"}}}})

(defn mongo-updates-for-remove-all-user-restrictions
  "Returns mongo updates for removing all restrictions that are set by current user."
  [{auth-id :id}]
  (when auth-id
    {$pull {:authRestrictions {:user.id auth-id}}}))

(defn mongo-updates-for-remove-other-user-restrictions
  "Returns mongo updates for removing restrictions that are applied to other users.
  If restriction is given, only mathing restrictions are removed."
  ([{auth-id :id}]
   (when auth-id
     {$pull {:authRestrictions {:user.id auth-id :target.type "others"}}}))
  ([{auth-id :id} restriction]
   (when auth-id
     {$pull {:authRestrictions {:user.id auth-id :target.type "others" :restriction (restriction-as-string restriction)}}})))

(defn check-auth-restriction-is-enabled-by-user
  "Pre for checking that auth-restriction is anebled by current user."
  [target-type restriction {{auth-restrictions :authRestrictions} :application {user-id :id {company-id :id} :company} :user}]
  (when (not-any? #(and (= (keyword target-type) (keyword (get-in % [:target :type])))
                        (= (keyword restriction) (keyword (get-in % [:restriction])))
                        (#{user-id company-id} (get-in % [:user :id])))
                  auth-restrictions)
    (fail :error.auth-restriction-not-enabled)))
