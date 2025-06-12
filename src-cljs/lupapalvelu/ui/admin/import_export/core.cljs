(ns lupapalvelu.ui.admin.import-export.core
  (:require [clojure.set :as set]
            [lupapalvelu.admin.import-export.model :as model]
            [lupapalvelu.next.event :refer [<sub >evt]]
            [lupapalvelu.ui.admin.import-export.components :as ie-components]
            [lupapalvelu.ui.admin.import-export.impl :as impl]
            [lupapalvelu.ui.admin.import-export.inspection-summary]
            [lupapalvelu.ui.admin.import-export.operations-attachment]
            [lupapalvelu.ui.admin.import-export.phrase]
            [lupapalvelu.ui.admin.import-export.phrase-category]
            [lupapalvelu.ui.admin.import-export.price-catalogue]
            [lupapalvelu.ui.admin.import-export.subs :as subs]
            [lupapalvelu.ui.admin.import-export.verdict-template]
            [lupapalvelu.ui.admin.import-export.verdict-template-settings]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.components :as components]
            [lupapalvelu.ui.components.input :as ui-input]
            [lupapalvelu.ui.dom :as ui-dom]))

(def mode-items (->> model/modes
                     (mapv (fn [mode]
                             (set/rename-keys mode {:id :value})))))

(def target-items (->> model/targets
                       (mapv (fn [target]
                               (set/rename-keys target {:id :value})))))

(defmulti render-mode
  "Render controls for a particular mode"
  {:arglists '([mode])}
  identity)

(defn- target-buttons
  "User decides which type of items we are importing/exporting"
  []
  [:div
   [:h2 "Kohde"]
   [:div.flex
    [components/toggle-group
     (if-let [target (<sub [::subs/selected-target])]
       #{target} #{})
     {:items     target-items
      :prefix    :person-vs-company
      :disabled? (<sub [::subs/import-uploaded?])
      :radio?    true
      :callback  #(>evt [::subs/target-selected %])}]]])

(defn- render-import-preview
  "Show organization selector and preview of the import"
  []
  (let [target  (<sub [::subs/selected-target])
        dst-org (<sub [::subs/selected-organization])]
    [:div.flex.flex--column.flex--gap2
     [:h2 "Valitse kohdeorganisaatio"]
     [ie-components/organization-selector]
     (when dst-org
       [:<>
        (when-let [items (<sub [::subs/items])]
          [:<>
           [:h2 "Nykyiset tiedot"]
           [impl/render-items target items]])
        (when-let [items (<sub [::subs/import-items])]
          [:<>
           [:h2 "Tuotavat tiedot"]
           [:span "Lähdeorganisaatio: " (<sub [::subs/import-org])]
           [:span "Kohdeorganisaatio: " dst-org]
           [:span "Ympäristöstä: " (<sub [::subs/import-env])]
           [impl/render-items target items]])
        [ie-components/replace-checkbox]
        [ie-components/import-button]
        (when-let [items (<sub [::subs/post-import-items])]
          [:<>
           [:h2 "Tulokset:"]
           [impl/render-items target items]])])]))

(defmethod render-mode :import
  [_]
  [:<>
   [:h2 "Valitse tiedosto (.json)"]
   [ie-components/import-file]
   (when (and (<sub [::subs/import-uploaded?])
              (not (<sub [::subs/import-errors?])))
     (when (<sub [::subs/selected-target])
       [:<>
        [target-buttons]
        [render-import-preview]]))])

(defn- select-all
  []
  (let [id "import-export-select-all"]
    [:div
     [:label {:for id}
      (common/loc :select-all)]
     [ui-input/checkbox
      {:id        id
       :checked?  (<sub [::subs/select-all?])
       :on-change #(>evt [::subs/select-all-clicked])}]]))

(defn- render-export
  "Show organization selector and allow user to choose what items to export"
  []
  (when-let [target (<sub [::subs/selected-target])]
    [:div.flex.flex--column.flex--gap2
     [:h2 "Valitse organisaatio"]
     [ie-components/organization-selector]
     (when (<sub [::subs/selected-organization])
       [:<>
        [:h2 "Valitse vietävät tiedot"]
        [select-all]
        [impl/select-items target]])
     [ie-components/export-button]]))

(defmethod render-mode :export
  [_]
  [:<>
   [target-buttons]
   [render-export]])

(defn- mode-buttons
  []
  [:div.flex
   [components/toggle-group
    (if-let [mode (<sub [::subs/selected-mode])]
      #{mode} #{})
    {:items     mode-items
     :prefix    :person-vs-company
     :disabled? false
     :radio?    true
     :callback  #(>evt [::subs/mode-selected %])}]])

(defn view
  []
  (when (<sub [::subs/visible?])
    [:div.flex.flex--column
     [:h1 (common/loc :admin.import-export)]
     [:div.flex.flex--column.flex--gap2
      [mode-buttons]
      (when-let [mode (<sub [::subs/selected-mode])]
        [render-mode mode])]]))

(defonce mounter (ui-dom/init :reagent (fn [] view)))

(defn ^:export start [dom-id _]
  (ui-dom/mount! mounter dom-id))
