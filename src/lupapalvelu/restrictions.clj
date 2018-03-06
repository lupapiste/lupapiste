(ns lupapalvelu.restrictions
  (:require [schema.core :refer [defschema] :as sc]
            [sade.schemas :as ssc]))

(defschema AuthRestriction
  {:restriction  sc/Str
   :user         {:id       sc/Str
                  :username ssc/Username}
   :target       {:type (sc/enum "others")}})

(defn- restrict [permissions restriction]
  (disj (set permissions) restriction))

(defmulti apply-auth-restriction
  {:arglists '([command permissions auth-restriction])}
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
