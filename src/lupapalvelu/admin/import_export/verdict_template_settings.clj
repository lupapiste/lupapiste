(ns lupapalvelu.admin.import-export.verdict-template-settings
  (:require [clojure.set :as set]
            [lupapalvelu.admin.import-export.impl :as impl]
            [lupapalvelu.admin.import-export.model :as model]
            [lupapalvelu.admin.import-export.util :as ie-util]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.organization :as org]
            [lupapalvelu.pate.schema-util :as pate-su]
            [monger.operators :refer [$exists $nin $not $set $size]]
            [sade.core :refer [fail fail!]]
            [sade.util :as util]
            [schema.core :as sc]))

(def ^:const target :verdict-template-settings)

(defmethod impl/-organization-query [:export target]
  [_ _]
  ;; Find all organizations with verdict templates
  (->> (mongo/select :organizations {:verdict-settings {$exists true $not {$size 0}}} [:name])
       (map (juxt :id :name))
       (into {})))

(defmethod impl/-organization-query [:import target]
  [_ _]
  ;; Find all organizations. Pate might not be enabled yet so let's not
  ;; use that as a prerequisite.
  (ie-util/export-all-organizations!))

(sc/defn get-items :- model/VerdictTemplateSettingsItems
  {:private true}
  [org-id :- org/OrgId
   item-ids :- (sc/maybe #{sc/Str})]
  (->> (or (mongo/by-id :organizations org-id [:verdict-settings])
           (fail! :error.organization-not-found))
       :verdict-settings
       (map (fn [[category settings]]
              [(name category) settings]))
       (filter (if (seq item-ids)
                 (comp item-ids first)
                 identity))
       (into {})))

(defmethod impl/-item-query target
  [_ org-id item-ids]
  (get-items org-id item-ids))

(defn- make-import-query
  [settings replace?]
  (if replace?
    {$set {:verdict-settings settings}}
    {$set (->> settings
               (map (fn [[category values]]
                      [(util/kw-path :verdict-settings category) values]))
               (into {}))}))

(defn- do-import
  [{:keys [id]} settings replace?]
  (mongo/update-by-id :organizations id (make-import-query settings replace?))
  true)

(defn org->categories
  [org-or-id]
  (->> (if (string? org-or-id)
         (mongo/by-id :organizations org-or-id [:scope])
         org-or-id)
       :scope
       (mapcat (comp pate-su/permit-type->categories :permitType))
       (map name)
       set))

(defmethod impl/-import! target
  [_ org-id {settings :items} replace?]
  (let [org                (mongo/by-id :organizations org-id [:scope
                                                               :verdict-settings])
        import-categories  (set (keys settings))
        unknown-categories (delay
                             ;; Mustn't import if the org does not have the corresponding scope(s)
                             ;; e.g., we can't import R settings to a YA org
                             (let [known-categories (org->categories org)]
                               (set/difference import-categories known-categories)))
        settings-in-use    (delay
                             ;; Mustn't accidentally overwrite existing settings if we're not explicitly replacing.
                             (when-not replace?
                               (let [in-use (->> org :verdict-settings keys (map name) set)]
                                 (set/intersection in-use import-categories))))
        templates-in-use   (delay
                             ;; Mustn't replace settings if we have templates that would be orphaned
                             ;; (= left without any settings).
                             ;; But you _can_ completely replace settings, even if they have templates that
                             ;; refer to them.
                             (when replace?
                               (mongo/any? :verdict-templates
                                           {:organization org-id
                                            :category {$nin import-categories}})))]
    (cond
      (nil? org)                       (fail :error.organization-not-found)
      (seq (force unknown-categories)) (fail :error.invalid-categories :text-args (force unknown-categories))
      (seq (force settings-in-use))    (fail :error.import.settings-in-use :text-args (force settings-in-use))
      (force templates-in-use)         (fail :error.import.templates-in-use)
      :else                            {:ok (do-import org settings replace?)})))
