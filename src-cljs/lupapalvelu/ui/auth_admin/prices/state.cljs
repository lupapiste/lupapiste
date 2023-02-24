(ns lupapalvelu.ui.auth-admin.prices.state
  (:require [lupapalvelu.invoices.shared.schemas :refer [CatalogueRow]]
            [lupapalvelu.ui.common :as common]
            [rum.core :as rum]
            [sade.shared-strings :as ss]
            [sade.shared-util :as util :include-macros true]
            [schema-tools.core :as st]
            [schema.core :as sc]))

(defonce state* (atom {:view :by-rows
                       :selected-catalogue nil}))

(defn- state-cursor [key]
  (rum/cursor-in state* [key]))

(def org-catalogue-type       (state-cursor :org-catalogue-type))
(def org-id                   (state-cursor :org-id))
(def org-operations           (state-cursor :org-operations))
(def catalogues               (state-cursor :catalogues))
(def selected-catalogue       (state-cursor :selected-catalogue))
(def no-billing-periods       (rum/cursor-in selected-catalogue [:no-billing-periods]))
(def view                     (state-cursor :view))
(def auth-trigger             (state-cursor :auth-trigger))
(def edit-state               (state-cursor ::edit-state))
(def product-constant-toggles (state-cursor ::product-constant-toggles))
(def row-order                (rum/cursor-in edit-state [::row-order]))
(def row-map                  (rum/cursor-in edit-state [::row-map]))
(def valid-period-errors      (rum/cursor-in edit-state [::valid-period]))

(defn toggle-valid-from-error [flag]
  (when @edit-state
    (if flag
      (swap! edit-state assoc :valid-from-error true)
      (swap! edit-state dissoc :valid-from-error))))

(defn check-field [field-path value]
  (let [value     (util/pcond-> value string? ss/trim)
        is-blank? (ss/blank? value)
        required  #{:text :unit :price-per-unit :discount-percent}]
    (cond
      (and is-blank? (required (first field-path)))
      :required

      (and (not is-blank?) (sc/check (st/get-in CatalogueRow field-path) value))
      :error)))

(defn check-row
  "Returns validation issues for row fields. An issue can be eiher `:required` or `:error`."
  [row]
  (into {}
        (for [field [:text :unit :price-per-unit :discount-percent
                     :max-total-price :min-total-price]
              :let [state (check-field [field] (field row))]
              :when state]
          [field state])))

(defn tomorrow-midnight-ts []
  (.valueOf (doto (js/moment())
     (.add 1 "days")
     (.startOf "day"))))

(defn validate-valid-period? []
  (let [{:keys [valid-from
                valid-until]} (some-> edit-state deref)
        errors?               (some->> valid-period-errors
                                       deref
                                       vals
                                       (filter #{:format})
                                       seq)]
    (cond
      errors?                     false
      (or (nil? valid-from)
          (nil? valid-until))     true

      (<= valid-from valid-until) (reset! valid-period-errors {})
      :else
      (not (reset! valid-period-errors {:valid-from  :value
                                        :valid-until :value})))))

(defn update-valid-period
  [datekey timestamp datestring]
  (swap! valid-period-errors assoc datekey
         (when (and (ss/not-blank? datestring) (nil? timestamp))
           :format))
  (swap! edit-state assoc datekey timestamp))


(defn update-edit-state
  [{:keys [rows valid-from valid-until name meta] :as catalog}]
  (if catalog
    (do
      ;; The first swap causes Warning: Cannot update during an existing state transition
      (swap! edit-state assoc
               :valid-from valid-from :valid-until valid-until
               :meta meta :name name)
        (reset! row-order (map :id rows))
        (when-let [new-rows (some->> rows
                                     (remove #(get @row-map (:id %)))
                                     seq
                                     (map #(assoc % ::validation (check-row %)))
                                     (map (juxt :id identity))
                                     (into {}))]
          (swap! row-map merge new-rows)))
    (reset! edit-state nil)))

(defn field-status [row-id field-path]
  {:value      (rum/react (rum/cursor-in row-map (concat [row-id] field-path)))
   :validation (rum/react (rum/cursor-in row-map (concat [row-id ::validation] field-path)))})

(defn update-field
  "Returns true if the value was changed."
  [row-id field-path value]
  (when (common/reset-if-needed! (rum/cursor-in row-map (cons row-id field-path))
                                 value)
    (common/reset-if-needed! (rum/cursor-in row-map (concat [row-id ::validation] field-path))
                             (check-field field-path value))
    true))


(defn row-field-status [row field]
  (get-in row [::validation field]))

(defn edit-state-valid? []
  (let [rm (rum/react row-map)]
    (and (validate-valid-period?)
         (every? (fn [row-id]
                   (not-any? identity (vals (get-in rm [row-id ::validation]))))
                 (rum/react row-order)))))

(defn selected-catalogue-id []
  (:id @selected-catalogue))

(defn set-selected
  ([catalogue init-edit-state?]
   (reset! selected-catalogue catalogue)
   (update-edit-state (when init-edit-state? catalogue)))
  ([catalogue]
   (set-selected catalogue false)))

(defn draft? [catalogue]
  (boolean (= (:state catalogue) "draft")))

(defn set-view [new-view]
  (reset! view new-view))

(defn auth? [action]
  (boolean (js/lupapisteApp.models.globalAuthModel.ok (name action))))

(defn react-auth? [action]
  (rum/react auth-trigger)
  (auth? action))

(defn refresh-auth
  ([callback]
   (js/lupapisteApp.models.globalAuthModel.refreshWithCallback (clj->js {:organizationId @org-id})
                                                               (fn []
                                                                 (reset! auth-trigger (gensym "auth-trigger"))
                                                                 (when callback (callback)))))
  ([]
   (refresh-auth nil)))


(defn set-no-billing-period-start [period-id date-str]
  (swap! no-billing-periods assoc-in [period-id :start] date-str))

(defn set-no-billing-period-end [period-id date-str]
  (swap! no-billing-periods assoc-in [period-id :end] date-str))

(defn remove-no-billing-period [period-id]
  (swap! no-billing-periods dissoc period-id))
