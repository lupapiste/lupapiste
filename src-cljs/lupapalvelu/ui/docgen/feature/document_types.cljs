(ns lupapalvelu.ui.docgen.feature.document-types
  "Group documents based on use-case (e.g. party docs)"
  (:require [lupapalvelu.next.event :refer [<sub]]
            [lupapalvelu.ui.docgen.feature.application :as app]
            [lupapalvelu.ui.docgen.feature.auth :as auth]
            [lupapalvelu.ui.docgen.feature.documents :as docs]
            [re-frame.core :as rf]))

(defn- doc-type
  [doc]
  (-> doc :schema-info :type))

(defn- doc-subtype
  [doc]
  (-> doc :schema-info :subtype))

(defn- doc-name
  [doc]
  (-> doc :schema-info :name))

(defn- doc-order
  "See `getDocumentOrder`"
  [doc]
  (let [n (or (-> doc :schema :info :order) 7)]
    (+ (* n 10000000000)
       (/ (:created doc) 1000))))

(defn- party-doc?
  "See `isPartyDoc`"
  [doc]
  (= "party" (doc-type doc)))

(defn- task-doc?
  [doc]
  (= "task" (doc-type doc)))

(defn- location-doc?
  [doc]
  (= "location" (doc-type doc)))

(defn- building-product-doc? [doc]
  (= "building-product-catalog" (doc-type doc)))

(defn- reused-building-product-doc? [doc]
  (#{"rakennustuoteluettelo-hyodynnetyt" "rakennustuoteluettelo-rakennuspaikka-hyodynnetyt"} (doc-name doc)))

(defn- climate-report-location-doc? [doc]
  (= "rakennuspaikan-ilmastoselvitys" (doc-name doc)))

(defn- climate-report-doc? [doc]
  (= "rakennuskohteen-osan-ilmastoselvitys" (doc-name doc)))

(def normal-doc?
  "Basic application documents."
  (every-pred (complement party-doc?)
              (complement building-product-doc?)
              (complement climate-report-doc?)
              (complement climate-report-location-doc?)
              (complement task-doc?)))

(defn- editable?
  "See `documentIsEditable`"
  [doc]
  (<sub [::auth/doc-auth? (:id doc) :update-doc]))

(defn doc-ids
  [docs & preds]
  (->> docs
       vals
       (filter (if (seq preds)
                 (apply every-pred preds)
                 (constantly true)))
       (map (fn [doc]
              (assoc doc ::order (doc-order doc))))
       (sort-by ::order)
       (map :id)
       (into [])))

(rf/reg-sub ::all-ids
  :<- [::docs/documents]
  (fn [docs]
    (doc-ids docs)))

(rf/reg-sub ::party-doc-ids
  :<- [::docs/documents]
  (fn [docs]
    (doc-ids docs party-doc?)))

(rf/reg-sub ::non-party-doc-ids
  :<- [::docs/documents]
  (fn [docs]
    (doc-ids docs normal-doc?)))

(def editable-doc? (every-pred normal-doc? editable?))

(rf/reg-sub ::editable-doc-ids
  :<- [::docs/documents]
  (fn [docs]
    (doc-ids docs editable-doc?)))

(rf/reg-sub ::validated-doc-ids
  :<- [::docs/documents]
  (fn [docs]
    (doc-ids docs (some-fn editable-doc?
                           party-doc?))))


;; We assume there's only one location document
(rf/reg-sub ::location-doc-id
  :<- [::docs/documents]
  (fn [docs]
    (first (doc-ids docs location-doc?))))

(rf/reg-sub ::reused-building-product-catalog
  :<- [::docs/building-product-catalogs]
  (fn [docs [_ ref-doc-id]]
    (first (doc-ids docs (fn [doc] (= (:building-ref doc) ref-doc-id)) reused-building-product-doc?))))

(rf/reg-sub ::base-building-product-catalog-doc-ids
  :<- [::docs/building-product-catalogs]
  :<- [::location-doc-id]
  (fn [[docs location-doc-id] [_ pred]]
    (doc-ids docs (fn [doc] (pred (:building-ref doc) location-doc-id)))))

(rf/reg-sub ::building-product-catalog-doc-ids
  (fn [_ _]
    @(rf/subscribe [::base-building-product-catalog-doc-ids not=])))

(rf/reg-sub ::building-product-catalog-location-doc-ids
  (fn [_ _]
    @(rf/subscribe [::base-building-product-catalog-doc-ids =])))

(rf/reg-sub ::climate-report-location-doc-ids
  :<- [::docs/documents]
  (fn [docs]
    (doc-ids docs climate-report-location-doc?)))

(rf/reg-sub ::climate-report-doc-ids
  :<- [::docs/documents]
  (fn [docs]
    (doc-ids docs climate-report-doc?)))

(rf/reg-sub ::uneditable-doc-ids
  :<- [::docs/documents]
  (fn [docs]
    (doc-ids docs normal-doc? (complement editable?))))

(rf/reg-sub ::app-and-parties-doc-ids
  :<- [::editable-doc-ids]
  :<- [::non-party-doc-ids]
  :<- [::uneditable-doc-ids]
  :<- [::app/facta-imported?]
  (fn [[editable non-party uneditable imported?]]
    (if (and imported? (seq editable))
      non-party
      uneditable)))

(rf/reg-sub ::building-product-catalogs-for-op-doc-id
  :<- [::docs/building-product-catalogs]
  (fn [catalogs [_ op-doc-id]]
    (->> catalogs
         vals
         (filter #(= (:building-ref %) op-doc-id))
         (mapv :id))))

(rf/reg-sub ::climate-reports-for-op-doc-id
  :<- [::docs/climate-reports]
  (fn [reports [_ op-doc-id]]
    (->> reports
         vals
         (filter #(= (:building-ref %) op-doc-id))
         (mapv :id))))

(rf/reg-sub ::designer-doc-ids
  :<- [::docs/documents]
  (fn [docs]
    (doc-ids docs (fn [doc]
                    (= "suunnittelija" (doc-subtype doc))))))

(rf/reg-sub ::post-verdict-party-doc-ids
  :<- [::docs/documents]
  (fn [docs]
    (doc-ids docs (fn [doc]
                    (-> doc :schema-info :post-verdict-party)))))
