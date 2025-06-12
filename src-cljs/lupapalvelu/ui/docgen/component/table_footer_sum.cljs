(ns lupapalvelu.ui.docgen.component.table-footer-sum
  "See `docgen-footer-sum-[model.js|template.html]`"
  (:require [lupapalvelu.next.event :refer [<sub]]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.docgen.component.footer-sum :as footer-sum]
            [lupapalvelu.ui.docgen.feature.value :as value]
            [lupapalvelu.ui.docgen.field.docgen-input-common :as di]))

(def unit-keys {:kg     :unit.kg
                :t      :unit.tonnia
                :tonnia :unit.tonnia
                :m2     :unit.m2})

(defn footer-sum-value
  "Calculate the sum total of a column in the appropriate unit."
  [{:keys [doc-id footer path subschema]}]
  (let [args   {:footer      footer
                :subschema   subschema
                ;; The sum calculation doesn't really care about `re-frame`,
                ;; as long as it has a mechanism for finding out the values of a column.
                :column-data (fn [column]
                               (<sub [::value/current-repeating-value doc-id path column]))}
        unit   (footer-sum/footer-unit args)
        amount (footer-sum/footer-amount args unit)]
    (if (= ::footer-sum/invalid amount)
      [:i.footer-sum--error.lupicon-warning]
      (let [result (str (di/truncate amount) " " (common/loc (get unit-keys unit)))]
        [:span.footer-sum--result
         result]))))
