(ns lupapalvelu.ui.pate.settings
  (:require [lupapalvelu.pate.path :as path]
            [lupapalvelu.pate.schema-helper :as helper]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.components :as components]
            [lupapalvelu.ui.pate.components :as pate-components]
            [lupapalvelu.ui.pate.sections :as sections]
            [lupapalvelu.ui.pate.service :as service]
            [lupapalvelu.ui.pate.state :as state]
            [rum.core :as rum]))

(defn settings-updater [{:keys [path state info] :as options}]
  (service/save-settings-value (path/value [:category] info)
                               path
                               (path/value path state)
                               (service/update-changes-and-errors state/settings-info
                                                                  options)))

(defn fetch-settings [category]
  (service/settings category
                    (fn [{:keys [settings filled]}]
                      (reset! state/settings (:draft settings))
                      (reset! state/settings-info
                              {:info  {:modified (:modified settings)
                                       :category category
                                       :filled? (boolean filled)}
                               :_meta {:updated  settings-updater
                                       :editing? true
                                       :enabled? (state/auth? :save-verdict-template-settings-value)}}))))

(defn name-edit [initial callback]
  (components/text-edit initial {:callback  callback
                                 :required? true}))

(rum/defcs type-edit < (components/initial-value-mixin ::selection)
  rum/reactive
  [local-state _ callback]
  (let [selection* (::selection local-state)]
    [:select.dropdown
     {:value     (rum/react selection*)
      :on-change (fn [event]
                   (let [value (.. event -target -value)]
                     (callback value)
                     (reset! selection* value)))}
     (->> helper/review-type-map
          keys
          (map (fn [k]
                 {:text (common/loc (str "pate.review-type." (name k)))
                  :value k}))
          (sort-by :text)
          (map (fn [{:keys [value text]}]
                 [:option {:key value :value value} text])))]))


;; -------------------------
;; Settings sections
;; -------------------------

(defmethod sections/section-header :settings [{:keys [schema] :as options} _]
  [:div.pate-grid-6.section-header
   [:div.row.row--tight
    [:div.col-4
     [:span.pate-label
      {:class (common/css-flags :required (:required? schema))}
      (path/loc options)]]]])

(rum/defc verdict-template-settings < rum/reactive
  [{:keys [schema] :as options}]
  [:div.pate-settings
   [:div.pate-grid-4
    [:div.row.row--tight
     [:div.col-2
      [:h2.pate-settings-title (common/loc :pate-settings
                                            (common/loc (:title schema)))]]
     [:div.col-1
      (pate-components/required-fields-note (assoc options
                                                   :test-id :settings-missing))]
     [:div.col-1.col--right
      (pate-components/last-saved options)]]]
   (sections/sections options :settings)])
