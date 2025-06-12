(ns lupapalvelu.admin.import-export.price-catalogue
  (:require [lupapalvelu.admin.import-export.impl :as impl]
            [lupapalvelu.admin.import-export.model :as model]
            [lupapalvelu.admin.import-export.util :as ie-util]
            [lupapalvelu.invoice-api :as invoice-api]
            [lupapalvelu.invoices.schemas :as invoice-schemas]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.organization :as org]
            [monger.operators :refer [$in]]
            [monger.result :as mresult]
            [sade.core :refer [fail]]
            [sade.shared-schemas :as ssc]
            [schema.core :as sc]))

(def ^:const target :price-catalogue)

(defmethod impl/-organization-query [:export target]
  [_ _]
  ;; Find all organizations with published price catalogues
  (let [org-ids (mongo/distinct :price-catalogues :organization-id {:state "published"})]
    (->> (mongo/select :organizations {:_id {$in org-ids}} [:name])
         (map (juxt :id :name))
         (into {}))))

(defmethod impl/-organization-query [:import target]
  [_ _]
  ;; Find all organizations with invoicing
  (->> (mongo/select :organizations {:scope.invoicing-enabled true} [:name :scope])
       ;; Only R and YA scopes support price catalogues
       (remove invoice-api/invalid-org?)
       ie-util/format-organizations))

(sc/defn get-items :- model/PriceCatalogueItems
  {:always-validate true
   :private         true}
  [org-id :- org/OrgId
   item-ids :- (sc/maybe #{ssc/ObjectIdStr})]
  (->> (mongo/select :price-catalogues (cond-> {:organization-id org-id
                                                :state           "published"}
                                         (seq item-ids) (assoc :_id {$in item-ids})))
       (map (juxt :id identity))
       (into {})))

(defmethod impl/-item-query target
  [_ org-id item-ids]
  (get-items org-id item-ids))

(sc/defn draftify :- invoice-schemas/PriceCatalogueDraft
  "Convert an exported published catalogue into an importable draft"
  {:always-validate true}
  [dst-org-id :- org/OrgId
   catalogue :- invoice-schemas/PriceCatalogue]
  (-> catalogue
      (assoc :id (mongo/create-id)
             :organization-id dst-org-id
             :state "draft")
      (dissoc :valid-from :valid-until)
      (update :rows (fn [rows]
                      (->> rows
                           (mapv (fn [row]
                                   (assoc row :id (mongo/create-id)))))))))

(defn- draftify-all
  [dst-org-id catalogues]
  (mapv (partial draftify dst-org-id) catalogues))

(defn- do-import
  [dst-org-id catalogues replace?]
  (let [to-insert (->> catalogues
                       vals
                       (draftify-all dst-org-id))]
    (when replace?
      (mongo/remove-many :price-catalogues {:organization-id dst-org-id}))
    (or (some->> to-insert
                 seq
                 (mongo/insert-batch :price-catalogues)
                 (mresult/acknowledged?))
        true)))

(defn- has-published-catalogues?
  [org-id]
  (mongo/any? :price-catalogues {:organization-id org-id
                                 :state           "published"}))

(defmethod impl/-import! target
  [_ org-id {catalogues :items} replace?]
  (let [org             (mongo/select-one :organizations {:_id                     org-id
                                                          :scope.invoicing-enabled true}
                                          [:scope])
        catalogue-types (set (map (comp keyword :type val) catalogues))
        invalid-types?  (delay (some (fn [pt] (not (org/has-permit-type? org pt))) catalogue-types))
        ;; Mustn't replace any published price catalogues
        published?      (delay (and replace? (has-published-catalogues? org-id)))]
    (cond
      (nil? org)                     (fail :error.organization-not-found)
      (invoice-api/invalid-org? org) (fail :error.price-catalogue.unsupported-permit-type)
      (force invalid-types?)         (fail :error.price-catalogue.incorrect-type)
      (force published?)             (fail :error.price-catalogue.cannot-replace-published)
      :else                          {:ok (do-import org-id catalogues replace?)})))
