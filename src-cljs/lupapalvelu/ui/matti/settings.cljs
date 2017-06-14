(ns lupapalvelu.ui.matti.settings
  (:require [lupapalvelu.matti.shared :as shared]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.matti.layout :as layout]
            [lupapalvelu.ui.matti.path :as path]
            [lupapalvelu.ui.matti.service :as service]
            [rum.core :as rum]))

(defn settings-updater [{:keys [path state]}]
  (service/save-settings-value (path/value [:category] state)
                               path
                               (path/value path state)
                               (common/response->state state :modified)))

(defonce settings* (atom {}))

(defn fetch-settings [category]
  (service/settings category
                    (fn [{settings :settings}]
                      (reset! settings*
                              (assoc (:draft settings)
                                     :modified (:modified settings)
                                     :category category
                                     :_meta {:updated settings-updater})))))

(rum/defc verdict-template-settings < rum/reactive
  [{id :id :as options}]
  (let [options (assoc options
                       :path [id]
                       :state settings*)]
    [:div
     [:div.matti-grid-2
      [:div.row
       [:div.col-1
        [:h2 (common/loc id)]]
       [:div.col-1.col--right
        (layout/last-saved options)]]]
     (layout/matti-grid (shared/child-schema options
                                             :grid
                                             options))]))
