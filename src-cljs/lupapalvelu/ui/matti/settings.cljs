(ns lupapalvelu.ui.matti.settings
  (:require [lupapalvelu.matti.shared :as shared]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.matti.path :as path]
            [lupapalvelu.ui.matti.sections :as sections]
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
     [:h2 (common/loc id)]
     (sections/matti-grid (shared/child-schema options
                                               :grid
                                               options))]))
