(ns lupapalvelu.ui.docgen.field.huoneistot.highlight
  "The 'show only edited rows' functionality.

  User can choose to only display edited rows.
  In original docgen this was called `showChangesOnly`."
  (:require [lupapalvelu.next.event :refer [<sub >evt]]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.components.input :as ui-input]
            [lupapalvelu.ui.docgen.feature.documents :as docs]
            [lupapalvelu.ui.docgen.field.huoneistot.cells-common :as cells-common]
            [lupapalvelu.ui.docgen.field.huoneistot.common :as huoneistot-common]
            [re-frame.core :as rf]
            [sade.util :as util]))

(defn unload-data
  [db]
  (dissoc db ::data))

(rf/reg-sub ::data :-> ::data)

(rf/reg-event-fx ::show-only-edited-clicked
  (fn show-only-edited-clicked-inputfn [{:keys [db]} [_ doc-id enabled?]]
    {:pre [(string? doc-id) (boolean? enabled?)]}
    {:db (assoc-in db [::data doc-id :show-only-edited?] enabled?)}))

(rf/reg-sub ::show-only-edited?
  :<- [::data]
  (fn [data [_ doc-id]]
    (boolean (get-in data [doc-id :show-only-edited?]))))

(rf/reg-sub ::modified-row-count
  (fn [[_ doc-id table-path]]
    (rf/subscribe [::docs/field-child-keys doc-id table-path]))
  (fn [row-ids [_ doc-id table-path]]
    (->> row-ids
         (filter (fn [row-id]
                   (let [row-path (conj table-path row-id)]
                     (<sub [::cells-common/row-differs-from-original? doc-id row-path]))))
         count)))

(rf/reg-sub ::show-only-edited-label
  (fn [[_ doc-id table-path]]
    (rf/subscribe [::modified-row-count doc-id table-path]))
  (fn [modified-count]
    (common/loc :huoneistot.showChangesOnly modified-count)))

(defn- show-only-edited-checkbox
  "Show only edited rows vs all rows."
  [{:keys [doc-id table-path]}]
  (let [id (str doc-id "-show-changed-only")]
    [:div.checkbox-wrapper.show-changes
     [ui-input/checkbox
      {:id        id
       :class     "always-enabled"
       :checked?  (<sub [::show-only-edited? doc-id])
       :on-change (fn [val] (>evt [::show-only-edited-clicked doc-id (boolean val)]))}]
     [:label.inline-label.checkbox-label.show-changes-label
      {:for         id
       :aria-hidden true}
      (<sub [::show-only-edited-label doc-id table-path])]]))

;; Highlight added / modified / removed data in UI
(rf/reg-sub ::highlight-changes?
  (fn highlight-changes-inputfn [[_ doc-id allow-highlight-changes?]]
    {:pre [(string? doc-id) (boolean? allow-highlight-changes?)]}
    (rf/subscribe [::huoneistot-common/change-permit?]))
  (fn [change-permit? [_ _doc-id allow-highlight-changes?]]
    (or change-permit?
        allow-highlight-changes?)))

(defn- legend-item
  [{:keys [class text]}]
  [:div.legend-item
   [:span.legend-rectangle {:class class}]
   [:p.legend-text (common/loc text)]])

(defn changes-container
  "Displays a legend for the table"
  [{:keys [doc-id table-path allow-highlight-changes?]}]
  (when (<sub [::highlight-changes? doc-id allow-highlight-changes?])
    [:div.show-changes-container
     [:p.legend-description
      (common/loc :huoneistot.legendDescription)]
     [legend-item {:class :legend-new :text :huoneistot.legendNew}]
     [legend-item {:class :legend-edited :text :huoneistot.legendEdit}]
     [legend-item {:class :legend-removed :text :huoneistot.legendRemoval}]
     [show-only-edited-checkbox (util/mmap doc-id table-path)]]))
