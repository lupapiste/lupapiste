(ns lupapalvelu.ui.auth-admin.imports.preview
  "Previews table for a draft configuration. Each preview cell shows how the value would be
  rendered according to the corresponding column type or an error."
  (:require [lupapalvelu.next.event :refer [<sub]]
            [lupapalvelu.pate.path :as path]
            [lupapalvelu.ui.auth-admin.imports.data.config :as dcfg]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.components :as components]
            [lupapalvelu.ui.components.day-edit :refer [day-edit]]
            [sade.util :as util]))

(defn- error-state? [{:keys [state]}]
  (util/=as-kw state :error))

(defn- text-preview
  [{:keys [lines]} {:keys [state value raw] :as preview}]
  [components/simple-text-edit (cond-> {:value     value
                                        :class     :w--min-15em
                                        :read-only true}
                                 (some-> lines (> 1))
                                 (assoc :lines lines)
                                 (error-state? preview)
                                 (assoc :value raw :invalid? true))])

(defn- number-preview [{unit :unit} preview]
  (let [txt    [text-preview nil preview]
        unitxt (some->> unit (path/loc :unit))
        elem   :div.flex.flex--gap1.flex--align-center]
    (case (keyword unit)
      nil      txt
      :section [elem unitxt txt]
      [elem txt unitxt])))

(defn- date-preview [{:keys [value] :as preview}]
  (if (error-state? preview)
    [text-preview nil preview]
    [day-edit value {:read-only? true :invalid? false}]))

(defn previews []
  (let [previews   (<sub [::dcfg/previews])
        column-ids (<sub [::dcfg/sorted-column-ids])]
    (when (and previews column-ids)
      [:div.flex--column.flex--gap1
       [:h3 (common/loc :imports.previews.title)]
       [:div {:style {:width      :fit-content
                      :max-width  "100%"
                      :overflow-x :scroll}}
        [:table {:style {:width :fit-content}}
         [:thead
          (into [:tr] (for [col-id column-ids]
                        [:th (<sub [::dcfg/titlestring col-id])]))]
         (into [:tbody]
               (for [p previews]
                 (into [:tr]
                       (for [col-id column-ids
                             :let   [col-type (<sub [::dcfg/column col-id :type])
                                     preview (get p col-id)]]
                         [:td {:style {:vertical-align :top}}
                          (case (keyword (:type col-type))
                            :number [number-preview col-type preview]
                            :date   [date-preview preview]
                            [text-preview col-type preview])]))))]]])))
