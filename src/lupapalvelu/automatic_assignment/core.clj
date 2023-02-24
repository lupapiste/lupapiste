(ns lupapalvelu.automatic-assignment.core
  (:require [clojure.core.memoize :as memo]
            [clojure.set :as set]
            [lupapalvelu.assignment :as assignment :refer [Recipient]]
            [lupapalvelu.attachment.type :as att-type]
            [lupapalvelu.automatic-assignment.schemas :as schemas
             :refer [Criteria Filter ResolverOptions ForemanRole]]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.states :as states]
            [lupapalvelu.user :as usr]
            [monger.operators :refer :all]
            [sade.core :refer :all]
            [sade.schemas :as ssc]
            [sade.strings :as ss]
            [sade.util :as util]
            [schema.core :as sc :refer [defschema]])
  (:import [java.util.regex Pattern]))

(def FILTERS :automatic-assignment-filters)
(def TTL (* 60 1000)) ; One minute

(defn get-organization
  "Reinvented here in order to avoid cyclical dependency."
  ([org-id]
   (get-organization org-id []))
  ([org-id projection]
   (mongo/by-id :organizations org-id projection)))

(defn organization-attachment-types [org-id]
  ;; TODO instead of getting all attachment types for all scopes you should get all attachment types
  ;; of all active operations. This listing is used in attachment filter and probably limiting only
  ;; those types organization actually use is relatively small improvement to UX
  (->> att-type/attachment-types-as-type-array
       (map (fn [{:keys [type-group type-id]}]
              (name (util/kw-path type-group type-id))))
       set))

(def cached-attachment-types (memo/ttl organization-attachment-types :ttl/threshold TTL))

(defn authority-in-organization
  "Return `usr/summary` if `user-id` refers to the authority in `org-id` organization. Nil otherwise."
  [org-id user-id]
  (usr/summary (mongo/select-one :users
                                 {:_id                            user-id
                                  :enabled                        true
                                  :role                           "authority"
                                  (util/kw-path :orgAuthz org-id) "authority"})))

(defn find-filter [org-id filter-id]
  (some->> (get-organization org-id [FILTERS])
           FILTERS
           (util/find-by-id filter-id)))

(defn command->filter [command]
  (let [{:keys [organizationId filter-id]} (:data command)]
    (find-filter organizationId filter-id)))

(defn filter-exists
  "Pre-check that fails if the filter is not present in the organization."
  [command]
  (let [{:keys [filter-id]} (:data command)]
    (when filter-id
      (when-not (command->filter command)
        (fail :error.automatic-assignment-filter-not-found)))))

(defn upsert-filter-exists
  "Filter must exist if the id is present"
  [{data :data}]
  (when-let [filter-id (get-in data [:filter :id])]
    (when-not (find-filter (:organizationId data) filter-id)
      (fail :error.automatic-assignment-filter-not-found))))

(defn commit [command {:keys [result updates]}]
  (let [{:keys [organizationId]} (:data command)]
    (mongo/update-by-id :organizations organizationId updates)
    result))

(defn commit-filter [{:keys [created data]} fltr]
  (let [fltr (assoc fltr :modified created)]
    (when (sc/check Filter fltr)
      (fail! :error.automatic-assignment-filter.bad-data))
    (mongo/update-by-query :organizations
                           {:_id    (:organizationId data)
                            FILTERS {$elemMatch {:id (:id fltr)}}}
                           {$set {(util/kw-path FILTERS :$) fltr}})
    fltr))

(defn delete-filter [filter-id]
  {:updates {$pull {FILTERS {:id filter-id}}}})

(defn resolve-organization [org-or-id projection]
  (util/pcond-> org-or-id
    string? (get-organization projection)))

(defn resolve-org-id [org-or-id]
  (util/pcond-> org-or-id
    map? :id))

(defmulti process-filter-field
  "Processeses and validates the field value. Processing is needed if (parts of) the value
  are outdated. For example, if an organization no longer supports an operation. For some
  fields (e.g., target fields) processing is not required, but validation is
  needed. Validation `fail!`s if needed."
  (fn [_ field _] (keyword field)))

(defmethod process-filter-field :default [_ _ value]
  ;; Used for fields that do not require any additional processing or validation apart
  ;; from the the schema check.
  value)

(defmethod process-filter-field :name
  [_ _ value]
  (ss/trim value))

(defmethod process-filter-field :areas
  [org-or-id _ value]
  (let [area-ids (some->> (resolve-organization org-or-id [:areas-wgs84])
                          :areas-wgs84
                          :features
                          (map :id)
                          set)]
    (filter (partial contains? area-ids) value)))

(defmethod process-filter-field :operations
  [org-or-id _ value]
  (let [ops (some->> (resolve-organization org-or-id [:selected-operations])
                          :selected-operations
                          set)]
    (filter (partial contains? ops) value)))

(defmethod process-filter-field :attachment-types
  [org-or-id _ value]
  (let [att-types (cached-attachment-types (resolve-org-id org-or-id))]
    (filter (partial contains? att-types) value)))

(defmethod process-filter-field :notice-forms
  [org-or-id _ value]
  (let [forms (some->> (resolve-organization org-or-id [:notice-forms])
                       :notice-forms
                       (map (fn [[k v]]
                              (when (:enabled v)
                                (name k))))
                       (remove nil?)
                       set)]
    (filter (partial contains? forms) value)))

(defn organization-handler-role-ids [organization]
  (some->> organization
           :handler-roles
           (remove :disabled)
           (map :id)
           seq
           set))

(defmethod process-filter-field :handler-role-id
  [org-or-id _ value]
  (if (some-> (resolve-organization org-or-id [:handler-roles])
              organization-handler-role-ids
              (contains? value))
    value
    (fail! :error.automatic-assignment-filter.handler-role-id)))

(defmethod process-filter-field :user-id
  [org-or-id _ value]
  (if (authority-in-organization (resolve-org-id org-or-id) value)
    value
    (fail! :error.automatic-assignment-filter.user-id)))

(defn process-filter [organization fltr]
  (reduce-kv (fn [acc k v]
               (let [v (if (map? v)
                         (process-filter organization v)
                         (process-filter-field organization k v))]
                 (cond-> acc
                   (util/fullish? v) (assoc k v))))
             {}
             fltr))

(defn upsert-filter [{:keys [data created] :as command}]
  (let[{org-id :organizationId
        fltr   :filter} (ss/trimwalk data)
       {filter-id :id
        :as       fltr} (process-filter (get-organization org-id [:areas-wgs84 :selected-operations
                                                                  :notice-forms :handler-roles])
                                        fltr)]
    (if filter-id
      (commit-filter command fltr)
      (let [fltr (sc/validate Filter (assoc fltr
                                            :id (mongo/create-id)
                                            :modified created))]
        (mongo/update-by-id :organizations org-id {$push {FILTERS fltr}})
        fltr))))

(defmulti match-criteria
  "Tri-state matcher for an individual criteria. Return value is either boolean (matches or
  not) or nil (criteria not applicable and thus ignored)"
  (fn [field-key field-value options]
    field-key))

;; Areas
;; Match: if the application is within any of the filter areas.
;; No match: if the application is outside of the filter areas
;; Ignored: there are no areas defined or the filter areas have been removed from the
;; organization.
;; The spatial resolution is done with mongo query.
(defmethod match-criteria :areas
  [_ areas {:keys [application organization]}]
  (when-let [features (seq (filter (fn [a]
                                     (contains? (set areas) (:id a)))
                                   (some-> organization :areas-wgs84 :features flatten)))]
    (boolean (mongo/select-one :applications
                               {:_id (:id application)
                                $or  (for [{:keys [geometry]} features]
                                       {:location-wgs84 {$geoWithin {"$geometry" geometry}}})}))))

;; Operations
;; Match/No match: if the application primary operation is listed in the filter.
;; Ignored: no operations in the filter
(defmethod match-criteria :operations
  [_ operations {:keys [application]}]
  (when (seq operations)
    (contains? (set operations) (-> application :primaryOperation :name))))

;; Attachment-types
;; Match: The attachment is listed in the filter attachment types
;; No match: The attachment not listed or no attachment types in the filter.
;; Ignored: Attachment-type is nil (not triggered by attachment)
(defmethod match-criteria :attachment-types
  [_ att-types {:keys [attachment-type]}]
  (some->> (cond
             (string? attachment-type) attachment-type
             (map? attachment-type)    (str (:type-group attachment-type)
                                            "."
                                            (:type-id attachment-type)))
           (contains? (set att-types))))

;; Notice-forms
;; Match: The notice-form is listed in the filter
;; No match: The notice form is not listed or the list is empty.
;; Ignored: No notice-form-type (not triggered by notice-form).
(defmethod match-criteria :notice-forms
  [_ notice-forms {:keys [notice-form-type]}]
  (when notice-form-type
    (contains? (set notice-forms) notice-form-type)))

;; Foreman-roles
;; Match: Foreman-role is listed in the filter.
;; No match: Foreman role is not listed or the filter role list is empty.
;; Ignored: No foreman-role (not triggered by foreman)
(defmethod match-criteria :foreman-roles
  [_ roles {:keys [foreman-role]}]
  (when foreman-role
    (contains? (set roles) (ss/lower-case foreman-role))))

;; Handler-role-id
;; Match/no match: Filter's handler-role-id criteria is assigned in the application.
;; It does not matter, whether the role is currently active in the organization or not.
;; Ignored: No handler-role-id criteria.
;; Note: Handler-role-id criteria is separate from the handler-role-id recipient.
(defmethod match-criteria :handler-role-id
  [_ handler-role-id {:keys [application]}]
  (when handler-role-id
    (boolean (some #(= handler-role-id (:roleId %))
                   (:handlers application)))))

(defn wildcard-matches?
  "True if `wildcard` string (e.g., *hello*) matches `target`. The wildcard must match the
  whole target. The matching is trimmed and case-insensitive. If either is blank, no
  match."
  [wildcard target]
  (boolean (when (not-any? ss/blank? [wildcard target])
             (when-let [pattern (some->> (ss/split (ss/trim wildcard) #"\*" -1)
                                         (map #(Pattern/quote %))
                                         (ss/join ".*")
                                         (str "(?i)")
                                         re-pattern)]
               (re-matches pattern (ss/trim target))))))

;; Reviews
;; Match/no match: Review name (taskname) matches a filter's review definition.
;; Ignored: No review-name (not triggered by review subscription).
(defmethod match-criteria :reviews
  [_ reviews {:keys [review-name]}]
  (when (ss/not-blank? review-name)
    (boolean (some #(wildcard-matches? % review-name) reviews))))

(defn filter-matches? [fltr options]
  (not-any? (fn [k]
              (false? (match-criteria k (get-in fltr [:criteria k]) options)))
            (map :k (keys Criteria))))

(defn top-matching-filters [{:keys [organization] :as options}]
  (when-let [ranked (some->> (FILTERS organization)
                             (filter #(filter-matches? % options))
                             (group-by :rank)
                             not-empty)]
    (let [top-rank (apply max (keys ranked))]
      (not-empty (get ranked top-rank)))))

(sc/defn recipient--application-handler-role
  [{:keys [target]} :- Filter {:keys [application]} :- ResolverOptions]
  (when-let [role-id (:handler-role-id target)]
    (when-let [handler (util/find-by-key :roleId role-id (:handlers application))]
      {:handler-role-id role-id
       :handler         handler})))

(sc/defn recipient--user-id
  [{:keys [target]} :- Filter {:keys [organization]} :- ResolverOptions]
  (when-let [user-id (:user-id target)]
    (if-let [user (authority-in-organization (:id organization) user-id)]
      {:user user}
      {:error :bad-user})))

(sc/defn recipient--organization-handler-role
  [{:keys [target]} :- Filter {:keys [organization]} :- ResolverOptions]
  (when-let [role-id (:handler-role-id target)]
    (if (contains? (organization-handler-role-ids organization) role-id)
      {:handler-role-id role-id}
      {:error :bad-role-id})))

(defschema FilterRecipient
  "Filter resolution result. Defined here in order to avoid cyclic dependency."
  {:filter-id                      ssc/ObjectIdStr
   :filter-name                    ssc/NonBlankStr
   (sc/optional-key :filter-email) schemas/Email
   (sc/optional-key :recipient)    Recipient})

(sc/defn ^:always-validate resolve-recipient :- (sc/maybe FilterRecipient)
  [options :- ResolverOptions fltr :- Filter]
  (let [fltr-base     (-> fltr
                      (select-keys [:id :name :email])
                      (set/rename-keys {:id    :filter-id
                                        :name  :filter-name
                                        :email :filter-email}))
        {:keys [error handler-role-id handler user]
         :as   found} (or (recipient--application-handler-role fltr options)
                          (recipient--user-id fltr options)
                          (recipient--organization-handler-role fltr options))]
    (cond
      error        nil
      (nil? found) fltr-base
      :else        (util/assoc-when fltr-base
                                    :recipient (cond
                                                 handler-role-id (assignment/create-recipient handler-role-id handler)
                                                 user            user)))))

(sc/defn ^:always-validate resolve-filters :- (sc/maybe [FilterRecipient])
  "List of `FilterRecipient` maps for each matching filter, otherwise nil. Note that
  `:recipient` can be nil."
  ([{:keys [application] :as options} :- ResolverOptions]
   (when (util/includes-as-kw? states/all-but-draft (:state application))
     (some->> (top-matching-filters options)
              (map (partial resolve-recipient options))
              (remove nil?)
              seq)))
  ([{:keys [application organization]} & kvs]
   (resolve-filters (assoc (apply hash-map kvs)
                          :application  application
                          :organization (force organization)))))

(sc/defn ^:always-validate resolve-foreman-role :- ForemanRole
  "Resolves the given foreman role (case insensitive) and falls back to the unknown role."
  [foreman-role]
  (get (set schemas/foreman-roles) (ss/lower-case foreman-role) schemas/UNKNOWN-FOREMAN-ROLE))
