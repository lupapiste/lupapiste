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

(defn last-saved-header [options]
  [:div.matti-grid-1.matti-settings-last-saved
   [:div.row.row--tight
    [:div.col-1.col--right
     (layout/last-saved (assoc options :state state/settings))]]])

(rum/defc verdict-template-settings < rum/reactive
  [{:keys [title sections] :as options}]
  [:div
   [:h2 (common/loc :matti-settings (common/loc title))]
   (for [{id :id :as sec} sections]
     (sections/section (assoc sec
                              :path [id]
                              :state state/settings)
                       (case (keyword id)
                         :verdict (partial last-saved-header options)
                         #())))])
