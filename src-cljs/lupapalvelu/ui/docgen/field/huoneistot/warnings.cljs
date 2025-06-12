(ns lupapalvelu.ui.docgen.field.huoneistot.warnings
  "Display warnings per row"
  (:require [lupapalvelu.next.event :refer [<sub]]
            [lupapalvelu.ui.docgen.component.warnings :as warnings]
            [lupapalvelu.ui.docgen.feature.documents :as docs]
            [lupapalvelu.ui.docgen.field.huoneistot.columns :as columns]
            [re-frame.core :as rf]
            [sade.util :as util]))

(rf/reg-sub ::column-order-map
  (fn [[_ doc-id table-path]]
    (rf/subscribe [::columns/columns doc-id table-path]))
  (fn [columns]
    (->> columns
         (map-indexed (fn [idx item]
                        [(name (:column-name item)) idx]))
         (into {}))))

(rf/reg-sub ::column-comparator
  (fn [[_ doc-id table-path]]
    (rf/subscribe [::column-order-map doc-id table-path]))
  (fn [column-order-map]
    (fn [a b]
      (compare (get column-order-map a) (get column-order-map b)))))

(defn row-warnings
  "Display any warnings related to a row in the column order."
  [{:keys [doc-id row-path schema]}]
  (let [doc-i18n-name  (<sub [::docs/i18n-name doc-id])
        {:keys [body]} schema]
    (when-let [warnings (warnings/body->warnings
                          (util/mmap {:path row-path} doc-id body doc-i18n-name))]
      (let [warning->column (comp name peek :path)
            comparator      (<sub [::column-comparator doc-id (pop row-path)])]
        [:tr
         [:td
          {:colSpan (count (<sub [::columns/columns doc-id (pop row-path)]))}
          [warnings/warnings {:warnings (sort-by warning->column
                                                 comparator
                                                 warnings)}]]]))))
