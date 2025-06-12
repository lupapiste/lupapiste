(ns lupapalvelu.admin.import-export.verdict-template
  (:require [clojure.set :as set]
            [lupapalvelu.admin.import-export.impl :as impl]
            [lupapalvelu.admin.import-export.model :as model]
            [lupapalvelu.admin.import-export.util :as ie-util]
            [lupapalvelu.admin.import-export.verdict-template-settings :as import-settings]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.organization :as org]
            [lupapalvelu.pate.schemas :as pate-schemas]
            [lupapalvelu.pate.verdict-template :as vt]
            [monger.operators :refer :all]
            [sade.core :refer [fail now]]
            [sade.shared-schemas :as ssc]
            [sade.util :as util]
            [schema.core :as sc]))

(def ^:const target :verdict-template)

(defmethod impl/-organization-query [:export target]
  [_ _]
  ;; Find all organizations with verdict templates
  (when-let [org-ids (seq (mongo/distinct :verdict-templates :organization))]
    (->> (mongo/select :organizations {:_id {$in org-ids}} [:name])
         (map (juxt :id :name))
         (into {}))))

(defmethod impl/-organization-query [:import target]
  [_ _]
  ;; Find all organizations. Pate might not be enabled yet so let's not
  ;; use that as a prerequisite.
  (ie-util/export-all-organizations!))

(sc/defn get-items :- model/VerdictTemplateItems
  {:private true}
  [org-id :- org/OrgId
   item-ids :- (sc/maybe #{ssc/ObjectIdStr})]
  (->> (vt/verdict-templates org-id :id (seq item-ids) :raw? true)
       (map (juxt :id identity))
       (into {})))

(defmethod impl/-item-query target
  [_ org-id item-ids]
  (get-items org-id item-ids))

(defn- ->import-template [org-id template]
  (-> template
      (update :_sheriff-notes util/conjv {:note    (format "Copied from %s." (:organization template))
                                          :created (now)
                                          :old-id  (:id template)})
      (assoc :id (mongo/create-id) :organization org-id)
      ;; Make sure imported templates are not published automatically
      (dissoc :published)
      pate-schemas/valid-pate-saved-template!))

(defn- do-import
  [org-id templates replace?]
  (when replace?
    (vt/remove-verdict-templates org-id))
  (->> templates
       (map (partial ->import-template org-id))
       (partition-all 10)
       (mapv (partial mongo/insert-batch :verdict-templates)))
  true)

(defmethod impl/-import! target
  [_ org-id {items :items} replace?]
  (let [templates (vals items)
        unknown-categories  (delay
                              ;; Mustn't import if the org does not have the corresponding scope
                              (let [known-categories (import-settings/org->categories org-id)
                                    given-categories (set (map :category templates))]
                                (not-empty (set/difference given-categories known-categories))))
        ;; NOTE: we don't check for settings; you can import templates even if
        ;; we have no settings for them (yet).
        replacing-published (delay
                              ;; Forbid replacing if we have any published templates
                              (when replace?
                                (some->> (vt/verdict-templates org-id :published? true :projection [:name])
                                         seq
                                         (map :name)
                                         set)))]
    (cond
      (not (org/get-organization org-id [:id])) (fail :error.organization-not-found)
      (force unknown-categories)                (fail :error.invalid-categories
                                                      :text-args (force unknown-categories))
      (force replacing-published)               (fail :error.import.replacing-published-templates
                                                      :text-args (force replacing-published))
      :else                                     {:ok (do-import org-id templates replace?)})))
