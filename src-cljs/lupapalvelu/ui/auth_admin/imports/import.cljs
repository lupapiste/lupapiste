(ns lupapalvelu.ui.auth-admin.imports.import
  (:require [lupapalvelu.next.event :refer [<sub >evt]]
            [lupapalvelu.ui.auth-admin.imports.data.config :as dcfg]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.components :as components]))

(defn progress []
  (when-let [percentage (<sub [::dcfg/import-progress])]
    [:div.bd--gray.rounded-xl.w--max-50em
     [:div.bg-blue-600
      {:class (if (= 100 percentage) :rounded-xl :rounded-l-xl)
       :style {:width  (str percentage "%")
               :height "2em"}}]]))

(defn importer []
  (when-not (<sub [::dcfg/imported?])
    [:div.flex--column.flex--gap1
     [:h3 (common/loc :imports.import.title)]
     (if (<sub [::dcfg/importing?])
       [progress]
       (let [confirmation (common/loc :imports.import.confirm
                                      (<sub [::dcfg/total-rows]))]
         [components/icon-button
          {:text-loc :imports.import.button
           :class    :primary.flex--self-start
           :enabled? (<sub [::dcfg/can-start-import?])
           :icon     :lupicon-document-list
           :on-click (fn [_]
                       (>evt [:dialog/show {:text confirmation
                                            :type     :yes-no
                                            :callback #(>evt [::dcfg/execute-import])}]))}]))]))
