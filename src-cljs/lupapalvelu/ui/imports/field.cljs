(ns lupapalvelu.ui.imports.field
  "Import components for individuall fields/cells. Used both by the authority admin row
  table and application section. Each component has both viewer and editor modes.

  Note that invalid values (e.g., bad dates or numbers) are _never_ stored as
  values. Thus, the only persistent errors are import errors during the initial importing."
  (:require [lupapalvelu.next.event :refer [<sub >evt]]
            [lupapalvelu.pate.path :as path]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.components :as components]
            [lupapalvelu.ui.components.day-edit :refer [day-edit]]
            [lupapalvelu.ui.date :as date]
            [lupapalvelu.ui.imports.data.core :as idata]
            [sade.strings :as ss]
            [sade.util :as util]))

(defn- date-cell
  [row-id cell-id]
  (let [editing?           (<sub [::idata/editing? row-id])
        deleted?           (<sub [::idata/row-deleted? row-id])
        {:keys [original]} (<sub [::idata/cell row-id cell-id])
        value              (<sub [::idata/cell-value row-id cell-id])
        error?             (boolean (<sub [::idata/cell-error row-id cell-id]))]
    (cond
      editing? [day-edit value {:id        cell-id
                                :disabled? deleted?
                                :callback  #(>evt [::idata/edit-cell row-id cell-id %])
                                :invalid?  error?}]
      error?   [:span.bd--negative {:id cell-id} (or value
                                                     (:value original)
                                                     (:raw original))]
      :else    (some->> value
                        date/finnish-date
                        (conj [:span {:id cell-id}])))))

(defn- text-cell [row-id cell-id]
  (let [editing?           (<sub [::idata/editing? row-id])
        deleted?           (<sub [::idata/row-deleted? row-id])
        {:keys [original]} (<sub [::idata/cell row-id cell-id])
        column             (<sub [::idata/cell-column row-id cell-id])
        lines              (some-> column :type :lines)
        other?             (some-> column :type :type (util/not=as-kw :text))
        wrap-cls           (if other? :whitespace-nowrap :whitespace-pre-line)
        value              (<sub [::idata/cell-value row-id cell-id])
        error?             (boolean (<sub [::idata/cell-error row-id cell-id]))]
    (cond
      editing?              [components/text-edit value
                             {:id       cell-id
                              :class    [:w--min-20em]
                              :disabled deleted?
                              :callback #(>evt [::idata/edit-cell row-id cell-id %])
                              :invalid? error?
                              :lines    lines}]
      error?                [:span.bd--negative {:class wrap-cls :id cell-id}
                             (or value
                                 (:value original)
                                 (:raw original))]
      (ss/not-blank? value) [:span {:class wrap-cls :id cell-id} value])))

(defn- number-cell [row-id cell-id]
  (let [column (<sub [::idata/cell-column row-id cell-id])
        unit   (some-> column :type :unit keyword)
        unitxt (some->> unit (path/loc :unit))
        txt    (text-cell row-id cell-id)
        elem   :div.flex.flex--gap1.flex--align-center]
    (when txt
      (case unit
        nil      txt
        :section [elem unitxt txt]
        [elem txt unitxt]))))

(defn column-title
  "Column title string. Fallbacks to Finnish, if the current language is not supported."
  [{:keys [title]}]
  (let [lang (keyword (common/get-current-language))]
    (or (some-> title lang ss/blank-as-nil)
        (:fi title))))

(defn field
  "Renders the correct component (in the correct) mode for the given cell. If `label?`
  option is true, then the component is rendered with label (default false). The authority
  admin row table does not use labels, but the application section does. Blank viewers are
  not rendered."
  [row-id cell-id {:keys [label?]}]
  (let [column (<sub [::idata/cell-column row-id cell-id])
        elem   (case (-> column :type :type keyword)
                 :date   (date-cell row-id cell-id)
                 :number (number-cell row-id cell-id)
                 (text-cell row-id cell-id))]
    (if (and elem label?)
      [:div.flex--column
       [:label.lux.txt--bold {:for cell-id} (column-title column)]
       elem]
      elem)))
