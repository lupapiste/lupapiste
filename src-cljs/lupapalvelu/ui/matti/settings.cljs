(ns lupapalvelu.ui.matti.settings
  (:require [lupapalvelu.matti.shared :as shared]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.matti.layout :as layout]
            [lupapalvelu.ui.matti.path :as path]
            [lupapalvelu.ui.matti.sections :as sections]
            [lupapalvelu.ui.matti.service :as service]
            [lupapalvelu.ui.matti.state :as state]
            [rum.core :as rum]))

(defn settings-updater [{:keys [path state]}]
  (service/save-settings-value (path/value [:category] state)
                               path
                               (path/value path state)
                               (common/response->state state :modified)))

(defn fetch-settings [category]
  (service/settings category
                    (fn [{settings :settings}]
                      (reset! state/settings
                              (assoc (:draft settings)
                                     :modified (:modified settings)
                                     :category category
                                     :_meta {:updated settings-updater})))))

(defn settings-section-header [edit? {:keys [path schema]}]
  [:div.matti-grid-6.section-header
   [:div.row.row--tight
    [:div.col-4
     [:span.matti-label
      (when edit? {:class :row-text})
      (common/loc (str "matti-settings."
                                                  (-> path first name)))]]
    (when edit?
      [:div.col-2.col--right
       [:button.ghost (common/loc :edit)]])]])

(rum/defc verdict-template-settings < rum/reactive
  [{:keys [title sections] :as options}]
  [:div.matti-settings
   [:div.matti-grid-2
    [:div.row.row--tight
     [:div.col-1
      [:h2.matti-settings-title (common/loc :matti-settings
                                            (common/loc title))]]
     [:div.col-1.col--right
      (layout/last-saved (assoc options :state state/settings))]]]
   (for [{id :id :as sec} sections]
     (sections/section (assoc sec
                              :path [id]
                              :state state/settings)
                       (partial settings-section-header
                                (contains? #{:reviews :plans}
                                           (keyword id)))))])
