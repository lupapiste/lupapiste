(ns lupapalvelu.migration.combine-orgs
  (:require [lupapalvelu.mongo :as mongo]
            [clojure.set :as set]
            [clojure.string :as str]
            [sade.core :as sade]
            [monger.operators :refer [$push $exists $or $in $set]]))


(defn- add-sheriff-note! [collection id & contents]
  (mongo/update-by-id collection
                      id
                      {$push {:_sheriff-notes {:note    (str/join contents)
                                                :created (sade/now)}}}))


(defn- org-auth-kw [org-code]
  (keyword (str "orgAuthz." org-code)))


(defn- auth-exists-clause [org-code]
  {(org-auth-kw org-code) {$exists true}})


(defn- auth-in-one-of-clause [org-codes]
  {$or (map auth-exists-clause org-codes)})


(defn- find-users [org-codes]
  (mongo/select :users (auth-in-one-of-clause org-codes)))


(defn- auth-clause-for [auth org-code]
  {(org-auth-kw org-code) auth})


(defn- add-authz-to-one! [to-org-id other-org-ids sheriff-note {:keys [orgAuthz id]}]
  (let [existing-authz (-> orgAuthz
                           (get (keyword to-org-id))
                           set)
        authz-to-add   (-> (->> (-> orgAuthz
                                    (select-keys (map keyword other-org-ids))
                                    vals)
                                (apply concat))
                           set
                           (set/difference existing-authz))]
    (when (seq authz-to-add)
      (->> authz-to-add
           (map #(mongo/update-by-id :users id {$push (auth-clause-for % to-org-id)}))
           doall)
      (add-sheriff-note! :users
                         id
                         sheriff-note
                         ": Added authz "
                         (str/join ", " authz-to-add)
                         " for "
                         to-org-id))))


(defn- add-authz-to! [sheriff-note to-org-id other-org-ids]
  (->> other-org-ids
       find-users
       (map (partial add-authz-to-one! to-org-id other-org-ids sheriff-note))
       doall))


(defn- select-by-ids [collection ids]
  (->> {:_id {$in ids}}
       (mongo/select collection)))


(defn- move-scopes-to! [sheriff-note to-org-id other-org-ids]
  (let [scopes-to-add (->> other-org-ids
                           (select-by-ids :organizations)
                           (mapcat :scope))]
    (when (seq scopes-to-add)
      (->> scopes-to-add
           (map #(mongo/update-by-id :organizations
                                     to-org-id
                                     {$push {:scope %}}))
           doall)
      (add-sheriff-note! :organizations
                         to-org-id
                         sheriff-note
                         ": Added scopes from "
                         (str/join ", " other-org-ids))
      (->> other-org-ids
           (map #(mongo/update-by-id :organizations
                                     %
                                     {$set {:scope []}}))
           doall)
      (->> other-org-ids
           (map #(add-sheriff-note! :organizations
                                    %
                                    sheriff-note
                                    ": Moved scopes to "
                                    to-org-id))
           doall))))


(defn copy-statement-givers-to! [sheriff-note to-org-id other-org-ids]
  (let [givers-to-add (->> other-org-ids
                           (select-by-ids :organizations)
                           (mapcat :statementGivers))]
    (when (seq givers-to-add)
      (->> givers-to-add
           (map #(mongo/update-by-id :organizations
                                     to-org-id
                                     {$push {:statementGivers %}}))
           doall)
      (add-sheriff-note! :organizations
                         to-org-id
                         sheriff-note
                         ": Added statementGivers from "
                         (str/join ", " other-org-ids)))))


(defn- update-general-handler [general-handler-ids
                               new-general-handler-id
                               {:keys [roleId] :as handler}]
  (if (general-handler-ids roleId)
    (assoc handler :roleId new-general-handler-id)
    handler))


(defn- change-org-for-one-application! [sheriff-note
                                       general-handler-ids
                                       new-general-handler-id
                                       to-org-id
                                       {:keys [id organization handlers]}]
  (mongo/update-by-id :applications id {$set {:handlers (map (partial update-general-handler
                                                                       general-handler-ids
                                                                       new-general-handler-id)
                                                              handlers)
                                               :organization to-org-id}})
  (add-sheriff-note! :applications
                     id
                     sheriff-note
                     ": Changed organization from "
                     organization
                     " to "
                     to-org-id))


(defn- is-general-handler? [handler]
  (:general handler))


(defn- change-org-for-applications! [sheriff-note to-org-id other-org-ids]
  (let [new-general-handler-id (->> (mongo/by-id :organizations to-org-id)
                                    :handler-roles
                                    (filter is-general-handler?)
                                    first
                                    :id)
        general-handler-ids    (->> other-org-ids
                                    (select-by-ids :organizations)
                                    (mapcat :handler-roles)
                                    (filter is-general-handler?)
                                    (map :id)
                                    set)
        apps                   (mongo/select :applications {:organization {$in other-org-ids}})]
    (->> apps
         (map (partial change-org-for-one-application!
                       sheriff-note
                       general-handler-ids
                       new-general-handler-id
                       to-org-id))
         doall)))


(defn combine-organizations!
  "Combines multiple organizations."
  [sheriff-note [to-org-id & other-org-ids]]
  ;; copy all user orgAuthz to apply to the target org
  (add-authz-to! sheriff-note to-org-id other-org-ids)
  ;; remove scopes from all but first
  (move-scopes-to! sheriff-note to-org-id other-org-ids)
  (copy-statement-givers-to! sheriff-note to-org-id other-org-ids)
  (change-org-for-applications! sheriff-note to-org-id other-org-ids))


(defn separate-orgs?
  "Checks whether more than one of the given organizations have scopes defined"
  [org-ids]
  (->> org-ids
       (select-by-ids :organizations)
       (map :scope)
       (filter seq)
       count
       (< 1)))
